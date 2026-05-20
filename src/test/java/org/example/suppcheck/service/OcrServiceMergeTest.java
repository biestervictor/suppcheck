package org.example.suppcheck.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.example.suppcheck.dto.IngredientDto;
import org.example.suppcheck.dto.OcrResult;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the multi-image merge logic in {@link OcrService}.
 *
 * <p>These tests exercise {@link OcrService#mergeResults(List)} directly
 * (package-private) without requiring a real Tesseract installation.</p>
 */
class OcrServiceMergeTest {

    // -------------------------------------------------------------------------
    // Single result — no merging needed
    // -------------------------------------------------------------------------

    @Test
    void mergeResults_singleResult_returnsSameResult() {
        IngredientDto dto = ingredient("Protein", 24_000);
        OcrResult input = new OcrResult("raw", List.of(dto));

        OcrResult result = OcrService.mergeResults(List.of(input));

        assertEquals("raw", result.getRawText());
        assertEquals(1, result.getIngredients().size());
        assertEquals("Protein", result.getIngredients().getFirst().getName());
    }

    // -------------------------------------------------------------------------
    // Deduplication — same ingredient in both images
    // -------------------------------------------------------------------------

    @Test
    void mergeResults_duplicateName_keptOnce() {
        OcrResult img1 = new OcrResult("img1", List.of(ingredient("L-Leucin", 2100)));
        OcrResult img2 = new OcrResult("img2", List.of(ingredient("L-Leucin", 2100)));

        OcrResult result = OcrService.mergeResults(List.of(img1, img2));

        assertEquals(1, result.getIngredients().size());
        assertEquals("L-Leucin", result.getIngredients().getFirst().getName());
        assertEquals(2100.0, result.getIngredients().getFirst().getMg(), 0.001);
    }

    @Test
    void mergeResults_duplicateNameCaseInsensitive_keptOnce() {
        // Different capitalisation still counts as same ingredient
        OcrResult img1 = new OcrResult("img1", List.of(ingredient("Vitamin D3", 0.01)));
        OcrResult img2 = new OcrResult("img2", List.of(ingredient("vitamin d3", 0.01)));

        OcrResult result = OcrService.mergeResults(List.of(img1, img2));

        assertEquals(1, result.getIngredients().size());
    }

    @Test
    void mergeResults_duplicateNameWithLeadingTrailingSpaces_keptOnce() {
        OcrResult img1 = new OcrResult("img1", List.of(ingredient("Magnesium", 75)));
        OcrResult img2 = new OcrResult("img2", List.of(ingredient("  Magnesium  ", 75)));

        OcrResult result = OcrService.mergeResults(List.of(img1, img2));

        assertEquals(1, result.getIngredients().size());
    }

    // -------------------------------------------------------------------------
    // Value upgrading — prefer non-zero mg from later image
    // -------------------------------------------------------------------------

    @Test
    void mergeResults_existingMgZero_upgradedFromSecondImage() {
        // Image 1 sees the name but OCR misses the amount (mg=0)
        OcrResult img1 = new OcrResult("img1", List.of(ingredient("Molybdän", 0)));
        // Image 2 (zoomed-in) reads the amount correctly
        OcrResult img2 = new OcrResult("img2", List.of(ingredient("Molybdän", 0.0174)));

        OcrResult result = OcrService.mergeResults(List.of(img1, img2));

        assertEquals(1, result.getIngredients().size());
        assertEquals(0.0174, result.getIngredients().getFirst().getMg(), 0.00001);
    }

    @Test
    void mergeResults_bothNonZeroMg_firstValueKept() {
        // When both images have a value, keep the first image's value
        OcrResult img1 = new OcrResult("img1", List.of(ingredient("Zink", 3.2)));
        OcrResult img2 = new OcrResult("img2", List.of(ingredient("Zink", 32.0))); // OCR noise

        OcrResult result = OcrService.mergeResults(List.of(img1, img2));

        assertEquals(1, result.getIngredients().size());
        assertEquals(3.2, result.getIngredients().getFirst().getMg(), 0.001);
    }

    // -------------------------------------------------------------------------
    // Sub-ingredient merging
    // -------------------------------------------------------------------------

    @Test
    void mergeResults_subIngredientFromSecondImage_appended() {
        // Image 1 misses the sub-ingredient
        IngredientDto trauben1 = ingredient("Traubenkernextrakt", 21.4);
        OcrResult img1 = new OcrResult("img1", List.of(trauben1));

        // Image 2 (zoomed) catches the sub-ingredient
        IngredientDto trauben2 = ingredient("Traubenkernextrakt", 21.4);
        IngredientDto opc = ingredient("OPC", 15.0);
        trauben2.getSubIngredients().add(opc);
        OcrResult img2 = new OcrResult("img2", List.of(trauben2));

        OcrResult result = OcrService.mergeResults(List.of(img1, img2));

        assertEquals(1, result.getIngredients().size());
        IngredientDto merged = result.getIngredients().getFirst();
        assertEquals("Traubenkernextrakt", merged.getName());
        assertEquals(1, merged.getSubIngredients().size());
        assertEquals("OPC", merged.getSubIngredients().getFirst().getName());
    }

    @Test
    void mergeResults_duplicateSubIngredient_notAddedTwice() {
        IngredientDto parent1 = ingredient("Protein", 24_000);
        parent1.getSubIngredients().add(ingredient("Whey Konzentrat", 20_000));

        IngredientDto parent2 = ingredient("Protein", 24_000);
        parent2.getSubIngredients().add(ingredient("Whey Konzentrat", 20_000));

        OcrResult result = OcrService.mergeResults(List.of(
                new OcrResult("img1", List.of(parent1)),
                new OcrResult("img2", List.of(parent2))));

        assertEquals(1, result.getIngredients().size());
        assertEquals(1, result.getIngredients().getFirst().getSubIngredients().size());
    }

    // -------------------------------------------------------------------------
    // Non-overlapping images — all unique entries preserved
    // -------------------------------------------------------------------------

    @Test
    void mergeResults_nonOverlapping_allEntriesPreserved() {
        // Image 1: minerals; image 2: vitamins (no overlap)
        OcrResult img1 = new OcrResult("minerals", List.of(
                ingredient("Magnesium", 75),
                ingredient("Zink", 3.2)));
        OcrResult img2 = new OcrResult("vitamins", List.of(
                ingredient("Vitamin C", 100),
                ingredient("Vitamin D3", 0.01)));

        OcrResult result = OcrService.mergeResults(List.of(img1, img2));

        assertEquals(4, result.getIngredients().size());
        List<String> names = result.getIngredients().stream()
                .map(IngredientDto::getName).toList();
        assertTrue(names.contains("Magnesium"));
        assertTrue(names.contains("Zink"));
        assertTrue(names.contains("Vitamin C"));
        assertTrue(names.contains("Vitamin D3"));
    }

    @Test
    void mergeResults_partialOverlap_correctCount() {
        // 2 shared + 1 unique each = 4 total
        OcrResult img1 = new OcrResult("img1", List.of(
                ingredient("Protein", 24_000),
                ingredient("L-Leucin", 2100),
                ingredient("L-Isoleucin", 1050)));
        OcrResult img2 = new OcrResult("img2", List.of(
                ingredient("Protein", 24_000),   // duplicate
                ingredient("L-Leucin", 2100),    // duplicate
                ingredient("L-Valin", 1050)));    // new

        OcrResult result = OcrService.mergeResults(List.of(img1, img2));

        assertEquals(4, result.getIngredients().size());
    }

    // -------------------------------------------------------------------------
    // Raw text combination
    // -------------------------------------------------------------------------

    @Test
    void mergeResults_rawTextsAreCombinedWithSeparator() {
        OcrResult img1 = new OcrResult("Text von Bild 1", List.of());
        OcrResult img2 = new OcrResult("Text von Bild 2", List.of());

        OcrResult result = OcrService.mergeResults(List.of(img1, img2));

        assertTrue(result.getRawText().contains("Text von Bild 1"));
        assertTrue(result.getRawText().contains("Text von Bild 2"));
        assertTrue(result.getRawText().contains("--- [Bild 2] ---"));
    }

    @Test
    void mergeResults_threeImages_twoSeparatorsInserted() {
        OcrResult result = OcrService.mergeResults(List.of(
                new OcrResult("A", List.of()),
                new OcrResult("B", List.of()),
                new OcrResult("C", List.of())));

        assertTrue(result.getRawText().contains("--- [Bild 2] ---"));
        assertTrue(result.getRawText().contains("--- [Bild 3] ---"));
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static IngredientDto ingredient(String name, double mg) {
        IngredientDto dto = new IngredientDto();
        dto.setName(name);
        dto.setMg(mg);
        return dto;
    }

    // -------------------------------------------------------------------------
    // parseText – direct text parsing without image/OCR
    // -------------------------------------------------------------------------

    private static OcrService ocrServiceWithNoopTranslation() {
        IngredientTranslationService noopTranslation = mock(IngredientTranslationService.class);
        when(noopTranslation.translateAll(any())).thenAnswer(inv -> inv.getArgument(0));
        return new OcrService(noopTranslation);
    }

    @Test
    void parseText_blankText_returnsEmptyResult() {
        OcrService svc = ocrServiceWithNoopTranslation();
        OcrResult result = svc.parseText("   ");
        assertEquals("", result.getRawText());
        assertTrue(result.getIngredients().isEmpty());
        assertFalse(result.isPer100g());
    }

    @Test
    void parseText_nullText_returnsEmptyResult() {
        OcrService svc = ocrServiceWithNoopTranslation();
        OcrResult result = svc.parseText(null);
        assertEquals("", result.getRawText());
        assertTrue(result.getIngredients().isEmpty());
    }

    @Test
    void parseText_singleIngredient_parsed() {
        OcrService svc = ocrServiceWithNoopTranslation();
        OcrResult result = svc.parseText("Protein 25 g");
        assertEquals(1, result.getIngredients().size());
        assertEquals("Protein", result.getIngredients().getFirst().getName());
        assertEquals(25_000.0, result.getIngredients().getFirst().getMg(), 0.001);
    }

    @Test
    void parseText_multipleIngredients_allParsed() {
        OcrService svc = ocrServiceWithNoopTranslation();
        String text = "Protein 25 g\nKohlenhydrate 3 g\nFett 2 g";
        OcrResult result = svc.parseText(text);
        assertEquals(3, result.getIngredients().size());
    }

    @Test
    void parseText_rawTextPreserved() {
        OcrService svc = ocrServiceWithNoopTranslation();
        String text = "Protein 25 g";
        OcrResult result = svc.parseText(text);
        assertEquals(text, result.getRawText());
    }

    @Test
    void parseText_per100gKeyword_detectedTrue() {
        OcrService svc = ocrServiceWithNoopTranslation();
        // OcrTextParser.detectPer100g looks for "pro 100" or "je 100"
        String text = "je 100 g\nProtein\t20000";
        OcrResult result = svc.parseText(text);
        assertTrue(result.isPer100g());
    }

    @Test
    void parseText_noPer100gKeyword_detectedFalse() {
        OcrService svc = ocrServiceWithNoopTranslation();
        OcrResult result = svc.parseText("Protein\t25000");
        assertFalse(result.isPer100g());
    }
}
