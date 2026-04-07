package org.example.suppcheck.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.suppcheck.model.Ingredient;
import org.example.suppcheck.model.PriceEntry;
import org.example.suppcheck.model.Supplement;
import org.example.suppcheck.service.SupplementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

class SupplementControllerTest {

    private SupplementService service;
    private SupplementController controller;

    @BeforeEach
    void setUp() {
        service = mock(SupplementService.class);
        controller = new SupplementController(service);
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
        Ingredient ing = new Ingredient();
        ing.setName("Kreatin");
        ing.setMg(5000);
        when(service.getAllSupplements()).thenReturn(List.of());
        when(service.getSummedIngredients(anyList(), eq(false))).thenReturn(List.of(ing));

        ConcurrentModel model = new ConcurrentModel();
        String view = controller.showIngredientsSummaryWithWorkout(false, model);

        assertEquals("ingredients_summary", view);
        assertTrue(model.containsAttribute("summedIngredients"));
        assertFalse((boolean) model.getAttribute("isWorkoutDay"));
    }

    @Test
    void showIngredientsSummary_workoutDay_setsFlag() {
        when(service.getAllSupplements()).thenReturn(List.of());
        when(service.getSummedIngredients(anyList(), eq(true))).thenReturn(List.of());

        ConcurrentModel model = new ConcurrentModel();
        controller.showIngredientsSummaryWithWorkout(true, model);

        assertTrue((boolean) model.getAttribute("isWorkoutDay"));
    }

    // --- saveSupplement ---

    @Test
    void saveSupplement_redirectsToNewForm() {
        org.example.suppcheck.dto.SupplementSaveDto dto = new org.example.suppcheck.dto.SupplementSaveDto();
        dto.setName("NewSupp");
        dto.setShop("ESN");
        dto.setPortionSize(10);
        dto.setSupplementType("BASIC");
        dto.setPrice(15.0);

        String view = controller.saveSupplement(dto);

        assertEquals("redirect:/supplements/new?success", view);
        verify(service).saveSupplement(any(Supplement.class));
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
}

