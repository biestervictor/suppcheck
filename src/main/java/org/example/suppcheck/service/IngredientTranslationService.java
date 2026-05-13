package org.example.suppcheck.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.example.suppcheck.dto.IngredientDto;
import org.springframework.stereotype.Service;

/**
 * Translates English ingredient names to their German equivalents using a
 * static dictionary.
 *
 * <p>Rules:</p>
 * <ul>
 *   <li>Entries are sorted longest-first so that compound names (e.g.
 *       {@code L-Citrulline Malate}) are replaced before shorter sub-strings
 *       ({@code L-Citrulline}).</li>
 *   <li>Matching is case-insensitive and word-boundary-aware:
 *       {@code (?i)(?<![a-zA-Z0-9])term(?![a-zA-Z0-9])}.</li>
 *   <li>{@code Protein} is intentionally <em>not</em> translated — it is
 *       identical in German and translating it to {@code Eiweiß} would break
 *       compound names like {@code Whey Protein}.</li>
 * </ul>
 */
@Service
public class IngredientTranslationService {

    /**
     * EN → DE dictionary.  Order matters: longest entries must come first so
     * that a compound like "L-Citrulline Malate" is replaced before the shorter
     * "L-Citrulline".  The map is built in declaration order via LinkedHashMap
     * and then re-sorted by key length descending before compiling patterns.
     */
    private static final Map<String, String> RAW_DICT = new LinkedHashMap<>();

    static {
        // Compound names first (also sorted longest-first within this block)
        RAW_DICT.put("L-Citrulline Malate",   "L-Citrullin Malat");
        RAW_DICT.put("Creatine Monohydrate",   "Kreatin Monohydrat");
        RAW_DICT.put("Saturated Fat",          "gesättigte Fettsäuren");
        RAW_DICT.put("Carbohydrates",          "Kohlenhydrate");
        RAW_DICT.put("Folic Acid",             "Folsäure");
        RAW_DICT.put("Beta-Alanine",           "Beta-Alanin");
        RAW_DICT.put("L-Isoleucine",           "L-Isoleucin");
        RAW_DICT.put("L-Glutamine",            "L-Glutamin");
        RAW_DICT.put("L-Citrulline",           "L-Citrullin");
        RAW_DICT.put("L-Arginine",             "L-Arginin");
        RAW_DICT.put("L-Carnitine",            "L-Carnitin");
        RAW_DICT.put("L-Theanine",             "L-Theanin");
        RAW_DICT.put("L-Tyrosine",             "L-Tyrosin");
        RAW_DICT.put("L-Leucine",              "L-Leucin");
        RAW_DICT.put("L-Valine",               "L-Valin");
        // Single-word terms
        RAW_DICT.put("Molybdenum",             "Molybdän");
        RAW_DICT.put("Manganese",              "Mangan");
        RAW_DICT.put("Potassium",              "Kalium");
        RAW_DICT.put("Creatine",               "Kreatin");
        RAW_DICT.put("Selenium",               "Selen");
        RAW_DICT.put("Thiamine",               "Thiamin");
        RAW_DICT.put("Chromium",               "Chrom");
        RAW_DICT.put("Caffeine",               "Koffein");
        RAW_DICT.put("Taurine",                "Taurin");
        RAW_DICT.put("Iodine",                 "Jod");
        RAW_DICT.put("Sodium",                 "Natrium");
        RAW_DICT.put("Folate",                 "Folat");
        RAW_DICT.put("Copper",                 "Kupfer");
        RAW_DICT.put("Fiber",                  "Ballaststoffe");
        RAW_DICT.put("Fibre",                  "Ballaststoffe");
        RAW_DICT.put("Iron",                   "Eisen");
        RAW_DICT.put("Zinc",                   "Zink");
        RAW_DICT.put("Salt",                   "Salz");
        RAW_DICT.put("Fat",                    "Fett");
    }

    /**
     * Compiled patterns, sorted longest-key-first, each paired with its replacement.
     */
    private static final List<Map.Entry<Pattern, String>> PATTERNS;

    static {
        // Sort by key length descending before compiling so longer entries win
        List<Map.Entry<String, String>> sorted = new ArrayList<>(RAW_DICT.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));

        PATTERNS = new ArrayList<>(sorted.size());
        for (Map.Entry<String, String> e : sorted) {
            String regex = "(?i)(?<![a-zA-Z0-9])" + Pattern.quote(e.getKey()) + "(?![a-zA-Z0-9])";
            PATTERNS.add(Map.entry(Pattern.compile(regex), e.getValue()));
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Translates a single ingredient name from English to German where a
     * dictionary match exists.  The original is returned unchanged when no
     * match applies.
     *
     * @param name ingredient name as returned by the OCR parser; may be null
     * @return translated name, or the original if no translation applies
     */
    public String translate(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        String result = name;
        for (Map.Entry<Pattern, String> entry : PATTERNS) {
            result = entry.getKey().matcher(result).replaceAll(entry.getValue());
        }
        return result;
    }

    /**
     * Applies {@link #translate(String)} to all ingredient names (top-level
     * and sub-ingredient) in the given list.  The list objects are modified
     * in-place.
     *
     * @param ingredients list of parsed ingredients; may be empty, not null
     * @return the same list with names translated
     */
    public List<IngredientDto> translateAll(List<IngredientDto> ingredients) {
        for (IngredientDto ing : ingredients) {
            ing.setName(translate(ing.getName()));
            for (IngredientDto sub : ing.getSubIngredients()) {
                sub.setName(translate(sub.getName()));
            }
        }
        return ingredients;
    }
}
