package org.example.suppcheck.gymbook.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ein Satz (Set) innerhalb einer Übungseinheit.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GymSetEntry {
    private double weightKg;
    private int    reps;
    private String type; // "default", "warmup", etc.
}
