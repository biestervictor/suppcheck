package org.example.suppcheck.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    // --- Hilfsmethode ---

    private Supplement createActiveSupplement(String type, List<Ingredient> ingredients) {
        Supplement supp = new Supplement();
        supp.setSupplementType(type);
        supp.setInactive(false);
        supp.setIngredients(new ArrayList<>(ingredients));
        return supp;
    }
}

