package org.example.suppcheck.health.service;

import org.example.suppcheck.health.model.HealthDailyMetric;
import org.example.suppcheck.health.model.HealthMetric;
import org.example.suppcheck.health.model.HealthWorkout;
import org.example.suppcheck.health.repository.HealthDailyMetricRepository;
import org.example.suppcheck.health.repository.HealthMetricRepository;
import org.example.suppcheck.health.repository.HealthWorkoutRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HealthDashboardServiceTest {

    private HealthMetricRepository      metricRepo;
    private HealthDailyMetricRepository dailyRepo;
    private HealthWorkoutRepository     workoutRepo;
    private HealthDashboardService      service;

    @BeforeEach
    void setUp() {
        metricRepo  = mock(HealthMetricRepository.class);
        dailyRepo   = mock(HealthDailyMetricRepository.class);
        workoutRepo = mock(HealthWorkoutRepository.class);
        service     = new HealthDashboardService(metricRepo, dailyRepo, workoutRepo);
    }

    // ── getLatestBodyMetrics ──────────────────────────────────────────────────

    @Test
    void getLatestBodyMetrics_returnsOnlyPresentMetrics() {
        HealthMetric bm = new HealthMetric("BodyMass", LocalDate.of(2025, 1, 1), 80.5, "kg", "Withings");
        when(metricRepo.findTopByTypeOrderByDateDesc("BodyMass")).thenReturn(Optional.of(bm));
        // All others return empty
        when(metricRepo.findTopByTypeOrderByDateDesc(argThat(t -> !t.equals("BodyMass")))).thenReturn(Optional.empty());

        Map<String, HealthMetric> result = service.getLatestBodyMetrics();

        assertEquals(1, result.size());
        assertTrue(result.containsKey("BodyMass"));
        assertEquals(80.5, result.get("BodyMass").getValue());
    }

    @Test
    void getLatestBodyMetrics_emptyWhenNoData() {
        when(metricRepo.findTopByTypeOrderByDateDesc(any())).thenReturn(Optional.empty());
        assertTrue(service.getLatestBodyMetrics().isEmpty());
    }

    @Test
    void getLatestBodyMetrics_multipleTypes() {
        HealthMetric bm  = new HealthMetric("BodyMass",          LocalDate.now(), 80.0, "kg",        "Withings");
        HealthMetric vo2 = new HealthMetric("VO2Max",            LocalDate.now(), 52.3, "mL/min·kg",  "Apple Watch");
        HealthMetric rhr = new HealthMetric("RestingHeartRate",  LocalDate.now(), 52.0, "count/min",  "Apple Watch");

        when(metricRepo.findTopByTypeOrderByDateDesc("BodyMass")).thenReturn(Optional.of(bm));
        when(metricRepo.findTopByTypeOrderByDateDesc("VO2Max")).thenReturn(Optional.of(vo2));
        when(metricRepo.findTopByTypeOrderByDateDesc("RestingHeartRate")).thenReturn(Optional.of(rhr));
        when(metricRepo.findTopByTypeOrderByDateDesc(argThat(
                t -> !List.of("BodyMass","VO2Max","RestingHeartRate").contains(t))))
                .thenReturn(Optional.empty());

        Map<String, HealthMetric> result = service.getLatestBodyMetrics();
        assertEquals(3, result.size());
        assertEquals(52.3, result.get("VO2Max").getValue());
    }

    // ── getAllMetricSeries ────────────────────────────────────────────────────

    @Test
    void getAllMetricSeries_delegatesAndReturns() {
        HealthMetric m1 = new HealthMetric("BodyMass", LocalDate.of(2025, 1, 1), 81.0, "kg", "Withings");
        HealthMetric m2 = new HealthMetric("BodyMass", LocalDate.of(2025, 2, 1), 80.0, "kg", "Withings");
        when(metricRepo.findByTypeOrderByDateAsc("BodyMass")).thenReturn(List.of(m1, m2));

        List<HealthMetric> result = service.getAllMetricSeries("BodyMass");

        assertEquals(2, result.size());
        assertEquals(81.0, result.get(0).getValue());
    }

    @Test
    void getAllMetricSeries_emptyList() {
        when(metricRepo.findByTypeOrderByDateAsc("VO2Max")).thenReturn(List.of());
        assertTrue(service.getAllMetricSeries("VO2Max").isEmpty());
    }

    // ── getRecentDailyMetrics ─────────────────────────────────────────────────

    @Test
    void getRecentDailyMetrics_callsRepoWithCorrectDateRange() {
        HealthDailyMetric d = new HealthDailyMetric("2025-01-01");
        when(dailyRepo.findByDateBetweenOrderByDateAsc(any(), any())).thenReturn(List.of(d));

        List<HealthDailyMetric> result = service.getRecentDailyMetrics(30);

        assertEquals(1, result.size());
        verify(dailyRepo).findByDateBetweenOrderByDateAsc(any(), any());
    }

    @Test
    void getRecentDailyMetrics_emptyResult() {
        when(dailyRepo.findByDateBetweenOrderByDateAsc(any(), any())).thenReturn(List.of());
        assertTrue(service.getRecentDailyMetrics(7).isEmpty());
    }

    // ── getWorkoutCountByType ─────────────────────────────────────────────────

    @Test
    void getWorkoutCountByType_countsCorrectly() {
        HealthWorkout w1 = new HealthWorkout(); w1.setActivityType("Running");
        HealthWorkout w2 = new HealthWorkout(); w2.setActivityType("Running");
        HealthWorkout w3 = new HealthWorkout(); w3.setActivityType("Walking");
        when(workoutRepo.findAll()).thenReturn(List.of(w1, w2, w3));

        Map<String, Long> result = service.getWorkoutCountByType();

        assertEquals(2L, result.get("Running"));
        assertEquals(1L, result.get("Walking"));
    }

    @Test
    void getWorkoutCountByType_sortedByCountDesc() {
        HealthWorkout a = new HealthWorkout(); a.setActivityType("A");
        HealthWorkout b = new HealthWorkout(); b.setActivityType("B");
        HealthWorkout b2 = new HealthWorkout(); b2.setActivityType("B");
        when(workoutRepo.findAll()).thenReturn(List.of(a, b, b2));

        Map<String, Long> result = service.getWorkoutCountByType();
        List<String> keys = List.copyOf(result.keySet());

        assertEquals("B", keys.get(0)); // B has 2, should be first
        assertEquals("A", keys.get(1));
    }

    @Test
    void getWorkoutCountByType_emptyList() {
        when(workoutRepo.findAll()).thenReturn(List.of());
        assertTrue(service.getWorkoutCountByType().isEmpty());
    }

    // ── getTotalWorkoutCount ──────────────────────────────────────────────────

    @Test
    void getTotalWorkoutCount_delegatesToRepo() {
        when(workoutRepo.count()).thenReturn(42L);
        assertEquals(42L, service.getTotalWorkoutCount());
    }

    // ── getAvgSleepHours ─────────────────────────────────────────────────────

    @Test
    void getAvgSleepHours_excludesZeroValues() {
        HealthDailyMetric d1 = new HealthDailyMetric("2025-01-01"); d1.addSleepMinutes(480); // 8h
        HealthDailyMetric d2 = new HealthDailyMetric("2025-01-02"); // sleepHours = 0
        HealthDailyMetric d3 = new HealthDailyMetric("2025-01-03"); d3.addSleepMinutes(420); // 7h
        when(dailyRepo.findByDateBetweenOrderByDateAsc(any(), any())).thenReturn(List.of(d1, d2, d3));

        double avg = service.getAvgSleepHours(3);
        // (8 + 7) / 2 = 7.5
        assertEquals(7.5, avg, 0.01);
    }

    @Test
    void getAvgSleepHours_allZeroReturnsZero() {
        HealthDailyMetric d1 = new HealthDailyMetric("2025-01-01");
        when(dailyRepo.findByDateBetweenOrderByDateAsc(any(), any())).thenReturn(List.of(d1));
        assertEquals(0.0, service.getAvgSleepHours(1), 0.001);
    }

    // ── getAvgSteps ──────────────────────────────────────────────────────────

    @Test
    void getAvgSteps_excludesZeroValues() {
        HealthDailyMetric d1 = new HealthDailyMetric("2025-01-01"); d1.addSteps(10000);
        HealthDailyMetric d2 = new HealthDailyMetric("2025-01-02");
        HealthDailyMetric d3 = new HealthDailyMetric("2025-01-03"); d3.addSteps(8000);
        when(dailyRepo.findByDateBetweenOrderByDateAsc(any(), any())).thenReturn(List.of(d1, d2, d3));

        double avg = service.getAvgSteps(3);
        assertEquals(9000.0, avg, 0.1);
    }

    @Test
    void getAvgSteps_emptyListReturnsZero() {
        when(dailyRepo.findByDateBetweenOrderByDateAsc(any(), any())).thenReturn(List.of());
        assertEquals(0.0, service.getAvgSteps(7), 0.001);
    }

    // ── getRecentWorkouts ─────────────────────────────────────────────────────

    @Test
    void getRecentWorkouts_delegatesToRepo() {
        HealthWorkout w = new HealthWorkout();
        w.setActivityType("Running");
        when(workoutRepo.findTop10ByOrderByDateDesc()).thenReturn(List.of(w));

        List<HealthWorkout> result = service.getRecentWorkouts();

        assertEquals(1, result.size());
        assertEquals("Running", result.get(0).getActivityType());
    }

    // ── getAllWorkouts ─────────────────────────────────────────────────────────

    @Test
    void getAllWorkouts_delegatesToRepo() {
        when(workoutRepo.findAllByOrderByDateDesc()).thenReturn(List.of(new HealthWorkout()));
        assertEquals(1, service.getAllWorkouts().size());
    }
}
