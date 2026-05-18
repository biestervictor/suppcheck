package org.example.suppcheck.gymbook.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Eine Trainingseinheit (Tag) aus dem GymBook-Backup.
 * Dokument-ID ist das ISO-Datum ("yyyy-MM-dd").
 */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = "gym_sessions")
public class GymSession {

    @Id
    private String date; // "yyyy-MM-dd"

    private String tag = "Sonstige"; // "Push", "Pull", "Beine", "Sonstige"

    private List<GymExerciseEntry> exercises = new ArrayList<>();

    public GymSession(String date) {
        this.date = date;
    }

    public void addExercise(GymExerciseEntry e) {
        exercises.add(e);
    }

    /** Gesamtanzahl Sätze über alle Übungen. */
    public int getTotalSets() {
        return exercises.stream().mapToInt(GymExerciseEntry::getTotalSets).sum();
    }

    /** Gesamtanzahl Wiederholungen über alle Übungen. */
    public int getTotalReps() {
        return exercises.stream().mapToInt(GymExerciseEntry::getTotalReps).sum();
    }
}
