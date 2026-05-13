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

    // --- Two-column table rows (Pro 100g | Pro Portion) ---

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
}
