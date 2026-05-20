package org.example.suppcheck.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.suppcheck.model.Ingredient;
import org.example.suppcheck.model.IngredientHistoryEntry;
import org.example.suppcheck.model.Supplement;
import org.example.suppcheck.repository.SupplementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the Vorgänger/Nachfolger (predecessor/successor) feature in SupplementService.
 */
class SupplementServiceVersionTest {

    private SupplementRepository repository;
    private SupplementService service;

    @BeforeEach
    void setUp() {
        repository = mock(SupplementRepository.class);
        service = new SupplementService(repository, new IngredientHistoryService());
    }

    // ── findVorgaenger ────────────────────────────────────────────────────

    @Test
    void findVorgaenger_returnsSupplementWhoseNachfolgerIdMatchesGivenId() {
        Supplement v1 = new Supplement();
        v1.setId("v1-id");
        v1.setName("Crank Pump Pro V1");
        v1.setNachfolgerId("v2-id");

        when(repository.findByNachfolgerId("v2-id")).thenReturn(List.of(v1));

        Optional<Supplement> result = service.findVorgaenger("v2-id");

        assertTrue(result.isPresent());
        assertEquals("v1-id", result.get().getId());
        assertEquals("Crank Pump Pro V1", result.get().getName());
    }

    @Test
    void findVorgaenger_returnsEmptyWhenNoMatch() {
        when(repository.findByNachfolgerId("unknown-id")).thenReturn(List.of());

        Optional<Supplement> result = service.findVorgaenger("unknown-id");

        assertTrue(result.isEmpty());
    }

    @Test
    void findVorgaenger_returnsFirstWhenMultipleMatchExist() {
        // Dateninkonsistenz – erster Treffer gewinnt
        Supplement v1a = new Supplement();
        v1a.setId("v1a");
        Supplement v1b = new Supplement();
        v1b.setId("v1b");

        when(repository.findByNachfolgerId("v2-id")).thenReturn(List.of(v1a, v1b));

        Optional<Supplement> result = service.findVorgaenger("v2-id");

        assertTrue(result.isPresent());
        assertEquals("v1a", result.get().getId());
    }

    // ── setNachfolgerOf ───────────────────────────────────────────────────

    @Test
    void setNachfolgerOf_updatesNachfolgerIdAndSaves() {
        Supplement v1 = new Supplement();
        v1.setId("v1-id");
        v1.setName("Crank Pump Pro V1");

        when(repository.findById("v1-id")).thenReturn(Optional.of(v1));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.setNachfolgerOf("v1-id", "v2-id");

        assertEquals("v2-id", v1.getNachfolgerId());
        verify(repository).save(v1);
    }

    @Test
    void setNachfolgerOf_throwsWhenVorgaengerNotFound() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.setNachfolgerOf("missing", "v2-id"));
        assertTrue(ex.getMessage().contains("missing"));
    }

    // ── computeVersionDiff ────────────────────────────────────────────────

    @Test
    void computeVersionDiff_detectsAddedIngredient() {
        Supplement v1 = supplementWithIngredients("Citrullin", 6000);
        Supplement v2 = supplementWithIngredients("Citrullin", 6000, "Arginin", 3000);

        Optional<IngredientHistoryEntry> diff = service.computeVersionDiff(v1, v2);

        assertTrue(diff.isPresent());
        boolean hasAdded = diff.get().getChanges().stream()
                .anyMatch(c -> "Arginin".equals(c.getName()) && "ADDED".equals(c.getChangeType()));
        assertTrue(hasAdded);
    }

    @Test
    void computeVersionDiff_detectsRemovedIngredient() {
        Supplement v1 = supplementWithIngredients("Citrullin", 6000, "Arginin", 3000);
        Supplement v2 = supplementWithIngredients("Citrullin", 6000);

        Optional<IngredientHistoryEntry> diff = service.computeVersionDiff(v1, v2);

        assertTrue(diff.isPresent());
        boolean hasRemoved = diff.get().getChanges().stream()
                .anyMatch(c -> "Arginin".equals(c.getName()) && "REMOVED".equals(c.getChangeType()));
        assertTrue(hasRemoved);
    }

    @Test
    void computeVersionDiff_detectsChangedValue() {
        Supplement v1 = supplementWithIngredients("Citrullin", 6000);
        Supplement v2 = supplementWithIngredients("Citrullin", 8000);

        Optional<IngredientHistoryEntry> diff = service.computeVersionDiff(v1, v2);

        assertTrue(diff.isPresent());
        boolean hasChanged = diff.get().getChanges().stream()
                .anyMatch(c -> "Citrullin".equals(c.getName()) && "VALUE_CHANGED".equals(c.getChangeType()));
        assertTrue(hasChanged);
    }

    @Test
    void computeVersionDiff_returnsEmptyWhenIdentical() {
        Supplement v1 = supplementWithIngredients("Citrullin", 6000);
        Supplement v2 = supplementWithIngredients("Citrullin", 6000);

        Optional<IngredientHistoryEntry> diff = service.computeVersionDiff(v1, v2);

        assertTrue(diff.isEmpty());
    }

    // ── saveSupplement: nachfolgerId wird im Update-Pfad gespeichert ───────

    @Test
    void saveSupplement_persistsNachfolgerIdOnUpdate() {
        Supplement existing = new Supplement();
        existing.setId("id-1");
        existing.setName("OldName");
        existing.setNachfolgerId(null);
        existing.setPrices(new ArrayList<>());
        existing.setIngredients(new ArrayList<>());
        existing.setStockBatches(new ArrayList<>());

        when(repository.findById("id-1")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findByStockBatchesInBenutzungIsTrue()).thenReturn(List.of());

        Supplement updated = new Supplement();
        updated.setId("id-1");
        updated.setName("NewName");
        updated.setNachfolgerId("v2-id");
        updated.setIngredients(new ArrayList<>());
        updated.setStockBatches(new ArrayList<>());

        service.saveSupplement(updated);

        assertEquals("v2-id", existing.getNachfolgerId());
    }

    // ── Hilfsmethode ─────────────────────────────────────────────────────

    /** Erstellt ein Supplement mit Zutaten aus abwechselnden Name-Mg-Paaren (Object-Varargs). */
    private Supplement supplementWithIngredients(Object... nameAndMgPairs) {
        Supplement s = new Supplement();
        s.setId("id-" + System.nanoTime());
        List<Ingredient> ingredients = new ArrayList<>();
        for (int i = 0; i < nameAndMgPairs.length - 1; i += 2) {
            Ingredient ing = new Ingredient();
            ing.setName((String) nameAndMgPairs[i]);
            ing.setMg(((Number) nameAndMgPairs[i + 1]).doubleValue());
            ingredients.add(ing);
        }
        s.setIngredients(ingredients);
        return s;
    }
}
