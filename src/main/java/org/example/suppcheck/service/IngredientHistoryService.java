package org.example.suppcheck.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.suppcheck.model.Ingredient;
import org.example.suppcheck.model.IngredientChange;
import org.example.suppcheck.model.IngredientHistoryEntry;
import org.springframework.stereotype.Service;

/**
 * Computes ingredient diffs between two saves and produces
 * {@link IngredientHistoryEntry} records embedded in the Supplement document.
 *
 * <p>Only top-level ingredients are compared; sub-ingredients are ignored in
 * the history to keep the diff readable.  Floating-point comparison uses a
 * tolerance of {@value #TOLERANCE} mg so that rounding noise is not flagged.</p>
 */
@Service
public class IngredientHistoryService {

  /** Minimum mg difference that counts as a real change. */
  static final double TOLERANCE = 0.001;

  /**
   * Builds a history entry for the diff between {@code before} and {@code after}.
   *
   * @param before ingredient list before the save (may be null/empty for new supplements)
   * @param after  ingredient list after the save
   * @return non-empty Optional when at least one ingredient was added, removed, or changed
   */
  public Optional<IngredientHistoryEntry> buildEntry(
      List<Ingredient> before, List<Ingredient> after) {

    Map<String, Double> beforeMap = toMap(before);
    Map<String, Double> afterMap  = toMap(after);

    List<IngredientChange> changes = new ArrayList<>();

    // ADDED: present in after, missing in before
    for (Map.Entry<String, Double> e : afterMap.entrySet()) {
      String name   = e.getKey();
      double newMg  = e.getValue();
      if (!beforeMap.containsKey(name)) {
        changes.add(change(name, "ADDED", null, newMg));
      } else {
        double oldMg = beforeMap.get(name);
        if (Math.abs(oldMg - newMg) > TOLERANCE) {
          changes.add(change(name, "VALUE_CHANGED", oldMg, newMg));
        }
      }
    }

    // REMOVED: present in before, missing in after
    for (Map.Entry<String, Double> e : beforeMap.entrySet()) {
      if (!afterMap.containsKey(e.getKey())) {
        changes.add(change(e.getKey(), "REMOVED", e.getValue(), null));
      }
    }

    if (changes.isEmpty()) {
      return Optional.empty();
    }

    IngredientHistoryEntry entry = new IngredientHistoryEntry();
    entry.setDate(LocalDate.now());
    entry.setChanges(changes);
    return Optional.of(entry);
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  /**
   * Converts a top-level ingredient list to a name→mg map.
   * Blank names are skipped. The map preserves insertion order.
   */
  private static Map<String, Double> toMap(List<Ingredient> list) {
    Map<String, Double> map = new LinkedHashMap<>();
    if (list == null) return map;
    for (Ingredient ing : list) {
      if (ing.getName() != null && !ing.getName().isBlank()) {
        map.put(ing.getName(), ing.getMg());
      }
    }
    return map;
  }

  private static IngredientChange change(String name, String type, Double oldMg, Double newMg) {
    IngredientChange c = new IngredientChange();
    c.setName(name);
    c.setChangeType(type);
    c.setOldMg(oldMg);
    c.setNewMg(newMg);
    return c;
  }
}
