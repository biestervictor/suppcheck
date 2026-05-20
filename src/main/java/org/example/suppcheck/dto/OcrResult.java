package org.example.suppcheck.dto;

import java.util.List;
import lombok.Getter;

/**
 * Result of an OCR extraction run.
 * Contains both the parsed ingredient list and the raw text as recognised
 * by Tesseract, so the UI can display the raw text for debugging / manual
 * review.
 */
@Getter
public class OcrResult {

    /** Raw text as returned by Tesseract (line-separated). */
    private final String rawText;

    /** Parsed ingredients derived from {@link #rawText}. */
    private final List<IngredientDto> ingredients;

    /**
     * {@code true} when the OCR text contains a "pro 100 g" marker but no
     * "pro Portion" column — meaning all {@link IngredientDto#getMg()} values
     * are per-100-g and must be scaled by {@code portionSize / 100.0}.
     */
    private final boolean per100g;

    public OcrResult(String rawText, List<IngredientDto> ingredients) {
        this(rawText, ingredients, false);
    }

    public OcrResult(String rawText, List<IngredientDto> ingredients, boolean per100g) {
        this.rawText = rawText;
        this.ingredients = ingredients;
        this.per100g = per100g;
    }
}
