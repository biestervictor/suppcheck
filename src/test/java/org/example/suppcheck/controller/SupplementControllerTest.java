package org.example.suppcheck.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

import org.example.suppcheck.dto.IngredientDto;
import org.example.suppcheck.dto.IngredientWithSources;
import org.example.suppcheck.dto.OcrResult;
import org.example.suppcheck.model.StockBatch;
import org.example.suppcheck.model.Ingredient;
import org.example.suppcheck.model.PriceEntry;
import org.example.suppcheck.model.Supplement;
import org.example.suppcheck.service.DailyIntakeSnapshotService;
import org.example.suppcheck.service.CheckService;
import org.example.suppcheck.service.HerstellerService;
import org.example.suppcheck.service.OcrService;
import org.example.suppcheck.service.SupplementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.ui.ConcurrentModel;

class SupplementControllerTest {

    private SupplementService service;
    private DailyIntakeSnapshotService snapshotService;
    private OcrService ocrService;
    private CheckService checkService;
    private HerstellerService herstellerService;
    private SupplementController controller;

    @BeforeEach
    void setUp() {
        service = mock(SupplementService.class);
        snapshotService = mock(DailyIntakeSnapshotService.class);
        ocrService = mock(OcrService.class);
        checkService = mock(CheckService.class);
        herstellerService = mock(HerstellerService.class);
        lenient().when(herstellerService.findAllNames()).thenReturn(List.of());
        controller = new SupplementController(service, snapshotService, ocrService, checkService, herstellerService);
        // Default: getAllSupplements returns empty list (needed by snapshot trigger)
        lenient().when(service.getAllSupplements()).thenReturn(List.of());
    }

    // --- showCreateForm ---

    @Test
    void showCreateForm_returnsFormView() {
        ConcurrentModel model = new ConcurrentModel();
        String view = controller.showCreateForm(model);

        assertEquals("supplement_form", view);
        assertTrue(model.containsAttribute("supplement"));
        assertTrue(model.containsAttribute("types"));
        assertTrue(model.containsAttribute("shops"));
    }

    @Test
    void showCreateForm_supplementHasOneEmptyIngredient() {
        ConcurrentModel model = new ConcurrentModel();
        controller.showCreateForm(model);

        Supplement supp = (Supplement) model.getAttribute("supplement");
        assertNotNull(supp);
        assertNotNull(supp.getIngredients());
        assertEquals(1, supp.getIngredients().size());
    }

    // --- editSupplement ---

    @Test
    void editSupplement_returnsFormView() {
        Supplement supp = new Supplement();
        supp.setId("id-1");
        supp.setName("TestSupp");
        when(service.getSupplementById("id-1")).thenReturn(Optional.of(supp));

        ConcurrentModel model = new ConcurrentModel();
        String view = controller.editSupplement("id-1", model);

        assertEquals("supplement_form", view);
        assertSame(supp, model.getAttribute("supplement"));
        assertTrue(model.containsAttribute("types"));
        assertTrue(model.containsAttribute("shops"));
    }

    @Test
    void editSupplement_notFound_throwsException() {
        when(service.getSupplementById("missing")).thenReturn(Optional.empty());

        ConcurrentModel model = new ConcurrentModel();
        assertThrows(IllegalArgumentException.class,
                () -> controller.editSupplement("missing", model));
    }

    // --- deleteSupplement ---

    @Test
    void deleteSupplement_callsServiceAndRedirects() {
        String view = controller.deleteSupplement("id-1");

        assertEquals("redirect:/supplements", view);
        verify(service).deleteSupplementById("id-1");
    }

    // --- showSupplementsList ---

    @Test
    void showSupplementsList_emptyList_returnsViewWithZeroPrices() {
        when(service.getAllSupplements()).thenReturn(List.of());

        ConcurrentModel model = new ConcurrentModel();
        String view = controller.showSupplementsList(model);

        assertEquals("supplements_list", view);
        assertTrue(model.containsAttribute("supplements"));
        assertTrue(model.containsAttribute("preisProTag"));
        assertTrue(model.containsAttribute("preisProMonat"));
        assertTrue(model.containsAttribute("types"));
        assertTrue(model.containsAttribute("shops"));

        assertEquals(0.0, (double) model.getAttribute("preisProTag"), 0.001);
        assertEquals(0.0, (double) model.getAttribute("preisProMonat"), 0.001);
    }

    @Test
    void showSupplementsList_calculatesBasicPricePerDay() {
        Supplement basic = createSupplement("BASIC", 30.0, 10, false);
        when(service.getAllSupplements()).thenReturn(List.of(basic));

        ConcurrentModel model = new ConcurrentModel();
        controller.showSupplementsList(model);

        // price/portionSize = 30/10 = 3.0
        assertEquals(3.0, (double) model.getAttribute("preisProTag"), 0.001);
    }

    @Test
    void showSupplementsList_inactiveSupplementsAreIgnored() {
        Supplement inactive = createSupplement("BASIC", 30.0, 10, true);
        when(service.getAllSupplements()).thenReturn(List.of(inactive));

        ConcurrentModel model = new ConcurrentModel();
        controller.showSupplementsList(model);

        assertEquals(0.0, (double) model.getAttribute("preisProTag"), 0.001);
        assertEquals(0.0, (double) model.getAttribute("preisProMonat"), 0.001);
    }

    @Test
    void showSupplementsList_wheyAverageCalculation() {
        // Two WHEY supplements: 20/10=2.0 and 40/10=4.0 -> avg = 3.0
        Supplement whey1 = createSupplement("WHEY", 20.0, 10, false);
        Supplement whey2 = createSupplement("WHEY", 40.0, 10, false);
        when(service.getAllSupplements()).thenReturn(List.of(whey1, whey2));

        ConcurrentModel model = new ConcurrentModel();
        controller.showSupplementsList(model);

        // preisProTagWhey = 2 * avgWheyPrice = 2 * 3.0 = 6.0
        assertEquals(6.0, (double) model.getAttribute("preisProTagWhey"), 0.001);
    }

    @Test
    void showSupplementsList_sportGoesToWorkout() {
        Supplement sport = createSupplement("SPORT", 15.0, 5, false);
        when(service.getAllSupplements()).thenReturn(List.of(sport));

        ConcurrentModel model = new ConcurrentModel();
        controller.showSupplementsList(model);

        // price/portionSize = 15/5 = 3.0
        assertEquals(3.0, (double) model.getAttribute("preisProWorkout"), 0.001);
        assertEquals(0.0, (double) model.getAttribute("preisProTag"), 0.001);
    }

    @Test
    void showSupplementsList_extendedPriceCalculation() {
        Supplement ext = createSupplement("EXTENDED", 60.0, 30, false);
        when(service.getAllSupplements()).thenReturn(List.of(ext));

        ConcurrentModel model = new ConcurrentModel();
        controller.showSupplementsList(model);

        // price/portionSize = 60/30 = 2.0
        assertEquals(2.0, (double) model.getAttribute("preisProTagExtended"), 0.001);
    }

    @Test
    void showSupplementsList_monthlyPriceFormula() {
        // BASIC: 30/10=3.0, WHEY: 20/10=2.0 (only one -> avg=2.0), SPORT: 10/5=2.0, EXTENDED: 60/30=2.0
        Supplement basic = createSupplement("BASIC", 30.0, 10, false);
        Supplement whey = createSupplement("WHEY", 20.0, 10, false);
        Supplement sport = createSupplement("SPORT", 10.0, 5, false);
        Supplement ext = createSupplement("EXTENDED", 60.0, 30, false);
        when(service.getAllSupplements()).thenReturn(List.of(basic, whey, sport, ext));

        ConcurrentModel model = new ConcurrentModel();
        controller.showSupplementsList(model);

        // preisProTagWhey = 2 * 2.0 = 4.0
        // preisProMonat = 3.0*30 + (2.0+2.0)*15 + 2.0*30 + 2*4.0
        //               = 90 + 60 + 60 + 8 = 218.0
        assertEquals(218.0, (double) model.getAttribute("preisProMonat"), 0.001);
    }

    // --- showIngredientsSummary ---

    @Test
    void showIngredientsSummary_returnsViewAndPopulatesModel() {
        IngredientWithSources ing = new IngredientWithSources("Kreatin", 5000, List.of("Kreatin Mono: 5.000 mg"));
        when(service.getSummedIngredientsWithSources(anyList(), eq(false))).thenReturn(List.of(ing));

        ConcurrentModel model = new ConcurrentModel();
        String view = controller.showIngredientsSummaryWithWorkout(false, model);

        assertEquals("ingredients_summary", view);
        assertTrue(model.containsAttribute("summedIngredients"));
        assertFalse((boolean) model.getAttribute("isWorkoutDay"));
    }

    @Test
    void showIngredientsSummary_workoutDay_setsFlag() {
        when(service.getSummedIngredientsWithSources(anyList(), eq(true))).thenReturn(List.of());

        ConcurrentModel model = new ConcurrentModel();
        controller.showIngredientsSummaryWithWorkout(true, model);

        assertTrue((boolean) model.getAttribute("isWorkoutDay"));
    }

     // --- saveSupplement ---

     @Test
     void saveSupplement_newSupplement_redirectsToSupplementsList() {
         org.example.suppcheck.dto.SupplementSaveDto dto = new org.example.suppcheck.dto.SupplementSaveDto();
         dto.setName("NewSupp");
         dto.setShop("ESN");
         dto.setPortionSize(10);
         dto.setSupplementType("BASIC");
         dto.setPrice(15.0);
         // no id → new supplement

         String view = controller.saveSupplement(dto);

         assertEquals("redirect:/supplements", view);
         verify(service).saveSupplement(any(Supplement.class));
     }

     @Test
     void saveSupplement_existingSupplement_redirectsToSupplementsList() {
         org.example.suppcheck.dto.SupplementSaveDto dto = new org.example.suppcheck.dto.SupplementSaveDto();
         dto.setId("abc123");
         dto.setName("ExistingSupp");
         dto.setShop("ESN");
         dto.setPortionSize(10);
         dto.setSupplementType("BASIC");
         dto.setPrice(15.0);

         String view = controller.saveSupplement(dto);

         assertEquals("redirect:/supplements", view);
         verify(service).saveSupplement(any(Supplement.class));
     }

     @Test
     void saveSupplement_newWithInitialBatch_callsAddStockBatch() {
         org.example.suppcheck.dto.SupplementSaveDto dto = new org.example.suppcheck.dto.SupplementSaveDto();
         dto.setName("NewSupp");
         dto.setShop("ESN");
         dto.setPortionSize(10);
         dto.setSupplementType("BASIC");
         dto.setInitialFlavor("Chocolate");
         dto.setInitialMhd("2026-12-31");
         dto.setInitialQty(5);

         doAnswer(inv -> { ((Supplement) inv.getArgument(0)).setId("new-id"); return null; })
                 .when(service).saveSupplement(any(Supplement.class));
         when(service.addStockBatch(eq("new-id"), any(StockBatch.class))).thenReturn(5);

         String view = controller.saveSupplement(dto);

         assertEquals("redirect:/supplements", view);
         verify(service).addStockBatch(eq("new-id"), any(StockBatch.class));
     }

     @Test
     void saveSupplement_newWithZeroInitialQty_doesNotCallAddStockBatch() {
         org.example.suppcheck.dto.SupplementSaveDto dto = new org.example.suppcheck.dto.SupplementSaveDto();
         dto.setName("NewSupp");
         dto.setPortionSize(10);
         dto.setSupplementType("BASIC");
         dto.setInitialQty(0);

         doAnswer(inv -> { ((Supplement) inv.getArgument(0)).setId("new-id"); return null; })
                 .when(service).saveSupplement(any(Supplement.class));

         controller.saveSupplement(dto);

         verify(service, never()).addStockBatch(any(), any(StockBatch.class));
     }

     @Test
     void saveSupplement_existingWithBatches_doesNotAddInitialBatch() {
         // Existing supplement that already has stock batches → initialQty is ignored
         org.example.suppcheck.dto.SupplementSaveDto dto = new org.example.suppcheck.dto.SupplementSaveDto();
         dto.setId("existing-id");
         dto.setName("ExistingSupp");
         dto.setPortionSize(10);
         dto.setSupplementType("BASIC");
         dto.setInitialQty(5); // would add a batch, but stockBatches not empty → ignored

         org.example.suppcheck.dto.StockBatchDto existingBatch = new org.example.suppcheck.dto.StockBatchDto();
         existingBatch.setFlavor("Chocolate");
         existingBatch.setQuantity(5);
         existingBatch.setRemaining(3);
         dto.setStockBatches(java.util.List.of(existingBatch));

         controller.saveSupplement(dto);

         verify(service, never()).addStockBatch(any(), any(StockBatch.class));
     }

     @Test
     void saveSupplement_existingWithNoBatches_addsInitialBatch() {
         // Existing supplement with no batches yet → works like first-time setup
         org.example.suppcheck.dto.SupplementSaveDto dto = new org.example.suppcheck.dto.SupplementSaveDto();
         dto.setId("existing-id");
         dto.setName("ExistingSupp");
         dto.setPortionSize(10);
         dto.setSupplementType("BASIC");
         dto.setInitialFlavor("Vanilla");
         dto.setInitialQty(4);
         // stockBatches is empty (default)

         when(service.addStockBatch(eq("existing-id"), any(StockBatch.class))).thenReturn(4);

         String view = controller.saveSupplement(dto);

         assertEquals("redirect:/supplements", view);
         verify(service).addStockBatch(eq("existing-id"), any(StockBatch.class));
     }

    // --- showComparePage ---

    @Test
    void showComparePage_returnsView() {
        when(service.getAllSupplements()).thenReturn(List.of());

        ConcurrentModel model = new ConcurrentModel();
        String view = controller.showComparePage(model);

        assertEquals("supplements_compare", view);
        assertTrue(model.containsAttribute("supplements"));
    }

    @Test
    void showComparePage_sortsIngredientsAlphabetically() {
        Ingredient z = new Ingredient();
        z.setName("Zink");
        z.setMg(10);
        Ingredient a = new Ingredient();
        a.setName("Arginin");
        a.setMg(1000);

        Supplement supp = new Supplement();
        supp.setId("id-1");
        supp.setIngredients(new ArrayList<>(List.of(z, a)));
        when(service.getAllSupplements()).thenReturn(List.of(supp));

        ConcurrentModel model = new ConcurrentModel();
        controller.showComparePage(model);

        @SuppressWarnings("unchecked")
        List<Supplement> result = (List<Supplement>) model.getAttribute("supplements");
        assertNotNull(result);
        assertEquals("Arginin", result.getFirst().getIngredients().get(0).getName());
        assertEquals("Zink", result.getFirst().getIngredients().get(1).getName());
    }

    // --- showPriceHistory ---

    @Test
    void showPriceHistory_notFound_throwsException() {
        when(service.getSupplementById("missing")).thenReturn(Optional.empty());

        ConcurrentModel model = new ConcurrentModel();
        assertThrows(IllegalArgumentException.class,
                () -> controller.showPriceHistory("missing", model));
    }

    // --- Hilfsmethode ---

    private Supplement createSupplement(String type, double price, int portionSize, boolean inactive) {
        Supplement supp = new Supplement();
        supp.setSupplementType(type);
        supp.setPortionSize(portionSize);
        supp.setInactive(inactive);
        PriceEntry entry = new PriceEntry();
        entry.setPrice(price);
        supp.setPrices(new ArrayList<>(List.of(entry)));
        return supp;
    }

    // --- getWheyTemplate ---

    @Test
    void getWheyTemplate_delegatesToServiceAndReturnsResult() {
        IngredientDto dto = new IngredientDto();
        dto.setName("Protein");
        dto.setMg(0);
        when(service.getWheyIngredientTemplate()).thenReturn(List.of(dto));

        List<IngredientDto> result = controller.getWheyTemplate();

        assertEquals(1, result.size());
        assertEquals("Protein", result.getFirst().getName());
        verify(service).getWheyIngredientTemplate();
    }

    @Test
    void getWheyTemplate_emptyWhenNoWheyExists() {
        when(service.getWheyIngredientTemplate()).thenReturn(List.of());

        List<IngredientDto> result = controller.getWheyTemplate();

        assertTrue(result.isEmpty());
    }

    // --- ocrExtract ---

    @Test
    void ocrExtract_success_returnsIngredients() throws Exception {
        IngredientDto dto = new IngredientDto();
        dto.setName("L-Leucin");
        dto.setMg(2100);

        MockMultipartFile file = new MockMultipartFile(
                "image", "label.jpg", "image/jpeg", new byte[]{1, 2, 3});
        OcrResult ocrResult = new OcrResult("raw ocr text", List.of(dto));
        when(ocrService.extractIngredients(anyList())).thenReturn(ocrResult);

        ResponseEntity<OcrResult> response = controller.ocrExtract(List.of(file));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getIngredients().size());
        assertEquals("L-Leucin", response.getBody().getIngredients().getFirst().getName());
    }

    @Test
    void ocrExtract_ocrThrowsException_returns500() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "image", "label.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(ocrService.extractIngredients(anyList())).thenThrow(new RuntimeException("tesseract not found"));

        ResponseEntity<OcrResult> response = controller.ocrExtract(List.of(file));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void ocrExtract_multipleFiles_delegatesToService() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile(
                "image", "label1.jpg", "image/jpeg", new byte[]{1});
        MockMultipartFile file2 = new MockMultipartFile(
                "image", "label2.jpg", "image/jpeg", new byte[]{2});

        IngredientDto dto = new IngredientDto();
        dto.setName("Protein");
        dto.setMg(24_000);
        OcrResult merged = new OcrResult("img1\n\n--- [Bild 2] ---\nimg2", List.of(dto));
        when(ocrService.extractIngredients(anyList())).thenReturn(merged);

        ResponseEntity<OcrResult> response = controller.ocrExtract(List.of(file1, file2));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getIngredients().size());
        assertEquals("Protein", response.getBody().getIngredients().getFirst().getName());
    }

    // --- adjustStock ---

    @Test
    void adjustStock_success_returnsNewStock() {
        when(service.adjustStock("id-1", 1)).thenReturn(4);

        ResponseEntity<java.util.Map<String, Integer>> resp = controller.adjustStock("id-1", 1);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(4, resp.getBody().get("stock"));
    }

    @Test
    void adjustStock_notFound_returns404() {
        when(service.adjustStock("missing", 1))
                .thenThrow(new IllegalArgumentException("not found"));

        ResponseEntity<java.util.Map<String, Integer>> resp = controller.adjustStock("missing", 1);

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    // --- addStockBatch ---

    @Test
    void addStockBatch_success_returnsNewStock() {
        when(service.addStockBatch(eq("id-1"), any(), eq(false))).thenReturn(5);

        ResponseEntity<java.util.Map<String, Integer>> resp =
                controller.addStockBatch("id-1", "Chocolate", "2026-12-31", 3, false);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(5, resp.getBody().get("stock"));
    }

    @Test
    void addStockBatch_noFlavorNoMhd_success() {
        when(service.addStockBatch(eq("id-2"), any(), eq(false))).thenReturn(1);

        ResponseEntity<java.util.Map<String, Integer>> resp =
                controller.addStockBatch("id-2", null, null, 1, false);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void addStockBatch_notFound_returns404() {
        when(service.addStockBatch(eq("missing"), any(), eq(false)))
                .thenThrow(new IllegalArgumentException("not found"));

        ResponseEntity<java.util.Map<String, Integer>> resp =
                controller.addStockBatch("missing", null, null, 1, false);

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    // --- getStockBatches ---

    @Test
    void getStockBatches_returnsBatchesForKnownId() {
        Supplement supp = new Supplement();
        supp.setId("id-1");
        StockBatch batch = new StockBatch("Chocolate", java.time.LocalDate.of(2026, 12, 31),
                java.time.LocalDate.now(), 2);
        supp.setStockBatches(new ArrayList<>(List.of(batch)));
        when(service.getSupplementById("id-1")).thenReturn(Optional.of(supp));

        ResponseEntity<List<StockBatch>> resp = controller.getStockBatches("id-1");

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(1, resp.getBody().size());
        assertEquals("Chocolate", resp.getBody().get(0).getFlavor());
    }

    @Test
    void getStockBatches_returnsEmptyListWhenNoBatches() {
        Supplement supp = new Supplement();
        supp.setId("id-2");
        when(service.getSupplementById("id-2")).thenReturn(Optional.of(supp));

        ResponseEntity<List<StockBatch>> resp = controller.getStockBatches("id-2");

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().isEmpty());
    }

    @Test
    void getStockBatches_returns404ForUnknownId() {
        when(service.getSupplementById("missing")).thenReturn(Optional.empty());

        ResponseEntity<List<StockBatch>> resp = controller.getStockBatches("missing");

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    // --- consumeFromBatch ---

    @Test
    void consumeFromBatch_success_returnsNewStock() {
        when(service.consumeFromBatch(eq("id-1"), eq("Chocolate"), eq("2026-12-31"), eq(2)))
                .thenReturn(java.util.Map.of("stock", 3, "wasInBenutzung", false));

        ResponseEntity<java.util.Map<String, Object>> resp =
                controller.consumeFromBatch("id-1", "Chocolate", "2026-12-31", 2);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(3, resp.getBody().get("stock"));
    }

    @Test
    void consumeFromBatch_noFlavorNoMhd_success() {
        when(service.consumeFromBatch(eq("id-2"), isNull(), isNull(), eq(1)))
                .thenReturn(java.util.Map.of("stock", 0, "wasInBenutzung", false));

        ResponseEntity<java.util.Map<String, Object>> resp =
                controller.consumeFromBatch("id-2", null, null, 1);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(0, resp.getBody().get("stock"));
    }

    @Test
    void consumeFromBatch_notFound_returns404() {
        when(service.consumeFromBatch(eq("missing"), any(), any(), anyInt()))
                .thenThrow(new IllegalArgumentException("not found"));

        ResponseEntity<java.util.Map<String, Object>> resp =
                controller.consumeFromBatch("missing", null, null, 1);

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    // --- addHistoricalPrice ---

    @Test
    void addHistoricalPrice_callsServiceAndRedirects() {
        LocalDate date = LocalDate.of(2025, 6, 15);

        String view = controller.addHistoricalPrice("supp-1", date, 14.99, 24.99);

        verify(service).addHistoricalPrice("supp-1", date, 14.99, 24.99);
        assertEquals("redirect:/supplements/supp-1/prices", view);
    }

    @Test
    void addHistoricalPrice_passesCorrectValues() {
        LocalDate date = LocalDate.of(2024, 12, 1);

        controller.addHistoricalPrice("abc", date, 9.50, 0.0);

        verify(service).addHistoricalPrice("abc", date, 9.50, 0.0);
    }
}

