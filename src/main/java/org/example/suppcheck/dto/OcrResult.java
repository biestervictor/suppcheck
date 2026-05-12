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

    public OcrResult(String rawText, List<IngredientDto> ingredients) {
        this.rawText = rawText;
        this.ingredients = ingredients;
    }
}
