package org.example.suppcheck.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * Overall result of checking a supplement's ingredient list against an OCR scan
 * of a nutrition-label image.
 */
@Getter
@AllArgsConstructor
public class CheckResult {

    private final String supplementId;
    private final String supplementName;

    /** True when at least one ingredient has a status other than MATCH. */
    private final boolean hasDiscrepancies;

    /** Raw Tesseract output, for debugging. */
    private final String rawText;

    private final List<IngredientCheckResult> ingredientResults;
}
