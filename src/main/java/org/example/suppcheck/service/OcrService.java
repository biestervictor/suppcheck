package org.example.suppcheck.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.example.suppcheck.dto.IngredientDto;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * OCR service that delegates to the system-installed {@code tesseract} binary.
 *
 * <p>Requires {@code tesseract-ocr} (+ language pack {@code tesseract-ocr-deu}) to be installed.
 * In Docker this is provided via the Dockerfile; locally via
 * {@code brew install tesseract} (macOS) or the OS package manager.</p>
 */
@Service
public class OcrService {

    private static final int TIMEOUT_SECONDS = 30;

    /**
     * Runs Tesseract OCR on the uploaded image and returns a parsed list of
     * ingredient entries found on the nutrition label.
     *
     * @param file the uploaded image (JPEG, PNG, TIFF, …)
     * @return list of detected ingredients; empty if nothing could be parsed
     * @throws IOException          on I/O errors with temp files
     * @throws InterruptedException if the Tesseract process is interrupted
     */
    public List<IngredientDto> extractIngredients(MultipartFile file)
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
                    "-l", "deu"
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

            return OcrTextParser.parse(ocrText);

        } finally {
            Files.deleteIfExists(tempInput);
            Files.deleteIfExists(tempOutputBase);
        }
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
}
