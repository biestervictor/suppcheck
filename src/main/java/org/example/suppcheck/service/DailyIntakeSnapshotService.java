package org.example.suppcheck.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.example.suppcheck.dto.IntakeChangeSummary;
import org.example.suppcheck.model.DailyIntakeSnapshot;
import org.example.suppcheck.model.Supplement;
import org.example.suppcheck.model.SupplementType;
import org.example.suppcheck.repository.DailyIntakeSnapshotRepository;
import org.springframework.stereotype.Service;

/**
 * Service for saving and analysing DailyIntakeSnapshots.
 */
@Service
public class DailyIntakeSnapshotService {

  private final DailyIntakeSnapshotRepository repository;

  public DailyIntakeSnapshotService(DailyIntakeSnapshotRepository repository) {
    this.repository = repository;
  }

  /**
   * Saves a Daily Intake snapshot for today.
   * If a snapshot for today already exists it is overwritten (idempotent per day).
   *
   * @param supplements all supplements (active + inactive – filtering happens here)
   * @return the persisted snapshot
   */
  public DailyIntakeSnapshot saveSnapshot(List<Supplement> supplements) {
    DailyIntakeSnapshot snapshot = repository.findByDate(LocalDate.now())
        .orElse(new DailyIntakeSnapshot());

    snapshot.setDate(LocalDate.now());
    snapshot.setRestDay(sumIngredients(supplements, false));
    snapshot.setWorkoutDay(sumIngredients(supplements, true));
    snapshot.setActiveSupplementNames(
        supplements.stream()
            .filter(s -> !s.isInactive())
            .map(Supplement::getName)
            .sorted()
            .toList()
    );
    return repository.save(snapshot);
  }

  /**
   * Returns all snapshots sorted by date ascending.
   */
  public List<DailyIntakeSnapshot> getAllSnapshots() {
    return repository.findAllByOrderByDateAsc();
  }

  /**
   * Computes a change summary between every pair of consecutive snapshots.
   * The list is returned in chronological order (oldest change first).
   */
  public List<IntakeChangeSummary> computeChangeSummaries() {
    List<DailyIntakeSnapshot> snapshots = getAllSnapshots();
    List<IntakeChangeSummary> summaries = new ArrayList<>();
    for (int i = 1; i < snapshots.size(); i++) {
      summaries.add(buildSummary(snapshots.get(i - 1), snapshots.get(i)));
    }
    return summaries;
  }

  // ── helpers ──────────────────────────────────────────────────────────────────

  private Map<String, Double> sumIngredients(List<Supplement> supplements, boolean isWorkoutDay) {
    Map<String, Double> totals = new TreeMap<>();
    for (Supplement s : supplements) {
      if (s.isInactive()) continue;
      if (!isWorkoutDay && SupplementType.SPORT.name().equals(s.getSupplementType())) continue;
      int intervalDays = (s.isNonDaily() && s.getConsumptionIntervalDays() > 1)
          ? s.getConsumptionIntervalDays() : 1;
      s.getIngredients().forEach(ing -> {
        if (ing.getName() == null || ing.getName().isBlank()) return;
        totals.merge(ing.getName(), ing.getMg() / intervalDays, Double::sum);
        ing.getSubIngredients().forEach(sub -> {
          if (sub.getName() == null || sub.getName().isBlank()) return;
          totals.merge(sub.getName(), sub.getMg() / intervalDays, Double::sum);
        });
      });
    }
    return totals;
  }

  private IntakeChangeSummary buildSummary(DailyIntakeSnapshot older, DailyIntakeSnapshot newer) {
    List<String> added = new ArrayList<>(newer.getActiveSupplementNames());
    added.removeAll(older.getActiveSupplementNames());

    List<String> removed = new ArrayList<>(older.getActiveSupplementNames());
    removed.removeAll(newer.getActiveSupplementNames());

    // Ingredient deltas (use restDay for comparison)
    Map<String, Double> olderMap = older.getRestDay() != null ? older.getRestDay() : Map.of();
    Map<String, Double> newerMap = newer.getRestDay() != null ? newer.getRestDay() : Map.of();

    Map<String, Double> deltas = new TreeMap<>();
    // all keys from both snapshots
    for (String key : olderMap.keySet()) {
      double delta = newerMap.getOrDefault(key, 0.0) - olderMap.get(key);
      if (Math.abs(delta) > 0.001) deltas.put(key, delta);
    }
    for (String key : newerMap.keySet()) {
      if (!olderMap.containsKey(key)) {
        double delta = newerMap.get(key);
        if (Math.abs(delta) > 0.001) deltas.put(key, delta);
      }
    }

    List<String> lines = buildSummaryLines(older.getDate(), newer.getDate(), added, removed, deltas);
    return new IntakeChangeSummary(added, removed, deltas, lines);
  }

  private List<String> buildSummaryLines(LocalDate olderDate, LocalDate newerDate,
      List<String> added, List<String> removed, Map<String, Double> deltas) {

    List<String> lines = new ArrayList<>();
    if (added.isEmpty() && removed.isEmpty() && deltas.isEmpty()) {
      lines.add("Keine Änderungen zwischen " + olderDate + " und " + newerDate + ".");
      return lines;
    }

    lines.add("Änderungen von " + olderDate + " → " + newerDate + ":");

    if (!removed.isEmpty()) {
      lines.add("  Entfernt: " + String.join(", ", removed));
    }
    if (!added.isEmpty()) {
      lines.add("  Hinzugefügt: " + String.join(", ", added));
    }

    // For each changed ingredient, try to correlate with supplement changes
    Map<String, Double> removedContributions = computeContributions(removed, deltas);
    Map<String, Double> addedContributions = computeContributions(added, deltas);

    for (Map.Entry<String, Double> entry : deltas.entrySet()) {
      String ingName = entry.getKey();
      double delta = entry.getValue();
      String sign = delta > 0 ? "+" : "";
      String line = "  " + ingName + ": " + sign + formatMg(delta);

      // Enrich with supplement names if available
      Double removedMg = removedContributions.get(ingName);
      Double addedMg = addedContributions.get(ingName);
      if ((removedMg != null && Math.abs(removedMg) > 0.001)
          || (addedMg != null && Math.abs(addedMg) > 0.001)) {
        line += buildCorrelationHint(removed, added, removedMg, addedMg, delta);
      }
      lines.add(line);
    }
    return lines;
  }

  /**
   * Builds a correlation hint like:
   * " (Entfernung von 'D3 10000', Ergänzung durch 'D3 6000' → Netto -4.000 mg)"
   */
  private String buildCorrelationHint(List<String> removed, List<String> added,
      Double removedMg, Double addedMg, double netDelta) {
    StringBuilder sb = new StringBuilder(" (");
    if (removedMg != null && Math.abs(removedMg) > 0.001 && !removed.isEmpty()) {
      sb.append("Entfernung von '").append(String.join("', '", removed)).append("'");
    }
    if (addedMg != null && Math.abs(addedMg) > 0.001 && !added.isEmpty()) {
      if (sb.length() > 2) sb.append(", ");
      sb.append("Ergänzung durch '").append(String.join("', '", added)).append("'");
    }
    sb.append(" → Netto ").append(netDelta > 0 ? "+" : "").append(formatMg(netDelta)).append(")");
    return sb.toString();
  }

  /**
   * Placeholder: returns an empty map (no per-supplement ingredient breakdown is stored in the
   * snapshot). Extend this if supplement-level detail is added to the snapshot model.
   */
  private Map<String, Double> computeContributions(List<String> supplementNames,
      Map<String, Double> deltas) {
    Map<String, Double> result = new HashMap<>();
    if (!supplementNames.isEmpty()) {
      // Mark all changed ingredients as potentially correlated with these supplements
      deltas.keySet().forEach(k -> result.put(k, deltas.get(k)));
    }
    return result;
  }

  private String formatMg(double mg) {
    if (Math.abs(mg) >= 1000) {
      return String.format("%,.0f mg", mg).replace(",", ".");
    }
    return String.format("%.1f mg", mg).replace(".", ",");
  }
}
