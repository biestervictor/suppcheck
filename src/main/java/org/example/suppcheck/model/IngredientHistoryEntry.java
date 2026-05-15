package org.example.suppcheck.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Snapshot of ingredient changes for one save event.
 * Stored as an embedded list inside {@link Supplement#getIngredientHistory()}.
 */
@Getter
@Setter
public class IngredientHistoryEntry {

  /** Date when the change was recorded. */
  private LocalDate date;

  /** Ordered list of individual ingredient changes for this save event. */
  private List<IngredientChange> changes = new ArrayList<>();
}
