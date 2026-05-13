package org.example.suppcheck.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.example.suppcheck.dto.IngredientDto;
import org.example.suppcheck.dto.OcrResult;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * OCR service that delegates to the system-installed {@code tesseract} binary.
 *
 * <p>Requires {@code tesseract-ocr} (+ language pack {@code tesseract-ocr-deu}) to be installed.
 * In Docker this is provided via the Dockerfile; locally via
 * {@code brew install tesseract} (macOS) or the OS package manager.</p>
 *
 * <h3>Multi-image support</h3>
 * <p>When several images of the same label are uploaded (e.g. one wide-angle and one
 * zoomed-in shot for small print), OCR is run on each independently.  The resulting
 * ingredient lists are then merged; duplicate entries — identified by
 * case-insensitive name comparison — are dropped, keeping the richer occurrence
 * (non-zero mg wins over zero, extra sub-ingredients are carried over).</p>
 */
@Service
public class OcrService {

    private static final int TIMEOUT_SECONDS = 30;

    private final IngredientTranslationService translationService;

    public OcrService(IngredientTranslationService translationService) {
        this.translationService = translationService;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Runs Tesseract OCR on all uploaded images, then merges and deduplicates
     * the parsed ingredient lists.
     *
     * @param files one or more uploaded images (JPEG, PNG, TIFF, …)
     * @return merged OCR result; empty result if every file is empty/null
     * @throws IOException          on I/O errors with temp files
     * @throws InterruptedException if a Tesseract process is interrupted
     */
    public OcrResult extractIngredients(List<MultipartFile> files)
            throws IOException, InterruptedException {

        List<OcrResult> results = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                results.add(extractSingle(file));
            }
        }

        if (results.isEmpty()) {
            return new OcrResult("", List.of());
        }
        if (results.size() == 1) {
            return results.get(0);
        }
        return mergeResults(results);
    }

    /**
     * Returns a human-readable description of the installed Tesseract version,
     * or an error message if Tesseract is not found.
     */
    public String getTesseractVersion() {
        try {
            ProcessBuilder pb = new ProcessBuilder("tesseract", "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.waitFor(5, TimeUnit.SECONDS);
            String out = new String(p.getInputStream().readAllBytes());
            return out.lines().findFirst().orElse("unknown").trim();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return "not available: " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // Merge logic (package-private for unit testing)
    // -------------------------------------------------------------------------

    /**
     * Merges multiple {@link OcrResult} objects into one.
     *
     * <ul>
     *   <li>Raw texts are concatenated with an image-separator marker.</li>
     *   <li>Ingredient lists are merged; duplicates (same name, case-insensitive)
     *       are resolved by keeping the entry with the highest information content:
     *       a non-zero mg value wins over zero, and missing sub-ingredients from
     *       later images are appended.</li>
     * </ul>
     */
    static OcrResult mergeResults(List<OcrResult> results) {
        StringBuilder combinedRaw = new StringBuilder();
        List<IngredientDto> merged = new ArrayList<>();
        // key (normalised name) → index in merged list
        Map<String, Integer> seen = new LinkedHashMap<>();

        for (int i = 0; i < results.size(); i++) {
            OcrResult r = results.get(i);
            if (i > 0) {
                combinedRaw.append("\n\n--- [Bild ").append(i + 1).append("] ---\n");
            }
            combinedRaw.append(r.getRawText());

            for (IngredientDto ing : r.getIngredients()) {
                mergeIngredient(ing, merged, seen);
            }
        }
        return new OcrResult(combinedRaw.toString(), merged);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Runs Tesseract on a single file and returns the raw + parsed result.
     */
    private OcrResult extractSingle(MultipartFile file)
            throws IOException, InterruptedException {

        String original = file.getOriginalFilename();
        String ext = (original != null && original.contains("."))
                ? original.substring(original.lastIndexOf('.'))
                : ".png";

        Path tempInput = Files.createTempFile("ocr-in-", ext);
        Path tempOutputBase = Files.createTempFile("ocr-out-", "");

        try {
            file.transferTo(tempInput.toFile());

            ProcessBuilder pb = new ProcessBuilder(
                    "tesseract",
                    tempInput.toAbsolutePath().toString(),
                    tempOutputBase.toAbsolutePath().toString(),
                    "-l", "deu",
                    "--psm", "6"   // uniform block: reads row-by-row, not column-by-column
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Tesseract timed out after " + TIMEOUT_SECONDS + " seconds");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String error = new String(process.getInputStream().readAllBytes());
                throw new IOException("Tesseract exited with code " + exitCode + ": " + error);
            }

            Path txtOutput = Path.of(tempOutputBase + ".txt");
            String ocrText = Files.readString(txtOutput);
            Files.deleteIfExists(txtOutput);

            List<IngredientDto> ingredients = OcrTextParser.parse(ocrText);
            translationService.translateAll(ingredients);
            return new OcrResult(ocrText, ingredients);

        } finally {
            Files.deleteIfExists(tempInput);
            Files.deleteIfExists(tempOutputBase);
        }
    }

    /**
     * Merges a single candidate ingredient into the running list.
     *
     * <p>If the name already exists, the existing entry is upgraded with better
     * data from the candidate (non-zero mg, new sub-ingredients) rather than
     * replacing it entirely.</p>
     */
    private static void mergeIngredient(IngredientDto candidate,
                                        List<IngredientDto> merged,
                                        Map<String, Integer> seen) {
        String key = normalizeKey(candidate.getName());
        if (seen.containsKey(key)) {
            // Duplicate: upgrade existing if candidate carries better data
            IngredientDto existing = merged.get(seen.get(key));
            if (existing.getMg() == 0.0 && candidate.getMg() > 0.0) {
                existing.setMg(candidate.getMg());
            }
            // Append sub-ingredients that are missing from the existing entry
            for (IngredientDto sub : candidate.getSubIngredients()) {
                String subKey = normalizeKey(sub.getName());
                boolean present = existing.getSubIngredients().stream()
                        .anyMatch(s -> normalizeKey(s.getName()).equals(subKey));
                if (!present) {
                    existing.getSubIngredients().add(sub);
                }
            }
        } else {
            seen.put(key, merged.size());
            merged.add(candidate);
        }
    }

    private static String normalizeKey(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).trim();
    }
}
