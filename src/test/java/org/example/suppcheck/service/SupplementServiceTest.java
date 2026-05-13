package org.example.suppcheck.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.suppcheck.dto.IngredientWithSources;
import org.example.suppcheck.model.Ingredient;
import org.example.suppcheck.model.PriceEntry;
import org.example.suppcheck.model.Supplement;
import org.example.suppcheck.repository.SupplementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SupplementServiceTest {

    private SupplementRepository repository;
    private SupplementService service;

    @BeforeEach
    void setUp() {
        repository = mock(SupplementRepository.class);
        service = new SupplementService(repository);
    }

    // --- getAllSupplements ---

    @Test
    void getAllSupplements_delegatesToRepository() {
        Supplement s1 = new Supplement();
        s1.setName("A");
        when(repository.findAll()).thenReturn(List.of(s1));

        List<Supplement> result = service.getAllSupplements();

        assertEquals(1, result.size());
        assertEquals("A", result.getFirst().getName());
    }

    @Test
    void getAllSupplements_emptyList() {
        when(repository.findAll()).thenReturn(List.of());

        assertTrue(service.getAllSupplements().isEmpty());
    }

    // --- getSupplementById ---

    @Test
    void getSupplementById_found() {
        Supplement s = new Supplement();
        s.setId("id-1");
        when(repository.findById("id-1")).thenReturn(Optional.of(s));

        assertTrue(service.getSupplementById("id-1").isPresent());
    }

    @Test
    void getSupplementById_notFound() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertTrue(service.getSupplementById("missing").isEmpty());
    }

    // --- deleteSupplementById ---

    @Test
    void deleteSupplementById_deletesExisting() {
        Supplement s = new Supplement();
        s.setId("id-1");
        when(repository.findById("id-1")).thenReturn(Optional.of(s));

        service.deleteSupplementById("id-1");

        verify(repository).delete(s);
    }

    @Test
    void deleteSupplementById_notFound_throwsException() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.deleteSupplementById("missing"));
    }

    // --- saveSupplement ---

    @Test
    void saveSupplement_null_doesNothing() {
        service.saveSupplement(null);

        verify(repository, never()).save(any());
    }

    @Test
    void saveSupplement_existingWithOnlyOvpChanged_appendsEntry() {
        Supplement existing = new Supplement();
        existing.setId("id-1");
        PriceEntry old = new PriceEntry();
        old.setPrice(10.0);
        old.setOvp(20.0);
        existing.setPrices(new ArrayList<>(List.of(old)));

        when(repository.findById("id-1")).thenReturn(Optional.of(existing));

        Supplement incoming = new Supplement();
        incoming.setId("id-1");
        incoming.setPrice(10.0); // same
        incoming.setOvp(25.0);   // changed

        service.saveSupplement(incoming);

        verify(repository).save(existing);
        assertEquals(2, existing.getPrices().size());
        assertEquals(25.0, existing.getPrices().getLast().getOvp(), 0.001);
        assertEquals(10.0, existing.getPrices().getLast().getPrice(), 0.001);
    }

    @Test
    void saveSupplement_newWithoutPrice_noPriceEntry() {
        Supplement supp = new Supplement();
        supp.setName("NoPrice");
        supp.setPortionSize(10);

        service.saveSupplement(supp);

        verify(repository).save(supp);
        assertTrue(supp.getPrices().isEmpty());
    }

    @Test
    void saveSupplement_existingUpdatesNonPriceFields() {
        Supplement existing = new Supplement();
        existing.setId("id-1");
        existing.setName("Old");
        existing.setShop("ESN");
        PriceEntry entry = new PriceEntry();
        entry.setPrice(10.0);
        entry.setOvp(20.0);
        existing.setPrices(new ArrayList<>(List.of(entry)));

        when(repository.findById("id-1")).thenReturn(Optional.of(existing));

        Supplement incoming = new Supplement();
        incoming.setId("id-1");
        incoming.setName("New");
        incoming.setShop("Bodylab24");
        incoming.setPortionSize(5);
        incoming.setSupplementType("SPORT");
        incoming.setInactive(true);
        incoming.setDiscount(15.0);
        incoming.setMhdProdukt(true);
        incoming.setPrice(10.0); // same price
        incoming.setOvp(20.0);   // same ovp

        service.saveSupplement(incoming);

        verify(repository).save(existing);
        assertEquals("New", existing.getName());
        assertEquals("Bodylab24", existing.getShop());
        assertEquals(5, existing.getPortionSize());
        assertEquals("SPORT", existing.getSupplementType());
        assertTrue(existing.isInactive());
        assertEquals(15.0, existing.getDiscount());
        assertTrue(existing.isMhdProdukt());
        // Price unchanged -> no new entry
        assertEquals(1, existing.getPrices().size());
    }

    // --- getSummedIngredients ---

    @Test
    void getSummedIngredients_sumsAcrossSupplements() {
        Ingredient ing1 = new Ingredient();
        ing1.setName("Kreatin");
        ing1.setMg(3000);

        Ingredient ing2 = new Ingredient();
        ing2.setName("Kreatin");
        ing2.setMg(2000);

        Supplement s1 = createActiveSupplement("BASIC", List.of(ing1));
        Supplement s2 = createActiveSupplement("BASIC", List.of(ing2));

        List<Ingredient> result = service.getSummedIngredients(List.of(s1, s2), false);

        assertEquals(1, result.size());
        assertEquals("Kreatin", result.getFirst().getName());
        assertEquals(5000, result.getFirst().getMg(), 0.001);
    }

    @Test
    void getSummedIngredients_includesSubIngredients() {
        Ingredient sub = new Ingredient();
        sub.setName("SubVitamin");
        sub.setMg(100);

        Ingredient main = new Ingredient();
        main.setName("MainVitamin");
        main.setMg(500);
        main.setSubIngredients(new ArrayList<>(List.of(sub)));

        Supplement s = createActiveSupplement("BASIC", List.of(main));

        List<Ingredient> result = service.getSummedIngredients(List.of(s), false);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(i -> i.getName().equals("MainVitamin") && i.getMg() == 500));
        assertTrue(result.stream().anyMatch(i -> i.getName().equals("SubVitamin") && i.getMg() == 100));
    }

    @Test
    void getSummedIngredients_skipsInactiveSupplements() {
        Ingredient ing = new Ingredient();
        ing.setName("Zink");
        ing.setMg(25);

        Supplement inactive = createActiveSupplement("BASIC", List.of(ing));
        inactive.setInactive(true);

        List<Ingredient> result = service.getSummedIngredients(List.of(inactive), false);

        assertTrue(result.isEmpty());
    }

    @Test
    void getSummedIngredients_skipsSportOnRestDay() {
        Ingredient ing = new Ingredient();
        ing.setName("Citrullin");
        ing.setMg(6000);

        Supplement sport = createActiveSupplement("SPORT", List.of(ing));

        List<Ingredient> result = service.getSummedIngredients(List.of(sport), false);

        assertTrue(result.isEmpty());
    }

    @Test
    void getSummedIngredients_includesSportOnWorkoutDay() {
        Ingredient ing = new Ingredient();
        ing.setName("Citrullin");
        ing.setMg(6000);

        Supplement sport = createActiveSupplement("SPORT", List.of(ing));

        List<Ingredient> result = service.getSummedIngredients(List.of(sport), true);

        assertEquals(1, result.size());
        assertEquals("Citrullin", result.getFirst().getName());
    }

    @Test
    void getSummedIngredients_emptyList_returnsEmpty() {
        List<Ingredient> result = service.getSummedIngredients(List.of(), false);

        assertTrue(result.isEmpty());
    }

    // --- nonDaily / consumptionIntervalDays ---

    @Test
    void getSummedIngredients_nonDailySupplement_dividesMgByInterval() {
        Ingredient vitD = new Ingredient();
        vitD.setName("Vitamin D3");
        vitD.setMg(5000);
        vitD.setSubIngredients(new ArrayList<>());

        Supplement s = createActiveSupplement("BASIC", List.of(vitD));
        s.setNonDaily(true);
        s.setConsumptionIntervalDays(5);

        List<Ingredient> result = service.getSummedIngredients(List.of(s), false);

        assertEquals(1, result.size());
        assertEquals("Vitamin D3", result.getFirst().getName());
        assertEquals(1000.0, result.getFirst().getMg(), 0.001); // 5000 / 5
    }

    @Test
    void getSummedIngredients_nonDailySubIngredient_dividesMgByInterval() {
        Ingredient sub = new Ingredient();
        sub.setName("Sub");
        sub.setMg(3000);

        Ingredient main = new Ingredient();
        main.setName("Main");
        main.setMg(6000);
        main.setSubIngredients(new ArrayList<>(List.of(sub)));

        Supplement s = createActiveSupplement("BASIC", List.of(main));
        s.setNonDaily(true);
        s.setConsumptionIntervalDays(3);

        List<Ingredient> result = service.getSummedIngredients(List.of(s), false);

        assertEquals(2000.0, result.stream().filter(i -> i.getName().equals("Main"))
                .findFirst().orElseThrow().getMg(), 0.001);
        assertEquals(1000.0, result.stream().filter(i -> i.getName().equals("Sub"))
                .findFirst().orElseThrow().getMg(), 0.001);
    }

    @Test
    void getSummedIngredients_nonDailyWithIntervalOne_treatedAsDaily() {
        Ingredient ing = new Ingredient();
        ing.setName("Kreatin");
        ing.setMg(3000);
        ing.setSubIngredients(new ArrayList<>());

        Supplement s = createActiveSupplement("BASIC", List.of(ing));
        s.setNonDaily(true);
        s.setConsumptionIntervalDays(1); // interval 1 → effectively daily

        List<Ingredient> result = service.getSummedIngredients(List.of(s), false);

        assertEquals(3000.0, result.getFirst().getMg(), 0.001); // no division
    }

    @Test
    void getSummedIngredientsWithSources_nonDaily_showsIntervalLabelInSource() {
        Ingredient d3 = new Ingredient();
        d3.setName("Vitamin D3");
        d3.setMg(6000);
        d3.setSubIngredients(new ArrayList<>());

        Supplement s = new Supplement();
        s.setName("D3 Wochendepot");
        s.setSupplementType("BASIC");
        s.setInactive(false);
        s.setIngredients(List.of(d3));
        s.setNonDaily(true);
        s.setConsumptionIntervalDays(3);

        List<IngredientWithSources> result =
                service.getSummedIngredientsWithSources(List.of(s), false);

        assertEquals(1, result.size());
        assertEquals(2000.0, result.getFirst().getMg(), 0.001); // 6000 / 3
        String src = result.getFirst().getSources().getFirst();
        assertTrue(src.contains("D3 Wochendepot"), "Source should contain supplement name");
        assertTrue(src.contains("(Alle 3 Tage)"), "Source should contain interval hint");
    }

    @Test
    void getSummedIngredientsWithSources_dailyAndNonDailyMixed_correctTotals() {
        Ingredient d3a = new Ingredient();
        d3a.setName("Vitamin D3"); d3a.setMg(5000); d3a.setSubIngredients(new ArrayList<>());
        Supplement daily = new Supplement();
        daily.setName("D3 täglich"); daily.setSupplementType("BASIC"); daily.setInactive(false);
        daily.setIngredients(List.of(d3a));
        daily.setNonDaily(false);

        Ingredient d3b = new Ingredient();
        d3b.setName("Vitamin D3"); d3b.setMg(6000); d3b.setSubIngredients(new ArrayList<>());
        Supplement triDay = new Supplement();
        triDay.setName("D3 alle3"); triDay.setSupplementType("BASIC"); triDay.setInactive(false);
        triDay.setIngredients(List.of(d3b));
        triDay.setNonDaily(true);
        triDay.setConsumptionIntervalDays(3);

        List<IngredientWithSources> result =
                service.getSummedIngredientsWithSources(List.of(daily, triDay), false);

        assertEquals(1, result.size());
        // 5000 + 6000/3 = 5000 + 2000 = 7000
        assertEquals(7000.0, result.getFirst().getMg(), 0.001);
        assertTrue(result.getFirst().getSources().stream()
                .anyMatch(s -> s.contains("D3 alle3") && s.contains("(Alle 3 Tage)")));
        assertTrue(result.getFirst().getSources().stream()
                .anyMatch(s -> s.contains("D3 täglich") && !s.contains("(Alle")));
    }

    @Test
    void saveSupplement_existingSavesNonDailyFields() {
        Supplement existing = new Supplement();
        existing.setId("id-1");
        PriceEntry entry = new PriceEntry();
        entry.setPrice(10.0); entry.setOvp(20.0);
        existing.setPrices(new ArrayList<>(List.of(entry)));
        when(repository.findById("id-1")).thenReturn(Optional.of(existing));

        Supplement incoming = new Supplement();
        incoming.setId("id-1");
        incoming.setPrice(10.0); incoming.setOvp(20.0);
        incoming.setNonDaily(true);
        incoming.setConsumptionIntervalDays(7);

        service.saveSupplement(incoming);

        assertTrue(existing.isNonDaily());
        assertEquals(7, existing.getConsumptionIntervalDays());
    }

    // --- Hilfsmethode ---

    private Supplement createActiveSupplement(String type, List<Ingredient> ingredients) {
        Supplement supp = new Supplement();
        supp.setSupplementType(type);
        supp.setInactive(false);
        supp.setIngredients(new ArrayList<>(ingredients));
        return supp;
    }

    // --- getSummedIngredientsWithSources ---

    @Test
    void getSummedIngredientsWithSources_showsContributingSupplements() {
        Ingredient d3 = new Ingredient();
        d3.setName("Vitamin D3"); d3.setMg(5000); d3.setSubIngredients(new ArrayList<>());
        Supplement s1 = new Supplement();
        s1.setName("D3 Tropfen"); s1.setSupplementType("BASIC"); s1.setInactive(false);
        s1.setIngredients(List.of(d3));

        Ingredient d3b = new Ingredient();
        d3b.setName("Vitamin D3"); d3b.setMg(3000); d3b.setSubIngredients(new ArrayList<>());
        Supplement s2 = new Supplement();
        s2.setName("Multivitamin"); s2.setSupplementType("BASIC"); s2.setInactive(false);
        s2.setIngredients(List.of(d3b));

        List<IngredientWithSources> result = service.getSummedIngredientsWithSources(
                List.of(s1, s2), false);

        assertEquals(1, result.size());
        IngredientWithSources ing = result.get(0);
        assertEquals("Vitamin D3", ing.getName());
        assertEquals(8000.0, ing.getMg(), 0.001);
        assertEquals(2, ing.getSources().size());
        assertTrue(ing.getSources().stream().anyMatch(src -> src.contains("D3 Tropfen")));
        assertTrue(ing.getSources().stream().anyMatch(src -> src.contains("Multivitamin")));
    }

    @Test
    void getSummedIngredientsWithSources_excludesInactiveAndSportOnRestDay() {
        Ingredient mg = new Ingredient();
        mg.setName("Magnesium"); mg.setMg(300); mg.setSubIngredients(new ArrayList<>());
        Supplement inactive = new Supplement();
        inactive.setName("Inaktiv"); inactive.setSupplementType("BASIC"); inactive.setInactive(true);
        inactive.setIngredients(List.of(mg));

        Ingredient leu = new Ingredient();
        leu.setName("L-Leucin"); leu.setMg(2000); leu.setSubIngredients(new ArrayList<>());
        Supplement sport = new Supplement();
        sport.setName("BCAA"); sport.setSupplementType("SPORT"); sport.setInactive(false);
        sport.setIngredients(List.of(leu));

        List<IngredientWithSources> result = service.getSummedIngredientsWithSources(
                List.of(inactive, sport), false);

        assertTrue(result.isEmpty(), "Inactive and SPORT supplements must be excluded on rest day");
    }

    // --- getWheyIngredientTemplate ---

    @Test
    void getWheyIngredientTemplate_noWheySupplements_returnsEmpty() {
        when(repository.findAll()).thenReturn(List.of());

        assertTrue(service.getWheyIngredientTemplate().isEmpty());
    }

    @Test
    void getWheyIngredientTemplate_skipsNonWheySupplements() {
        Supplement basic = createActiveSupplementWithType("BASIC");
        when(repository.findAll()).thenReturn(List.of(basic));

        assertTrue(service.getWheyIngredientTemplate().isEmpty());
    }

    @Test
    void getWheyIngredientTemplate_skipsInactiveWhey() {
        Supplement inactiveWhey = createActiveSupplementWithType("WHEY");
        inactiveWhey.setInactive(true);
        when(repository.findAll()).thenReturn(List.of(inactiveWhey));

        assertTrue(service.getWheyIngredientTemplate().isEmpty());
    }

    @Test
    void getWheyIngredientTemplate_returnsIngredientNamesWithZeroMg() {
        Ingredient protein = new Ingredient();
        protein.setName("Protein");
        protein.setMg(24_000);
        protein.setSubIngredients(new ArrayList<>());

        Supplement whey = createActiveSupplementWithType("WHEY");
        whey.setIngredients(new ArrayList<>(List.of(protein)));
        when(repository.findAll()).thenReturn(List.of(whey));

        List<org.example.suppcheck.dto.IngredientDto> result = service.getWheyIngredientTemplate();

        assertEquals(1, result.size());
        assertEquals("Protein", result.getFirst().getName());
        assertEquals(0.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void getWheyIngredientTemplate_includesSubIngredients() {
        Ingredient leucin = new Ingredient();
        leucin.setName("L-Leucin");
        leucin.setMg(2100);

        Ingredient protein = new Ingredient();
        protein.setName("Protein");
        protein.setMg(24_000);
        protein.setSubIngredients(new ArrayList<>(List.of(leucin)));

        Supplement whey = createActiveSupplementWithType("WHEY");
        whey.setIngredients(new ArrayList<>(List.of(protein)));
        when(repository.findAll()).thenReturn(List.of(whey));

        List<org.example.suppcheck.dto.IngredientDto> result = service.getWheyIngredientTemplate();

        assertEquals(1, result.size());
        assertEquals(1, result.getFirst().getSubIngredients().size());
        assertEquals("L-Leucin", result.getFirst().getSubIngredients().getFirst().getName());
        assertEquals(0.0, result.getFirst().getSubIngredients().getFirst().getMg(), 0.001);
    }

    @Test
    void getWheyIngredientTemplate_deduplicatesAcrossMultipleWheys() {
        Ingredient ing1 = new Ingredient();
        ing1.setName("Protein"); ing1.setMg(24_000); ing1.setSubIngredients(new ArrayList<>());

        Ingredient ing2 = new Ingredient();
        ing2.setName("Protein"); ing2.setMg(22_000); ing2.setSubIngredients(new ArrayList<>());

        Supplement whey1 = createActiveSupplementWithType("WHEY");
        whey1.setIngredients(new ArrayList<>(List.of(ing1)));

        Supplement whey2 = createActiveSupplementWithType("WHEY");
        whey2.setIngredients(new ArrayList<>(List.of(ing2)));

        when(repository.findAll()).thenReturn(List.of(whey1, whey2));

        List<org.example.suppcheck.dto.IngredientDto> result = service.getWheyIngredientTemplate();

        assertEquals(1, result.size(), "Duplicate ingredient names must be deduplicated");
    }

    @Test
    void getWheyIngredientTemplate_mergesSubIngredientsFromMultipleWheys() {
        Ingredient leucin = new Ingredient();
        leucin.setName("L-Leucin"); leucin.setMg(2100);

        Ingredient valin = new Ingredient();
        valin.setName("L-Valin"); valin.setMg(1050);

        Ingredient protein1 = new Ingredient();
        protein1.setName("Protein"); protein1.setMg(24_000);
        protein1.setSubIngredients(new ArrayList<>(List.of(leucin)));

        Ingredient protein2 = new Ingredient();
        protein2.setName("Protein"); protein2.setMg(22_000);
        protein2.setSubIngredients(new ArrayList<>(List.of(valin)));

        Supplement whey1 = createActiveSupplementWithType("WHEY");
        whey1.setIngredients(new ArrayList<>(List.of(protein1)));

        Supplement whey2 = createActiveSupplementWithType("WHEY");
        whey2.setIngredients(new ArrayList<>(List.of(protein2)));

        when(repository.findAll()).thenReturn(List.of(whey1, whey2));

        List<org.example.suppcheck.dto.IngredientDto> result = service.getWheyIngredientTemplate();

        assertEquals(1, result.size());
        assertEquals(2, result.getFirst().getSubIngredients().size(),
                "Sub-ingredients from both Wheys must be merged");
    }

    private Supplement createActiveSupplementWithType(String type) {
        Supplement supp = new Supplement();
        supp.setSupplementType(type);
        supp.setInactive(false);
        supp.setIngredients(new ArrayList<>());
        return supp;
    }

    // --- adjustStock ---

    @Test
    void adjustStock_increment_returnsNewStock() {
        Supplement supp = new Supplement();
        supp.setId("id-1");
        supp.setStock(3);
        when(repository.findById("id-1")).thenReturn(Optional.of(supp));

        int result = service.adjustStock("id-1", 1);

        assertEquals(4, result);
        assertEquals(4, supp.getStock());
        verify(repository).save(supp);
    }

    @Test
    void adjustStock_decrement_returnsNewStock() {
        Supplement supp = new Supplement();
        supp.setId("id-1");
        supp.setStock(5);
        when(repository.findById("id-1")).thenReturn(Optional.of(supp));

        int result = service.adjustStock("id-1", -2);

        assertEquals(3, result);
        assertEquals(3, supp.getStock());
        verify(repository).save(supp);
    }

    @Test
    void adjustStock_decrementBelowZero_floorsAtZero() {
        Supplement supp = new Supplement();
        supp.setId("id-1");
        supp.setStock(1);
        when(repository.findById("id-1")).thenReturn(Optional.of(supp));

        int result = service.adjustStock("id-1", -5);

        assertEquals(0, result);
        assertEquals(0, supp.getStock());
        verify(repository).save(supp);
    }

    @Test
    void adjustStock_notFound_throwsIllegalArgumentException() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.adjustStock("missing", 1));
        verify(repository, never()).save(any());
    }
}

