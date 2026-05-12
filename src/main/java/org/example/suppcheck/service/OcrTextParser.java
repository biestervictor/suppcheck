package org.example.suppcheck.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.example.suppcheck.dto.IngredientDto;

/**
 * Parses raw OCR text from supplement / nutrition labels and extracts
 * ingredients with their amounts.
 *
 * <p>Supported units: {@code g}, {@code mg}, {@code µg / mcg / ug}.</p>
 * <p>Handles German number format (comma as decimal separator,
 * period as thousands separator).</p>
 */
public final class OcrTextParser {

    /**
     * Matches lines of the form:  Name [:]  Amount  Unit [optional extra]
     * Examples:
     *   L-Leucin 2100 mg
     *   Eiweiß: 24 g
     *   Vitamin D3 25 µg  25%*
     *   Kohlenhydrate 2,5g
     */
    private static final Pattern INGREDIENT_LINE = Pattern.compile(
            "^\\s*(.+?)\\s*:?\\s*(\\d+[.,]?\\d*)\\s*(g|mg|µg|mcg|ug)\\b.*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private OcrTextParser() {
    }

    /**
     * Parses the given OCR text and returns a list of detected ingredients.
     *
     * @param ocrText raw text as returned by Tesseract
     * @return list of ingredients with name and mg amount; never null
     */
    public static List<IngredientDto> parse(String ocrText) {
        if (ocrText == null || ocrText.isBlank()) {
            return new ArrayList<>();
        }

        List<IngredientDto> result = new ArrayList<>();
        for (String rawLine : ocrText.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isBlank()) {
                continue;
            }

            Matcher m = INGREDIENT_LINE.matcher(line);
            if (!m.matches()) {
                continue;
            }

            String name = m.group(1).trim();
            if (name.isBlank()) {
                continue;
            }

            double amount;
            try {
                amount = parseAmount(m.group(2));
            } catch (NumberFormatException e) {
                continue;
            }

            double mg = toMg(amount, m.group(3).toLowerCase());

            IngredientDto dto = new IngredientDto();
            dto.setName(name);
            dto.setMg(mg);
            result.add(dto);
        }
        return result;
    }

    /**
     * Converts the given amount from the given unit to milligrams.
     */
    static double toMg(double amount, String unit) {
        return switch (unit) {
            case "g"           -> amount * 1000.0;
            case "µg", "mcg", "ug" -> amount / 1000.0;
            default            -> amount; // already mg
        };
    }

    /**
     * Parses a number string that may use German formatting:
     * <ul>
     *   <li>{@code 2100}  → 2100</li>
     *   <li>{@code 2,1}   → 2.1  (decimal comma)</li>
     *   <li>{@code 2.100} → 2100 (thousands separator)</li>
     *   <li>{@code 1.234,5} → 1234.5</li>
     * </ul>
     */
    static double parseAmount(String raw) {
        raw = raw.trim();

        if (raw.contains(".") && raw.contains(",")) {
            // German full format: 1.234,56
            raw = raw.replace(".", "").replace(",", ".");
        } else if (raw.contains(",")) {
            // German decimal comma: 2,5
            raw = raw.replace(",", ".");
        } else if (raw.contains(".")) {
            // Period: thousands separator if exactly 3 digits follow the last dot
            int dotIdx = raw.lastIndexOf('.');
            if (raw.length() - dotIdx - 1 == 3) {
                raw = raw.replace(".", ""); // thousands separator
            }
            // else: treat as decimal (e.g. 0.5)
        }

        return Double.parseDouble(raw);
    }
}
