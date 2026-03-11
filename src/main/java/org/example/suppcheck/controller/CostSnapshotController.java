package org.example.suppcheck.controller;

import java.util.List;
import org.example.suppcheck.model.CostSnapshot;
import org.example.suppcheck.service.CostSnapshotService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller für Kosten-Snapshots: Speichern und Diagramm-Ansicht.
 */
@Controller
@RequestMapping("/costs")
public class CostSnapshotController {

  private final CostSnapshotService costSnapshotService;

  public CostSnapshotController(CostSnapshotService costSnapshotService) {
    this.costSnapshotService = costSnapshotService;
  }

  /**
   * Speichert einen Kosten-Snapshot und leitet zurück zur Supplemente-Übersicht.
   */
  @PostMapping("/save")
  public String saveSnapshot(@RequestParam double preisProTag,
                             @RequestParam double preisProTagWhey,
                             @RequestParam double preisProTagExtended,
                             @RequestParam double preisProWorkout,
                             @RequestParam double preisProMonat) {
    costSnapshotService.saveSnapshot(preisProTag, preisProTagWhey, preisProTagExtended,
        preisProWorkout, preisProMonat);
    return "redirect:/supplements?saved";
  }

  /**
   * Zeigt das Kosten-Verlaufsdiagramm an.
   */
  @GetMapping("/history")
  public String showHistory(Model model) {
    List<CostSnapshot> snapshots = costSnapshotService.getAllSnapshots();
    model.addAttribute("snapshots", snapshots);
    return "cost_history";
  }
}

