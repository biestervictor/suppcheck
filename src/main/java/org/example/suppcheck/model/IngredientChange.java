package org.example.suppcheck.model;

import lombok.Getter;
import lombok.Setter;

/**
 * One ingredient-level change recorded inside an {@link IngredientHistoryEntry}.
 * changeType is one of: {@code ADDED}, {@code REMOVED}, {@code VALUE_CHANGED}.
 */
@Getter
@Setter
public class IngredientChange {

  /** Display name of the ingredient. */
  private String name;

  /** ADDED, REMOVED, or VALUE_CHANGED. */
  private String changeType;

  /** Previous mg value; null for ADDED entries. */
  private Double oldMg;

  /** New mg value; null for REMOVED entries. */
  private Double newMg;
}
