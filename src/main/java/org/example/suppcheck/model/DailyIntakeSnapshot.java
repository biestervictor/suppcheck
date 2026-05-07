package org.example.suppcheck.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Point-in-time snapshot of the summed daily intake across all active supplements.
 * Stored separately for rest day and workout day.
 */
@Getter
@Setter
@Document(collection = "daily_intake_snapshots")
public class DailyIntakeSnapshot {

  @Id
  private String id;

  /** Date when this snapshot was taken. */
  private LocalDate date;

  /** Summed ingredient mg per name on a rest day (SPORT excluded). */
  private Map<String, Double> restDay;

  /** Summed ingredient mg per name on a workout day (all types included). */
  private Map<String, Double> workoutDay;

  /**
   * Names of all active supplements at snapshot time.
   * Used to compute change summaries between two consecutive snapshots.
   */
  private List<String> activeSupplementNames;
}
