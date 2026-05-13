package org.example.suppcheck.service;

import org.example.suppcheck.dto.CheckResult;
import org.example.suppcheck.dto.IngredientCheckResult;
import org.example.suppcheck.dto.IngredientDto;
import org.example.suppcheck.dto.OcrResult;
import org.example.suppcheck.model.Ingredient;
import org.example.suppcheck.model.Supplement;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Compares a supplement's stored ingredient list against the result of an OCR
 * scan of a nutrition-label image.
 *
 * <p>Matching is done case-insensitively on the ingredient name.  Amounts are
 * considered equal when they differ by less than 5 % (or less than 1 mg for
 * very small values).</p>
 */
@Service
public class CheckService {

    static final double TOLERANCE_FACTOR = 0.05;
    static final double TOLERANCE_MIN_MG = 1.0;

    /**
     * Compares the supplement stored in the database against the OCR result and
     * returns a {@link CheckResult} describing every discrepancy found.
     *
     * @param supplement the supplement loaded from the database
     * @param ocrResult  the parsed OCR output from the label image
     * @return a check result (never {@code null})
     */
    public CheckResult compare(Supplement supplement, OcrResult ocrResult) {
        List<IngredientDto> ocrIngredients =
                ocrResult.getIngredients() != null ? ocrResult.getIngredients() : List.of();

        // Build a mutable map: normalised name → OCR DTO so we can track unmatched ones.
        Map<String, IngredientDto> ocrMap = new LinkedHashMap<>();
        for (IngredientDto dto : ocrIngredients) {
            ocrMap.put(normalize(dto.getName()), dto);
        }

        List<IngredientCheckResult> results = new ArrayList<>();

        // Walk the stored ingredients
        List<Ingredient> storedIngredients =
                supplement.getIngredients() != null ? supplement.getIngredients() : List.of();

        for (Ingredient stored : storedIngredients) {
            String key = normalize(stored.getName());
            IngredientDto ocr = ocrMap.remove(key); // remove so we can detect ONLY_IN_OCR later

            if (ocr == null) {
                results.add(new IngredientCheckResult(
                        stored.getName(), stored.getMg(), 0.0, "ONLY_IN_DB",
                        Collections.emptyList()));
            } else {
                String status = isClose(stored.getMg(), ocr.getMg()) ? "MATCH" : "VALUE_MISMATCH";
                List<IngredientCheckResult> subResults =
                        compareSubIngredients(stored.getSubIngredients(), ocr.getSubIngredients());
                results.add(new IngredientCheckResult(
                        stored.getName(), stored.getMg(), ocr.getMg(), status, subResults));
            }
        }

        // Whatever remains in the OCR map was not found in the stored supplement
        for (IngredientDto extra : ocrMap.values()) {
            results.add(new IngredientCheckResult(
                    extra.getName(), 0.0, extra.getMg(), "ONLY_IN_OCR",
                    Collections.emptyList()));
        }

        boolean hasDiscrepancies = results.stream()
                .anyMatch(r -> !"MATCH".equals(r.getStatus()));

        return new CheckResult(
                supplement.getId(),
                supplement.getName(),
                hasDiscrepancies,
                ocrResult.getRawText(),
                results);
    }

    // --- helpers ---

    private List<IngredientCheckResult> compareSubIngredients(
            List<Ingredient> storedSubs, List<IngredientDto> ocrSubs) {

        boolean storedEmpty = storedSubs == null || storedSubs.isEmpty();
        boolean ocrEmpty = ocrSubs == null || ocrSubs.isEmpty();
        if (storedEmpty && ocrEmpty) {
            return Collections.emptyList();
        }

        Map<String, IngredientDto> ocrSubMap = new LinkedHashMap<>();
        if (ocrSubs != null) {
            for (IngredientDto dto : ocrSubs) {
                ocrSubMap.put(normalize(dto.getName()), dto);
            }
        }

        List<IngredientCheckResult> results = new ArrayList<>();

        if (storedSubs != null) {
            for (Ingredient stored : storedSubs) {
                String key = normalize(stored.getName());
                IngredientDto ocr = ocrSubMap.remove(key);
                if (ocr == null) {
                    results.add(new IngredientCheckResult(
                            stored.getName(), stored.getMg(), 0.0, "ONLY_IN_DB",
                            Collections.emptyList()));
                } else {
                    String status = isClose(stored.getMg(), ocr.getMg()) ? "MATCH" : "VALUE_MISMATCH";
                    results.add(new IngredientCheckResult(
                            stored.getName(), stored.getMg(), ocr.getMg(), status,
                            Collections.emptyList()));
                }
            }
        }

        for (IngredientDto extra : ocrSubMap.values()) {
            results.add(new IngredientCheckResult(
                    extra.getName(), 0.0, extra.getMg(), "ONLY_IN_OCR",
                    Collections.emptyList()));
        }

        return results;
    }

    /**
     * Returns {@code true} when {@code a} and {@code b} are within the allowed
     * tolerance (5 % of the larger value, at least 1 mg).
     */
    boolean isClose(double a, double b) {
        if (a == 0 && b == 0) return true;
        double maxAbs = Math.max(Math.abs(a), Math.abs(b));
        return Math.abs(a - b) <= Math.max(TOLERANCE_MIN_MG, TOLERANCE_FACTOR * maxAbs);
    }

    /**
     * Normalises a name for case-insensitive, whitespace-tolerant comparison.
     */
    static String normalize(String name) {
        if (name == null) return "";
        return name.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
