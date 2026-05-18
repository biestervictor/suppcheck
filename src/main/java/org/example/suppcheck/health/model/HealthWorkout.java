package org.example.suppcheck.health.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

/**
 * Eine einzelne Workout-Einheit aus Apple Health.
 */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = "health_workouts")
public class HealthWorkout {

    @Id
    private String id;

    /** Workout-Typ, z.B. "TraditionalStrengthTraining", "Running". */
    @Indexed
    private String activityType;

    /**
     * Klassifiziertes Anzeige-Tag, z.B. "Krafttraining (Push)", "Gehen", "Joggen", "Sonstiges".
     * Wird beim Import gesetzt (ggf. via GymBook-Lookup).
     */
    private String workoutTag;

    @Indexed
    private LocalDate date;

    private String startDate;
    private String endDate;
    private double durationMinutes;
    private double caloriesBurned;
    private double distanceKm;
    private String sourceName;

    // ── Label / Icon-Helpers ─────────────────────────────────────────────────

    public String getActivityLabel() {
        if (activityType == null) return "Unknown";
        return switch (activityType) {
            case "TraditionalStrengthTraining"  -> "Krafttraining";
            case "FunctionalStrengthTraining"   -> "Functional Strength";
            case "Running"                      -> "Laufen";
            case "Walking"                      -> "Gehen";
            case "Cycling"                      -> "Radfahren";
            case "Hiking"                       -> "Wandern";
            case "CoreTraining"                 -> "Core Training";
            case "Yoga"                         -> "Yoga";
            case "Swimming"                     -> "Schwimmen";
            case "CardioDance"                  -> "Tanz/Cardio";
            case "Rowing"                       -> "Rudern";
            default                             -> activityType;
        };
    }

    public String getActivityIcon() {
        if (activityType == null) return "bi-activity";
        return switch (activityType) {
            case "TraditionalStrengthTraining", "FunctionalStrengthTraining" -> "bi-lightning-charge-fill";
            case "Running"      -> "bi-person-walking";
            case "Walking"      -> "bi-person-walking";
            case "Cycling"      -> "bi-bicycle";
            case "Hiking"       -> "bi-geo-alt-fill";
            case "CoreTraining" -> "bi-person-arms-up";
            case "Yoga"         -> "bi-peace";
            case "Swimming"     -> "bi-water";
            default             -> "bi-activity";
        };
    }
}
