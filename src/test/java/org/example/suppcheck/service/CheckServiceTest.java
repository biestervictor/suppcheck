package org.example.suppcheck.service;

import org.example.suppcheck.dto.CheckResult;
import org.example.suppcheck.dto.IngredientCheckResult;
import org.example.suppcheck.dto.IngredientDto;
import org.example.suppcheck.dto.OcrResult;
import org.example.suppcheck.model.Ingredient;
import org.example.suppcheck.model.Supplement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CheckServiceTest {

    private CheckService service;

    @BeforeEach
    void setUp() {
        service = new CheckService();
    }

    // -----------------------------------------------------------------------
    // Helper builders
    // -----------------------------------------------------------------------

    private static Supplement supplement(String id, String name, List<Ingredient> ingredients) {
        Supplement s = new Supplement();
        s.setId(id);
        s.setName(name);
        s.setIngredients(ingredients);
        return s;
    }

    private static Ingredient ingredient(String name, double mg) {
        Ingredient i = new Ingredient();
        i.setName(name);
        i.setMg(mg);
        return i;
    }

    private static Ingredient ingredientWithSub(String name, double mg, List<Ingredient> subs) {
        Ingredient i = ingredient(name, mg);
        i.setSubIngredients(new ArrayList<>(subs));
        return i;
    }

    private static IngredientDto dto(String name, double mg) {
        IngredientDto d = new IngredientDto();
        d.setName(name);
        d.setMg(mg);
        return d;
    }

    private static IngredientDto dtoWithSub(String name, double mg, List<IngredientDto> subs) {
        IngredientDto d = dto(name, mg);
        d.setSubIngredients(new ArrayList<>(subs));
        return d;
    }

    private static OcrResult ocr(List<IngredientDto> ingredients) {
        return new OcrResult("raw", new ArrayList<>(ingredients));
    }

    private static IngredientCheckResult findByName(List<IngredientCheckResult> results, String name) {
        return results.stream()
                .filter(r -> r.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No result found for: " + name));
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    void allMatch_noDiscrepancies() {
        Supplement supp = supplement("1", "Multi", List.of(
                ingredient("Vitamin C", 1000.0),
                ingredient("Zink", 15.0)));
        OcrResult ocrResult = ocr(List.of(
                dto("Vitamin C", 1000.0),
                dto("Zink", 15.0)));

        CheckResult result = service.compare(supp, ocrResult);

        assertThat(result.isHasDiscrepancies()).isFalse();
        assertThat(result.getIngredientResults()).hasSize(2);
        result.getIngredientResults().forEach(r -> assertThat(r.getStatus()).isEqualTo("MATCH"));
    }

    @Test
    void valueMismatch_flaggedAsValueMismatch() {
        Supplement supp = supplement("1", "Multi", List.of(ingredient("Vitamin C", 1000.0)));
        OcrResult ocrResult = ocr(List.of(dto("Vitamin C", 800.0)));

        CheckResult result = service.compare(supp, ocrResult);

        assertThat(result.isHasDiscrepancies()).isTrue();
        IngredientCheckResult icr = findByName(result.getIngredientResults(), "Vitamin C");
        assertThat(icr.getStatus()).isEqualTo("VALUE_MISMATCH");
        assertThat(icr.getStoredMg()).isEqualTo(1000.0);
        assertThat(icr.getOcrMg()).isEqualTo(800.0);
    }

    @Test
    void ingredientOnlyInDb_flaggedAsOnlyInDb() {
        Supplement supp = supplement("1", "Multi", List.of(
                ingredient("Vitamin C", 1000.0),
                ingredient("Magnesium", 300.0)));
        OcrResult ocrResult = ocr(List.of(dto("Vitamin C", 1000.0))); // Magnesium missing

        CheckResult result = service.compare(supp, ocrResult);

        assertThat(result.isHasDiscrepancies()).isTrue();
        IngredientCheckResult icr = findByName(result.getIngredientResults(), "Magnesium");
        assertThat(icr.getStatus()).isEqualTo("ONLY_IN_DB");
        assertThat(icr.getOcrMg()).isEqualTo(0.0);
    }

    @Test
    void ingredientOnlyInOcr_flaggedAsOnlyInOcr() {
        Supplement supp = supplement("1", "Multi", List.of(ingredient("Vitamin C", 1000.0)));
        OcrResult ocrResult = ocr(List.of(
                dto("Vitamin C", 1000.0),
                dto("Selen", 55.0))); // extra ingredient

        CheckResult result = service.compare(supp, ocrResult);

        assertThat(result.isHasDiscrepancies()).isTrue();
        IngredientCheckResult icr = findByName(result.getIngredientResults(), "Selen");
        assertThat(icr.getStatus()).isEqualTo("ONLY_IN_OCR");
        assertThat(icr.getStoredMg()).isEqualTo(0.0);
        assertThat(icr.getOcrMg()).isEqualTo(55.0);
    }

    @Test
    void caseInsensitiveNameMatching_match() {
        Supplement supp = supplement("1", "Multi", List.of(ingredient("Vitamin C", 500.0)));
        OcrResult ocrResult = ocr(List.of(dto("VITAMIN C", 500.0)));

        CheckResult result = service.compare(supp, ocrResult);

        assertThat(result.isHasDiscrepancies()).isFalse();
        assertThat(findByName(result.getIngredientResults(), "Vitamin C").getStatus()).isEqualTo("MATCH");
    }

    @Test
    void withinTolerance_treatedAsMatch() {
        // 5 % of 1000 = 50; difference of 49 should be MATCH
        Supplement supp = supplement("1", "Multi", List.of(ingredient("Vitamin C", 1000.0)));
        OcrResult ocrResult = ocr(List.of(dto("Vitamin C", 1049.0)));

        CheckResult result = service.compare(supp, ocrResult);

        assertThat(findByName(result.getIngredientResults(), "Vitamin C").getStatus()).isEqualTo("MATCH");
    }

    @Test
    void outsideTolerance_treatedAsMismatch() {
        // 5 % of 1000 = 50; difference of 100 should be MISMATCH
        Supplement supp = supplement("1", "Multi", List.of(ingredient("Vitamin C", 1000.0)));
        OcrResult ocrResult = ocr(List.of(dto("Vitamin C", 1100.0)));

        CheckResult result = service.compare(supp, ocrResult);

        assertThat(findByName(result.getIngredientResults(), "Vitamin C").getStatus()).isEqualTo("VALUE_MISMATCH");
    }

    @Test
    void subIngredients_matchedCorrectly() {
        Ingredient parent = ingredientWithSub("Eiweiß", 24000.0, List.of(
                ingredient("davon L-Leucin", 2500.0)));
        Supplement supp = supplement("1", "Whey", List.of(parent));

        IngredientDto parentDto = dtoWithSub("Eiweiß", 24000.0, List.of(dto("davon L-Leucin", 2500.0)));
        OcrResult ocrResult = ocr(List.of(parentDto));

        CheckResult result = service.compare(supp, ocrResult);

        assertThat(result.isHasDiscrepancies()).isFalse();
        IngredientCheckResult icr = findByName(result.getIngredientResults(), "Eiweiß");
        assertThat(icr.getSubResults()).hasSize(1);
        assertThat(icr.getSubResults().get(0).getStatus()).isEqualTo("MATCH");
    }

    @Test
    void subIngredient_valueMismatch_parentCountsAsDiscrepancy() {
        Ingredient parent = ingredientWithSub("Eiweiß", 24000.0, List.of(
                ingredient("davon L-Leucin", 2500.0)));
        Supplement supp = supplement("1", "Whey", List.of(parent));

        IngredientDto parentDto = dtoWithSub("Eiweiß", 24000.0, List.of(dto("davon L-Leucin", 1000.0)));
        OcrResult ocrResult = ocr(List.of(parentDto));

        CheckResult result = service.compare(supp, ocrResult);

        // The top-level ingredient itself matches, but sub does not
        assertThat(result.isHasDiscrepancies()).isFalse(); // top level MATCH

        IngredientCheckResult icr = findByName(result.getIngredientResults(), "Eiweiß");
        assertThat(icr.getStatus()).isEqualTo("MATCH"); // parent mg matches
        IngredientCheckResult sub = icr.getSubResults().get(0);
        assertThat(sub.getStatus()).isEqualTo("VALUE_MISMATCH");
    }

    @Test
    void emptyStoredIngredients_allOcrIngredientsAreOnlyInOcr() {
        Supplement supp = supplement("1", "Multi", new ArrayList<>());
        OcrResult ocrResult = ocr(List.of(dto("Vitamin C", 500.0)));

        CheckResult result = service.compare(supp, ocrResult);

        assertThat(result.isHasDiscrepancies()).isTrue();
        assertThat(findByName(result.getIngredientResults(), "Vitamin C").getStatus()).isEqualTo("ONLY_IN_OCR");
    }

    @Test
    void emptyOcrResult_allStoredIngredientsAreOnlyInDb() {
        Supplement supp = supplement("1", "Multi", List.of(ingredient("Vitamin C", 500.0)));
        OcrResult ocrResult = ocr(new ArrayList<>());

        CheckResult result = service.compare(supp, ocrResult);

        assertThat(result.isHasDiscrepancies()).isTrue();
        assertThat(findByName(result.getIngredientResults(), "Vitamin C").getStatus()).isEqualTo("ONLY_IN_DB");
    }

    @Test
    void isClose_bothZero_returnsTrue() {
        assertThat(service.isClose(0, 0)).isTrue();
    }

    @Test
    void isClose_smallAbsoluteDifference_withinMinTolerance() {
        // 0.5 mg difference, min tolerance is 1 mg → MATCH
        assertThat(service.isClose(1.0, 1.5)).isTrue();
    }

    @Test
    void normalize_lowercasesAndTrimsAndCollapsesWhitespace() {
        assertThat(CheckService.normalize("  Vitamin  C  ")).isEqualTo("vitamin c");
    }

    @Test
    void supplementMetadata_carriedIntoResult() {
        Supplement supp = supplement("abc123", "MyProd", List.of());
        OcrResult ocrResult = new OcrResult("some raw text", List.of());

        CheckResult result = service.compare(supp, ocrResult);

        assertThat(result.getSupplementId()).isEqualTo("abc123");
        assertThat(result.getSupplementName()).isEqualTo("MyProd");
        assertThat(result.getRawText()).isEqualTo("some raw text");
    }
}
