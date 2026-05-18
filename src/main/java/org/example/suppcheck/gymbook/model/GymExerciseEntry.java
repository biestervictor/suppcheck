package org.example.suppcheck.gymbook.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Eine Übung innerhalb einer Trainingseinheit,
 * mit allen zugehörigen Sätzen und Muskelinformationen aus GymBook.
 */
@Getter
@Setter
@NoArgsConstructor
public class GymExerciseEntry {

    private String name;

    /** Pipe-separierte Muskel-IDs, z.B. "020.pectorals|010.shoulders|040.armExtensors" */
    private String primaryMuscles;

    /** Sekundäre Muskeln (pipe-separiert) */
    private String secondaryMuscles;

    /** Übergeordnete Region, z.B. "020.chest", "030.back", "070.legs" */
    private String region;

    private List<GymSetEntry> sets = new ArrayList<>();

    // ── Aggregatwerte (berechnet beim Import) ────────────────────────────────
    private double maxWeightKg;
    private int    totalReps;
    private int    totalSets;

    public void addSet(GymSetEntry s) {
        sets.add(s);
        totalSets++;
        totalReps  += s.getReps();
        if (s.getWeightKg() > maxWeightKg) maxWeightKg = s.getWeightKg();
    }
}
