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
 * <h3>Image preprocessing</h3>
 * <p>Before handing an image to Tesseract, this service optionally pre-processes it
 * with ImageMagick ({@code convert}): the image is upscaled to at least 2000 px on
 * the short edge (only if currently smaller) and converted to grayscale.  This
 * significantly improves OCR accuracy on blurry or low-resolution label photos.
 * If ImageMagick is not available, the original image is used unchanged.</p>
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

    /** ImageMagick command name (v6 on Debian/Ubuntu uses {@code convert}). */
    static final String IMAGEMAGICK_CMD = "convert";

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
        // per100g = true when ANY of the merged results detected a per-100g-only table
        boolean per100g = results.stream().anyMatch(OcrResult::isPer100g);
        return new OcrResult(combinedRaw.toString(), merged, per100g);
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
        Path preprocessed = null;

        try {
            file.transferTo(tempInput.toFile());

            preprocessed = preprocessWithImageMagick(tempInput);
            Path ocrInput = (preprocessed != null) ? preprocessed : tempInput;

            ProcessBuilder pb = new ProcessBuilder(
                    "tesseract",
                    ocrInput.toAbsolutePath().toString(),
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
            boolean per100g = OcrTextParser.detectPer100g(ocrText);
            return new OcrResult(ocrText, ingredients, per100g);

        } finally {
            Files.deleteIfExists(tempInput);
            Files.deleteIfExists(tempOutputBase);
            if (preprocessed != null) {
                Files.deleteIfExists(preprocessed);
            }
        }
    }

    /**
     * Pre-processes an image with ImageMagick before OCR:
     * <ul>
     *   <li>Upscales to at least 2000 px on the long edge (only if currently smaller)</li>
     *   <li>Converts to grayscale</li>
     *   <li>Applies a mild unsharp mask to compensate for blur</li>
     * </ul>
     *
     * @param input path to the original image
     * @return path to the preprocessed PNG temp file, or {@code null} if ImageMagick
     *         is not available (caller should fall back to the original image)
     */
    Path preprocessWithImageMagick(Path input) throws IOException, InterruptedException {
        Path output = Files.createTempFile("ocr-pre-", ".png");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    IMAGEMAGICK_CMD,
                    input.toAbsolutePath().toString(),
                    "-resize", "2000x2000<",   // enlarge only if smaller than 2000px
                    "-colorspace", "Gray",
                    output.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean done = p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                Files.deleteIfExists(output);
                return null;
            }
            if (p.exitValue() != 0) {
                Files.deleteIfExists(output);
                return null;
            }
            return output;
        } catch (IOException e) {
            // ImageMagick not installed — silently fall back to original
            Files.deleteIfExists(output);
            return null;
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
