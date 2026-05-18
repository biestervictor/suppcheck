package org.example.suppcheck.health.service;

import org.example.suppcheck.health.model.HealthDailyMetric;
import org.example.suppcheck.health.model.HealthMetric;
import org.example.suppcheck.health.model.HealthWorkout;
import org.example.suppcheck.health.repository.HealthDailyMetricRepository;
import org.example.suppcheck.health.repository.HealthMetricRepository;
import org.example.suppcheck.health.repository.HealthWorkoutRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Liefert aggregierte Daten für das Health-Dashboard und alle Unter-Seiten.
 */
@Service
public class HealthDashboardService {

    private static final List<String> BODY_METRIC_TYPES = List.of(
            "BodyMass", "LeanBodyMass", "BodyFatPercentage", "BMI",
            "VO2Max", "RestingHeartRate", "WalkingHeartRate",
            "SystolicBP", "DiastolicBP", "WristTemperature",
            "WalkingSteadiness", "SixMinuteWalkDistance", "HRRecovery"
    );

    private final HealthMetricRepository    metricRepo;
    private final HealthDailyMetricRepository dailyRepo;
    private final HealthWorkoutRepository   workoutRepo;

    public HealthDashboardService(HealthMetricRepository metricRepo,
                                  HealthDailyMetricRepository dailyRepo,
                                  HealthWorkoutRepository workoutRepo) {
        this.metricRepo  = metricRepo;
        this.dailyRepo   = dailyRepo;
        this.workoutRepo = workoutRepo;
    }

    // ── Körper-Metriken ───────────────────────────────────────────────────────

    /**
     * Neuester Messwert je Körper-Metrik-Typ.
     * Typen ohne Datenpunkte werden nicht zurückgegeben.
     */
    public Map<String, HealthMetric> getLatestBodyMetrics() {
        Map<String, HealthMetric> result = new LinkedHashMap<>();
        for (String type : BODY_METRIC_TYPES) {
            metricRepo.findTopByTypeOrderByDateDesc(type).ifPresent(m -> result.put(type, m));
        }
        return result;
    }

    /** Gesamte Zeitreihe für einen Metrik-Typ, aufsteigend sortiert. */
    public List<HealthMetric> getAllMetricSeries(String type) {
        return metricRepo.findByTypeOrderByDateAsc(type);
    }

    /** Zeitreihe für die letzten {@code days} Tage, aufsteigend sortiert. */
    public List<HealthMetric> getMetricSeries(String type, int days) {
        LocalDate from = LocalDate.now().minusDays(days - 1L);
        LocalDate to   = LocalDate.now();
        return metricRepo.findByTypeAndDateBetweenOrderByDateAsc(type, from, to);
    }

    // ── Tägliche Aggregate ────────────────────────────────────────────────────

    /** Alle täglichen Metriken, aufsteigend nach Datum. */
    public List<HealthDailyMetric> getAllDailyMetrics() {
        return dailyRepo.findAllByOrderByDateAsc();
    }

    /**
     * Tägliche Metriken der letzten {@code days} Tage (einschließlich heute),
     * aufsteigend sortiert.
     */
    public List<HealthDailyMetric> getRecentDailyMetrics(int days) {
        LocalDate from = LocalDate.now().minusDays(days - 1L);
        LocalDate to   = LocalDate.now();
        return dailyRepo.findByDateBetweenOrderByDateAsc(from.toString(), to.toString());
    }

    /** Tägliche Metriken im angegebenen ISO-Datumsbereich (yyyy-MM-dd). */
    public List<HealthDailyMetric> getDailyMetricsInRange(String from, String to) {
        return dailyRepo.findByDateBetweenOrderByDateAsc(from, to);
    }

    // ── Workouts ──────────────────────────────────────────────────────────────

    /** Die 10 jüngsten Workouts, absteigend nach Datum. */
    public List<HealthWorkout> getRecentWorkouts() {
        return workoutRepo.findTop10ByOrderByDateDesc();
    }

    /** Alle Workouts, absteigend nach Datum. */
    public List<HealthWorkout> getAllWorkouts() {
        return workoutRepo.findAllByOrderByDateDesc();
    }

    /**
     * Liefert eine Map Datum (yyyy-MM-dd) → Dauer in Minuten
     * für alle Krafttraining-Workouts aus Apple Health.
     *
     * <p>Wird vom GymBook-Dashboard verwendet, um Sessions mit Dauer anzureichern.</p>
     */
    public Map<String, Double> getStrengthDurationByDate() {
        Map<String, Double> result = new LinkedHashMap<>();
        for (HealthWorkout w : workoutRepo.findAllByOrderByDateDesc()) {
            if (("TraditionalStrengthTraining".equals(w.getActivityType())
                 || "FunctionalStrengthTraining".equals(w.getActivityType()))
                    && w.getDurationMinutes() > 0) {
                result.put(w.getDate().toString(), w.getDurationMinutes());
            }
        }
        return result;
    }

    /** Anzahl Workouts je Aktivitätstyp, sortiert nach Häufigkeit absteigend. */
    public Map<String, Long> getWorkoutCountByType() {
        List<HealthWorkout> all = workoutRepo.findAll();
        Map<String, Long> raw = new HashMap<>();
        for (HealthWorkout w : all) {
            raw.merge(w.getActivityType(), 1L, Long::sum);
        }
        return raw.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    /** Gesamtanzahl der Workouts. */
    public long getTotalWorkoutCount() {
        return workoutRepo.count();
    }

    // ── Hilfsmethoden für Statistiken ─────────────────────────────────────────

    /**
     * Durchschnittlicher Schlaf (h) über die letzten {@code days} Tage.
     * Tage ohne Schlafwert werden ignoriert.
     */
    public double getAvgSleepHours(int days) {
        return getRecentDailyMetrics(days).stream()
                .filter(d -> d.getSleepHours() > 0)
                .mapToDouble(HealthDailyMetric::getSleepHours)
                .average()
                .orElse(0.0);
    }

    /**
     * Durchschnittliche Schrittzahl über die letzten {@code days} Tage.
     * Tage ohne Schrittwert werden ignoriert.
     */
    public double getAvgSteps(int days) {
        return getRecentDailyMetrics(days).stream()
                .filter(d -> d.getSteps() > 0)
                .mapToDouble(HealthDailyMetric::getSteps)
                .average()
                .orElse(0.0);
    }
}
