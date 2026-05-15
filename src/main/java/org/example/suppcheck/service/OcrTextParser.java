package org.example.suppcheck.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
     * Matches lines that contain only an amount + unit (no name).
     * Used to handle two-line OCR output where name and value land on separate lines:
     * <pre>
     *   Pomelo Extrakt
     *
     *   100 mg
     * </pre>
     */
    private static final Pattern AMOUNT_ONLY_LINE = Pattern.compile(
            "^\\s*(\\d+[.,]?\\d*)\\s*(g|mg|µg|mcg|ug)\\b.*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    /**
     * Detects a two-column table row of the form:
     * <pre>
     *   Name   &lt;pro-100g-amount&gt; &lt;unit&gt;   &lt;per-portion-amount&gt; &lt;unit&gt;
     * </pre>
     * Group 1 = name, group 2/3 = first (Pro 100G) value,
     * group 4/5 = second (Pro Portion) value.
     * When this pattern matches we use the <b>second</b> value.
     */
    private static final Pattern TWO_COLUMN_LINE = Pattern.compile(
            "^\\s*(.+?)\\s*(\\d+[.,]?\\d*)\\s*(g|mg|µg|mcg|ug)\\b\\s+"
            + "(\\d+[.,]?\\d*)\\s*(g|mg|µg|mcg|ug)\\b.*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern LEADING_SPECIAL = Pattern.compile(
            "^[\\s\\-\u2013\u2014\u2022*\u00b7>]+"
    );

    /** Matches a line that consists solely of a unit (possibly with surrounding whitespace). */
    private static final Pattern UNIT_ONLY_LINE = Pattern.compile(
            "^\\s*(g|mg|µg|mcg|ug)\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    /** Matches a line whose last non-whitespace character is a digit. */
    private static final Pattern ENDS_WITH_DIGIT = Pattern.compile(".*\\d\\s*$");

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

        ocrText = joinSplitUnitLines(ocrText);

        List<IngredientDto> result = new ArrayList<>();
        IngredientDto lastTopLevel = null;
        // Carries a name-only line forward until the next amount-only line resolves it.
        String pendingName = null;
        // Counts consecutive non-blank, non-matching lines since the last successful parse.
        // Once this exceeds MAX_CARRY_DISTANCE we abandon pendingName to avoid combining
        // noise text (dosage instructions, headings, etc.) with a stray unit further down.
        int noMatchStreak = 0;
        final int MAX_CARRY_DISTANCE = 2;

        for (String rawLine : ocrText.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isBlank()) {
                continue; // blank lines don't increment the streak or clear pendingName
            }

            // Strip leading OCR table-cell artefacts produced by vertical lines or cell borders:
            // "|", "°", "·", "<", "_", "}", ")" at the start of a line are OCR noise, not text.
            // ")" appears when a closing paren from the previous entry bleeds onto the next line.
            // "\uFF3F" = U+FF3F FULLWIDTH LOW LINE (＿), OCR sometimes produces this instead of "_".
            line = line.replaceAll("^[|°·<_\uFF3F})]+\\s*", "");

            // Normalize space-thousands separator: "4 500" → "4500"
            // Negative lookbehind (?<!\p{L}) ensures we don't merge digit sequences
            // that are part of a name token (e.g. "E6C6 506" or "VitaminB2 525").
            line = line.replaceAll("(?<!\\p{L})(\\d) (\\d{3})(?=[^\\d]|$)", "$1$2");

            // ── OCR misreading corrections ────────────────────────────────────
            // © (copyright symbol) OCR'd instead of letter C — common on EU labels
            line = line.replace("\u00a9", "C");
            // Turkish dotless i (ı, U+0131) OCR'd instead of digit 1
            line = line.replace("\u0131", "1");
            // Vitamin directly followed by letter/digit without space → insert space
            // "VitaminE" → "Vitamin E", "VitaminB12" → "Vitamin B12"
            line = line.replaceAll("(?i)\\bVitamin([ABCDEK0-9])", "Vitamin $1");
            // Specific ingredient name OCR misreadings (common multi-lingual EU labels)
            line = line.replaceAll("(?i)\\bCalclum\\b", "Calcium");
            line = line.replaceAll("(?i)\\bSelenlum\\b", "Selenium");
            line = line.replaceAll("(?i)\\bBlotin\\b", "Biotin");
            line = line.replaceAll("(?i)\\bZine\\b", "Zinc");
            // "Vitamin Da" → "Vitamin D3": digit 3 OCR'd as letter a after D
            line = line.replaceAll("(?i)\\bVitamin D[aA]\\b", "Vitamin D3");
            // "Lryrosin" → "L-Tyrosin": OCR confuses "T-" with "r" after "L"
            line = line.replaceAll("(?i)\\bLryrosin\\b", "L-Tyrosin");
            // "Sitterorangenschalen" → "Bitterorangenschalen": S misread for B
            line = line.replaceAll("(?i)\\bSitterorangenschalen\\b", "Bitterorangenschalen");
            // Unit misreadings — use (?<![a-zA-Z]) instead of \b because a digit
            // immediately before the unit ("4meg") has no word-boundary before the
            // first unit letter.
            line = line.replaceAll("(?<![a-zA-Z])meg\\b", "mcg"); // "17,4meg" → "17,4mcg"
            line = line.replaceAll("(?<![a-zA-Z])u9\\b",  "µg");  // "10u9"    → "10µg"
            // µg OCR'd as "19" directly before "/" (e.g. "10 19/400 I.E.")
            line = line.replaceAll("(\\d+)\\s+19(?=/)", "$1 µg"); // "10 19/…" → "10 µg/…"
            // Digit/letter confusions adjacent to a unit
            line = line.replaceAll("(?<=\\d)a(g|mg|mcg|ug)\\b",   ",4$1"); // "1amg"   → "1,4mg"
            line = line.replaceAll("(?<=\\d,)A(g|mg|mcg|ug)\\b",  "4$1");  // "21,Amg" → "21,4mg"
            line = line.replaceAll("(?<![a-zA-Z])I(mg|mcg|g|ug)\\b", "1$1"); // "Img"  → "1mg"
            line = line.replaceAll("(?<=[0-9])ma\\b",              "mg");   // "18ma"   → "18mg" (g→a)
            // ─────────────────────────────────────────────────────────────────

            // Check TWO_COLUMN_LINE before INGREDIENT_LINE: a line like
            // "Eiweiß 70 g 30 g" (Pro 100g | Pro Portion) must use the second value.
            // INGREDIENT_LINE's greedy .*$ would otherwise capture the first value.
            Matcher twoCol = TWO_COLUMN_LINE.matcher(line);
            if (twoCol.matches()) {
                pendingName = null;
                noMatchStreak = 0;
                String name = twoCol.group(1).trim();
                if (!name.isBlank()) {
                    double amount;
                    try {
                        amount = parseAmount(twoCol.group(4)); // group 4/5 = second (per-portion) value
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    double mg = toMg(amount, twoCol.group(5).toLowerCase());
                    lastTopLevel = addParsedEntry(name, mg, result, lastTopLevel);
                }
                continue;
            }

            // Check amount-only BEFORE INGREDIENT_LINE: a line like "100 mg" must not be
            // mis-parsed by INGREDIENT_LINE as name="1", amount="00".
            Matcher amountOnly = AMOUNT_ONLY_LINE.matcher(line);
            if (amountOnly.matches()) {
                if (pendingName != null) {
                    double amount;
                    try {
                        amount = parseAmount(amountOnly.group(1));
                    } catch (NumberFormatException e) {
                        pendingName = null;
                        noMatchStreak = 0;
                        continue;
                    }
                    double mg = toMg(amount, amountOnly.group(2).toLowerCase());
                    lastTopLevel = addParsedEntry(pendingName, mg, result, lastTopLevel);
                }
                // amount-only with no pending name → skip (unresolvable)
                pendingName = null;
                noMatchStreak = 0;
                continue;
            }

            Matcher m = INGREDIENT_LINE.matcher(line);
            if (m.matches()) {
                String name = m.group(1).trim();
                // Multi-line name: if a name-only line set pendingName and the next parsed
                // fragment starts with '(' but contains no "davon", it is a parenthetical
                // continuation of the name — not a sub-ingredient.
                // Example: "Adenosin 5'-Triphosphat Dinatrium" + "(ATP) (als PEAK ATP®)"
                if (pendingName != null
                        && name.startsWith("(")
                        && !name.toLowerCase(Locale.ROOT).contains("davon")) {
                    name = pendingName + " " + name;
                }
                pendingName = null;
                noMatchStreak = 0;
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
                lastTopLevel = addParsedEntry(name, mg, result, lastTopLevel);
                continue;
            }

            // Non-blank, non-matching line.
            noMatchStreak++;
            if (noMatchStreak <= MAX_CARRY_DISTANCE) {
                // Still close enough to a previous match — candidate carry-forward name.
                pendingName = line;
            } else {
                // Too many noise lines in a row; give up on carry-forward.
                pendingName = null;
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Pre-processing
    // -------------------------------------------------------------------------

    /**
     * Joins lines where the amount ends on one line and the unit appears alone
     * on the very next non-blank line.  Example:
     * <pre>
     *   L-Citrullin Malat 10000
     *   mg
     * </pre>
     * becomes {@code "L-Citrullin Malat 10000 mg"}.
     *
     * <p>Blank lines between the number-line and the unit-line are skipped but
     * not removed; the unit line itself is consumed (not emitted separately).</p>
     */
    static String joinSplitUnitLines(String text) {
        if (text == null) {
            return null;
        }
        String[] lines = text.split("\\r?\\n", -1);
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            if (ENDS_WITH_DIGIT.matcher(line).matches()) {
                // Look ahead for the next non-blank line
                int j = i + 1;
                while (j < lines.length && lines[j].isBlank()) {
                    j++;
                }
                if (j < lines.length && UNIT_ONLY_LINE.matcher(lines[j]).matches()) {
                    // Join: append unit to current line, skip the unit line
                    sb.append(line.stripTrailing())
                      .append(' ')
                      .append(lines[j].trim())
                      .append('\n');
                    i = j; // advance past the consumed unit line
                    continue;
                }
            }

            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Ingredient classification
    // -------------------------------------------------------------------------

    /**
     * Classifies {@code name} as sub-ingredient or top-level and adds it to the result list.
     *
     * @return the last top-level ingredient (possibly updated)
     */
    private static IngredientDto addParsedEntry(String name, double mg,
                                                 List<IngredientDto> result,
                                                 IngredientDto lastTopLevel) {
        if (isSubIngredient(name)) {
            IngredientDto sub = new IngredientDto();
            sub.setName(cleanSubIngredientName(name));
            sub.setMg(mg);
            if (lastTopLevel != null) {
                lastTopLevel.getSubIngredients().add(sub);
            } else {
                result.add(sub);
            }
            return lastTopLevel; // sub-ingredients don't update lastTopLevel
        } else {
            IngredientDto dto = new IngredientDto();
            dto.setName(cleanTopLevelName(name));
            dto.setMg(mg);
            result.add(dto);
            return dto;
        }
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
     *   <li>The name starts with {@code "davon "} (German "of which").</li>
     *   <li>The name starts with OCR noise characters ({@code {}, {@code "},
     *       {@code '} …) followed by {@code "davon "} — e.g.
     *       {@code { davon Piperin} where {@code {} is a table-border artefact.
     * </ol>
     */
    static boolean isSubIngredient(String name) {
        // Case 1: name starts with at least one leading special char
        if (LEADING_SPECIAL.matcher(name).find()) {
            return true;
        }
        // Case 2: bare '(' at the very start
        if (name.startsWith("(")) {
            return true;
        }
        // Case 3: name starts with "davon " (German "of which") — also a sub-ingredient indicator
        if (name.toLowerCase(Locale.ROOT).startsWith("davon ")) {
            return true;
        }
        // Case 4: OCR noise prefix (curly brace, quote marks, …) followed by "davon "
        // e.g. "{ davon Piperin" where { is a table-border artefact
        String stripped = name.replaceAll("^[{\"'`\u201c\u201d\u2018\u2019\\s]+", "");
        return stripped.toLowerCase(Locale.ROOT).startsWith("davon ");
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
     *   "{ davon Piperin"   →  "Piperin"
     * </pre>
     */
    static String cleanSubIngredientName(String name) {
        // Strip leading special chars (including OCR noise: {, ", ', `) and opening paren
        name = name.replaceAll("^[\\s\\-\u2013\u2014\u2022*\u00b7>({\"'`\u201c\u201d\u2018\u2019]+", "");
        // Strip trailing closing paren, opening paren (amount-format artifact), and whitespace
        name = name.replaceAll("[()\\s]+$", "");
        // Strip "davon " prefix — it is a sub-ingredient indicator, not part of the name
        if (name.toLowerCase(Locale.ROOT).startsWith("davon ")) {
            name = name.substring(6);
        }
        return name.trim();
    }

    /**
     * Strips OCR artefacts from a top-level ingredient name:
     * <ul>
     *   <li>Leading noise characters: {@code {}, {@code "}, {@code '}, `` ` ``,
     *       and Unicode variants of quote/curly marks.</li>
     *   <li>Leading line-number digit(s) followed by whitespace before an uppercase
     *       letter (including Ä/Ö/Ü): e.g. {@code "3 Calcium"} → {@code "Calcium"},
     *       {@code "12 Zink"} → {@code "Zink"}.</li>
     * </ul>
     */
    static String cleanTopLevelName(String name) {
        // Strip leading OCR noise characters (table artefacts, misread quotes)
        name = name.replaceAll("^[{\"'`\u201c\u201d\u2018\u2019\\s]+", "");
        // Strip leading line-number digit(s) + whitespace before a capital letter
        name = name.replaceAll("^\\d+\\s+(?=[A-Z\u00c4\u00d6\u00dc])", "");
        // Strip trailing large numbers (≥4 digits) — Pro-100G value bleeding into the name
        // Keeps "B12", "K2", "D3" which are at most 2 digits
        name = name.replaceAll("\\s+\\d{4,}[.,]?\\d*\\s*$", "").trim();
        // Strip trailing punctuation/bracket OCR artefacts (e.g. "Vitamin K2 -" → "Vitamin K2")
        name = name.replaceAll("[\\s\\-,\\.\\(]+$", "").trim();
        // Strip trailing short (1–3 char) lowercase noise tokens (e.g. "au," from "Vitamin B6 au,")
        name = name.replaceAll("\\s+[a-z\u00e4\u00f6\u00fc\u00df]{1,3}[,.]?$", "").trim();
        return name;
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
