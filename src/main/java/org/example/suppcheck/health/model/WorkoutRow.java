package org.example.suppcheck.health.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Unified view-row for the health dashboard workout table.
 *
 * <p>Either sourced from a GymBook session (strength = true) or a HealthWorkout
 * (strength = false). The controller merges both sources into this list.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutRow {

    /** ISO date string "yyyy-MM-dd". */
    private String date;

    /** Display tag, e.g. "Krafttraining (Push)", "Gehen", "Joggen", "Sonstiges". */
    private String tag;

    /** Duration in minutes; 0 if unknown (GymBook sessions don't store duration). */
    private double durationMin;

    /** Active calories burned; 0 if unknown. */
    private double kcal;

    /** Distance in km; only set for walking/running workouts. */
    private double distKm;

    /** Comma-separated exercise names; only set for strength (GymBook) rows. */
    private String exercises;

    /** True if this row comes from a GymBook strength session. */
    private boolean strength;
}
