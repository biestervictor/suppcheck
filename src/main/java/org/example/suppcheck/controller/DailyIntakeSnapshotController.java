package org.example.suppcheck.controller;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;
import java.util.TreeMap;
import org.example.suppcheck.dto.IntakeChangeSummary;
import org.example.suppcheck.model.DailyIntakeSnapshot;
import org.example.suppcheck.model.Supplement;
import org.example.suppcheck.service.DailyIntakeSnapshotService;
import org.example.suppcheck.service.SupplementService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller for Daily Intake Snapshots.
 */
@Controller
@RequestMapping("/intake")
public class DailyIntakeSnapshotController {

  private final DailyIntakeSnapshotService snapshotService;
  private final SupplementService supplementService;

  public DailyIntakeSnapshotController(DailyIntakeSnapshotService snapshotService,
      SupplementService supplementService) {
    this.snapshotService = snapshotService;
    this.supplementService = supplementService;
  }

  /**
   * Saves a Daily Intake snapshot and redirects back to the daily intake overview.
   */
  @PostMapping("/snapshot")
  public String saveSnapshot() {
    List<Supplement> supplements = supplementService.getAllSupplements();
    snapshotService.saveSnapshot(supplements);
    return "redirect:/supplements/ingredients/summary?snapshotSaved";
  }

  /**
   * Shows the Intake History page with chart and change summaries.
   */
  @GetMapping("/history")
  public String showHistory(Model model) {
    List<DailyIntakeSnapshot> snapshots = snapshotService.getAllSnapshots();
    List<IntakeChangeSummary> changes = snapshotService.computeChangeSummaries();

    // Collect the union of all ingredient names (sorted) for the table header and chart
    SequencedSet<String> allNames = new LinkedHashSet<>();
    for (DailyIntakeSnapshot s : snapshots) {
      if (s.getRestDay() != null)    allNames.addAll(new TreeMap<>(s.getRestDay()).keySet());
      if (s.getWorkoutDay() != null) allNames.addAll(new TreeMap<>(s.getWorkoutDay()).keySet());
    }
    List<String> allIngredientNames = List.copyOf(allNames);

    // Build matrices: ingredientName → [value per snapshot in chronological order]
    Map<String, List<Double>> restMatrix    = buildMatrix(snapshots, allIngredientNames, false);
    Map<String, List<Double>> workoutMatrix = buildMatrix(snapshots, allIngredientNames, true);

    model.addAttribute("snapshots", snapshots);
    model.addAttribute("changes", changes);
    model.addAttribute("allIngredientNames", allIngredientNames);
    model.addAttribute("restDayMatrix", restMatrix);
    model.addAttribute("workoutDayMatrix", workoutMatrix);
    return "intake_history";
  }

  private Map<String, List<Double>> buildMatrix(List<DailyIntakeSnapshot> snapshots,
      List<String> names, boolean workout) {
    Map<String, List<Double>> matrix = new LinkedHashMap<>();
    for (String name : names) {
      List<Double> values = snapshots.stream()
          .map(s -> {
            Map<String, Double> day = workout ? s.getWorkoutDay() : s.getRestDay();
            return day != null ? day.getOrDefault(name, null) : null;
          })
          .toList();
      matrix.put(name, values);
    }
    return matrix;
  }
}
