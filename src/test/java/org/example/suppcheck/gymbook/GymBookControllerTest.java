package org.example.suppcheck.gymbook;

import org.example.suppcheck.gymbook.controller.GymBookController;
import org.example.suppcheck.gymbook.model.GymSession;
import org.example.suppcheck.gymbook.service.GymBookDashboardService;
import org.example.suppcheck.gymbook.service.GymBookImportService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GymBookControllerTest {

    private GymBookDashboardService dashboardService;
    private GymBookImportService    importService;
    private GymBookController       controller;

    @BeforeEach
    void setUp() {
        dashboardService = mock(GymBookDashboardService.class);
        importService    = mock(GymBookImportService.class);
        controller       = new GymBookController(dashboardService, importService);

        lenient().when(dashboardService.getRecentSessions()).thenReturn(List.of());
        lenient().when(dashboardService.getTotalSessionCount()).thenReturn(0L);
        lenient().when(dashboardService.getTopExercises(anyInt())).thenReturn(Map.of());
        lenient().when(dashboardService.getAllExerciseNames()).thenReturn(List.of());
        lenient().when(dashboardService.getMuscleHeatmap(anyInt())).thenReturn(Map.of());
        lenient().when(dashboardService.getWeightProgression(anyString())).thenReturn(List.of());
        lenient().when(importService.getStatus()).thenReturn("idle");
        lenient().when(importService.getImportedSessions()).thenReturn(0L);
        lenient().when(importService.getImportedSets()).thenReturn(0L);
        lenient().when(importService.getImportError()).thenReturn(null);
        lenient().when(importService.isRunning()).thenReturn(false);
    }

    // ── dashboard ─────────────────────────────────────────────────────────────

    @Test
    void dashboard_returnsCorrectView() {
        assertEquals("gymbook/gymbook-dashboard", controller.dashboard(new ConcurrentModel()));
    }

    @Test
    void dashboard_addsRequiredAttributes() {
        Model model = new ConcurrentModel();
        controller.dashboard(model);
        assertTrue(model.containsAttribute("recentSessions"));
        assertTrue(model.containsAttribute("totalSessions"));
        assertTrue(model.containsAttribute("topExLabels"));
        assertTrue(model.containsAttribute("topExValues"));
    }

    @Test
    void dashboard_topExercisesOrderedCorrectly() {
        Map<String, Long> top = Map.of("Kniebeugen", 30L, "Bankdrücken", 25L);
        when(dashboardService.getTopExercises(8)).thenReturn(top);

        Model model = new ConcurrentModel();
        controller.dashboard(model);

        @SuppressWarnings("unchecked")
        List<String> labels = (List<String>) model.getAttribute("topExLabels");
        assertNotNull(labels);
        assertFalse(labels.isEmpty());
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
}
