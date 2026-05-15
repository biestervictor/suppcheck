package org.example.suppcheck.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.example.suppcheck.dto.IngredientDto;
import org.junit.jupiter.api.Test;

class OcrTextParserTest {

    // --- parse() ---

    @Test
    void parse_null_returnsEmptyList() {
        assertTrue(OcrTextParser.parse(null).isEmpty());
    }

    @Test
    void parse_blank_returnsEmptyList() {
        assertTrue(OcrTextParser.parse("   \n  ").isEmpty());
    }

    @Test
    void parse_simpleGramLine_convertsToMg() {
        List<IngredientDto> result = OcrTextParser.parse("Protein 24 g");

        assertEquals(1, result.size());
        assertEquals("Protein", result.getFirst().getName());
        assertEquals(24_000.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_simpleMgLine_keepsValue() {
        List<IngredientDto> result = OcrTextParser.parse("L-Leucin 2100 mg");

        assertEquals(1, result.size());
        assertEquals("L-Leucin", result.getFirst().getName());
        assertEquals(2100.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_microgramLine_convertsMcg() {
        List<IngredientDto> result = OcrTextParser.parse("Vitamin D3 25 µg");

        assertEquals(1, result.size());
        assertEquals("Vitamin D3", result.getFirst().getName());
        assertEquals(0.025, result.getFirst().getMg(), 0.0001);
    }

    @Test
    void parse_mcgAlias_convertsMcg() {
        List<IngredientDto> result = OcrTextParser.parse("Vitamin B12 2 mcg");

        assertEquals(1, result.size());
        assertEquals(0.002, result.getFirst().getMg(), 0.0001);
    }

    @Test
    void parse_colonSeparator_parsesCorrectly() {
        List<IngredientDto> result = OcrTextParser.parse("L-Leucin: 2100 mg");

        assertEquals(1, result.size());
        assertEquals("L-Leucin", result.getFirst().getName());
        assertEquals(2100.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_germanDecimalComma_parsesCorrectly() {
        List<IngredientDto> result = OcrTextParser.parse("Kohlenhydrate 2,5 g");

        assertEquals(1, result.size());
        assertEquals(2500.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_germanThousandsSeparator_parsesCorrectly() {
        List<IngredientDto> result = OcrTextParser.parse("L-Glutaminsäure 3.500 mg");

        assertEquals(1, result.size());
        assertEquals(3500.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_multipleLines_returnsAllIngredients() {
        String text = "Protein 24 g\n" +
                      "L-Leucin 2100 mg\n" +
                      "L-Isoleucin 1050 mg\n" +
                      "L-Valin 1050 mg";

        List<IngredientDto> result = OcrTextParser.parse(text);

        assertEquals(4, result.size());
        assertEquals("Protein",     result.get(0).getName());
        assertEquals("L-Leucin",    result.get(1).getName());
        assertEquals("L-Isoleucin", result.get(2).getName());
        assertEquals("L-Valin",     result.get(3).getName());
    }

    @Test
    void parse_headerLinesWithoutUnit_areSkipped() {
        // Lines that have no number+unit pattern are skipped
        String text = "Nährwertangaben pro Tagesportion:\n" +
                      "L-Leucin 2100 mg\n" +
                      "Aminosäureprofil:";

        List<IngredientDto> result = OcrTextParser.parse(text);

        assertEquals(1, result.size());
        assertEquals("L-Leucin", result.getFirst().getName());
    }

    @Test
    void parse_emptyLines_areSkipped() {
        String text = "L-Leucin 2100 mg\n\n\nL-Valin 1050 mg";

        List<IngredientDto> result = OcrTextParser.parse(text);

        assertEquals(2, result.size());
    }

    @Test
    void parse_trailingPercentage_isIgnored() {
        // Labels often have  "L-Leucin 2100 mg 25%*"
        List<IngredientDto> result = OcrTextParser.parse("L-Leucin 2100 mg 25%");

        assertEquals(1, result.size());
        assertEquals(2100.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_unitAttachedToNumber_parsesCorrectly() {
        // "24g" without space between number and unit
        List<IngredientDto> result = OcrTextParser.parse("Protein 24g");

        assertEquals(1, result.size());
        assertEquals(24_000.0, result.getFirst().getMg(), 0.001);
    }

    // --- parseAmount() ---

    @Test
    void parseAmount_integerString() {
        assertEquals(2100.0, OcrTextParser.parseAmount("2100"), 0.001);
    }

    @Test
    void parseAmount_germanCommaDecimal() {
        assertEquals(2.5, OcrTextParser.parseAmount("2,5"), 0.001);
    }

    @Test
    void parseAmount_germanThousandsPeriod() {
        assertEquals(3500.0, OcrTextParser.parseAmount("3.500"), 0.001);
    }

    @Test
    void parseAmount_germanFullFormat() {
        assertEquals(1234.5, OcrTextParser.parseAmount("1.234,5"), 0.001);
    }

    // --- Sub-ingredient detection ---

    @Test
    void parse_subIngredientWithParen_isAttachedToPrevious() {
        String text = "Protein 24 g\n(davon Whey-Konzentrat) 20 g";

        List<IngredientDto> result = OcrTextParser.parse(text);

        assertEquals(1, result.size());
        assertEquals("Protein", result.getFirst().getName());
        assertEquals(1, result.getFirst().getSubIngredients().size());
        IngredientDto sub = result.getFirst().getSubIngredients().getFirst();
        assertEquals("Whey-Konzentrat", sub.getName());
        assertEquals(20_000.0, sub.getMg(), 0.001);
    }

    @Test
    void parse_subIngredientWithDashBeforeParen_isAttachedToPrevious() {
        String text = "BCAA 5000 mg\n- (L-Leucin 2500 mg)";

        List<IngredientDto> result = OcrTextParser.parse(text);

        assertEquals(1, result.size());
        assertEquals(1, result.getFirst().getSubIngredients().size());
        assertEquals("L-Leucin", result.getFirst().getSubIngredients().getFirst().getName());
        assertEquals(2500.0, result.getFirst().getSubIngredients().getFirst().getMg(), 0.001);
    }

    @Test
    void parse_subIngredientWithUnicodeDash_isAttachedToPrevious() {
        // – (en-dash) before paren
        String text = "BCAA 5000 mg\n\u2013 (L-Isoleucin) 1050 mg";

        List<IngredientDto> result = OcrTextParser.parse(text);

        assertEquals(1, result.size());
        assertEquals(1, result.getFirst().getSubIngredients().size());
        assertEquals("L-Isoleucin", result.getFirst().getSubIngredients().getFirst().getName());
    }

    @Test
    void parse_subIngredientDoubleAngleNoParen_isAttachedToParent() {
        // Reproduces: "Konjakwurzel-Extrakt 6000mg / >> davon Glucomannan 4500mg"
        String text = "Konjakwurzel-Extrakt 6000 mg\n>> davon Glucomannan 4500 mg";

        List<IngredientDto> result = OcrTextParser.parse(text);

        assertEquals(1, result.size());
        assertEquals("Konjakwurzel-Extrakt", result.getFirst().getName());
        assertEquals(1, result.getFirst().getSubIngredients().size());
        IngredientDto sub = result.getFirst().getSubIngredients().getFirst();
        assertEquals("Glucomannan", sub.getName());
        assertEquals(4500.0, sub.getMg(), 0.001);
    }

    @Test
    void parse_subIngredientSingleAngleNoParen_isAttachedToParent() {
        String text = "Protein 24 g\n> Whey Konzentrat 20 g";

        List<IngredientDto> result = OcrTextParser.parse(text);

        assertEquals(1, result.size());
        assertEquals(1, result.getFirst().getSubIngredients().size());
        assertEquals("Whey Konzentrat", result.getFirst().getSubIngredients().getFirst().getName());
        assertEquals(20_000.0, result.getFirst().getSubIngredients().getFirst().getMg(), 0.001);
    }

    @Test
    void parse_subIngredientWithDoubleAngleBracket_isAttachedToPrevious() {
        String text = "BCAA 5000 mg\n>> (L-Valin) 1050 mg";

        List<IngredientDto> result = OcrTextParser.parse(text);

        assertEquals(1, result.size());
        assertEquals(1, result.getFirst().getSubIngredients().size());
        assertEquals("L-Valin", result.getFirst().getSubIngredients().getFirst().getName());
        assertEquals(1050.0, result.getFirst().getSubIngredients().getFirst().getMg(), 0.001);
    }

    @Test
    void parse_multipleSubIngredients_allAttachedToSameParent() {
        String text = "Protein 24 g\n" +
                      "(davon Whey-Konzentrat) 20 g\n" +
                      "(davon Whey-Isolat) 4 g";

        List<IngredientDto> result = OcrTextParser.parse(text);

        assertEquals(1, result.size());
        assertEquals(2, result.getFirst().getSubIngredients().size());
        assertEquals("Whey-Konzentrat", result.getFirst().getSubIngredients().get(0).getName());
        assertEquals("Whey-Isolat",     result.getFirst().getSubIngredients().get(1).getName());
    }

    @Test
    void parse_subIngredientAfterNewTopLevel_isAttachedToNewParent() {
        String text = "Protein 24 g\n" +
                      "(davon Whey-Konzentrat) 20 g\n" +
                      "Fett 2 g\n" +
                      "(davon gesättigte Fettsäuren) 1 g";

        List<IngredientDto> result = OcrTextParser.parse(text);

        assertEquals(2, result.size());
        assertEquals("Protein", result.get(0).getName());
        assertEquals(1, result.get(0).getSubIngredients().size());
        assertEquals("Whey-Konzentrat", result.get(0).getSubIngredients().getFirst().getName());
        assertEquals("Fett", result.get(1).getName());
        assertEquals(1, result.get(1).getSubIngredients().size());
        assertEquals("gesättigte Fettsäuren", result.get(1).getSubIngredients().getFirst().getName());
    }

    @Test
    void parse_subIngredientWithoutPreviousParent_isAddedAsTopLevel() {
        // Sub-ingredient line appears first — no parent yet, so add as top-level
        List<IngredientDto> result = OcrTextParser.parse("(L-Leucin) 2100 mg");

        assertEquals(1, result.size());
        assertEquals("L-Leucin", result.getFirst().getName());
        assertEquals(0, result.getFirst().getSubIngredients().size());
    }

    @Test
    void parse_subIngredientMicrogramConversion_isCorrect() {
        String text = "Vitamin-Mix 500 mg\n(davon Vitamin D3) 25 µg";

        List<IngredientDto> result = OcrTextParser.parse(text);

        assertEquals(1, result.size());
        IngredientDto sub = result.getFirst().getSubIngredients().getFirst();
        assertEquals("Vitamin D3", sub.getName());
        assertEquals(0.025, sub.getMg(), 0.0001);
    }

    // --- isSubIngredient() ---

    @Test
    void isSubIngredient_nameStartsWithParen_returnsTrue() {
        assertTrue(OcrTextParser.isSubIngredient("(davon L-Leucin)"));
    }

    @Test
    void isSubIngredient_nameStartsWithDashParen_returnsTrue() {
        assertTrue(OcrTextParser.isSubIngredient("- (L-Leucin)"));
    }

    @Test
    void isSubIngredient_nameStartsWithEnDashParen_returnsTrue() {
        assertTrue(OcrTextParser.isSubIngredient("\u2013 (L-Leucin)"));
    }

    @Test
    void isSubIngredient_nameStartsWithSingleAngle_returnsTrue() {
        assertTrue(OcrTextParser.isSubIngredient("> (L-Leucin)"));
    }

    @Test
    void isSubIngredient_nameStartsWithDoubleAngle_returnsTrue() {
        assertTrue(OcrTextParser.isSubIngredient(">> (L-Leucin)"));
    }

    @Test
    void isSubIngredient_doubleAngleWithoutParen_returnsTrue() {
        // >> without ( must also be detected as sub-ingredient
        assertTrue(OcrTextParser.isSubIngredient(">> davon Glucomannan"));
    }

    @Test
    void isSubIngredient_singleAngleWithoutParen_returnsTrue() {
        assertTrue(OcrTextParser.isSubIngredient("> davon Glucomannan"));
    }

    @Test
    void isSubIngredient_dashWithoutParen_returnsTrue() {
        assertTrue(OcrTextParser.isSubIngredient("- L-Leucin"));
    }

    @Test
    void isSubIngredient_normalName_returnsFalse() {
        assertFalse(OcrTextParser.isSubIngredient("L-Leucin"));
        assertFalse(OcrTextParser.isSubIngredient("Protein"));
        assertFalse(OcrTextParser.isSubIngredient("Vitamin D3"));
    }

    // --- cleanSubIngredientName() ---

    @Test
    void cleanSubIngredientName_removesParens() {
        assertEquals("L-Leucin", OcrTextParser.cleanSubIngredientName("(davon L-Leucin)"));
    }

    @Test
    void cleanSubIngredientName_removesLeadingDashAndParen() {
        assertEquals("L-Leucin", OcrTextParser.cleanSubIngredientName("- (L-Leucin)"));
    }

    @Test
    void cleanSubIngredientName_nameWithoutClosingParen() {
        // Closing paren may be consumed by the unit regex — still cleans correctly
        assertEquals("L-Leucin", OcrTextParser.cleanSubIngredientName("- (L-Leucin"));
    }

    // --- toMg() ---

    @Test
    void toMg_gToMg() {
        assertEquals(24_000.0, OcrTextParser.toMg(24, "g"), 0.001);
    }

    @Test
    void toMg_mgStaysAsMg() {
        assertEquals(2100.0, OcrTextParser.toMg(2100, "mg"), 0.001);
    }

    @Test
    void toMg_microgramToMg() {
        assertEquals(0.025, OcrTextParser.toMg(25, "µg"), 0.0001);
        assertEquals(0.025, OcrTextParser.toMg(25, "mcg"), 0.0001);
        assertEquals(0.025, OcrTextParser.toMg(25, "ug"), 0.0001);
    }

    // --- davon as bare sub-ingredient trigger ---

    @Test
    void isSubIngredient_barelyDavon_returnsTrue() {
        assertTrue(OcrTextParser.isSubIngredient("davon Glucomannan"));
        assertTrue(OcrTextParser.isSubIngredient("davon gesättigte Fettsäuren"));
    }

    @Test
    void parse_subIngredientBareDavon_isAttachedToParent() {
        String text = "Ballaststoffe 5 g\ndavon Glucomannan 4 g";

        List<IngredientDto> result = OcrTextParser.parse(text);

        assertEquals(1, result.size());
        assertEquals("Ballaststoffe", result.getFirst().getName());
        assertEquals(1, result.getFirst().getSubIngredients().size());
        assertEquals("Glucomannan", result.getFirst().getSubIngredients().getFirst().getName());
        assertEquals(4_000.0, result.getFirst().getSubIngredients().getFirst().getMg(), 0.001);
    }

    @Test
    void cleanSubIngredientName_stripsDavonPrefix() {
        assertEquals("Glucomannan", OcrTextParser.cleanSubIngredientName("davon Glucomannan"));
        assertEquals("L-Leucin", OcrTextParser.cleanSubIngredientName("(davon L-Leucin)"));
        assertEquals("Glucomannan", OcrTextParser.cleanSubIngredientName(">> davon Glucomannan"));
    }

    // --- OCR normalization: table-cell artefacts (leading | and °) ---

    @Test
    void parse_leadingPipeStripped() {
        // "|" is a vertical table-line OCR artefact
        List<IngredientDto> result = OcrTextParser.parse("| Kupfer 350 mcg 35%");

        assertEquals(1, result.size());
        assertEquals("Kupfer", result.getFirst().getName());
        assertEquals(0.35, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_leadingDegreeSignStripped() {
        // "°" before the name is a table-cell artefact
        List<IngredientDto> result = OcrTextParser.parse("° Molybdän 17,4mcg 35%");

        assertEquals(1, result.size());
        assertEquals("Molybdän", result.getFirst().getName());
        assertEquals(0.0174, result.getFirst().getMg(), 0.00001);
    }

    @Test
    void parse_multiplePipesStripped() {
        List<IngredientDto> result = OcrTextParser.parse("|| Mangan 500 mcg 25%");

        assertEquals(1, result.size());
        assertEquals("Mangan", result.getFirst().getName());
    }

    // --- OCR normalization: ma → mg (g misread as a) ---

    @Test
    void parse_maUnitNormalizedToMg() {
        // "18ma" → "18mg"  (OCR reads "g" as "a")
        List<IngredientDto> result = OcrTextParser.parse("Riboflavin (Vitamin B2 18ma 128%");

        assertEquals(1, result.size());
        assertEquals("Riboflavin (Vitamin B2", result.getFirst().getName());
        assertEquals(18.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_maNotNormalizedWhenPrecededByLetter() {
        // "omega" must not be affected — "ma" here is part of the name, not a unit
        List<IngredientDto> result = OcrTextParser.parse("Gamma-Oryzanol 100 mg");

        assertEquals(1, result.size());
        assertEquals("Gamma-Oryzanol", result.getFirst().getName());
        assertEquals(100.0, result.getFirst().getMg(), 0.001);
    }

    // --- zoom1 + zoom2 integration test (PSM-6 OCR text) ---

    @Test
    void parse_zoom1Psm6OcrText_parsesAllMineralAndVitaminRows() {
        // Exact PSM-6 OCR output from zoom1.png
        String zoom1 = "Sale Bestseller Bedürfnisse & Ziele v Sportnahrung v Proteine v Vitalstoff\n" +
                "\\e napscı)\n" +
                "Mineralien & Spurenelemente\n" +
                "Magnesium 75 mg 20%\n" +
                "Zink 3,2 mg 32%\n" +
                "° Molybdän 17,4mcg 35%\n" +
                "\n" +
                "Mangan 500 mcg 25%\n" +
                "\n" +
                "| Kupfer 350 mcg 35%\n" +
                "\n" +
                "| Chrom 20 mcg 50%\n" +
                "\n" +
                "| Jod 30 mcg 32%\n" +
                "\n" +
                "| Selen 25 mcg 46%\n" +
                "Vitamine\n" +
                "Vitamin C 100 mg 125%\n" +
                "Vitamin A 488 mcg / 1625 1.E. 61%\n" +
                "Vitamin D3 10 ug / 400 1.E. 200 %\n" +
                "Vitamin E 6mg/9LE. 50%\n" +
                "Vitamin K2 (MK7) 15 mcg 20%\n" +
                "Vitamin K1 23 mcg 30%\n" +
                "Thiamin (Vitamin B1) 1,4mg 127,2%\n" +
                "Riboflavin (Vitamin B? 18ma 128%";

        List<IngredientDto> result = OcrTextParser.parse(zoom1);
        List<String> names = result.stream().map(IngredientDto::getName).toList();

        // All minerals
        assertTrue(names.contains("Magnesium"),       "Magnesium");
        assertTrue(names.contains("Zink"),            "Zink");
        assertTrue(names.contains("Molybdän"),        "Molybdän");
        assertTrue(names.contains("Mangan"),          "Mangan");
        assertTrue(names.contains("Kupfer"),          "Kupfer");
        assertTrue(names.contains("Chrom"),           "Chrom");
        assertTrue(names.contains("Jod"),             "Jod");
        assertTrue(names.contains("Selen"),           "Selen");
        // Vitamins from zoom1
        assertTrue(names.contains("Vitamin C"),       "Vitamin C");
        assertTrue(names.contains("Vitamin A"),       "Vitamin A");
        assertTrue(names.contains("Vitamin D3"),      "Vitamin D3");
        assertTrue(names.contains("Vitamin E"),       "Vitamin E");
        assertTrue(names.contains("Vitamin K2 (MK7)"), "Vitamin K2");
        assertTrue(names.contains("Vitamin K1"),      "Vitamin K1");
        assertTrue(names.contains("Thiamin (Vitamin B1)"), "Thiamin");
        // Spot-check artefact-stripped values
        assertEquals(0.35,    result.stream().filter(i -> "Kupfer".equals(i.getName()))
                .findFirst().orElseThrow().getMg(), 0.001);
        assertEquals(0.0174,  result.stream().filter(i -> "Molybdän".equals(i.getName()))
                .findFirst().orElseThrow().getMg(), 0.00001);
    }

    @Test
    void parse_zoom2Psm6OcrText_parsesAllBVitaminsAndExtras() {
        // Exact PSM-6 OCR output from zoom2.png
        String zoom2 = "Sale Bestseller Bedürfnisse & Ziele v Sportnahrung v Proteine v Vitalstoff\n" +
                "Thiamin (Vitamin B1) 1,4mg 127,2%\n" +
                "Riboflavin (Vitamin B2 1,8 mg 128%\n" +
                "Niacin (Vitamin B3) 14mg 88%\n" +
                "Cholin 5mg 150%\n" +
                "DJ\n" +
                "\n" +
                "Pantothensäure (Vitamin B5) Img 124%\n" +
                "Vitamin B6 1,75 mg 136%\n" +
                "\n" +
                "| Folat (Vitamin B9) 100 mcg 50%\n" +
                "\n" +
                "| Vitamin B7 (Biotin) 72,5 mcg 136%\n" +
                "\n" +
                "| Vitamin 12 10 mcg 400 %\n" +
                "\n" +
                "| Sonstige Inhaltsstoffe\n" +
                "Coenzym Q10 5mg *r\n" +
                "Inositol 5mg *r\n" +
                "Traubenkernextrakt 21,4mg sk\n" +
                ">> davon OPC 15 mg **";

        List<IngredientDto> result = OcrTextParser.parse(zoom2);
        List<String> names = result.stream().map(IngredientDto::getName).toList();

        assertTrue(names.contains("Thiamin (Vitamin B1)"),        "Thiamin");
        assertTrue(names.contains("Niacin (Vitamin B3)"),         "Niacin");
        assertTrue(names.contains("Cholin"),                      "Cholin");
        assertTrue(names.contains("Pantothensäure (Vitamin B5)"), "Pantothensäure");
        assertTrue(names.contains("Vitamin B6"),                  "Vitamin B6");
        assertTrue(names.contains("Folat (Vitamin B9)"),          "Folat");
        assertTrue(names.contains("Vitamin B7 (Biotin)"),         "Vitamin B7");
        assertTrue(names.contains("Coenzym Q10"),                 "Coenzym Q10");
        assertTrue(names.contains("Inositol"),                    "Inositol");
        assertTrue(names.contains("Traubenkernextrakt"),          "Traubenkernextrakt");

        // OPC is sub-ingredient of Traubenkernextrakt
        IngredientDto trauben = result.stream()
                .filter(i -> "Traubenkernextrakt".equals(i.getName())).findFirst().orElseThrow();
        assertEquals(1, trauben.getSubIngredients().size());
        assertEquals("OPC", trauben.getSubIngredients().getFirst().getName());
    }

    @Test
    void parse_zoom1AndZoom2Merged_allIngredientsPresent() {
        // Merged result of both zoomed images should cover all 26 ingredients
        String zoom1 = "Magnesium 75 mg 20%\n" +
                "Zink 3,2 mg 32%\n" +
                "° Molybdän 17,4mcg 35%\n" +
                "Mangan 500 mcg 25%\n" +
                "| Kupfer 350 mcg 35%\n" +
                "| Chrom 20 mcg 50%\n" +
                "| Jod 30 mcg 32%\n" +
                "| Selen 25 mcg 46%\n" +
                "Vitamin C 100 mg 125%\n" +
                "Vitamin A 488 mcg / 1625 1.E. 61%\n" +
                "Vitamin D3 10 ug / 400 1.E. 200 %\n" +
                "Vitamin E 6mg/9LE. 50%\n" +
                "Vitamin K2 (MK7) 15 mcg 20%\n" +
                "Vitamin K1 23 mcg 30%\n" +
                "Thiamin (Vitamin B1) 1,4mg 127,2%\n" +
                "Riboflavin (Vitamin B? 18ma 128%";   // overlaps with zoom2

        String zoom2 = "Thiamin (Vitamin B1) 1,4mg 127,2%\n" +  // duplicate
                "Riboflavin (Vitamin B2 1,8 mg 128%\n" +         // different OCR name, NOT deduped
                "Niacin (Vitamin B3) 14mg 88%\n" +
                "Cholin 5mg 150%\n" +
                "Pantothensäure (Vitamin B5) Img 124%\n" +
                "Vitamin B6 1,75 mg 136%\n" +
                "| Folat (Vitamin B9) 100 mcg 50%\n" +
                "| Vitamin B7 (Biotin) 72,5 mcg 136%\n" +
                "Coenzym Q10 5mg *r\n" +
                "Inositol 5mg *r\n" +
                "Traubenkernextrakt 21,4mg sk\n" +
                ">> davon OPC 15 mg **";

        // Simulate the merge that OcrService.mergeResults() performs
        List<IngredientDto> img1 = OcrTextParser.parse(zoom1);
        List<IngredientDto> img2 = OcrTextParser.parse(zoom2);

        // Combine: img1 first, then add img2 entries not already in img1 (by name)
        java.util.Map<String, IngredientDto> byName = new java.util.LinkedHashMap<>();
        for (IngredientDto i : img1) byName.put(i.getName().toLowerCase().trim(), i);
        for (IngredientDto i : img2) byName.putIfAbsent(i.getName().toLowerCase().trim(), i);
        List<IngredientDto> merged = new java.util.ArrayList<>(byName.values());

        List<String> names = merged.stream().map(IngredientDto::getName).toList();

        // Minerals from zoom1
        assertTrue(names.contains("Magnesium"),             "Magnesium missing");
        assertTrue(names.contains("Zink"),                  "Zink missing");
        assertTrue(names.contains("Molybdän"),              "Molybdän missing");
        assertTrue(names.contains("Mangan"),                "Mangan missing");
        assertTrue(names.contains("Kupfer"),                "Kupfer missing");
        assertTrue(names.contains("Chrom"),                 "Chrom missing");
        assertTrue(names.contains("Jod"),                   "Jod missing");
        assertTrue(names.contains("Selen"),                 "Selen missing");
        // Vitamins
        assertTrue(names.contains("Vitamin C"),             "Vitamin C missing");
        assertTrue(names.contains("Vitamin D3"),            "Vitamin D3 missing");
        assertTrue(names.contains("Thiamin (Vitamin B1)"),  "Thiamin missing (dedup)");
        assertTrue(names.contains("Niacin (Vitamin B3)"),   "Niacin missing");
        assertTrue(names.contains("Pantothensäure (Vitamin B5)"), "Pantothensäure missing");
        assertTrue(names.contains("Vitamin B6"),            "Vitamin B6 missing");
        assertTrue(names.contains("Folat (Vitamin B9)"),    "Folat missing");
        assertTrue(names.contains("Vitamin B7 (Biotin)"),   "Vitamin B7 missing");
        // Extras
        assertTrue(names.contains("Coenzym Q10"),           "Coenzym Q10 missing");
        assertTrue(names.contains("Inositol"),              "Inositol missing");
        assertTrue(names.contains("Traubenkernextrakt"),    "Traubenkernextrakt missing");
        // Thiamin must appear ONLY ONCE despite being in both images
        assertEquals(1, names.stream().filter(n -> n.equalsIgnoreCase("Thiamin (Vitamin B1)")).count(),
                "Thiamin should appear exactly once");
    }



    @Test
    void parse_megUnit_normalizedToMcg() {
        // "17,4meg" → "17,4mcg"
        List<IngredientDto> result = OcrTextParser.parse("Molybdän 17,4meg 35%");

        assertEquals(1, result.size());
        assertEquals("Molybdän", result.getFirst().getName());
        assertEquals(0.0174, result.getFirst().getMg(), 0.00001);
    }

    @Test
    void parse_megUnit_normalizedToMcg_alsoForBiotin() {
        // "72,5meg" → "72,5mcg"
        List<IngredientDto> result = OcrTextParser.parse("Vitamin B7 (Biotin) 72,5meg 136%");

        assertEquals(1, result.size());
        assertEquals("Vitamin B7 (Biotin)", result.getFirst().getName());
        assertEquals(0.0725, result.getFirst().getMg(), 0.00001);
    }

    @Test
    void parse_u9Unit_normalizedToMicrog() {
        // "10 u9" → "10 µg"
        List<IngredientDto> result = OcrTextParser.parse("Vitamin D3 10 u9");

        assertEquals(1, result.size());
        assertEquals("Vitamin D3", result.getFirst().getName());
        assertEquals(0.01, result.getFirst().getMg(), 0.0001);
    }

    @Test
    void parse_19BeforeSlash_normalizedToMicrog() {
        // "10 19/400 I.E." → "10 µg/400 I.E."  (µg OCR'd as "19")
        List<IngredientDto> result = OcrTextParser.parse("Vitamin D3 10 19/400 .E. 200%");

        assertEquals(1, result.size());
        assertEquals("Vitamin D3", result.getFirst().getName());
        assertEquals(0.01, result.getFirst().getMg(), 0.0001);
    }

    @Test
    void parse_19NotBeforeSlash_isNotNormalized() {
        // "Niacin 19 mg" — "19" is the actual amount, NOT a misread unit
        List<IngredientDto> result = OcrTextParser.parse("Niacin 19 mg");

        assertEquals(1, result.size());
        assertEquals("Niacin", result.getFirst().getName());
        assertEquals(19.0, result.getFirst().getMg(), 0.001);
    }

    // --- OCR normalization: digit/letter misreadings ---

    @Test
    void parse_aAfterDigitBeforeUnit_normalizedToCommaFour() {
        // "1amg" → "1,4mg"  (OCR misreads ",4" as "a")
        List<IngredientDto> result = OcrTextParser.parse("Thiamin (Vitamin B1) 1amg 1272%");

        assertEquals(1, result.size());
        assertEquals("Thiamin (Vitamin B1)", result.getFirst().getName());
        assertEquals(1.4, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_AAfterCommaBeforeUnit_normalizedToFour() {
        // "21,Amg" → "21,4mg"  (OCR misreads digit "4" as uppercase "A")
        List<IngredientDto> result = OcrTextParser.parse("Traubenkernextrakt 21,Amg bu");

        assertEquals(1, result.size());
        assertEquals("Traubenkernextrakt", result.getFirst().getName());
        assertEquals(21.4, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_IBeforeUnit_normalizedToOne() {
        // "Img" → "1mg"  (OCR misreads digit "1" or "9" as uppercase "I")
        // Value captured is 1 mg (OCR digit loss), but the ingredient name is preserved.
        List<IngredientDto> result = OcrTextParser.parse("Pantothensäure (Vitamin B5) Img 124%");

        assertEquals(1, result.size());
        assertEquals("Pantothensäure (Vitamin B5)", result.getFirst().getName());
        assertEquals(1.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_INotPrecededByLetter_onlyNormalized() {
        // "Inositol 5mg" — the leading "I" of the ingredient name must NOT be consumed
        List<IngredientDto> result = OcrTextParser.parse("Inositol 5mg");

        assertEquals(1, result.size());
        assertEquals("Inositol", result.getFirst().getName());
        assertEquals(5.0, result.getFirst().getMg(), 0.001);
    }

    // --- Vivi label full integration test ---

    @Test
    void parse_viviLabelOcrText_parsesAllExpectedIngredients() {
        // Exact OCR output from the vivi label including all known OCR errors
        String ocrText =
                "Sale Bestseller Bedürfnisse & Ziele v Sportnahrung v Proteine v Vitalstoffe -\n" +
                "\n" +
                "Mineralien & Spurenelemente\n" +
                "\n" +
                "Magnesium 75mg 20%\n" +
                "Zink 32mg 32%\n" +
                "Molybdän 17,4meg 35%\n" +      // meg → mcg
                "Mangan 500mcg 25%\n" +
                "Kupfer 350mcg 35%\n" +
                "Chrom 20 mcg 50%\n" +
                "Jod 30 mcg 32%\n" +
                "Selen 25 mcg 46%\n" +
                "Vitamine\n" +
                "\n" +
                "Vitamin C 100mg 125%\n" +
                "Vitamin A 488 mcg / 1625 LE. 61%\n" +
                "Vitamin D3 10 19/400 .E. 200%\n" +  // 19 → µg
                "Vitamin E 6mg/91E. 50%\n" +
                "Vitamin K2 (MK7) 15 mcg 20%\n" +
                "Vitamin K1 23 mcg 30%\n" +
                "Thiamin (Vitamin B1) 1amg 1272%\n" + // 1amg → 1,4mg
                "Riboflavin (Vitamin B2 18mg 128%\n" +
                "Niacin (Vitamin B3) 14mg 88%\n" +
                "Cholin 5mg 150%\n" +
                "Pantothensäure (Vitamin B5) Img 124%\n" + // Img → 1mg
                "Vitamin B6 1,75mg 136%\n" +
                "Folat (Vitamin B9) 100mcg 50%\n" +
                "Vitamin B7 (Biotin) 72,5meg 136%\n" + // meg → mcg
                "Vitamin B12 10 mcg 400%\n" +
                "\n" +
                "Sonstige Inhaltsstoffe\n" +
                "\n" +
                "Coenzym Q10 5mg bu\n" +
                "Inositol 5mg bu\n" +
                "Traubenkernextrakt 21,Amg bu\n" +  // 21,Amg → 21,4mg
                "\n" +
                ">> davon OPC 15mg bu";

        List<IngredientDto> result = OcrTextParser.parse(ocrText);
        List<String> names = result.stream().map(IngredientDto::getName).toList();

        // Formerly-missing ingredients must now be present
        assertTrue(names.contains("Molybdän"),                     "Molybdän missing");
        assertTrue(names.contains("Vitamin D3"),                   "Vitamin D3 missing");
        assertTrue(names.contains("Thiamin (Vitamin B1)"),         "Thiamin missing");
        assertTrue(names.contains("Pantothensäure (Vitamin B5)"),  "Pantothensäure missing");
        assertTrue(names.contains("Vitamin B7 (Biotin)"),          "Vitamin B7 (Biotin) missing");
        assertTrue(names.contains("Traubenkernextrakt"),           "Traubenkernextrakt missing");

        // OPC must be sub-ingredient of Traubenkernextrakt
        IngredientDto trauben = result.stream()
                .filter(i -> "Traubenkernextrakt".equals(i.getName()))
                .findFirst().orElseThrow();
        assertEquals(1, trauben.getSubIngredients().size());
        assertEquals("OPC", trauben.getSubIngredients().getFirst().getName());
        assertEquals(15.0, trauben.getSubIngredients().getFirst().getMg(), 0.001);

        // Other expected top-level ingredients
        assertTrue(names.contains("Magnesium"),         "Magnesium missing");
        assertTrue(names.contains("Mangan"),            "Mangan missing");
        assertTrue(names.contains("Vitamin C"),         "Vitamin C missing");
        assertTrue(names.contains("Vitamin A"),         "Vitamin A missing");
        assertTrue(names.contains("Vitamin E"),         "Vitamin E missing");
        assertTrue(names.contains("Vitamin K2 (MK7)"), "Vitamin K2 missing");
        assertTrue(names.contains("Niacin (Vitamin B3)"), "Niacin missing");
        assertTrue(names.contains("Cholin"),            "Cholin missing");
        assertTrue(names.contains("Vitamin B6"),        "Vitamin B6 missing");
        assertTrue(names.contains("Folat (Vitamin B9)"), "Folat missing");
        assertTrue(names.contains("Vitamin B12"),       "Vitamin B12 missing");
        assertTrue(names.contains("Coenzym Q10"),       "Coenzym Q10 missing");
        assertTrue(names.contains("Inositol"),          "Inositol missing");

        // Spot-check correct values for the OCR-corrected ingredients
        assertEquals(0.0174, result.stream()
                .filter(i -> "Molybdän".equals(i.getName())).findFirst().orElseThrow().getMg(), 0.00001);
        assertEquals(0.01,   result.stream()
                .filter(i -> "Vitamin D3".equals(i.getName())).findFirst().orElseThrow().getMg(), 0.0001);
        assertEquals(1.4,    result.stream()
                .filter(i -> "Thiamin (Vitamin B1)".equals(i.getName())).findFirst().orElseThrow().getMg(), 0.001);
        assertEquals(21.4,   trauben.getMg(), 0.001);
        assertEquals(0.0725, result.stream()
                .filter(i -> "Vitamin B7 (Biotin)".equals(i.getName())).findFirst().orElseThrow().getMg(), 0.00001);
    }



    @Test
    void parse_twoColumnLine_mgMg_usesSecondValue() {
        // "Name  val1 unit1  val2 unit2" — second value is the per-portion amount
        List<IngredientDto> result = OcrTextParser.parse("L-Leucin 4200 mg 2100 mg");

        assertEquals(1, result.size());
        assertEquals("L-Leucin", result.getFirst().getName());
        assertEquals(2100.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_twoColumnLine_gramGram_usesSecondValue() {
        List<IngredientDto> result = OcrTextParser.parse("Eiweiß 70 g 30 g");

        assertEquals(1, result.size());
        assertEquals("Eiweiß", result.getFirst().getName());
        assertEquals(30_000.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_twoColumnLine_mixedUnits_usesSecondValueWithItsUnit() {
        // First column mcg, second column µg — second value + second unit is used
        List<IngredientDto> result = OcrTextParser.parse("Vitamin D3 50 mcg 25 µg");

        assertEquals(1, result.size());
        assertEquals("Vitamin D3", result.getFirst().getName());
        assertEquals(0.025, result.getFirst().getMg(), 0.0001); // 25 µg → 0.025 mg
    }

    @Test
    void parse_twoColumnLine_doesNotMatchSingleValueLine() {
        // A plain single-value line must NOT be consumed by TWO_COLUMN_LINE
        List<IngredientDto> result = OcrTextParser.parse("L-Leucin 2100 mg");

        assertEquals(1, result.size());
        assertEquals(2100.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_twoColumnLines_multipleRows_allUsesSecondValue() {
        String text = "Protein 70 g 30 g\n" +
                      "Fett 5 g 2 g\n" +
                      "Kohlenhydrate 10 g 4 g";

        List<IngredientDto> result = OcrTextParser.parse(text);

        assertEquals(3, result.size());
        assertEquals("Protein",       result.get(0).getName());
        assertEquals(30_000.0,        result.get(0).getMg(), 0.001);
        assertEquals("Fett",          result.get(1).getName());
        assertEquals(2_000.0,         result.get(1).getMg(), 0.001);
        assertEquals("Kohlenhydrate", result.get(2).getName());
        assertEquals(4_000.0,         result.get(2).getMg(), 0.001);
    }



    @Test
    void parse_spaceThousandsSeparator_parsesCorrectly() {
        List<IngredientDto> result = OcrTextParser.parse("L-Glutaminsäure 4 500 mg");

        assertEquals(1, result.size());
        assertEquals(4500.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_spaceThousandsInGrams_parsesCorrectly() {
        List<IngredientDto> result = OcrTextParser.parse("Kohlenhydrate 1 000 g");

        assertEquals(1, result.size());
        assertEquals(1_000_000.0, result.getFirst().getMg(), 0.001);
    }

    // --- Multi-line name / value pairs ---

    @Test
    void parse_nameOnOneLine_amountOnNextLine_parsed() {
        // OCR sometimes puts the ingredient name and its amount on separate lines
        String text = "Pomelo Extrakt\n100 mg";

        List<IngredientDto> result = OcrTextParser.parse(text);

        assertEquals(1, result.size());
        assertEquals("Pomelo Extrakt", result.getFirst().getName());
        assertEquals(100.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_nameOnOneLine_blankLine_amountOnLaterLine_parsed() {
        // Blank line between name and value — pendingName must survive blank lines
        String text = "Pomelo Extrakt\n\n100 mg";

        List<IngredientDto> result = OcrTextParser.parse(text);

        assertEquals(1, result.size());
        assertEquals("Pomelo Extrakt", result.getFirst().getName());
        assertEquals(100.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_mixedSingleAndMultiLine_allParsed() {
        // Normal single-line entries followed by a multi-line entry
        String text = "Taurin 1569 mg\nPomelo Extrakt\n\n100 mg\nL-Theanin 100 mg";

        List<IngredientDto> result = OcrTextParser.parse(text);

        assertEquals(3, result.size());
        assertEquals("Taurin",         result.get(0).getName());
        assertEquals(1569.0,           result.get(0).getMg(), 0.001);
        assertEquals("Pomelo Extrakt", result.get(1).getName());
        assertEquals(100.0,            result.get(1).getMg(), 0.001);
        assertEquals("L-Theanin",      result.get(2).getName());
        assertEquals(100.0,            result.get(2).getMg(), 0.001);
    }

    @Test
    void parse_multiLineWithDavonSubIngredient_correctHierarchy() {
        // davon sub-ingredient following a multi-line top-level entry
        String text = "Grüner Kaffeebohnen-Extrakt\n500 mg\ndavon Chlorogensäure 250 mg";

        List<IngredientDto> result = OcrTextParser.parse(text);

        assertEquals(1, result.size());
        assertEquals("Grüner Kaffeebohnen-Extrakt", result.getFirst().getName());
        assertEquals(500.0, result.getFirst().getMg(), 0.001);
        assertEquals(1, result.getFirst().getSubIngredients().size());
        assertEquals("Chlorogensäure", result.getFirst().getSubIngredients().getFirst().getName());
    }

    // --- Noise filtering: carry-forward distance limit ---

    @Test
    void parse_noiseAfterIngredientsDoesNotCreateFalseEntry() {
        // After the ingredient block ends, noise text must not be combined with a
        // stray unit value further down (e.g. "230g" as a serving-size hint).
        String text = "Konjakwurzel-Extrakt 4500 mg\n" +
                      "Chrom 200 mcg\n" +
                      "\n" +
                      "3x3 Kapseln taeglich\n" +           // noise line 1 → streak=1
                      "taeglich vor den Mahlzeiten\n" +    // noise line 2 → streak=2
                      "Ernaehrung mit reichlich Wasser\n" + // noise line 3 → streak=3 > 2 → pendingName=null
                      "230 g";                              // amount-only, pendingName=null → skipped

        List<IngredientDto> result = OcrTextParser.parse(text);

        assertEquals(2, result.size());
        assertEquals("Konjakwurzel-Extrakt", result.get(0).getName());
        assertEquals("Chrom",               result.get(1).getName());
    }

    @Test
    void parse_preIngredientHeaderDoesNotCreateFalseEntry() {
        // Text before the actual ingredient table (headings, disclaimers) must be
        // ignored even if a unit eventually appears.
        String text = "(9 Kapseln) Grüntee Extrakt, pflanzliche\n" +  // streak=1
                      "Kapselhuelle: HPMC\n" +                         // streak=2
                      "Chromium-Picolinat.\n" +                        // streak=3 > 2 → pendingName=null
                      "\n" +
                      "Konjakwurzel-Extrakt 4500 mg\n" +
                      "Chrom 200 mcg";

        List<IngredientDto> result = OcrTextParser.parse(text);

        assertEquals(2, result.size());
        assertEquals("Konjakwurzel-Extrakt", result.get(0).getName());
        assertEquals("Chrom",               result.get(1).getName());
    }

    @Test
    void parse_realWorldLabelWithNoise_parsesOnlyIngredients() {
        // Full realistic label from user report
        String text =
                "(9 Kapseln) Grüntee Extrakt, pflanzliche\n" +
                "Kapselhuelle: Hydroxypropylmethyl-\n" +
                "cellulose, Ceylon Zimt Extrakt,\n" +
                "Silberweidenrinden Extrakt,\n" +
                "Chromium-Picolinat.\n" +
                "\n" +
                "Konjakwurzel-Extrakt 4500 mg\n" +
                ">> davon Glucomannan 4275 mg\n" +
                "Grüntee Extrakt 1125 mg\n" +
                ">> davon Polyphenols 1103 mg\n" +
                ">> davon EGCG 506 mg\n" +
                "Ceylon Zimt Extrakt 450 mg\n" +
                "Silberweidenrinden Extrakt 450 mg\n" +
                ">> davon Salicin 68 mg\n" +
                "Chrom 200 mcg\n" +
                "\n" +
                "3x3 Kapseln\n" +
                "taeglich vor den Mahlzeiten\n" +
                "Ernaehrung mit reichlich Wasser\n" +
                "MORGENS & NACHMITTAGS\n" +
                "230 g";

        List<IngredientDto> result = OcrTextParser.parse(text);

        // Exactly the 5 top-level ingredients — no noise entries
        assertEquals(5, result.size());
        assertEquals("Konjakwurzel-Extrakt",          result.get(0).getName());
        assertEquals(4500.0,                           result.get(0).getMg(), 0.001);
        assertEquals(1,                                result.get(0).getSubIngredients().size());
        assertEquals("Glucomannan",                    result.get(0).getSubIngredients().getFirst().getName());

        assertEquals("Grüntee Extrakt",                result.get(1).getName());
        assertEquals(1125.0,                           result.get(1).getMg(), 0.001);
        assertEquals(2,                                result.get(1).getSubIngredients().size());
        assertEquals("Polyphenols",                    result.get(1).getSubIngredients().get(0).getName());
        assertEquals("EGCG",                           result.get(1).getSubIngredients().get(1).getName());

        assertEquals("Ceylon Zimt Extrakt",            result.get(2).getName());
        assertEquals("Silberweidenrinden Extrakt",     result.get(3).getName());
        assertEquals(1,                                result.get(3).getSubIngredients().size());
        assertEquals("Salicin",                        result.get(3).getSubIngredients().getFirst().getName());

        assertEquals("Chrom",                          result.get(4).getName());
        assertEquals(0.2,                              result.get(4).getMg(), 0.0001); // 200 mcg → 0.2 mg
    }

    // --- joinSplitUnitLines() ---

    @Test
    void joinSplitUnitLines_numberThenUnitOnNextLine_joinsLines() {
        String input = "L-Citrullin Malat 10000\nmg";
        String result = OcrTextParser.joinSplitUnitLines(input);
        assertTrue(result.contains("L-Citrullin Malat 10000 mg"), "Expected joined line, got: " + result);
    }

    @Test
    void joinSplitUnitLines_numberThenUnitAfterBlankLine_joinsLines() {
        String input = "L-Citrullin Malat 10000\n\nmg";
        String result = OcrTextParser.joinSplitUnitLines(input);
        assertTrue(result.contains("L-Citrullin Malat 10000 mg"), "Expected joined line across blank, got: " + result);
    }

    @Test
    void joinSplitUnitLines_unitAlreadyOnSameLine_unchanged() {
        String input = "L-Citrullin Malat 10000 mg";
        String result = OcrTextParser.joinSplitUnitLines(input);
        assertTrue(result.contains("L-Citrullin Malat 10000 mg"));
    }

    @Test
    void joinSplitUnitLines_noSplitNeeded_unchanged() {
        String input = "Protein 24 g\nL-Leucin 2100 mg";
        String result = OcrTextParser.joinSplitUnitLines(input);
        assertTrue(result.contains("Protein 24 g"));
        assertTrue(result.contains("L-Leucin 2100 mg"));
    }

    @Test
    void joinSplitUnitLines_null_returnsNull() {
        assertNull(OcrTextParser.joinSplitUnitLines(null));
    }

    @Test
    void joinSplitUnitLines_unitLineIsConsumed_notEmittedSeparately() {
        // The "mg" line must not appear as a standalone line after join
        String input = "L-Citrullin Malat 10000\nmg\nTaurin 2000 mg";
        String result = OcrTextParser.joinSplitUnitLines(input);
        String[] lines = result.split("\\r?\\n");
        long mgOnlyLines = java.util.Arrays.stream(lines)
                .filter(l -> l.trim().equals("mg"))
                .count();
        assertEquals(0, mgOnlyLines, "Unit-only line should be consumed, not emitted separately");
    }

    @Test
    void joinSplitUnitLines_gUnit_joinsCorrectly() {
        String input = "Eiweiß 24\ng";
        String result = OcrTextParser.joinSplitUnitLines(input);
        assertTrue(result.contains("Eiweiß 24 g"));
    }

    @Test
    void joinSplitUnitLines_mcgUnit_joinsCorrectly() {
        String input = "Vitamin D3 25\nmcg";
        String result = OcrTextParser.joinSplitUnitLines(input);
        assertTrue(result.contains("Vitamin D3 25 mcg"));
    }

    // --- cleanTopLevelName() ---

    @Test
    void cleanTopLevelName_leadingDoubleQuote_stripped() {
        // OCR artefact: double-quote before the name
        assertEquals("Niacin", OcrTextParser.cleanTopLevelName("\"Niacin"));
    }

    @Test
    void cleanTopLevelName_leadingDigitPlusSpace_stripped() {
        // OCR artefact: line-number digit before the name
        assertEquals("Calcium", OcrTextParser.cleanTopLevelName("3 Calcium"));
    }

    @Test
    void cleanTopLevelName_leadingMultiDigitPlusSpace_stripped() {
        assertEquals("Zink", OcrTextParser.cleanTopLevelName("12 Zink"));
    }

    @Test
    void cleanTopLevelName_leadingCurlyBrace_stripped() {
        assertEquals("Extrakt", OcrTextParser.cleanTopLevelName("{ Extrakt"));
    }

    @Test
    void cleanTopLevelName_normalName_unchanged() {
        assertEquals("L-Leucin", OcrTextParser.cleanTopLevelName("L-Leucin"));
        assertEquals("Vitamin D3", OcrTextParser.cleanTopLevelName("Vitamin D3"));
    }

    @Test
    void cleanTopLevelName_digitNotFollowedByCapital_unchanged() {
        // "3 calcium" (lowercase) must NOT be stripped — only strip before uppercase
        assertEquals("3 calcium", OcrTextParser.cleanTopLevelName("3 calcium"));
    }

    // --- cleanSubIngredientName() with OCR noise prefixes ---

    @Test
    void cleanSubIngredientName_curlyBracePlusDavon_returnsPureName() {
        // "{ davon Piperin" is a sub-ingredient with table-border artefact
        assertEquals("Piperin", OcrTextParser.cleanSubIngredientName("{ davon Piperin"));
    }

    @Test
    void cleanSubIngredientName_doubleQuotePlusName_returnsPureName() {
        assertEquals("Niacin", OcrTextParser.cleanSubIngredientName("\"Niacin"));
    }

    // --- isSubIngredient() Case 4 ---

    @Test
    void isSubIngredient_curlyBraceDavon_returnsTrue() {
        assertTrue(OcrTextParser.isSubIngredient("{ davon Piperin"));
    }

    @Test
    void isSubIngredient_doubleQuoteDavon_returnsTrue() {
        assertTrue(OcrTextParser.isSubIngredient("\"davon Piperin"));
    }

    @Test
    void isSubIngredient_singleQuoteDavon_returnsTrue() {
        assertTrue(OcrTextParser.isSubIngredient("'davon Glucomannan"));
    }

    // --- parse() integration tests for OCR noise prefixes ---

    @Test
    void parse_curlyBraceDavonSubIngredient_isAttachedToParent() {
        // "{ davon Piperin" is a table-border artefact followed by sub-ingredient marker
        String text = "Schwarzer Pfeffer Extrakt 10 mg\n{ davon Piperin 10,5 mg";

        List<IngredientDto> result = OcrTextParser.parse(text);

        assertEquals(1, result.size());
        assertEquals("Schwarzer Pfeffer Extrakt", result.getFirst().getName());
        assertEquals(1, result.getFirst().getSubIngredients().size());
        IngredientDto sub = result.getFirst().getSubIngredients().getFirst();
        assertEquals("Piperin", sub.getName());
        assertEquals(10.5, sub.getMg(), 0.001);
    }

    @Test
    void parse_doubleQuotePrefixTopLevel_strippedToCorrectName() {
        // OCR artefact double-quote before ingredient name
        List<IngredientDto> result = OcrTextParser.parse("\"Niacin 14 mg");

        assertEquals(1, result.size());
        assertEquals("Niacin", result.getFirst().getName());
        assertEquals(14.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_lineNumberPrefixTopLevel_strippedToCorrectName() {
        // OCR artefact line-number "3" before ingredient name
        List<IngredientDto> result = OcrTextParser.parse("3 Calcium 500 mg");

        assertEquals(1, result.size());
        assertEquals("Calcium", result.getFirst().getName());
        assertEquals(500.0, result.getFirst().getMg(), 0.001);
    }

    // ── New OCR fixes: line-level character corrections ────────────────────

    @Test
    void parse_copyrightSymbol_normalizedToLetterC() {
        // "Vitamin ©" → "Vitamin C" (© is OCR misread of letter C)
        List<IngredientDto> result = OcrTextParser.parse("Vitamin © 200 mg");

        assertEquals(1, result.size());
        assertEquals("Vitamin C", result.getFirst().getName());
        assertEquals(200.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_turkishDotlessI_normalizedToDigitOne() {
        // "Vitamin Bı" → "Vitamin B1" (ı = U+0131 is OCR misread of digit 1)
        List<IngredientDto> result = OcrTextParser.parse("Vitamin B\u0131 3,5 mg");

        assertEquals(1, result.size());
        assertEquals("Vitamin B1", result.getFirst().getName());
        assertEquals(3.5, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_vitaminWithoutSpace_spaceInserted() {
        // "VitaminE" → "Vitamin E"
        List<IngredientDto> result = OcrTextParser.parse("VitaminE 12 mg");

        assertEquals(1, result.size());
        assertEquals("Vitamin E", result.getFirst().getName());
        assertEquals(12.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_vitaminBWithoutSpace_spaceInserted() {
        // "VitaminB12" → "Vitamin B12"
        List<IngredientDto> result = OcrTextParser.parse("VitaminB12 63 mcg");

        assertEquals(1, result.size());
        assertEquals("Vitamin B12", result.getFirst().getName());
        assertEquals(0.063, result.getFirst().getMg(), 0.0001);
    }

    @Test
    void parse_vitaminCopyrightSign_parsedAsVitaminC() {
        // "Vitamin ©" — U+00A9 COPYRIGHT SIGN is OCR misread of capital C on some label fonts
        List<IngredientDto> result = OcrTextParser.parse("Vitamin \u00a9 200 mg");

        assertEquals(1, result.size());
        assertEquals("Vitamin C", result.getFirst().getName());
        assertEquals(200.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_vitaminLowercaseCPrefixUppercaseC_parsedAsVitaminC() {
        // "Vitamin cC" — OCR double-reads the C: lowercase artifact + actual uppercase.
        // Seen on Bodylab labels where the 'C' has a stylised lowercase-c shadow.
        List<IngredientDto> result = OcrTextParser.parse("Vitamin cC 229 mg");

        assertEquals(1, result.size());
        assertEquals("Vitamin C", result.getFirst().getName());
        assertEquals(229.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_vitaminLowercasePrefixUppercaseLetter_generalCase() {
        // Generalisation: any "Vitamin [lowercase][uppercase]" pattern → "Vitamin [uppercase]"
        // e.g. "Vitamin eE" (rare but defensive) → "Vitamin E"
        List<IngredientDto> result = OcrTextParser.parse("Vitamin eE 12 mg");

        assertEquals(1, result.size());
        assertEquals("Vitamin E", result.getFirst().getName());
    }

    @Test
    void parse_calclum_normalizedToCalcium() {
        List<IngredientDto> result = OcrTextParser.parse("Calclum 420 mg");

        assertEquals(1, result.size());
        assertEquals("Calcium", result.getFirst().getName());
        assertEquals(420.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_selenlum_normalizedToSelenium() {
        List<IngredientDto> result = OcrTextParser.parse("Selenlum 82 ug");

        assertEquals(1, result.size());
        assertEquals("Selenium", result.getFirst().getName());
        assertEquals(0.082, result.getFirst().getMg(), 0.0001);
    }

    @Test
    void parse_blotin_normalizedToBiotin() {
        List<IngredientDto> result = OcrTextParser.parse("Blotin 60 ug");

        assertEquals(1, result.size());
        assertEquals("Biotin", result.getFirst().getName());
        assertEquals(0.06, result.getFirst().getMg(), 0.0001);
    }

    @Test
    void parse_zine_normalizedToZinc() {
        List<IngredientDto> result = OcrTextParser.parse("Zine 20 mg");

        assertEquals(1, result.size());
        assertEquals("Zinc", result.getFirst().getName());
        assertEquals(20.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_vitaminDa_normalizedToVitaminD3() {
        // "Vitamin Da" → "Vitamin D3" (a = OCR misread of digit 3)
        List<IngredientDto> result = OcrTextParser.parse("Vitamin Da 25 ug");

        assertEquals(1, result.size());
        assertEquals("Vitamin D3", result.getFirst().getName());
        assertEquals(0.025, result.getFirst().getMg(), 0.0001);
    }

    // ── cleanTopLevelName: trailing noise stripping ────────────────────────

    @Test
    void cleanTopLevelName_trailingDash_stripped() {
        assertEquals("Vitamin K2", OcrTextParser.cleanTopLevelName("Vitamin K2 -"));
    }

    @Test
    void cleanTopLevelName_trailingLargeNumber_stripped() {
        // Pro-100G value (≥4 digits) bleeding into name
        assertEquals("Selenium", OcrTextParser.cleanTopLevelName("Selenium 1397,73"));
    }

    @Test
    void cleanTopLevelName_trailingShortLowercaseToken_stripped() {
        // "au," is a 2-char lowercase noise suffix
        assertEquals("Vitamin B6", OcrTextParser.cleanTopLevelName("Vitamin B6 au,"));
    }

    @Test
    void cleanTopLevelName_trailingOpenParen_stripped() {
        assertEquals("Vitamin K2", OcrTextParser.cleanTopLevelName("Vitamin K2 - ("));
    }

    @Test
    void cleanTopLevelName_smallVitaminDesignatorPreserved() {
        // "B12", "K2", "D3" must NOT be stripped — only 1-2 digits, no space before them
        assertEquals("Vitamin B12", OcrTextParser.cleanTopLevelName("Vitamin B12"));
        assertEquals("Vitamin K2", OcrTextParser.cleanTopLevelName("Vitamin K2"));
        assertEquals("Vitamin D3", OcrTextParser.cleanTopLevelName("Vitamin D3"));
    }

    @Test
    void parse_trailingDashInName_strippedToCleanName() {
        // "Vitamin K2 - 0,0 mg" → Vitamin K2, 0.0 mg
        List<IngredientDto> result = OcrTextParser.parse("Vitamin K2 - 0,0 mg");

        assertEquals(1, result.size());
        assertEquals("Vitamin K2", result.getFirst().getName());
        assertEquals(0.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_trailingLowercaseNoise_strippedToCleanName() {
        // "Vitamin B6 au, 4,8 mg" — "au," is noise between name and amount
        List<IngredientDto> result = OcrTextParser.parse("Vitamin B6 au, 4,8 mg");

        assertEquals(1, result.size());
        assertEquals("Vitamin B6", result.getFirst().getName());
        assertEquals(4.8, result.getFirst().getMg(), 0.001);
    }

    // ── bodylab2 integration test (two-column OCR, mixed language label) ──

    @Test
    void parse_bodylab2OcrText_parsesAllIngredientsCorrectly() {
        // Simulated Tesseract output from bodylab2 — a two-column (Pro 100G | Pro Portion)
        // English-language EU supplement table with various OCR artefacts.
        String ocr =
                "Vitamine Pro 100G Pro Portion % RM\n" +
                "Vitamin A 13636,36 ug 800 ug 100 %\n" +
                "VitaminE 204,55 mg 12 mg 100 %\n" +           // no space → insert
                "Vitamin © 3409,09 mg 200 mg 250 %\n" +         // © → C
                "Vitamin B\u0131 59,66 mg 3,5 mg 318 %\n" +     // ı → 1
                "Vitamin B2 57,95 mg 3,4 mg 243 %\n" +
                "Vitamin B3 545,45 mg 32 mg 200 %\n" +
                "Vitamin B6 au, 81,82 mg 4,8 mg 343 %\n" +      // trailing noise
                "Vitamin B12 63,07 ug 3,7 ug 148 %\n" +
                "Blotin 1022,73 ug 60 ug 120 %\n" +             // Blotin → Biotin
                "Vitamin B5 306,82 mg 18 mg 300 %\n" +
                "Vitamin Da 426,14 ug 25 ug 500 %\n" +           // Da → D3
                "Mineralien Pro 100G Pro Portion % RM\n" +        // header — skipped
                "Calclum 7159,09 mg 420 mg 53 %\n" +             // Calclum → Calcium
                "Magnesium 3579,55 mg 210 mg 56 %\n" +
                "Zine 340,91 mg 20 mg 200 %\n" +                 // Zine → Zinc
                "Selenlum 1397,73 ug 82 ug 149 %\n" +            // Selenlum → Selenium
                "Vitamin K2 - 0,0 mg 0,0 mg 100 %";             // trailing dash

        List<IngredientDto> result = OcrTextParser.parse(ocr);
        List<String> names = result.stream().map(IngredientDto::getName).toList();

        // Name fixes
        assertTrue(names.contains("Vitamin E"),   "VitaminE → Vitamin E");
        assertTrue(names.contains("Vitamin C"),   "© → C");
        assertTrue(names.contains("Vitamin B1"),  "Bı → B1");
        assertTrue(names.contains("Vitamin B6"),  "trailing au, stripped");
        assertTrue(names.contains("Biotin"),      "Blotin → Biotin");
        assertTrue(names.contains("Vitamin D3"),  "Da → D3");
        assertTrue(names.contains("Calcium"),     "Calclum → Calcium");
        assertTrue(names.contains("Zinc"),        "Zine → Zinc");
        assertTrue(names.contains("Selenium"),    "Selenlum → Selenium");
        assertTrue(names.contains("Vitamin K2"),  "trailing dash stripped");

        // Other expected top-level names
        assertTrue(names.contains("Vitamin A"),   "Vitamin A");
        assertTrue(names.contains("Vitamin B2"),  "Vitamin B2");
        assertTrue(names.contains("Vitamin B3"),  "Vitamin B3");
        assertTrue(names.contains("Vitamin B12"), "Vitamin B12");
        assertTrue(names.contains("Vitamin B5"),  "Vitamin B5");
        assertTrue(names.contains("Magnesium"),   "Magnesium");

        // Per-portion values (second column)
        assertEquals(12.0,   findMg(result, "Vitamin E"),   0.001);
        assertEquals(200.0,  findMg(result, "Vitamin C"),   0.001);
        assertEquals(3.5,    findMg(result, "Vitamin B1"),  0.001);
        assertEquals(4.8,    findMg(result, "Vitamin B6"),  0.001);
        assertEquals(0.06,   findMg(result, "Biotin"),      0.001);
        assertEquals(0.025,  findMg(result, "Vitamin D3"),  0.0001);
        assertEquals(420.0,  findMg(result, "Calcium"),     0.001);
        assertEquals(20.0,   findMg(result, "Zinc"),        0.001);
        assertEquals(0.082,  findMg(result, "Selenium"),    0.001);
    }

    private static double findMg(List<IngredientDto> list, String name) {
        return list.stream()
                .filter(i -> name.equals(i.getName()))
                .findFirst()
                .map(IngredientDto::getMg)
                .orElseThrow(() -> new AssertionError("Ingredient not found: " + name));
    }

    // --- parse() integration with joinSplitUnitLines ---

    @Test
    void parse_splitAmountAndUnit_parsesCorrectly() {
        List<IngredientDto> result = OcrTextParser.parse("L-Citrullin Malat 10000\nmg");

        assertEquals(1, result.size());
        assertEquals("L-Citrullin Malat", result.getFirst().getName());
        assertEquals(10000.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_splitAmountAndUnitWithBlankLine_parsesCorrectly() {
        List<IngredientDto> result = OcrTextParser.parse("L-Citrullin Malat 10000\n\nmg");

        assertEquals(1, result.size());
        assertEquals("L-Citrullin Malat", result.getFirst().getName());
        assertEquals(10000.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_splitUnitAmongOtherIngredients_allParsed() {
        String text = "Taurin 2000 mg\nL-Citrullin Malat 10000\nmg\nKoffein 200 mg";

        List<IngredientDto> result = OcrTextParser.parse(text);

        assertEquals(3, result.size());
        assertEquals("Taurin",            result.get(0).getName());
        assertEquals(2000.0,              result.get(0).getMg(), 0.001);
        assertEquals("L-Citrullin Malat", result.get(1).getName());
        assertEquals(10000.0,             result.get(1).getMg(), 0.001);
        assertEquals("Koffein",           result.get(2).getName());
        assertEquals(200.0,               result.get(2).getMg(), 0.001);
    }

    // ── OCR noise: leading chars <, _, }, ) ──────────────────────────────────

    @Test
    void parse_leadingLessThan_strippedAndParsedAsTopLevel() {
        List<IngredientDto> result = OcrTextParser.parse("_ Glucuronolacton (500 mg)");
        assertEquals(1, result.size());
        assertEquals("Glucuronolacton", result.getFirst().getName());
        assertEquals(500.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_leadingUnderscore_strippedAndParsedAsTopLevel() {
        List<IngredientDto> result = OcrTextParser.parse("< Taurin (1500 mg)");
        assertEquals(1, result.size());
        assertEquals("Taurin", result.getFirst().getName());
        assertEquals(1500.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_fullwidthUnderscore_strippedAndParsedAsTopLevel() {
        // U+FF3F FULLWIDTH LOW LINE (＿) — OCR produces this instead of ASCII _ on some labels
        List<IngredientDto> result = OcrTextParser.parse("\uFF3F Glucuronolacton (500 mg)");
        assertEquals(1, result.size());
        assertEquals("Glucuronolacton", result.getFirst().getName());
        assertEquals(500.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_leadingClosingBraceWithDavon_parsedAsSubIngredient() {
        // "} davon aus Guarana Extrakt" appears under Koffein
        String text = "Koffein (255 mg)\n} davon aus Guarana Extrakt (55 mg)";
        List<IngredientDto> result = OcrTextParser.parse(text);

        assertEquals(1, result.size());
        IngredientDto koffein = result.getFirst();
        assertEquals("Koffein", koffein.getName());
        assertEquals(255.0, koffein.getMg(), 0.001);
        assertEquals(1, koffein.getSubIngredients().size());
        assertEquals("aus Guarana Extrakt", koffein.getSubIngredients().getFirst().getName());
        assertEquals(55.0, koffein.getSubIngredients().getFirst().getMg(), 0.001);
    }

    @Test
    void parse_leadingClosingParenStripped_parsedAsTopLevel() {
        // ") Guarana-Extrakt" — closing paren is OCR noise
        List<IngredientDto> result = OcrTextParser.parse(") Guarana-Extrakt (275 mg)");
        assertEquals(1, result.size());
        assertEquals("Guarana-Extrakt", result.getFirst().getName());
        assertEquals(275.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_leadingClosingParenWithDavon_parsedAsSubIngredient() {
        // ") davon Piperin" under Schwarzer Pfeffer-Extrakt
        String text = "Schwarzer Pfeffer-Extrakt (4,2 mg)\n) davon Piperin (4 mg)";
        List<IngredientDto> result = OcrTextParser.parse(text);

        assertEquals(1, result.size());
        IngredientDto pfeffer = result.getFirst();
        assertEquals("Schwarzer Pfeffer-Extrakt", pfeffer.getName());
        assertEquals(4.2, pfeffer.getMg(), 0.01);
        assertEquals(1, pfeffer.getSubIngredients().size());
        assertEquals("Piperin", pfeffer.getSubIngredients().getFirst().getName());
        assertEquals(4.0, pfeffer.getSubIngredients().getFirst().getMg(), 0.001);
    }

    @Test
    void parse_leadingLessThanWithDavon_parsedAsSubIngredient() {
        // "< davon Glycerin" appears under Glycerinpulver
        String text = "Glycerinpulver (HydroPrime\u00ae) (2000 mg)\n< davon Glycerin (1300 mg)";
        List<IngredientDto> result = OcrTextParser.parse(text);

        assertEquals(1, result.size());
        IngredientDto glycerin = result.getFirst();
        assertEquals("Glycerinpulver (HydroPrime\u00ae)", glycerin.getName());
        assertEquals(2000.0, glycerin.getMg(), 0.001);
        assertEquals(1, glycerin.getSubIngredients().size());
        assertEquals("Glycerin", glycerin.getSubIngredients().getFirst().getName());
        assertEquals(1300.0, glycerin.getSubIngredients().getFirst().getMg(), 0.001);
    }

    // ── Multi-line ingredient names (OCR splits long name over two lines) ────

    // ── Additional noise chars: ], [, =, ‚, i/l as | ─────────────────────────

    @Test
    void parse_leadingSquareBracket_strippedAndParsedAsSubIngredient() {
        // "] davon L-Arginin" — "]" is OCR table-border artefact
        String text = "L-Arginin AAKG (4000 mg)\n] davon L-Arginin (2800 mg)";
        List<IngredientDto> result = OcrTextParser.parse(text);
        assertEquals(1, result.size());
        IngredientDto aakg = result.getFirst();
        assertEquals("L-Arginin AAKG", aakg.getName());
        assertEquals(1, aakg.getSubIngredients().size());
        assertEquals("L-Arginin", aakg.getSubIngredients().getFirst().getName());
        assertEquals(2800.0, aakg.getSubIngredients().getFirst().getMg(), 0.001);
    }

    @Test
    void parse_leadingEquals_strippedAndParsedAsTopLevel() {
        // "= Glucuronolacton" — "=" is a table-separator OCR artefact
        List<IngredientDto> result = OcrTextParser.parse("= Glucuronolacton (500 mg)");
        assertEquals(1, result.size());
        assertEquals("Glucuronolacton", result.getFirst().getName());
        assertEquals(500.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_leadingLow9Quote_strippedAndParsedAsTopLevel() {
        // "\u201a Rhodiola Rosea" — U+201A SINGLE LOW-9 QUOTATION MARK is table-decoration artefact
        List<IngredientDto> result = OcrTextParser.parse("\u201a Rhodiola Rosea Extrakt (250 mg)");
        assertEquals(1, result.size());
        assertEquals("Rhodiola Rosea Extrakt", result.getFirst().getName());
        assertEquals(250.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_leadingLowercaseI_strippedBeforeUppercase() {
        // "i Schisandra-Extrakt" — "i" is "|" (vertical border) misread by OCR
        List<IngredientDto> result = OcrTextParser.parse("i Schisandra-Extrakt (200 mg)");
        assertEquals(1, result.size());
        assertEquals("Schisandra-Extrakt", result.getFirst().getName());
        assertEquals(200.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_uppercaseJAndExclamation_pipeArtefact_stripped() {
        // "J Ginsengwurzel-Extrakt" — 'J' is a common OCR misread of '|' (vertical table border)
        List<IngredientDto> resultJ = OcrTextParser.parse("J Ginsengwurzel-Extrakt (200 mg)");
        assertEquals(1, resultJ.size());
        assertEquals("Ginsengwurzel-Extrakt", resultJ.getFirst().getName());
        assertEquals(200.0, resultJ.getFirst().getMg(), 0.001);

        // "! Curcuma-Extrakt" — '!' is also a common OCR misread of '|'
        List<IngredientDto> resultExcl = OcrTextParser.parse("! Curcuma-Extrakt (100 mg)");
        assertEquals(1, resultExcl.size());
        assertEquals("Curcuma-Extrakt", resultExcl.getFirst().getName());
        assertEquals(100.0, resultExcl.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_digitPrefixDavon_parsedAsSubIngredient() {
        // "2 davon aus Guarana Extrakt" — table row number "2" prepended by OCR
        String text = "Koffein (255 mg)\n2 davon aus Guarana Extrakt (55 mg)";
        List<IngredientDto> result = OcrTextParser.parse(text);
        assertEquals(1, result.size());
        IngredientDto koffein = result.getFirst();
        assertEquals("Koffein", koffein.getName());
        assertEquals(1, koffein.getSubIngredients().size());
        assertEquals("aus Guarana Extrakt", koffein.getSubIngredients().getFirst().getName());
        assertEquals(55.0, koffein.getSubIngredients().getFirst().getMg(), 0.001);
    }

    @Test
    void parse_digitPrefixDavon_singleWord_parsedAsSubIngredient() {
        // "3 davon Piperin" — table row number "3" prepended by OCR
        String text = "Schwarzer Pfeffer-Extrakt (4,2 mg)\n3 davon Piperin (4 mg)";
        List<IngredientDto> result = OcrTextParser.parse(text);
        assertEquals(1, result.size());
        IngredientDto pfeffer = result.getFirst();
        assertEquals("Schwarzer Pfeffer-Extrakt", pfeffer.getName());
        assertEquals(1, pfeffer.getSubIngredients().size());
        assertEquals("Piperin", pfeffer.getSubIngredients().getFirst().getName());
        assertEquals(4.0, pfeffer.getSubIngredients().getFirst().getMg(), 0.001);
    }

    @Test
    void parse_digitPrefixDavonNoSpace_parsedAsSubIngredient() {
        // "3 davonPiperin" — OCR merges "davon" and ingredient name without space
        String text = "Schwarzer Pfeffer-Extrakt (4,2 mg)\n3 davonPiperin (4 mg)";
        List<IngredientDto> result = OcrTextParser.parse(text);
        assertEquals(1, result.size());
        IngredientDto pfeffer = result.getFirst();
        assertEquals("Schwarzer Pfeffer-Extrakt", pfeffer.getName());
        assertEquals(1, pfeffer.getSubIngredients().size());
        assertEquals("Piperin", pfeffer.getSubIngredients().getFirst().getName());
        assertEquals(4.0, pfeffer.getSubIngredients().getFirst().getMg(), 0.001);
    }

    @Test
    void parse_adenosinAtp_multiLineName_firstLineHasNoAmount() {
        // OCR splits "Adenosin 5'-Triphosphat Dinatrium (ATP) (als PEAK ATP®) 400 mg" into two lines.
        // Line 1 has no amount → pendingName; Line 2 starts with '(' but is NOT a sub-ingredient.
        String ocrText =
                "Adenosin 5'-Triphosphat Dinatrium\n" +
                "(ATP) (als PEAK ATP\u00ae) 400 mg";
        List<IngredientDto> result = OcrTextParser.parse(ocrText);
        assertEquals(1, result.size(), "should be exactly one top-level ingredient");
        assertEquals("Adenosin 5'-Triphosphat Dinatrium (ATP) (als PEAK ATP\u00ae)",
                result.getFirst().getName());
        assertEquals(400.0, result.getFirst().getMg(), 0.001);
        assertTrue(result.getFirst().getSubIngredients().isEmpty());
    }

    @Test
    void parse_adenosinAtp_multiLineName_twoPartParenthetical() {
        // Variant: first line includes "(ATP)", second line starts with "(als PEAK ATP®)"
        String ocrText =
                "Adenosin 5'-Triphosphat Dinatrium (ATP)\n" +
                "(als PEAK ATP\u00ae) 400 mg";
        List<IngredientDto> result = OcrTextParser.parse(ocrText);
        assertEquals(1, result.size());
        assertEquals("Adenosin 5'-Triphosphat Dinatrium (ATP) (als PEAK ATP\u00ae)",
                result.getFirst().getName());
        assertEquals(400.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_adenosin_realTesseractOutput_isDropped() {
        // DOCUMENTATION TEST — explains why Adenosin is ONLY_IN_DB in real checks.
        //
        // Actual Tesseract output from crankultimate.png (verified 2026-05-15):
        //   "Adenosin 5-Triphosphat Dinatrium (ATP) (als zoomg"
        //
        // Tesseract reads "PEAK ATP®) 400 mg" as "zoomg" — no digit precedes "mg",
        // so INGREDIENT_LINE / TWO_COLUMN_LINE never match.  The line becomes a
        // pendingName but is discarded when the next proper ingredient line arrives.
        // Result: Adenosin completely absent from the OCR ingredient list → ONLY_IN_DB.
        //
        // WHY EARLIER TESTS DID NOT CATCH THIS:
        // All previous Adenosin tests used IDEALISED multi-line splits
        // ("Adenosin 5'-Triphosphat Dinatrium\n(ATP) (als PEAK ATP®) 400 mg")
        // that never actually occur in practice.  No test ran Tesseract on the
        // real image.  This test documents the real behaviour so regressions are visible.
        String realOcrLine =
                "Adenosin 5-Triphosphat Dinatrium (ATP) (als zoomg\n" +
                "Bitterorangenschalen-Extrakt 350 mg";
        List<IngredientDto> result = OcrTextParser.parse(realOcrLine);

        List<String> names = result.stream().map(IngredientDto::getName).toList();
        assertFalse(names.stream().anyMatch(n -> n.toLowerCase().contains("adenosin")),
                "Adenosin with unreadable amount must NOT appear in parsed result");
        assertTrue(names.contains("Bitterorangenschalen-Extrakt"),
                "Next ingredient after dropped Adenosin line must still be parsed");
    }

    @Test
    void parse_multiLineNameCombining_doesNotAffectRealSubIngredients() {
        // Ensure that a real "davon"-sub-ingredient is NOT combined even when there's a pendingName
        String ocrText =
                "Protein Matrix\n" +
                "(davon L-Leucin) 2500 mg";
        List<IngredientDto> result = OcrTextParser.parse(ocrText);
        // "Protein Matrix" has no amount → discarded as pendingName, but "(davon L-Leucin)"
        // contains "davon" → treated as sub-ingredient of... nothing (no top-level yet)
        // → added as top-level entry
        assertEquals(1, result.size());
        assertEquals("L-Leucin", result.getFirst().getName());
        assertEquals(2500.0, result.getFirst().getMg(), 0.001);
    }

    // ── OCR corrections: Lryrosin, Sitterorangenschalen ──────────────────────

    @Test
    void parse_ocrCorrection_LryrosinRecognizedAsLTyrosin() {
        List<IngredientDto> result = OcrTextParser.parse("Lryrosin (1500 mg)");
        assertEquals(1, result.size());
        assertEquals("L-Tyrosin", result.getFirst().getName());
        assertEquals(1500.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void parse_ocrCorrection_SitterorangenschalenRecognizedAsBitterorangenschalen() {
        List<IngredientDto> result = OcrTextParser.parse("Sitterorangenschalen-Extrakt (350 mg)");
        assertEquals(1, result.size());
        assertEquals("Bitterorangenschalen-Extrakt", result.getFirst().getName());
        assertEquals(350.0, result.getFirst().getMg(), 0.001);
    }

    // ── cleanSubIngredientName: trailing ( stripped ──────────────────────────

    @Test
    void cleanSubIngredientName_trailingOpenParenStripped() {
        // Amount-in-parens format leaves a trailing "(" in the parsed name group
        assertEquals("Glycerin",
                OcrTextParser.cleanSubIngredientName("davon Glycerin ("));
    }

    @Test
    void cleanSubIngredientName_trailingOpenParenAfterDavonPiperin() {
        assertEquals("Piperin",
                OcrTextParser.cleanSubIngredientName("davon Piperin ("));
    }

    @Test
    void cleanSubIngredientName_trailingOpenParenAusGuaranaExtrakt() {
        assertEquals("aus Guarana Extrakt",
                OcrTextParser.cleanSubIngredientName("davon aus Guarana Extrakt ("));
    }

    // ── Crank Ultimate OCR integration test ──────────────────────────────────

    @Test
    void parse_crankUltimateOcr_parsesKeyIngredients() {
        // Realistic OCR output with common artefacts from table borders and misreads
        String ocrText =
            "L-Citrullin Malat (6000 mg)\n" +
            "L-Citrullin (4800 mg)\n" +
            "L-Arginin Alpha-Ketoglutarat (4000 mg)\n" +
            "] davon L-Arginin (2800 mg)\n" +            // "]" = OCR table border
            "Glycerinpulver (HydroPrime\u00ae) (2000 mg)\n" +
            "< davon Glycerin (1300 mg)\n" +
            "Lryrosin (1500 mg)\n" +
            "Taurin (1500 mg)\n" +
            "L-Glycin (1000 mg)\n" +
            "Adenosin 5'-Triphosphat Dinatrium\n" +       // multi-line name
            "(ATP) (als PEAK ATP\u00ae) 400 mg\n" +
            "\uFF3F Glucuronolacton (500 mg)\n" +         // U+FF3F fullwidth underscore
            "Sitterorangenschalen-Extrakt (350 mg)\n" +
            ") Guarana-Extrakt (275 mg)\n" +
            "Koffein (255 mg)\n" +
            "2 davon aus Guarana Extrakt (55 mg)\n" +     // digit prefix before davon
            "Citicolin (250 mg)\n" +
            "Gr\u00fcntee-Extrakt (250 mg)\n" +
            "Rhodiola Rosea Extrakt (250 mg)\n" +
            "Traubenkern-Extrakt (250 mg)\n" +
            "Schisandra-Extrakt (200 mg)\n" +
            "i Ginsengwurzel-Extrakt (200 mg)\n" +        // "i" = "|" OCR misread
            "Ginkgo Biloba-Extrakt (75 mg)\n" +
            "\u201a Schwarzer Pfeffer-Extrakt (4,2 mg)\n" + // U+201A low-9 quote
            "3 davon Piperin (4 mg)";                      // digit prefix before davon

        List<IngredientDto> result = OcrTextParser.parse(ocrText);
        List<String> topNames = result.stream().map(IngredientDto::getName).toList();

        // OCR corrections applied
        assertTrue(topNames.contains("L-Tyrosin"),                    "Lryrosin → L-Tyrosin");
        assertTrue(topNames.contains("Bitterorangenschalen-Extrakt"),  "Sitter → Bitter");
        assertTrue(topNames.contains("Guarana-Extrakt"),               "leading ) stripped");
        assertTrue(topNames.contains("Glucuronolacton"),               "leading \uFF3F stripped");
        assertTrue(topNames.contains("Ginsengwurzel-Extrakt"),         "leading i stripped");
        assertTrue(topNames.contains("Schwarzer Pfeffer-Extrakt"),     "leading \u201a stripped");

        // Multi-line name: Adenosin ATP
        assertTrue(topNames.contains("Adenosin 5'-Triphosphat Dinatrium (ATP) (als PEAK ATP\u00ae)"),
                "Adenosin multi-line name combined");
        assertEquals(400.0, findMg(result, "Adenosin 5'-Triphosphat Dinatrium (ATP) (als PEAK ATP\u00ae)"),
                0.001);

        // Top-level amounts
        assertEquals(6000.0, findMg(result, "L-Citrullin Malat"), 0.001);
        assertEquals(1500.0, findMg(result, "Taurin"),            0.001);
        assertEquals(255.0,  findMg(result, "Koffein"),           0.001);
        assertEquals(4.2,    findMg(result, "Schwarzer Pfeffer-Extrakt"), 0.01);

        // Sub-ingredient: L-Arginin under L-Arginin AAKG
        IngredientDto aakg = result.stream()
                .filter(i -> i.getName().startsWith("L-Arginin Alpha")).findFirst().orElseThrow();
        assertEquals(1, aakg.getSubIngredients().size());
        assertEquals("L-Arginin", aakg.getSubIngredients().getFirst().getName());
        assertEquals(2800.0, aakg.getSubIngredients().getFirst().getMg(), 0.001);

        // Sub-ingredient: Glycerin under Glycerinpulver
        IngredientDto glycerinpulver = result.stream()
                .filter(i -> i.getName().startsWith("Glycerinpulver")).findFirst().orElseThrow();
        assertEquals(1, glycerinpulver.getSubIngredients().size());
        assertEquals("Glycerin", glycerinpulver.getSubIngredients().getFirst().getName());
        assertEquals(1300.0, glycerinpulver.getSubIngredients().getFirst().getMg(), 0.001);

        // Sub-ingredient: aus Guarana Extrakt under Koffein (digit prefix "2" stripped)
        IngredientDto koffein = result.stream()
                .filter(i -> "Koffein".equals(i.getName())).findFirst().orElseThrow();
        assertEquals(1, koffein.getSubIngredients().size());
        assertEquals("aus Guarana Extrakt", koffein.getSubIngredients().getFirst().getName());
        assertEquals(55.0, koffein.getSubIngredients().getFirst().getMg(), 0.001);

        // Sub-ingredient: Piperin under Schwarzer Pfeffer-Extrakt (digit prefix "3" stripped)
        IngredientDto pfeffer = result.stream()
                .filter(i -> "Schwarzer Pfeffer-Extrakt".equals(i.getName())).findFirst().orElseThrow();
        assertEquals(1, pfeffer.getSubIngredients().size());
        assertEquals("Piperin", pfeffer.getSubIngredients().getFirst().getName());
        assertEquals(4.0, pfeffer.getSubIngredients().getFirst().getMg(), 0.001);
    }

    // ── OCR correction: lodine → Iodine ──────────────────────────────────────

    @Test
    void parse_lodine_correctedToIodine() {
        // OCR misreads capital I as lowercase l on EU nutrition labels
        List<IngredientDto> result = OcrTextParser.parse("lodine 150 ug");
        assertEquals(1, result.size());
        assertEquals("Iodine", result.getFirst().getName());
        assertEquals(0.15, result.getFirst().getMg(), 0.0001);
    }

    @Test
    void parse_lodine_twoColumn_correctedToIodine() {
        // Two-column row: per-100g | per-portion — parser uses per-portion value
        List<IngredientDto> result = OcrTextParser.parse("lodine 2556,82 ug 150,00 ug 100,00%");
        assertEquals(1, result.size());
        assertEquals("Iodine", result.getFirst().getName());
        assertEquals(0.15, result.getFirst().getMg(), 0.0001);
    }

    // ── Header-line filter ────────────────────────────────────────────────────

    @Test
    void parse_headerLineProHundredG_skipped() {
        // "Mineralien Pro 100G Pro Portion" must not produce an ingredient
        List<IngredientDto> result = OcrTextParser.parse(
                "Mineralien Pro 100G Pro Portion % RM* pro Portion\n" +
                "Selenium 1397,73ug 82,00 ug");
        assertEquals(1, result.size(), "Header row must be skipped");
        assertEquals("Selenium", result.getFirst().getName());
        assertEquals(0.082, result.getFirst().getMg(), 0.0001);
    }

    @Test
    void parse_headerLineWeitereInformationen_skipped() {
        // "Weitere Informationen Pro 100G Pro Portion" must not produce an ingredient
        List<IngredientDto> result = OcrTextParser.parse(
                "Vitamin K2 - 35ug\n" +
                "Weitere Informationen Pro 100G Pro Portion");
        assertEquals(1, result.size(), "Header row must be skipped");
        assertEquals("Vitamin K2", result.getFirst().getName());
    }

    @Test
    void parse_vitaminePro100G_headerSkipped() {
        List<IngredientDto> result = OcrTextParser.parse(
                "Vitamine Pro 100G Pro Portion % RM* pro Portion\n" +
                "Vitamin C 200 mg");
        assertEquals(1, result.size(), "Header row must be skipped");
        assertEquals("Vitamin C", result.getFirst().getName());
    }

    // ── Bodylab2 integration test (real Tesseract + ImageMagick output) ───────

    /**
     * Integration test using the real OCR output produced by
     * {@code magick bodylab2.png -resize "2000x2000<" -colorspace Gray out.png &&
     * tesseract out.png stdout --psm 6 -l deu}.
     *
     * <p>Verifies:</p>
     * <ul>
     *   <li>Header rows ("Mineralien Pro 100G …", "Weitere Informationen …") are skipped</li>
     *   <li>{@code lodine} is corrected to {@code Iodine}</li>
     *   <li>{@code VitamincC} is corrected to {@code Vitamin C}</li>
     *   <li>Two-column table rows use the per-portion value (second column)</li>
     * </ul>
     */
    @Test
    void parse_bodylab2_realOcrOutput_skipsHeaders_correctsLodineAndVitaminC() {
        String ocrText =
                "VitamincC 3409,09mg 200,00 mg\n" +
                "Mineralien Pro 100G Pro Portion % RM* pro Portion\n" +
                "lodine 2556,82 ug 150,00 ug 100,00%\n" +
                "Weitere Informationen Pro 100G Pro Portion\n" +
                "Vitamin K2 - 35ug\n" +
                "Chrome 852,27 ug 50,00 ug\n" +
                "Selenium 1397,73ug 82,00 ug";

        List<IngredientDto> result = OcrTextParser.parse(ocrText);
        List<String> names = result.stream().map(IngredientDto::getName).toList();

        // Headers must not appear as ingredients
        assertTrue(names.stream().noneMatch(n -> n.contains("Pro 100") || n.startsWith("Mineralien Pro")),
                "Header rows must be skipped; found: " + names);
        assertTrue(names.stream().noneMatch(n -> n.startsWith("Weitere")),
                "Weitere Informationen header must be skipped; found: " + names);

        // VitamincC → Vitamin C (two-column → per-portion value)
        assertTrue(names.contains("Vitamin C"), "VitamincC must be corrected to Vitamin C");
        assertEquals(200.0,
                result.stream().filter(i -> "Vitamin C".equals(i.getName())).findFirst().orElseThrow().getMg(),
                0.001);

        // lodine → Iodine (two-column → per-portion value 150 µg = 0.15 mg)
        assertTrue(names.contains("Iodine"), "lodine must be corrected to Iodine");
        assertEquals(0.15,
                result.stream().filter(i -> "Iodine".equals(i.getName())).findFirst().orElseThrow().getMg(),
                0.0001);

        // Vitamin K2 parsed correctly
        assertTrue(names.contains("Vitamin K2"), "Vitamin K2 must be parsed");
        assertEquals(0.035,
                result.stream().filter(i -> "Vitamin K2".equals(i.getName())).findFirst().orElseThrow().getMg(),
                0.0001);

        // Chrome (translation Chrome→Chrom happens in IngredientTranslationService, not parser)
        assertTrue(names.contains("Chrome"), "Chrome must be parsed");
        assertEquals(0.050,
                result.stream().filter(i -> "Chrome".equals(i.getName())).findFirst().orElseThrow().getMg(),
                0.0001);

        // Selenium (translation Selenium→Selen happens in IngredientTranslationService, not parser)
        assertTrue(names.contains("Selenium"), "Selenium must be parsed");
        assertEquals(0.082,
                result.stream().filter(i -> "Selenium".equals(i.getName())).findFirst().orElseThrow().getMg(),
                0.0001);
    }
}
