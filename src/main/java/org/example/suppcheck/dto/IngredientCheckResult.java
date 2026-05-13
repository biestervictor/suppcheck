package org.example.suppcheck.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * Result of comparing a single ingredient (and its sub-ingredients) between
 * the stored supplement data and the OCR output from a label image.
 *
 * <p>Status values:
 * <ul>
 *   <li>{@code MATCH}         – name found in both, mg within tolerance</li>
 *   <li>{@code VALUE_MISMATCH} – name found in both, mg differs significantly</li>
 *   <li>{@code ONLY_IN_DB}   – stored in the supplement but not detected by OCR</li>
 *   <li>{@code ONLY_IN_OCR}  – detected by OCR but not stored in the supplement</li>
 * </ul>
 */
@Getter
@AllArgsConstructor
public class IngredientCheckResult {

    private final String name;

    /** Amount stored in the database (mg). 0 when status is ONLY_IN_OCR. */
    private final double storedMg;

    /** Amount detected by OCR (mg). 0 when status is ONLY_IN_DB. */
    private final double ocrMg;

    /** One of: MATCH, VALUE_MISMATCH, ONLY_IN_DB, ONLY_IN_OCR. */
    private final String status;

    private final List<IngredientCheckResult> subResults;
}
