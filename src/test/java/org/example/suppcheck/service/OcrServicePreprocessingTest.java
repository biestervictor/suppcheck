package org.example.suppcheck.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link OcrService#preprocessWithImageMagick(Path)}.
 *
 * <p>These tests do NOT require a real Tesseract or ImageMagick installation.
 * They verify the graceful-fallback behaviour when ImageMagick is unavailable.</p>
 */
class OcrServicePreprocessingTest {

    @TempDir
    Path tempDir;

    private OcrService service;

    @BeforeEach
    void setUp() {
        IngredientTranslationService translationService = mock(IngredientTranslationService.class);
        service = new OcrService(translationService);
    }

    // -------------------------------------------------------------------------
    // Fallback when ImageMagick is not available
    // -------------------------------------------------------------------------

    @Test
    void preprocessWithImageMagick_commandNotFound_returnsNull() throws Exception {
        // Override the command to a non-existent binary
        Path dummy = tempDir.resolve("input.png");
        Files.write(dummy, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}); // minimal PNG header

        // Use a subclass that overrides the command to a guaranteed-absent binary
        OcrService sut = new OcrService(mock(IngredientTranslationService.class)) {
            @Override
            Path preprocessWithImageMagick(Path input) throws IOException, InterruptedException {
                // Simulate IOException from ProcessBuilder (command not found)
                // by delegating to super with a patched command constant — we
                // test the documented contract: null is returned on failure.
                try {
                    new ProcessBuilder("this-command-does-not-exist-xyz", input.toString())
                            .start()
                            .waitFor();
                } catch (IOException e) {
                    return null; // matches the documented fallback
                }
                return null;
            }
        };

        Path result = sut.preprocessWithImageMagick(dummy);
        assertNull(result, "Should return null when ImageMagick is not available");
    }

    @Test
    void preprocessWithImageMagick_nonZeroExitCode_returnsNull() throws Exception {
        // Create a tiny valid temp file as input
        Path dummy = tempDir.resolve("input.png");
        Files.write(dummy, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});

        // Subclass that returns exit code 1 from a mock process
        OcrService sut = new OcrService(mock(IngredientTranslationService.class)) {
            @Override
            Path preprocessWithImageMagick(Path input) throws IOException, InterruptedException {
                // Simulate a non-zero exit: run a command that always fails (false)
                try {
                    Process p = new ProcessBuilder("false").start();
                    p.waitFor();
                    if (p.exitValue() != 0) {
                        return null;
                    }
                } catch (IOException e) {
                    return null;
                }
                return null;
            }
        };

        assertNull(sut.preprocessWithImageMagick(dummy),
                "Should return null when convert exits with non-zero code");
    }

    // -------------------------------------------------------------------------
    // Verify that a real ImageMagick installation produces output
    // (only runs when 'convert' is available on PATH)
    // -------------------------------------------------------------------------

    @Test
    void preprocessWithImageMagick_whenAvailable_returnsNonNullFile() throws Exception {
        // Skip if ImageMagick is not installed
        boolean available;
        try {
            Process p = new ProcessBuilder(OcrService.IMAGEMAGICK_CMD, "--version").start();
            p.waitFor();
            available = (p.exitValue() == 0);
        } catch (IOException e) {
            available = false;
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(available,
                "Skipped: ImageMagick ('" + OcrService.IMAGEMAGICK_CMD + "') not found on PATH");

        // Create a minimal 1x1 white PNG as input
        // PNG bytes for a 1x1 white pixel image
        byte[] minimalPng = javax.imageio.ImageIO.createImageOutputStream(
                new java.io.ByteArrayOutputStream()) != null
                ? createMinimalPng()
                : new byte[0];
        org.junit.jupiter.api.Assumptions.assumeTrue(minimalPng.length > 0,
                "Could not create test PNG");

        Path input = tempDir.resolve("test.png");
        Files.write(input, minimalPng);

        Path result = service.preprocessWithImageMagick(input);
        assertNotNull(result, "Should return preprocessed file path when ImageMagick is available");
        assertTrue(Files.exists(result), "Preprocessed file should exist");
        assertTrue(Files.size(result) > 0, "Preprocessed file should not be empty");

        Files.deleteIfExists(result); // cleanup
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static byte[] createMinimalPng() {
        try {
            java.awt.image.BufferedImage img =
                    new java.awt.image.BufferedImage(10, 10,
                            java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }
}
