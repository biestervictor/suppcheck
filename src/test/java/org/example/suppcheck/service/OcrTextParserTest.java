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
}
