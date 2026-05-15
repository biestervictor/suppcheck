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

        // The top-level ingredient itself matches, but sub does not →
        // hasDiscrepancies must be true because sub-results are included in the check
        assertThat(result.isHasDiscrepancies()).isTrue();

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
    void normalize_stripsParenthesesContent() {
        // Brand suffix in parentheses is stripped
        assertThat(CheckService.normalize("HydroPrime® (Glycerin-Pulver)")).isEqualTo("hydroprime®");
        assertThat(CheckService.normalize("Schwarzer Pfeffer-Extrakt (HydroPrime®)")).isEqualTo("schwarzer pfeffer extrakt");
    }

    @Test
    void normalize_replacesHyphensWithSpace() {
        assertThat(CheckService.normalize("L-Citrullin-Malat")).isEqualTo("l citrullin malat");
        assertThat(CheckService.normalize("L-Citrullin Malat")).isEqualTo("l citrullin malat");
    }

    @Test
    void normalize_stripsDavonPrefix() {
        assertThat(CheckService.normalize("davon Piperin")).isEqualTo("piperin");
        assertThat(CheckService.normalize("Davon L-Citrullin")).isEqualTo("l citrullin");
    }

    @Test
    void hyphenVariants_treatedAsSameName() {
        // "L-Citrullin Malat" stored, "L-Citrullin-Malat" on label → should MATCH
        Supplement supp = supplement("1", "Pre", List.of(ingredient("L-Citrullin Malat", 10000.0)));
        OcrResult ocrResult = ocr(List.of(dto("L-Citrullin-Malat", 10000.0)));

        CheckResult result = service.compare(supp, ocrResult);

        assertThat(result.isHasDiscrepancies()).isFalse();
        assertThat(findByName(result.getIngredientResults(), "L-Citrullin Malat").getStatus())
                .isEqualTo("MATCH");
    }

    @Test
    void brandNameInParentheses_ignored() {
        // stored: "Schwarzer Pfeffer-Extrakt", OCR: "Schwarzer Pfeffer-Extrakt (HydroPrime®)"
        Supplement supp = supplement("1", "Pre", List.of(ingredient("Schwarzer Pfeffer-Extrakt", 11.0)));
        OcrResult ocrResult = ocr(List.of(dto("Schwarzer Pfeffer-Extrakt (HydroPrime®)", 11.0)));

        CheckResult result = service.compare(supp, ocrResult);

        assertThat(result.isHasDiscrepancies()).isFalse();
        assertThat(findByName(result.getIngredientResults(), "Schwarzer Pfeffer-Extrakt").getStatus())
                .isEqualTo("MATCH");
    }

    @Test
    void davonPiperinSubIngredient_matchedWithOcrPiperin() {
        // Stored: sub-ingredient "davon Piperin" (10.5 mg)
        // OCR parses as sub-ingredient "Piperin" (10.5 mg, "davon " stripped by OcrTextParser)
        // → should MATCH after normalize() strips "davon " prefix
        Ingredient parent = ingredientWithSub("Schwarzer Pfeffer-Extrakt", 11.0, List.of(
                ingredient("davon Piperin", 10.5)));
        Supplement supp = supplement("1", "Pre", List.of(parent));

        IngredientDto parentDto = dtoWithSub("Schwarzer Pfeffer-Extrakt", 11.0,
                List.of(dto("Piperin", 10.5)));
        OcrResult ocrResult = ocr(List.of(parentDto));

        CheckResult result = service.compare(supp, ocrResult);

        assertThat(result.isHasDiscrepancies()).isFalse();
        IngredientCheckResult icr = findByName(result.getIngredientResults(), "Schwarzer Pfeffer-Extrakt");
        assertThat(icr.getStatus()).isEqualTo("MATCH");
        assertThat(icr.getSubResults()).hasSize(1);
        assertThat(icr.getSubResults().get(0).getStatus()).isEqualTo("MATCH");
    }

    @Test
    void onlyInOcr_includesSubIngredients() {
        // OCR finds "L-Citrullin Malat" with sub "L-Citrullin" – not in DB
        // → ONLY_IN_OCR result should carry the sub-ingredient in subResults
        Supplement supp = supplement("1", "Pre", List.of());
        IngredientDto ocr = dtoWithSub("L-Citrullin Malat", 10000.0,
                List.of(dto("L-Citrullin", 6800.0)));
        OcrResult ocrResult = ocr(List.of(ocr));

        CheckResult result = service.compare(supp, ocrResult);

        assertThat(result.isHasDiscrepancies()).isTrue();
        IngredientCheckResult icr = findByName(result.getIngredientResults(), "L-Citrullin Malat");
        assertThat(icr.getStatus()).isEqualTo("ONLY_IN_OCR");
        assertThat(icr.getSubResults()).hasSize(1);
        assertThat(icr.getSubResults().get(0).getName()).isEqualTo("L-Citrullin");
        assertThat(icr.getSubResults().get(0).getOcrMg()).isEqualTo(6800.0);
    }

    @Test
    void ocrIngredients_carriedIntoResult() {
        Supplement supp = supplement("1", "Pre", List.of(ingredient("Vitamin C", 500.0)));
        IngredientDto vitC = dto("Vitamin C", 500.0);
        OcrResult ocrResult = ocr(List.of(vitC));

        CheckResult result = service.compare(supp, ocrResult);

        assertThat(result.getOcrIngredients()).hasSize(1);
        assertThat(result.getOcrIngredients().get(0).getName()).isEqualTo("Vitamin C");
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

    /**
     * End-to-end check simulating a real Crank Ultimate OCR scan result.
     *
     * <p>Expected discrepancies:
     * <ol>
     *   <li>{@code L-Citrullin} sub-ingredient under {@code L-Citrullin Malat} → ONLY_IN_DB
     *       (OCR shows it as a standalone top-level entry instead)</li>
     *   <li>{@code L-Citrullin} (4800 mg) as top-level OCR → ONLY_IN_OCR</li>
     *   <li>{@code Adenosin 5'-Triphosphat Dinatrium (ATP) (als PEAK ATP®)} → ONLY_IN_DB
     *       (Tesseract misreads it as "pe ospnat Dinatrium (ATP)")</li>
     *   <li>{@code pe ospnat Dinatrium (ATP)} → ONLY_IN_OCR</li>
     *   <li>{@code Koffein} → VALUE_MISMATCH (stored 255 mg, OCR reads 200 mg)</li>
     *   <li>{@code Guarana-Extrakt} (275 mg) → ONLY_IN_OCR (appears standalone, not as sub)</li>
     * </ol>
     * Everything else must be MATCH.
     */
    @Test
    void check_crankUltimate_realOcrOutput_onlyExpectedDeviations() {
        // ── DB supplement ────────────────────────────────────────────────────────
        Ingredient citrullinMalat = ingredientWithSub("L-Citrullin Malat", 6000.0,
                List.of(ingredient("L-Citrullin", 4800.0)));
        Ingredient argininAakg = ingredientWithSub("L-Arginin Alpha-Ketoglutarat (AAKG)", 5600.0,
                List.of(ingredient("L-Arginin", 2800.0)));
        Ingredient glycerinpulver = ingredientWithSub("Glycerinpulver (HydroPrime\u00ae)", 2000.0,
                List.of(ingredient("Glycerin", 1300.0)));
        Ingredient koffeinDb = ingredientWithSub("Koffein", 255.0,
                List.of(ingredient("aus Guarana Extrakt", 55.0)));
        Ingredient pfefferDb = ingredientWithSub("Schwarzer Pfeffer-Extrakt", 4.2,
                List.of(ingredient("davon Piperin", 4.0)));

        Supplement supp = supplement("crank-1", "Crank Ultimate", List.of(
                citrullinMalat,
                argininAakg,
                glycerinpulver,
                ingredient("L-Tyrosin", 1500.0),
                ingredient("Taurin", 1500.0),
                ingredient("L-Glycin", 1000.0),
                ingredient("Adenosin 5'-Triphosphat Dinatrium (ATP) (als PEAK ATP\u00ae)", 400.0),
                ingredient("Glucuronolacton", 500.0),
                ingredient("Bitterorangenschalen-Extrakt", 350.0),
                koffeinDb,
                ingredient("Citicolin", 250.0),
                ingredient("Gr\u00fcntee-Extrakt", 250.0),
                ingredient("Rhodiola Rosea Extrakt", 250.0),
                ingredient("Traubenkern-Extrakt", 250.0),
                ingredient("Schisandra-Extrakt", 200.0),
                ingredient("Ginsengwurzel-Extrakt", 200.0),
                ingredient("Ginkgo Biloba-Extrakt", 75.0),
                pfefferDb
        ));

        // ── OCR result (as produced by OcrTextParser after all noise-strip corrections) ──
        // L-Citrullin Malat matches top-level, but sub-ingredient relationship is lost in OCR;
        // instead L-Citrullin appears as a separate top-level entry.
        IngredientDto ocrCitrullinMalat = dto("L-Citrullin Malat", 6000.0); // no subs
        IngredientDto ocrCitrullin      = dto("L-Citrullin", 4800.0);       // ONLY_IN_OCR

        IngredientDto ocrArgininAakg = dtoWithSub("L-Arginin Alpha-Ketoglutarat (AAKG)", 5600.0,
                List.of(dto("L-Arginin", 2800.0)));
        IngredientDto ocrGlycerinpulver = dtoWithSub("Glycerinpulver (HydroPrime\u00ae)", 2000.0,
                List.of(dto("Glycerin", 1300.0)));

        // Adenosin completely misread by Tesseract — no parser correction possible
        IngredientDto ocrPeOspnat = dto("pe ospnat Dinatrium (ATP)", 400.0); // ONLY_IN_OCR

        // Guarana-Extrakt appears as a standalone top-level (not as sub of Koffein)
        IngredientDto ocrGuarana = dto("Guarana-Extrakt", 275.0); // ONLY_IN_OCR

        // Koffein: OCR reads 200 mg instead of stored 255 mg
        IngredientDto ocrKoffein = dtoWithSub("Koffein", 200.0,
                List.of(dto("aus Guarana Extrakt", 55.0)));

        // Schwarzer Pfeffer-Extrakt: OCR strips "davon" prefix from sub
        IngredientDto ocrPfeffer = dtoWithSub("Schwarzer Pfeffer-Extrakt", 4.2,
                List.of(dto("Piperin", 4.0)));

        OcrResult ocrResult = ocr(List.of(
                ocrCitrullinMalat,
                ocrCitrullin,
                ocrArgininAakg,
                ocrGlycerinpulver,
                dto("L-Tyrosin", 1500.0),
                dto("Taurin", 1500.0),
                dto("L-Glycin", 1000.0),
                ocrPeOspnat,
                dto("Glucuronolacton", 500.0),
                dto("Bitterorangenschalen-Extrakt", 350.0),
                ocrGuarana,
                ocrKoffein,
                dto("Citicolin", 250.0),
                dto("Gr\u00fcntee-Extrakt", 250.0),
                dto("Rhodiola Rosea Extrakt", 250.0),
                dto("Traubenkern-Extrakt", 250.0),
                dto("Schisandra-Extrakt", 200.0),
                dto("Ginsengwurzel-Extrakt", 200.0),
                dto("Ginkgo Biloba-Extrakt", 75.0),
                ocrPfeffer
        ));

        // ── Run check ────────────────────────────────────────────────────────────
        CheckResult result = service.compare(supp, ocrResult);

        assertThat(result.isHasDiscrepancies()).isTrue();

        // ── Expected MATCHes ─────────────────────────────────────────────────────
        assertThat(findByName(result.getIngredientResults(), "L-Citrullin Malat").getStatus())
                .isEqualTo("MATCH");
        assertThat(findByName(result.getIngredientResults(), "L-Arginin Alpha-Ketoglutarat (AAKG)").getStatus())
                .isEqualTo("MATCH");
        assertThat(findByName(result.getIngredientResults(), "Glycerinpulver (HydroPrime\u00ae)").getStatus())
                .isEqualTo("MATCH");
        for (String name : List.of("L-Tyrosin", "Taurin", "L-Glycin", "Glucuronolacton",
                "Bitterorangenschalen-Extrakt", "Citicolin", "Gr\u00fcntee-Extrakt",
                "Rhodiola Rosea Extrakt", "Traubenkern-Extrakt", "Schisandra-Extrakt",
                "Ginsengwurzel-Extrakt", "Ginkgo Biloba-Extrakt", "Schwarzer Pfeffer-Extrakt")) {
            assertThat(findByName(result.getIngredientResults(), name).getStatus())
                    .as("status of " + name).isEqualTo("MATCH");
        }

        // "davon Piperin" (DB) vs "Piperin" (OCR) → normalize strips "davon " → MATCH
        IngredientCheckResult pfefferResult =
                findByName(result.getIngredientResults(), "Schwarzer Pfeffer-Extrakt");
        assertThat(pfefferResult.getSubResults()).hasSize(1);
        assertThat(pfefferResult.getSubResults().get(0).getStatus()).isEqualTo("MATCH");

        // ── Expected discrepancies ────────────────────────────────────────────────

        // 1. L-Citrullin sub under L-Citrullin Malat → ONLY_IN_DB
        IngredientCheckResult citrullinMalatResult =
                findByName(result.getIngredientResults(), "L-Citrullin Malat");
        assertThat(citrullinMalatResult.getSubResults()).hasSize(1);
        IngredientCheckResult subCitrullin = citrullinMalatResult.getSubResults().get(0);
        assertThat(subCitrullin.getName()).isEqualTo("L-Citrullin");
        assertThat(subCitrullin.getStatus()).isEqualTo("ONLY_IN_DB");
        assertThat(subCitrullin.getStoredMg()).isEqualTo(4800.0);

        // 2. L-Citrullin as standalone top-level → ONLY_IN_OCR
        IngredientCheckResult ocrCitrullinResult =
                findByName(result.getIngredientResults(), "L-Citrullin");
        assertThat(ocrCitrullinResult.getStatus()).isEqualTo("ONLY_IN_OCR");
        assertThat(ocrCitrullinResult.getOcrMg()).isEqualTo(4800.0);

        // 3. Adenosin 5'-Triphosphat … → ONLY_IN_DB (Tesseract cannot read it)
        IngredientCheckResult adenosinResult = findByName(result.getIngredientResults(),
                "Adenosin 5'-Triphosphat Dinatrium (ATP) (als PEAK ATP\u00ae)");
        assertThat(adenosinResult.getStatus()).isEqualTo("ONLY_IN_DB");
        assertThat(adenosinResult.getStoredMg()).isEqualTo(400.0);

        // 4. pe ospnat Dinatrium (ATP) → ONLY_IN_OCR (OCR misread of Adenosin)
        IngredientCheckResult peOspnatResult =
                findByName(result.getIngredientResults(), "pe ospnat Dinatrium (ATP)");
        assertThat(peOspnatResult.getStatus()).isEqualTo("ONLY_IN_OCR");
        assertThat(peOspnatResult.getOcrMg()).isEqualTo(400.0);

        // 5. Koffein → VALUE_MISMATCH (stored 255 mg, OCR reads 200 mg)
        IngredientCheckResult koffeinResult =
                findByName(result.getIngredientResults(), "Koffein");
        assertThat(koffeinResult.getStatus()).isEqualTo("VALUE_MISMATCH");
        assertThat(koffeinResult.getStoredMg()).isEqualTo(255.0);
        assertThat(koffeinResult.getOcrMg()).isEqualTo(200.0);

        // 6. Guarana-Extrakt (275 mg) → ONLY_IN_OCR
        IngredientCheckResult guaranaResult =
                findByName(result.getIngredientResults(), "Guarana-Extrakt");
        assertThat(guaranaResult.getStatus()).isEqualTo("ONLY_IN_OCR");
        assertThat(guaranaResult.getOcrMg()).isEqualTo(275.0);
    }

    // -----------------------------------------------------------------------
    // Translation-aware matching (CheckService + IngredientTranslationService)
    // -----------------------------------------------------------------------

    /**
     * DB stores "Selenium" (English), OCR parser emits "Selenium", which
     * IngredientTranslationService translates to "Selen".
     * After effectiveName() on both sides the keys agree → MATCH.
     */
    @Test
    void check_seleniumInDb_translatedToSelen_matchesOcrSelen() {
        // DB stores English name "Selenium"
        Supplement supp = supplement("1", "Multi", List.of(ingredient("Selenium", 0.082)));
        // OCR produces "Selen" (German — already translated by IngredientTranslationService)
        OcrResult ocrResult = ocr(List.of(dto("Selen", 0.082)));

        CheckResult result = service.compare(supp, ocrResult);

        assertThat(result.isHasDiscrepancies()).isFalse();
        assertThat(findByName(result.getIngredientResults(), "Selenium").getStatus())
                .isEqualTo("MATCH");
    }

    @Test
    void check_seleniumInOcr_matchesSelenInDb() {
        // DB stores German "Selen", OCR emits English "Selenium"
        Supplement supp = supplement("1", "Multi", List.of(ingredient("Selen", 0.082)));
        OcrResult ocrResult = ocr(List.of(dto("Selenium", 0.082)));

        CheckResult result = service.compare(supp, ocrResult);

        assertThat(result.isHasDiscrepancies()).isFalse();
        assertThat(findByName(result.getIngredientResults(), "Selen").getStatus())
                .isEqualTo("MATCH");
    }

    @Test
    void check_chromeInOcr_matchesChromInDb() {
        // DB stores "Chrom", OCR emits "Chrome" — translation must normalise both
        Supplement supp = supplement("1", "Multi", List.of(ingredient("Chrom", 0.050)));
        OcrResult ocrResult = ocr(List.of(dto("Chrome", 0.050)));

        CheckResult result = service.compare(supp, ocrResult);

        assertThat(result.isHasDiscrepancies()).isFalse();
        assertThat(findByName(result.getIngredientResults(), "Chrom").getStatus())
                .isEqualTo("MATCH");
    }

    @Test
    void check_iodineInOcr_matchesJodInDb() {
        // DB stores "Jod", OCR parser corrects lodine→Iodine, translation maps Iodine→Jod
        Supplement supp = supplement("1", "Multi", List.of(ingredient("Jod", 0.15)));
        OcrResult ocrResult = ocr(List.of(dto("Iodine", 0.15)));

        CheckResult result = service.compare(supp, ocrResult);

        assertThat(result.isHasDiscrepancies()).isFalse();
        assertThat(findByName(result.getIngredientResults(), "Jod").getStatus())
                .isEqualTo("MATCH");
    }
}
