package org.example.suppcheck.model;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Speichert eine Momentaufnahme der berechneten Kosten zu einem bestimmten Datum.
 */
@Setter
@Getter
@Document(collection = "cost_snapshots")
public class CostSnapshot {

  @Id
  private String id;

  /** Datum der Aufnahme. */
  private LocalDate date;

  /** Monat im Format YYYY-MM (zur Gruppierung). */
  private String month;

  private double preisProTag;
  private double preisProTagWhey;
  private double preisProTagExtended;
  private double preisProWorkout;
  private double preisProMonat;
}

