package org.example.suppcheck.gymbook;

import org.example.suppcheck.gymbook.controller.GymBookController;
import org.example.suppcheck.gymbook.model.GymExerciseEntry;
import org.example.suppcheck.gymbook.model.GymSession;
import org.example.suppcheck.gymbook.model.GymSetEntry;
import org.example.suppcheck.gymbook.service.GymBookDashboardService;
import org.example.suppcheck.gymbook.service.GymBookImportService;
import org.example.suppcheck.health.service.HealthDashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GymBookControllerTest {

    private GymBookDashboardService dashboardService;
    private GymBookImportService    importService;
    private HealthDashboardService  healthDashboardService;
    private GymBookController       controller;

    @BeforeEach
    void setUp() {
        dashboardService      = mock(GymBookDashboardService.class);
        importService         = mock(GymBookImportService.class);
        healthDashboardService = mock(HealthDashboardService.class);
        controller            = new GymBookController(dashboardService, importService, healthDashboardService);

        lenient().when(dashboardService.getRecentSessions()).thenReturn(List.of());
        lenient().when(dashboardService.getTotalSessionCount()).thenReturn(0L);
        lenient().when(dashboardService.getTopExercises(anyInt())).thenReturn(Map.of());
        lenient().when(dashboardService.getAllExerciseNames()).thenReturn(List.of());
        lenient().when(dashboardService.getMuscleHeatmap(anyInt())).thenReturn(Map.of());
        lenient().when(dashboardService.getMuscleExercises(anyInt())).thenReturn(Map.of());
        lenient().when(dashboardService.getWeightProgression(anyString())).thenReturn(List.of());
        lenient().when(dashboardService.getSessionByDate(anyString())).thenReturn(Optional.empty());
        lenient().when(healthDashboardService.getStrengthDurationByDate()).thenReturn(Map.of());
        lenient().when(importService.getStatus()).thenReturn("idle");
        lenient().when(importService.getImportedSessions()).thenReturn(0L);
        lenient().when(importService.getImportedSets()).thenReturn(0L);
        lenient().when(importService.getImportError()).thenReturn(null);
        lenient().when(importService.isRunning()).thenReturn(false);
    }

    // ── dashboard ─────────────────────────────────────────────────────────────

    @Test
    void dashboard_redirectsToHealth() {
        assertEquals("redirect:/health", controller.dashboard());
    }

    // ── bodyMap ───────────────────────────────────────────────────────────────

    @Test
    void bodyMap_returnsCorrectView() {
        assertEquals("gymbook/gymbook-body-map", controller.bodyMap(90, new ConcurrentModel()));
    }

    @Test
    void bodyMap_addsHeatmapAndDays() {
        when(dashboardService.getMuscleHeatmap(30)).thenReturn(Map.of("020.pectorals", 5));
        Model model = new ConcurrentModel();
        controller.bodyMap(30, model);
        assertTrue(model.containsAttribute("heatmap"));
        assertEquals(30, model.getAttribute("days"));
    }

    @Test
    void bodyMap_addsMuscleExercises() {
        when(dashboardService.getMuscleExercises(90)).thenReturn(Map.of("020.pectorals", List.of("Bankdrücken")));
        Model model = new ConcurrentModel();
        controller.bodyMap(90, model);
        assertTrue(model.containsAttribute("muscleExercises"));
    }

    // ── exerciseProgress ─────────────────────────────────────────────────────

    @Test
    void exerciseProgress_returnsCorrectView() {
        assertEquals("gymbook/gymbook-exercise", controller.exerciseProgress("Bankdrücken", new ConcurrentModel()));
    }

    @Test
    void exerciseProgress_addsRequiredAttributes() {
        Model model = new ConcurrentModel();
        controller.exerciseProgress("Bankdrücken", model);
        assertTrue(model.containsAttribute("exerciseName"));
        assertTrue(model.containsAttribute("progressData"));
        assertTrue(model.containsAttribute("dateLabels"));
        assertTrue(model.containsAttribute("weightValues"));
        assertEquals("Bankdrücken", model.getAttribute("exerciseName"));
    }

    // ── importPage ────────────────────────────────────────────────────────────

    @Test
    void importPage_returnsCorrectView() {
        assertEquals("gymbook/gymbook-import", controller.importPage(new ConcurrentModel()));
    }

    @Test
    void importPage_addsStatusAttributes() {
        when(importService.getStatus()).thenReturn("done");
        when(importService.getImportedSessions()).thenReturn(25L);
        when(importService.getImportedSets()).thenReturn(300L);

        Model model = new ConcurrentModel();
        controller.importPage(model);

        assertEquals("done", model.getAttribute("status"));
        assertEquals(25L, model.getAttribute("importedSessions"));
        assertEquals(300L, model.getAttribute("importedSets"));
    }

    // ── uploadAndImport ───────────────────────────────────────────────────────

    @Test
    void upload_conflictWhenAlreadyRunning() {
        when(importService.isRunning()).thenReturn(true);
        MockMultipartFile file = new MockMultipartFile("file", "backup.database", "application/octet-stream", "data".getBytes());

        ResponseEntity<Map<String, Object>> response = controller.uploadAndImport(file);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("error"));
        verify(importService, never()).startImportAsync(any(File.class), anyBoolean());
    }

    @Test
    void upload_badRequestWhenEmpty() {
        MockMultipartFile file = new MockMultipartFile("file", "backup.database", "application/octet-stream", new byte[0]);

        ResponseEntity<Map<String, Object>> response = controller.uploadAndImport(file);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("error"));
    }

    @Test
    void upload_successStartsImport() {
        MockMultipartFile file = new MockMultipartFile("file", "backup.database", "application/octet-stream", "sqlite3data".getBytes());

        ResponseEntity<Map<String, Object>> response = controller.uploadAndImport(file);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("started", response.getBody().get("status"));
        verify(importService).startImportAsync(any(File.class), eq(true));
    }

    // ── importStatus ──────────────────────────────────────────────────────────

    @Test
    void importStatus_returnsAllKeys() {
        when(importService.getStatus()).thenReturn("running");
        when(importService.getImportedSessions()).thenReturn(5L);
        when(importService.getImportedSets()).thenReturn(100L);

        Map<String, Object> result = controller.importStatus();

        assertTrue(result.containsKey("status"));
        assertTrue(result.containsKey("importedSessions"));
        assertTrue(result.containsKey("importedSets"));
        assertTrue(result.containsKey("error"));
        assertEquals("running", result.get("status"));
    }

    @Test
    void importStatus_nullErrorBecomesEmptyString() {
        when(importService.getImportError()).thenReturn(null);
        assertEquals("", controller.importStatus().get("error"));
    }

    // ── sessionDetail ─────────────────────────────────────────────────────────

    @Test
    void sessionDetail_returnsErrorWhenNotFound() {
        when(dashboardService.getSessionByDate("2026-01-01")).thenReturn(Optional.empty());
        Map<String, Object> result = controller.sessionDetail("2026-01-01");
        assertTrue(result.containsKey("error"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sessionDetail_skippedFlagTrueForZeroSets() {
        GymSession session = new GymSession("2026-05-01");
        GymExerciseEntry skipped = new GymExerciseEntry();
        skipped.setName("Bankdrücken");
        skipped.setPrimaryMuscles("020.pectorals");
        // no sets added → totalSets == 0

        session.addExercise(skipped);
        when(dashboardService.getSessionByDate("2026-05-01")).thenReturn(Optional.of(session));

        Map<String, Object> result = controller.sessionDetail("2026-05-01");

        List<Map<String, Object>> exercises = (List<Map<String, Object>>) result.get("exercises");
        assertNotNull(exercises);
        assertEquals(1, exercises.size());
        assertEquals(true, exercises.get(0).get("skipped"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sessionDetail_skippedFlagFalseWhenSetsPresent() {
        GymSession session = new GymSession("2026-05-02");
        GymExerciseEntry ex = new GymExerciseEntry();
        ex.setName("Kniebeugen");
        ex.setPrimaryMuscles("070.quadriceps");
        ex.addSet(new GymSetEntry(100.0, 5, "default"));

        session.addExercise(ex);
        when(dashboardService.getSessionByDate("2026-05-02")).thenReturn(Optional.of(session));

        Map<String, Object> result = controller.sessionDetail("2026-05-02");

        List<Map<String, Object>> exercises = (List<Map<String, Object>>) result.get("exercises");
        assertEquals(false, exercises.get(0).get("skipped"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sessionDetail_skippedExerciseExcludedFromMuscleExercises() {
        GymSession session = new GymSession("2026-05-03");
        GymExerciseEntry skipped = new GymExerciseEntry();
        skipped.setName("Bankdrücken");
        skipped.setPrimaryMuscles("020.pectorals");
        // no sets → totalSets == 0

        session.addExercise(skipped);
        when(dashboardService.getSessionByDate("2026-05-03")).thenReturn(Optional.of(session));

        Map<String, Object> result = controller.sessionDetail("2026-05-03");

        Map<String, List<String>> muscleExercises = (Map<String, List<String>>) result.get("muscleExercises");
        assertNotNull(muscleExercises);
        assertFalse(muscleExercises.containsKey("020.pectorals"),
                "Skipped exercise must not appear in muscleExercises");
    }

    @Test
    @SuppressWarnings("unchecked")
    void sessionDetail_avgKgComputedFromSets() {
        GymSession session = new GymSession("2026-05-04");
        GymExerciseEntry ex = new GymExerciseEntry();
        ex.setName("Bankdrücken");
        ex.setPrimaryMuscles("020.pectorals");
        ex.addSet(new GymSetEntry(80.0, 8, "default"));
        ex.addSet(new GymSetEntry(90.0, 6, "default"));
        // avg = (80 + 90) / 2 = 85.0

        session.addExercise(ex);
        when(dashboardService.getSessionByDate("2026-05-04")).thenReturn(Optional.of(session));

        Map<String, Object> result = controller.sessionDetail("2026-05-04");

        List<Map<String, Object>> exercises = (List<Map<String, Object>>) result.get("exercises");
        assertEquals(85.0, exercises.get(0).get("avgKg"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sessionDetail_avgKgIsZeroWhenNoWeightSets() {
        GymSession session = new GymSession("2026-05-05");
        GymExerciseEntry ex = new GymExerciseEntry();
        ex.setName("Klimmzüge");
        ex.setPrimaryMuscles("031.dorsalMuscles");
        ex.addSet(new GymSetEntry(0.0, 10, "default")); // bodyweight

        session.addExercise(ex);
        when(dashboardService.getSessionByDate("2026-05-05")).thenReturn(Optional.of(session));

        Map<String, Object> result = controller.sessionDetail("2026-05-05");

        List<Map<String, Object>> exercises = (List<Map<String, Object>>) result.get("exercises");
        assertEquals(0.0, exercises.get(0).get("avgKg"));
    }
}
