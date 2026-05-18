package org.example.suppcheck.health.controller;

import org.example.suppcheck.health.model.HealthDailyMetric;
import org.example.suppcheck.health.model.HealthMetric;
import org.example.suppcheck.health.model.HealthWorkout;
import org.example.suppcheck.gymbook.service.GymBookDashboardService;
import org.example.suppcheck.health.service.HealthDashboardService;
import org.example.suppcheck.health.service.HealthImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HealthControllerTest {

    private HealthDashboardService  dashboardService;
    private HealthImportService     importService;
    private GymBookDashboardService gymBookDashboardService;
    private HealthController        controller;

    @BeforeEach
    void setUp() {
        dashboardService        = mock(HealthDashboardService.class);
        importService           = mock(HealthImportService.class);
        gymBookDashboardService = mock(GymBookDashboardService.class);
        controller              = new HealthController(dashboardService, importService, gymBookDashboardService);

        // Sensible defaults
        lenient().when(dashboardService.getLatestBodyMetrics()).thenReturn(Map.of());
        lenient().when(dashboardService.getRecentDailyMetrics(anyInt())).thenReturn(List.of());
        lenient().when(dashboardService.getAllDailyMetrics()).thenReturn(List.of());
        lenient().when(dashboardService.getRecentWorkouts()).thenReturn(List.of());
        lenient().when(dashboardService.getTotalWorkoutCount()).thenReturn(0L);
        lenient().when(dashboardService.getAvgSleepHours(anyInt())).thenReturn(7.5);
        lenient().when(dashboardService.getAvgSteps(anyInt())).thenReturn(8000.0);
        lenient().when(dashboardService.getAllMetricSeries(anyString())).thenReturn(List.of());
        lenient().when(dashboardService.getAllWorkouts()).thenReturn(List.of());
        lenient().when(dashboardService.getWorkoutCountByType()).thenReturn(Map.of());
        lenient().when(gymBookDashboardService.getRecentSessions()).thenReturn(List.of());
        lenient().when(gymBookDashboardService.getMuscleHeatmap(anyInt())).thenReturn(Map.of());
        lenient().when(gymBookDashboardService.getTopExercises(anyInt())).thenReturn(Map.of());
        lenient().when(gymBookDashboardService.getTopExercises(anyInt(), anyInt())).thenReturn(Map.of());
        lenient().when(importService.getStatus()).thenReturn("idle");
        lenient().when(importService.getProcessedRecords()).thenReturn(0L);
        lenient().when(importService.getImportedMetrics()).thenReturn(0L);
        lenient().when(importService.getImportedWorkouts()).thenReturn(0L);
        lenient().when(importService.getImportedDays()).thenReturn(0L);
        lenient().when(importService.getImportError()).thenReturn(null);
    }

    // ── dashboard ────────────────────────────────────────────────────────────

    @Test
    void dashboard_returnsCorrectView() {
        Model model = new ConcurrentModel();
        assertEquals("health/health-dashboard", controller.dashboard(model));
    }

    @Test
    void dashboard_addsRequiredAttributes() {
        Model model = new ConcurrentModel();
        controller.dashboard(model);

        assertTrue(model.containsAttribute("latestMetrics"));
        assertTrue(model.containsAttribute("recentDays"));
        assertTrue(model.containsAttribute("workoutRows"));
        assertTrue(model.containsAttribute("gymHeatmap"));
        assertTrue(model.containsAttribute("totalWorkouts"));
        assertTrue(model.containsAttribute("avgSleep30"));
        assertTrue(model.containsAttribute("avgSteps30"));
        assertTrue(model.containsAttribute("stepLabels"));
        assertTrue(model.containsAttribute("stepValues"));
    }

    @Test
    void dashboard_stepLabelsAndValuesComputedFromDailyMetrics() {
        HealthDailyMetric d = new HealthDailyMetric("2025-06-01");
        d.addSteps(10000);
        when(dashboardService.getAllDailyMetrics()).thenReturn(List.of(d));

        Model model = new ConcurrentModel();
        controller.dashboard(model);

        @SuppressWarnings("unchecked")
        List<String> labels = (List<String>) model.getAttribute("stepLabels");
        @SuppressWarnings("unchecked")
        List<Long> values = (List<Long>) model.getAttribute("stepValues");

        assertNotNull(labels);
        assertEquals(1, labels.size());
        assertEquals("2025-06-01", labels.get(0));
        assertEquals(10000L, values.get(0));
    }

    // ── importPage ───────────────────────────────────────────────────────────

    @Test
    void importPage_returnsCorrectView() {
        Model model = new ConcurrentModel();
        assertEquals("health/health-import", controller.importPage(model));
    }

    @Test
    void importPage_addsStatusAttributes() {
        when(importService.getStatus()).thenReturn("done");
        when(importService.getImportedMetrics()).thenReturn(5000L);

        Model model = new ConcurrentModel();
        controller.importPage(model);

        assertEquals("done", model.getAttribute("status"));
        assertEquals(5000L, model.getAttribute("importedMetrics"));
    }

    // ── uploadAndImport ──────────────────────────────────────────────────────

    @Test
    void uploadAndImport_conflictWhenAlreadyRunning() {
        when(importService.isRunning()).thenReturn(true);
        MockMultipartFile file = new MockMultipartFile(
                "file", "Export.xml", "text/xml", "<data/>".getBytes());

        ResponseEntity<Map<String, Object>> response = controller.uploadAndImport(file);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("error"));
        verify(importService, never()).startImportAsync(any(File.class), anyBoolean());
    }

    @Test
    void uploadAndImport_badRequestWhenFileEmpty() {
        when(importService.isRunning()).thenReturn(false);
        MockMultipartFile file = new MockMultipartFile(
                "file", "Export.xml", "text/xml", new byte[0]);

        ResponseEntity<Map<String, Object>> response = controller.uploadAndImport(file);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("error"));
        verify(importService, never()).startImportAsync(any(File.class), anyBoolean());
    }

    @Test
    void uploadAndImport_successStartsImportAsync() {
        when(importService.isRunning()).thenReturn(false);
        MockMultipartFile file = new MockMultipartFile(
                "file", "Export.xml", "text/xml", "<HealthData/>".getBytes());

        ResponseEntity<Map<String, Object>> response = controller.uploadAndImport(file);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("started", response.getBody().get("status"));
        verify(importService).startImportAsync(any(File.class), eq(true));
    }

    // ── startImport ──────────────────────────────────────────────────────────

    @Test
    void startImport_callsImportServiceAndRedirects() {
        String result = controller.startImport("/some/path/Export.xml");

        verify(importService).startImportAsync("/some/path/Export.xml");
        assertEquals("redirect:/health/import", result);
    }

    // ── importStatus JSON ────────────────────────────────────────────────────

    @Test
    void importStatus_returnsMapWithAllKeys() {
        when(importService.getStatus()).thenReturn("running");
        when(importService.getProcessedRecords()).thenReturn(1000L);

        Map<String, Object> result = controller.importStatus();

        assertTrue(result.containsKey("status"));
        assertTrue(result.containsKey("processedRecords"));
        assertTrue(result.containsKey("importedMetrics"));
        assertTrue(result.containsKey("importedWorkouts"));
        assertTrue(result.containsKey("importedDays"));
        assertTrue(result.containsKey("error"));
        assertEquals("running", result.get("status"));
        assertEquals(1000L, result.get("processedRecords"));
    }

    @Test
    void importStatus_errorNullBecomesEmptyString() {
        when(importService.getImportError()).thenReturn(null);
        Map<String, Object> result = controller.importStatus();
        assertEquals("", result.get("error"));
    }

    @Test
    void importStatus_errorMessageIsForwarded() {
        when(importService.getImportError()).thenReturn("File not found");
        Map<String, Object> result = controller.importStatus();
        assertEquals("File not found", result.get("error"));
    }

    // ── bodyComposition ──────────────────────────────────────────────────────

    @Test
    void bodyComposition_returnsCorrectView() {
        Model model = new ConcurrentModel();
        assertEquals("health/health-body-composition", controller.bodyComposition(model));
    }

    @Test
    void bodyComposition_addsChartDataAttributes() {
        HealthMetric m = new HealthMetric("BodyMass", LocalDate.of(2025, 1, 1), 80.0, "kg", "Withings");
        when(dashboardService.getAllMetricSeries("BodyMass")).thenReturn(List.of(m));

        Model model = new ConcurrentModel();
        controller.bodyComposition(model);

        assertTrue(model.containsAttribute("bodyMassLabels"));
        assertTrue(model.containsAttribute("bodyMassValues"));
        assertTrue(model.containsAttribute("bodyFatLabels"));
        assertTrue(model.containsAttribute("bodyFatValues"));
        assertTrue(model.containsAttribute("leanMassLabels"));
        assertTrue(model.containsAttribute("leanMassValues"));
        assertTrue(model.containsAttribute("bmiLabels"));
        assertTrue(model.containsAttribute("bmiValues"));
    }

    // ── bodyMap ──────────────────────────────────────────────────────────────

    @Test
    void bodyMap_returnsCorrectView() {
        Model model = new ConcurrentModel();
        assertEquals("health/health-body-map", controller.bodyMap(model));
    }

    @Test
    void bodyMap_addsLatestMetrics() {
        Model model = new ConcurrentModel();
        controller.bodyMap(model);
        assertTrue(model.containsAttribute("latestMetrics"));
    }

    // ── workouts ─────────────────────────────────────────────────────────────

    @Test
    void workouts_returnsCorrectView() {
        Model model = new ConcurrentModel();
        assertEquals("health/health-workouts", controller.workouts(model));
    }

    @Test
    void workouts_addsWorkoutAttributes() {
        HealthWorkout w = new HealthWorkout();
        w.setActivityType("Running");
        when(dashboardService.getAllWorkouts()).thenReturn(List.of(w));
        when(dashboardService.getWorkoutCountByType()).thenReturn(Map.of("Running", 1L));

        Model model = new ConcurrentModel();
        controller.workouts(model);

        assertTrue(model.containsAttribute("allWorkouts"));
        assertTrue(model.containsAttribute("workoutCounts"));
        assertTrue(model.containsAttribute("totalWorkouts"));
        assertTrue(model.containsAttribute("typeLabels"));
        assertTrue(model.containsAttribute("typeValues"));
    }

    // ── chartData API ─────────────────────────────────────────────────────────

    @Test
    void chartData_returnsTopExLabelsAndHeatmap() {
        when(gymBookDashboardService.getTopExercises(8, 30)).thenReturn(Map.of("Bankdrücken", 10L));
        when(gymBookDashboardService.getMuscleHeatmap(30)).thenReturn(Map.of("020.pectorals", 3));

        Map<String, Object> result = controller.chartData(30);

        assertTrue(result.containsKey("topExLabels"));
        assertTrue(result.containsKey("topExValues"));
        assertTrue(result.containsKey("heatmap"));
    }

    @Test
    void chartData_defaultsToAllTime() {
        Map<String, Object> result = controller.chartData(9999);
        assertTrue(result.containsKey("topExLabels"));
        assertTrue(result.containsKey("heatmap"));
    }

    // ── cardio ───────────────────────────────────────────────────────────────

    @Test
    void cardio_returnsCorrectView() {
        Model model = new ConcurrentModel();
        assertEquals("health/health-cardio", controller.cardio(model));
    }

    @Test
    void cardio_addsChartAttributes() {
        when(dashboardService.getRecentDailyMetrics(90)).thenReturn(List.of());

        Model model = new ConcurrentModel();
        controller.cardio(model);

        assertTrue(model.containsAttribute("vo2maxLabels"));
        assertTrue(model.containsAttribute("vo2maxValues"));
        assertTrue(model.containsAttribute("restingHrLabels"));
        assertTrue(model.containsAttribute("restingHrValues"));
        assertTrue(model.containsAttribute("dailyLabels"));
        assertTrue(model.containsAttribute("sleepValues"));
        assertTrue(model.containsAttribute("hrvValues"));
        assertTrue(model.containsAttribute("avgHrValues"));
    }

    // ── nutrition ────────────────────────────────────────────────────────────

    @Test
    void nutrition_returnsCorrectView() {
        Model model = new ConcurrentModel();
        assertEquals("health/health-nutrition", controller.nutrition(model));
    }

    @Test
    void nutrition_addsNutritionAttributes() {
        Model model = new ConcurrentModel();
        controller.nutrition(model);

        assertTrue(model.containsAttribute("recentDays"));
        assertTrue(model.containsAttribute("dailyLabels"));
        assertTrue(model.containsAttribute("kcalValues"));
        assertTrue(model.containsAttribute("proteinValues"));
        assertTrue(model.containsAttribute("carbsValues"));
        assertTrue(model.containsAttribute("fatValues"));
    }

    @Test
    void nutrition_kcalValuesExtractedFromDailyMetrics() {
        HealthDailyMetric d = new HealthDailyMetric("2025-01-01");
        d.addDietaryKcal(2100.0);
        when(dashboardService.getAllDailyMetrics()).thenReturn(List.of(d));

        Model model = new ConcurrentModel();
        controller.nutrition(model);

        @SuppressWarnings("unchecked")
        List<Double> kcal = (List<Double>) model.getAttribute("kcalValues");
        assertNotNull(kcal);
        assertEquals(1, kcal.size());
        assertEquals(2100.0, kcal.get(0));
    }
}
