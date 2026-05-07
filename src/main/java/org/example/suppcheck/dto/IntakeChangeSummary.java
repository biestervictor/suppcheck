package org.example.suppcheck.dto;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Summary of changes between two consecutive DailyIntakeSnapshots.
 */
@Getter
@Setter
public class IntakeChangeSummary {

  /** Supplements that were present in the newer snapshot but not the older one. */
  private List<String> addedSupplements;

  /** Supplements that were present in the older snapshot but not the newer one. */
  private List<String> removedSupplements;

  /**
   * Net ingredient delta: ingredient name → change in mg (positive = gained, negative = lost).
   * Only contains ingredients that actually changed.
   */
  private Map<String, Double> ingredientDeltas;

  /**
   * Human-readable summary lines explaining what changed, e.g.:
   * "Entfernung von 'D3 10000 IE', Ergänzung durch 'D3 6000 IE' → Vitamin D3 netto -4.000 mg"
   */
  private List<String> summaryLines;

  public IntakeChangeSummary(List<String> addedSupplements, List<String> removedSupplements,
      Map<String, Double> ingredientDeltas, List<String> summaryLines) {
    this.addedSupplements = addedSupplements;
    this.removedSupplements = removedSupplements;
    this.ingredientDeltas = ingredientDeltas;
    this.summaryLines = summaryLines;
  }
}
