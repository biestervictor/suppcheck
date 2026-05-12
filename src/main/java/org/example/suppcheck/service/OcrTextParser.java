package org.example.suppcheck.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.example.suppcheck.dto.IngredientDto;

/**
 * Parses raw OCR text from supplement / nutrition labels and extracts
 * ingredients with their daily-dose amounts.
 *
 * <p>Supported units: {@code g}, {@code mg}, {@code µg / mcg / ug}.</p>
 * <p>All amounts are stored in <b>milligrams (mg)</b> representing the
 * Tagesdosis (daily dose / per-serving value).</p>
 * <p>Handles German number format (comma as decimal separator,
 * period as thousands separator).</p>
 *
 * <h3>Sub-ingredient detection</h3>
 * <p>A line is treated as a sub-ingredient of the immediately preceding
 * top-level ingredient when its name starts with {@code (} — possibly
 * preceded by a few special characters such as {@code -}, {@code –},
 * {@code —}, {@code •}, {@code *}, {@code ·}.  Examples that are
 * detected as sub-ingredients:</p>
 * <pre>
 *   (davon L-Leucin) 2500 mg
 *   - (Whey Isolat 15 g)
 *   – (L-Isoleucin) 1050 mg
 * </pre>
 */
public final class OcrTextParser {

    /**
     * Matches lines of the form:  Name [:]  Amount  Unit [optional extra]
     * Examples:
     *   L-Leucin 2100 mg
     *   Eiweiß: 24 g
     *   Vitamin D3 25 µg  25%*
     *   Kohlenhydrate 2,5g
     *   (davon L-Leucin) 2500 mg
     *   - (Whey Isolat 15 g)
     */
    private static final Pattern INGREDIENT_LINE = Pattern.compile(
            "^\\s*(.+?)\\s*:?\\s*(\\d+[.,]?\\d*)\\s*(g|mg|µg|mcg|ug)\\b.*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    /**
     * Leading characters that may appear before the opening parenthesis
     * of a sub-ingredient line: whitespace, dashes (ASCII + unicode),
     * bullets, asterisks, middle-dots, greater-than signs (e.g. {@code >}, {@code >>}).
     */
    private static final Pattern LEADING_SPECIAL = Pattern.compile(
            "^[\\s\\-\u2013\u2014\u2022*\u00b7>]+"
    );

    private OcrTextParser() {
    }

    /**
     * Parses the given OCR text and returns a list of detected ingredients.
     *
     * <p>Each returned {@link IngredientDto#getMg()} value is the
     * <em>Tagesdosis</em> (daily dose / per-serving amount) converted to mg:
     * <ul>
     *   <li>{@code g}  → value × 1 000</li>
     *   <li>{@code mg} → value (unchanged)</li>
     *   <li>{@code µg / mcg / ug} → value ÷ 1 000</li>
     * </ul>
     *
     * <p>Sub-ingredients (lines whose name starts with {@code (}, optionally
     * preceded by special chars) are attached to the last top-level ingredient.
     * If no top-level ingredient has been seen yet, they are added as
     * top-level entries.</p>
     *
     * @param ocrText raw text as returned by Tesseract
     * @return list of ingredients; never null
     */
    public static List<IngredientDto> parse(String ocrText) {
        if (ocrText == null || ocrText.isBlank()) {
            return new ArrayList<>();
        }

        List<IngredientDto> result = new ArrayList<>();
        IngredientDto lastTopLevel = null;

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

            if (isSubIngredient(name)) {
                IngredientDto sub = new IngredientDto();
                sub.setName(cleanSubIngredientName(name));
                sub.setMg(mg);
                if (lastTopLevel != null) {
                    lastTopLevel.getSubIngredients().add(sub);
                } else {
                    // No parent yet — treat as top-level
                    result.add(sub);
                }
            } else {
                IngredientDto dto = new IngredientDto();
                dto.setName(name);
                dto.setMg(mg);
                result.add(dto);
                lastTopLevel = dto;
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Sub-ingredient helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} when the name indicates a sub-ingredient.
     *
     * <p>Two cases are recognised:</p>
     * <ol>
     *   <li>The name starts with one or more leading special characters
     *       ({@code >}, {@code -}, {@code –}, {@code •}, etc.) — with or
     *       without a following {@code (}.
     *       Examples: {@code >> davon Glucomannan}, {@code - L-Leucin},
     *       {@code – (Whey Isolat}</li>
     *   <li>The name starts directly with {@code (} (no leading special chars).
     *       Example: {@code (davon L-Leucin)}</li>
     * </ol>
     */
    static boolean isSubIngredient(String name) {
        // Case 1: name starts with at least one leading special char
        if (LEADING_SPECIAL.matcher(name).find()) {
            return true;
        }
        // Case 2: bare '(' at the very start
        return name.startsWith("(");
    }

    /**
     * Removes the parenthesis markers and leading special chars from a
     * sub-ingredient name.
     *
     * <p>Examples:
     * <pre>
     *   "(davon L-Leucin)"  →  "davon L-Leucin"
     *   "– (Whey Isolat"    →  "Whey Isolat"
     *   "- (L-Isoleucin)"   →  "L-Isoleucin"
     * </pre>
     */
    static String cleanSubIngredientName(String name) {
        // Strip leading special chars and opening paren
        name = name.replaceAll("^[\\s\\-\u2013\u2014\u2022*\u00b7>(]+", "");
        // Strip trailing closing paren and whitespace
        name = name.replaceAll("[)\\s]+$", "");
        return name.trim();
    }

    // -------------------------------------------------------------------------
    // Unit conversion
    // -------------------------------------------------------------------------

    /**
     * Converts the given amount from the given unit to milligrams (Tagesdosis).
     *
     * @param amount numeric value as parsed from the label
     * @param unit   lower-case unit string: {@code g}, {@code mg},
     *               {@code µg}, {@code mcg}, or {@code ug}
     * @return amount in mg
     */
    static double toMg(double amount, String unit) {
        return switch (unit) {
            case "g"               -> amount * 1_000.0;
            case "µg", "mcg", "ug" -> amount / 1_000.0;
            default                -> amount; // already mg
        };
    }

    // -------------------------------------------------------------------------
    // Number parsing
    // -------------------------------------------------------------------------

    /**
     * Parses a number string that may use German formatting:
     * <ul>
     *   <li>{@code 2100}    → 2100</li>
     *   <li>{@code 2,1}     → 2.1  (decimal comma)</li>
     *   <li>{@code 2.100}   → 2100 (thousands separator)</li>
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
