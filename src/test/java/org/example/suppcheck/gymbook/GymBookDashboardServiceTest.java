package org.example.suppcheck.gymbook;

import org.example.suppcheck.gymbook.model.GymExerciseEntry;
import org.example.suppcheck.gymbook.model.GymSession;
import org.example.suppcheck.gymbook.model.GymSetEntry;
import org.example.suppcheck.gymbook.repository.GymSessionRepository;
import org.example.suppcheck.gymbook.service.GymBookDashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GymBookDashboardServiceTest {

    private GymSessionRepository repo;
    private GymBookDashboardService service;

    @BeforeEach
    void setUp() {
        repo    = mock(GymSessionRepository.class);
        service = new GymBookDashboardService(repo);
        lenient().when(repo.findAllByOrderByDateDesc()).thenReturn(List.of());
        lenient().when(repo.findTop30ByOrderByDateDesc()).thenReturn(List.of());
        lenient().when(repo.count()).thenReturn(0L);
    }

    // ── getTotalSessionCount ──────────────────────────────────────────────────

    @Test
    void totalSessionCount_delegatesToRepository() {
        when(repo.count()).thenReturn(42L);
        assertEquals(42L, service.getTotalSessionCount());
    }

    // ── getRecentSessions ─────────────────────────────────────────────────────

    @Test
    void recentSessions_returnsTop30() {
        GymSession s = new GymSession("2026-05-01");
        when(repo.findTop30ByOrderByDateDesc()).thenReturn(List.of(s));
        List<GymSession> result = service.getRecentSessions();
        assertEquals(1, result.size());
        assertEquals("2026-05-01", result.get(0).getDate());
    }

    // ── getMuscleHeatmap ──────────────────────────────────────────────────────

    @Test
    void muscleHeatmap_countsMusclesPerSession() {
        GymExerciseEntry ex = new GymExerciseEntry();
        ex.setName("Bankdrücken");
        ex.setPrimaryMuscles("020.pectorals|010.shoulders");
        ex.setSecondaryMuscles("040.armExtensors"); // secondary muscles are NOT counted
        ex.setRegion("020.chest");

        GymSession s = new GymSession("2026-05-16");
        s.addExercise(ex);

        when(repo.findAllByOrderByDateDesc()).thenReturn(List.of(s));

        Map<String, Integer> heatmap = service.getMuscleHeatmap(90);
        assertEquals(1, heatmap.get("020.pectorals"));
        assertEquals(1, heatmap.get("010.shoulders"));
        // secondary muscles are excluded from the heatmap (only primaryMuscles count)
        assertFalse(heatmap.containsKey("040.armExtensors"));
    }

    @Test
    void muscleHeatmap_excludesSessionsOutsideDateWindow() {
        GymExerciseEntry ex = new GymExerciseEntry();
        ex.setName("Kniebeugen");
        ex.setPrimaryMuscles("070.quadriceps");
        ex.setSecondaryMuscles("");

        // Session 200 days ago – outside 30-day window
        String oldDate = LocalDate.now().minusDays(200).toString();
        GymSession old = new GymSession(oldDate);
        old.addExercise(ex);

        when(repo.findAllByOrderByDateDesc()).thenReturn(List.of(old));

        Map<String, Integer> heatmap = service.getMuscleHeatmap(30);
        assertFalse(heatmap.containsKey("070.quadriceps"));
    }

    @Test
    void muscleHeatmap_sameMuscleTwoSessionsCounts2() {
        GymExerciseEntry ex1 = new GymExerciseEntry();
        ex1.setName("Beinstrecken");
        ex1.setPrimaryMuscles("070.quadriceps");
        ex1.setSecondaryMuscles("");

        GymExerciseEntry ex2 = new GymExerciseEntry();
        ex2.setName("Hackenschmidt");
        ex2.setPrimaryMuscles("070.quadriceps");
        ex2.setSecondaryMuscles("");

        GymSession s1 = new GymSession(LocalDate.now().minusDays(1).toString());
        s1.addExercise(ex1);
        GymSession s2 = new GymSession(LocalDate.now().minusDays(3).toString());
        s2.addExercise(ex2);

        when(repo.findAllByOrderByDateDesc()).thenReturn(List.of(s1, s2));

        Map<String, Integer> heatmap = service.getMuscleHeatmap(30);
        assertEquals(2, heatmap.get("070.quadriceps"));
    }

    // ── getWeightProgression ──────────────────────────────────────────────────

    @Test
    void weightProgression_returnsProgressForExercise() {
        GymExerciseEntry ex = new GymExerciseEntry();
        ex.setName("Beinstrecken");
        ex.setPrimaryMuscles("070.quadriceps");
        ex.addSet(new GymSetEntry(50.0, 12, "default"));
        ex.addSet(new GymSetEntry(55.0, 10, "default"));

        GymSession s = new GymSession("2026-05-16");
        s.addExercise(ex);

        when(repo.findAllByOrderByDateDesc()).thenReturn(List.of(s));

        List<GymBookDashboardService.ExerciseProgress> prog = service.getWeightProgression("Beinstrecken");
        assertEquals(1, prog.size());
        assertEquals("2026-05-16", prog.get(0).date());
        assertEquals(55.0, prog.get(0).maxWeightKg());
        assertEquals(2, prog.get(0).totalSets());
    }

    @Test
    void weightProgression_emptyForUnknownExercise() {
        when(repo.findAllByOrderByDateDesc()).thenReturn(List.of());
        assertTrue(service.getWeightProgression("Nichtexistent").isEmpty());
    }

    // ── getAllExerciseNames ───────────────────────────────────────────────────

    @Test
    void allExerciseNames_returnsSortedDistinctNames() {
        GymExerciseEntry e1 = new GymExerciseEntry(); e1.setName("Kniebeugen");
        GymExerciseEntry e2 = new GymExerciseEntry(); e2.setName("Bankdrücken");
        GymExerciseEntry e3 = new GymExerciseEntry(); e3.setName("Kniebeugen"); // duplicate

        GymSession s = new GymSession("2026-05-16");
        s.addExercise(e1); s.addExercise(e2); s.addExercise(e3);

        when(repo.findAllByOrderByDateDesc()).thenReturn(List.of(s));

        List<String> names = service.getAllExerciseNames();
        assertEquals(2, names.size());
        assertEquals("Bankdrücken", names.get(0)); // alphabetically first
        assertEquals("Kniebeugen", names.get(1));
    }

    // ── getMuscleExercises ────────────────────────────────────────────────────

    @Test
    void muscleExercises_returnsExercisesPerMuscle() {
        GymExerciseEntry ex = new GymExerciseEntry();
        ex.setName("Bankdrücken");
        ex.setPrimaryMuscles("020.pectorals|010.shoulders");
        ex.setSecondaryMuscles("040.armExtensors");
        ex.addSet(new GymSetEntry(80.0, 10, "default"));

        GymSession s = new GymSession(java.time.LocalDate.now().toString());
        s.addExercise(ex);

        when(repo.findAllByOrderByDateDesc()).thenReturn(List.of(s));

        Map<String, List<String>> result = service.getMuscleExercises(90);
        assertTrue(result.get("020.pectorals").contains("Bankdrücken"));
        assertTrue(result.get("010.shoulders").contains("Bankdrücken"));
        // secondary muscles not included
        assertFalse(result.containsKey("040.armExtensors"));
    }

    @Test
    void muscleExercises_excludesSessionsOutsideDateWindow() {
        GymExerciseEntry ex = new GymExerciseEntry();
        ex.setName("Kniebeugen");
        ex.setPrimaryMuscles("070.quadriceps");
        ex.addSet(new GymSetEntry(60.0, 10, "default"));

        String oldDate = java.time.LocalDate.now().minusDays(200).toString();
        GymSession old = new GymSession(oldDate);
        old.addExercise(ex);

        when(repo.findAllByOrderByDateDesc()).thenReturn(List.of(old));

        Map<String, List<String>> result = service.getMuscleExercises(30);
        assertFalse(result.containsKey("070.quadriceps"));
    }

    // ── getTopExercises ───────────────────────────────────────────────────────

    @Test
    void topExercises_sortsByTotalSetsDescending() {
        GymExerciseEntry e1 = new GymExerciseEntry(); e1.setName("A");
        e1.addSet(new GymSetEntry(10, 10, "default"));
        e1.addSet(new GymSetEntry(10, 10, "default"));
        e1.addSet(new GymSetEntry(10, 10, "default")); // 3 sets

        GymExerciseEntry e2 = new GymExerciseEntry(); e2.setName("B");
        e2.addSet(new GymSetEntry(10, 10, "default")); // 1 set

        GymSession s = new GymSession("2026-05-16");
        s.addExercise(e1); s.addExercise(e2);

        when(repo.findAllByOrderByDateDesc()).thenReturn(List.of(s));

        Map<String, Long> top = service.getTopExercises(5);
        List<String> keys = List.copyOf(top.keySet());
        assertEquals("A", keys.get(0));
        assertEquals("B", keys.get(1));
        assertEquals(3L, top.get("A"));
    }

    // ── GymSession model ──────────────────────────────────────────────────────

    @Test
    void gymSession_totalSetsAndRepsAggregate() {
        GymExerciseEntry ex = new GymExerciseEntry();
        ex.addSet(new GymSetEntry(100.0, 10, "default"));
        ex.addSet(new GymSetEntry(100.0, 8,  "default"));

        GymSession s = new GymSession("2026-05-16");
        s.addExercise(ex);

        assertEquals(2, s.getTotalSets());
        assertEquals(18, s.getTotalReps());
    }

    @Test
    void gymExerciseEntry_maxWeightTracked() {
        GymExerciseEntry ex = new GymExerciseEntry();
        ex.addSet(new GymSetEntry(40.0, 12, "default"));
        ex.addSet(new GymSetEntry(50.0, 10, "default"));
        ex.addSet(new GymSetEntry(45.0, 8,  "default"));

        assertEquals(50.0, ex.getMaxWeightKg());
        assertEquals(3, ex.getTotalSets());
        assertEquals(30, ex.getTotalReps());
    }
}
