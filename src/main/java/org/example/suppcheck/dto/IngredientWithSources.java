package org.example.suppcheck.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Ingredient with a list of source supplements that contribute to its total mg.
 * Used in the Daily Intake overview to show which supplements make up each nutrient.
 */
@Getter
@Setter
public class IngredientWithSources {

  /** Nutrient name, e.g. "Vitamin D3". */
  private String name;

  /** Total summed mg across all active supplements. */
  private double mg;

  /**
   * Human-readable source descriptions, e.g. ["D3 Tropfen: 5.000,0 mg", "Multivitamin: 3.000,0 mg"].
   */
  private List<String> sources;

  public IngredientWithSources(String name, double mg, List<String> sources) {
    this.name = name;
    this.mg = mg;
    this.sources = sources;
  }
}
