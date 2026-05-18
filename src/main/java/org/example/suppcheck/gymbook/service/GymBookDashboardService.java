package org.example.suppcheck.gymbook.service;

import org.example.suppcheck.gymbook.model.GymExerciseEntry;
import org.example.suppcheck.gymbook.model.GymSession;
import org.example.suppcheck.gymbook.model.GymSetEntry;
import org.example.suppcheck.gymbook.repository.GymSessionRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Analytics-Service für GymBook-Daten aus MongoDB.
 */
@Service
public class GymBookDashboardService {

    private final GymSessionRepository sessionRepo;

    public GymBookDashboardService(GymSessionRepository sessionRepo) {
        this.sessionRepo = sessionRepo;
    }

    // ── Sessions ──────────────────────────────────────────────────────────────

    /** Letzte 30 Trainingseinheiten, absteigend. */
    public List<GymSession> getRecentSessions() {
        return sessionRepo.findTop30ByOrderByDateDesc();
    }

    /** Alle Sessions absteigend. */
    public List<GymSession> getAllSessions() {
        return sessionRepo.findAllByOrderByDateDesc();
    }

    /** Eine einzelne Session nach Datum. */
    public Optional<GymSession> getSessionByDate(String date) {
        return sessionRepo.findById(date);
    }

    public long getTotalSessionCount() {
        return sessionRepo.count();
    }

    // ── Muskel-Heatmap ────────────────────────────────────────────────────────

    /**
     * Zählt pro Muskel-ID, wie oft er in den letzten {@code days} Tagen
     * trainiert wurde (Anzahl Sessions, in denen der Muskel vorkam).
     *
     * @return Map: muscleId → Anzahl Sessions
     */
    public Map<String, Integer> getMuscleHeatmap(int days) {
        List<GymSession> recent = getAllSessions();
        String cutoff = cutoffDate(days);

        Map<String, Integer> heatmap = new LinkedHashMap<>();
        for (GymSession s : recent) {
            if (s.getDate().compareTo(cutoff) < 0) continue;
            Set<String> musclesThisSession = new LinkedHashSet<>();
            for (GymExerciseEntry ex : s.getExercises()) {
                splitMuscles(ex.getPrimaryMuscles()).forEach(musclesThisSession::add);
                splitMuscles(ex.getSecondaryMuscles()).forEach(musclesThisSession::add);
            }
            for (String m : musclesThisSession) {
                heatmap.merge(m, 1, Integer::sum);
            }
        }
        return heatmap;
    }

    // ── Gewichtsverlauf ───────────────────────────────────────────────────────

    /**
     * Gibt für eine Übung eine geordnete Liste von (Datum, MaxGewicht)-Einträgen zurück.
     * Ein Eintrag pro Trainingstag.
     */
    public List<ExerciseProgress> getWeightProgression(String exerciseName) {
        return getAllSessions().stream()
                .sorted(Comparator.comparing(GymSession::getDate))
                .flatMap(s -> s.getExercises().stream()
                        .filter(ex -> exerciseName.equalsIgnoreCase(ex.getName()))
                        .map(ex -> new ExerciseProgress(
                                s.getDate(),
                                ex.getMaxWeightKg(),
                                ex.getTotalSets(),
                                ex.getTotalReps(),
                                ex.getSets()
                        )))
                .collect(Collectors.toList());
    }

    /**
     * Alle Übungsnamen, alphabetisch sortiert (nur Übungen mit Gewichtseinträgen).
     */
    public List<String> getAllExerciseNames() {
        return getAllSessions().stream()
                .flatMap(s -> s.getExercises().stream())
                .map(GymExerciseEntry::getName)
                .filter(n -> n != null && !n.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Häufigste Übungen (Top-N nach Anzahl Sätze insgesamt).
     */
    public Map<String, Long> getTopExercises(int topN) {
        return getAllSessions().stream()
                .flatMap(s -> s.getExercises().stream())
                .collect(Collectors.groupingBy(
                        GymExerciseEntry::getName,
                        Collectors.summingLong(GymExerciseEntry::getTotalSets)
                ))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(topN)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    // ── Hilfstypen ────────────────────────────────────────────────────────────

    public record ExerciseProgress(
            String date,
            double maxWeightKg,
            int    totalSets,
            int    totalReps,
            List<GymSetEntry> sets
    ) {}

    // ── Private Helpers ───────────────────────────────────────────────────────

    private List<String> splitMuscles(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split("\\|"))
                .map(String::trim)
                .filter(s -> !s.isBlank() && !s.equals("null"))
                .collect(Collectors.toList());
    }

    private String cutoffDate(int days) {
        return java.time.LocalDate.now().minusDays(days).toString();
    }
}
