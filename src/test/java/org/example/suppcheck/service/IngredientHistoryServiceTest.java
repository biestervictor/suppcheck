package org.example.suppcheck.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;

import org.example.suppcheck.model.Ingredient;
import org.example.suppcheck.model.IngredientChange;
import org.example.suppcheck.model.IngredientHistoryEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IngredientHistoryServiceTest {

    private IngredientHistoryService service;

    @BeforeEach
    void setUp() {
        service = new IngredientHistoryService();
    }

    // ── no change ──────────────────────────────────────────────────────────

    @Test
    void buildEntry_identicalLists_returnsEmpty() {
        List<Ingredient> ings = List.of(ing("L-Leucin", 2100), ing("L-Isoleucin", 1050));

        Optional<IngredientHistoryEntry> result = service.buildEntry(ings, ings);

        assertTrue(result.isEmpty());
    }

    @Test
    void buildEntry_bothEmpty_returnsEmpty() {
        assertTrue(service.buildEntry(List.of(), List.of()).isEmpty());
    }

    @Test
    void buildEntry_nullBefore_emptyAfter_returnsEmpty() {
        assertTrue(service.buildEntry(null, List.of()).isEmpty());
    }

    @Test
    void buildEntry_differenceWithinTolerance_notFlagged() {
        // change of 0.0005 is within TOLERANCE → no entry
        List<Ingredient> before = List.of(ing("L-Leucin", 2100.0));
        List<Ingredient> after  = List.of(ing("L-Leucin", 2100.0005));

        assertTrue(service.buildEntry(before, after).isEmpty());
    }

    // ── ADDED ──────────────────────────────────────────────────────────────

    @Test
    void buildEntry_newIngredient_producesAddedChange() {
        List<Ingredient> before = List.of(ing("L-Leucin", 2100));
        List<Ingredient> after  = List.of(ing("L-Leucin", 2100), ing("L-Valin", 1050));

        IngredientHistoryEntry entry = service.buildEntry(before, after).orElseThrow();

        assertEquals(1, entry.getChanges().size());
        IngredientChange c = entry.getChanges().getFirst();
        assertEquals("L-Valin",  c.getName());
        assertEquals("ADDED",    c.getChangeType());
        assertNull(c.getOldMg());
        assertEquals(1050.0, c.getNewMg(), 0.001);
    }

    @Test
    void buildEntry_firstIngredient_allAddedWhenBeforeIsNull() {
        List<Ingredient> after = List.of(ing("L-Leucin", 2100), ing("L-Valin", 1050));

        IngredientHistoryEntry entry = service.buildEntry(null, after).orElseThrow();

        assertEquals(2, entry.getChanges().size());
        assertTrue(entry.getChanges().stream().allMatch(c -> "ADDED".equals(c.getChangeType())));
    }

    // ── REMOVED ────────────────────────────────────────────────────────────

    @Test
    void buildEntry_removedIngredient_producesRemovedChange() {
        List<Ingredient> before = List.of(ing("L-Leucin", 2100), ing("L-Valin", 1050));
        List<Ingredient> after  = List.of(ing("L-Leucin", 2100));

        IngredientHistoryEntry entry = service.buildEntry(before, after).orElseThrow();

        assertEquals(1, entry.getChanges().size());
        IngredientChange c = entry.getChanges().getFirst();
        assertEquals("L-Valin",  c.getName());
        assertEquals("REMOVED",  c.getChangeType());
        assertEquals(1050.0, c.getOldMg(), 0.001);
        assertNull(c.getNewMg());
    }

    // ── VALUE_CHANGED ──────────────────────────────────────────────────────

    @Test
    void buildEntry_changedValue_producesValueChangedEntry() {
        List<Ingredient> before = List.of(ing("L-Leucin", 2100));
        List<Ingredient> after  = List.of(ing("L-Leucin", 2500));

        IngredientHistoryEntry entry = service.buildEntry(before, after).orElseThrow();

        assertEquals(1, entry.getChanges().size());
        IngredientChange c = entry.getChanges().getFirst();
        assertEquals("L-Leucin",       c.getName());
        assertEquals("VALUE_CHANGED",  c.getChangeType());
        assertEquals(2100.0, c.getOldMg(), 0.001);
        assertEquals(2500.0, c.getNewMg(), 0.001);
    }

    // ── mixed changes ──────────────────────────────────────────────────────

    @Test
    void buildEntry_mixedChanges_allTypesDetected() {
        List<Ingredient> before = List.of(
                ing("L-Leucin",    2100),
                ing("L-Valin",     1050),
                ing("Koffein",      200));
        List<Ingredient> after = List.of(
                ing("L-Leucin",    2500),  // VALUE_CHANGED
                ing("Taurin",      1000),  // ADDED
                // L-Valin and Koffein removed
                ing("L-Isoleucin", 1050)); // ADDED

        IngredientHistoryEntry entry = service.buildEntry(before, after).orElseThrow();
        List<IngredientChange> changes = entry.getChanges();

        long added    = changes.stream().filter(c -> "ADDED".equals(c.getChangeType())).count();
        long removed  = changes.stream().filter(c -> "REMOVED".equals(c.getChangeType())).count();
        long modified = changes.stream().filter(c -> "VALUE_CHANGED".equals(c.getChangeType())).count();

        assertEquals(2, added);
        assertEquals(2, removed);
        assertEquals(1, modified);
    }

    // ── date ───────────────────────────────────────────────────────────────

    @Test
    void buildEntry_date_isSetToToday() {
        List<Ingredient> before = List.of(ing("L-Leucin", 2100));
        List<Ingredient> after  = List.of(ing("L-Leucin", 2500));

        IngredientHistoryEntry entry = service.buildEntry(before, after).orElseThrow();

        assertEquals(java.time.LocalDate.now(), entry.getDate());
    }

    // ── blank names ignored ────────────────────────────────────────────────

    @Test
    void buildEntry_blankNameIngredients_ignored() {
        List<Ingredient> before = List.of(ing("", 100), ing("  ", 200));
        List<Ingredient> after  = List.of(ing("", 150), ing("L-Leucin", 2100));

        IngredientHistoryEntry entry = service.buildEntry(before, after).orElseThrow();

        // Only L-Leucin (ADDED) should appear
        assertEquals(1, entry.getChanges().size());
        assertEquals("L-Leucin", entry.getChanges().getFirst().getName());
        assertEquals("ADDED",    entry.getChanges().getFirst().getChangeType());
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static Ingredient ing(String name, double mg) {
        Ingredient i = new Ingredient();
        i.setName(name);
        i.setMg(mg);
        return i;
    }
}
