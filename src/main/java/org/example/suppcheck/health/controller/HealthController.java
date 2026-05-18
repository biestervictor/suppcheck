package org.example.suppcheck.health.controller;

import org.example.suppcheck.health.model.HealthDailyMetric;
import org.example.suppcheck.health.model.HealthMetric;
import org.example.suppcheck.health.model.HealthWorkout;
import org.example.suppcheck.health.service.HealthDashboardService;
import org.example.suppcheck.health.service.HealthImportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * HTTP-Endpunkte für das Apple-Health-Dashboard.
 *
 * <pre>
 * GET  /health                  → Übersichts-Dashboard
 * GET  /health/import           → Import-Formular
 * POST /health/import           → Import starten (async)
 * GET  /health/import/status    → JSON-Status des laufenden Imports
 * GET  /health/body-composition → Körperzusammensetzung (Graphen)
 * GET  /health/body-map         → Aktuelle Messwerte (Karten-Übersicht)
 * GET  /health/workouts         → Workout-Liste und -Statistiken
 * GET  /health/cardio           → Kardio / Vitalwerte (Graphen)
 * GET  /health/nutrition        → Ernährung (Graphen)
 * </pre>
 */
@Controller
@RequestMapping("/health")
public class HealthController {

    private final HealthDashboardService dashboardService;
    private final HealthImportService    importService;

    public HealthController(HealthDashboardService dashboardService,
                            HealthImportService importService) {
        this.dashboardService = dashboardService;
        this.importService    = importService;
    }

    // ── Dashboard ──────────────────────────────────────────────────────────────

    @GetMapping
    public String dashboard(Model model) {
        Map<String, HealthMetric> latest = dashboardService.getLatestBodyMetrics();
        List<HealthDailyMetric>   recent = dashboardService.getRecentDailyMetrics(30);

        // Zeitreihe Schritte & Energie für Minigraph
        model.addAttribute("latestMetrics",  latest);
        model.addAttribute("recentDays",     recent);
        model.addAttribute("recentWorkouts", dashboardService.getRecentWorkouts());
        model.addAttribute("totalWorkouts",  dashboardService.getTotalWorkoutCount());
        model.addAttribute("avgSleep30",     dashboardService.getAvgSleepHours(30));
        model.addAttribute("avgSteps30",     dashboardService.getAvgSteps(30));

        // Labels & Values für Schritte-Chart
        model.addAttribute("stepLabels", toDateLabels(recent));
        model.addAttribute("stepValues", recent.stream().map(HealthDailyMetric::getSteps).collect(Collectors.toList()));

        return "health/health-dashboard";
    }

    // ── Import ─────────────────────────────────────────────────────────────────

    @GetMapping("/import")
    public String importPage(Model model) {
        model.addAttribute("status",           importService.getStatus());
        model.addAttribute("processedRecords", importService.getProcessedRecords());
        model.addAttribute("importedMetrics",  importService.getImportedMetrics());
        model.addAttribute("importedWorkouts", importService.getImportedWorkouts());
        model.addAttribute("importedDays",     importService.getImportedDays());
        model.addAttribute("importError",      importService.getImportError());
        return "health/health-import";
    }

    /**
     * AJAX-Upload-Endpoint: empfängt die Export.xml als Multipart,
     * speichert sie in einer Temp-Datei und startet den asynchronen Import.
     */
    @PostMapping(value = "/import/upload", consumes = "multipart/form-data", produces = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadAndImport(
            @RequestParam("file") MultipartFile file) {
        if (importService.isRunning()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Import läuft bereits."));
        }
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Keine Datei empfangen."));
        }
        try {
            File tempFile = File.createTempFile("health-export-", ".xml");
            file.transferTo(tempFile);
            importService.startImportAsync(tempFile, true);
            return ResponseEntity.ok(Map.of("status", "started"));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Upload fehlgeschlagen: " + e.getMessage()));
        }
    }

    @PostMapping("/import")
    public String startImport(@RequestParam String filePath) {
        importService.startImportAsync(filePath);
        return "redirect:/health/import";
    }

    @GetMapping(value = "/import/status", produces = "application/json")
    @ResponseBody
    public Map<String, Object> importStatus() {
        return Map.of(
                "status",           importService.getStatus(),
                "processedRecords", importService.getProcessedRecords(),
                "importedMetrics",  importService.getImportedMetrics(),
                "importedWorkouts", importService.getImportedWorkouts(),
                "importedDays",     importService.getImportedDays(),
                "error",            importService.getImportError() != null ? importService.getImportError() : ""
        );
    }

    // ── Körperzusammensetzung ──────────────────────────────────────────────────

    @GetMapping("/body-composition")
    public String bodyComposition(Model model) {
        addMetricSeries(model, "bodyMass",    "BodyMass");
        addMetricSeries(model, "bodyFat",     "BodyFatPercentage");
        addMetricSeries(model, "leanMass",    "LeanBodyMass");
        addMetricSeries(model, "bmi",         "BMI");
        return "health/health-body-composition";
    }

    // ── Body-Map (aktuelle Messwerte als Karten) ───────────────────────────────

    @GetMapping("/body-map")
    public String bodyMap(Model model) {
        model.addAttribute("latestMetrics", dashboardService.getLatestBodyMetrics());
        return "health/health-body-map";
    }

    // ── Workouts ──────────────────────────────────────────────────────────────

    @GetMapping("/workouts")
    public String workouts(Model model) {
        Map<String, Long> counts = dashboardService.getWorkoutCountByType();
        List<HealthWorkout> all  = dashboardService.getAllWorkouts();

        model.addAttribute("workoutCounts",  counts);
        model.addAttribute("allWorkouts",    all);
        model.addAttribute("totalWorkouts",  dashboardService.getTotalWorkoutCount());

        // Chart-Daten: Typ-Labels und Anzahl-Werte
        model.addAttribute("typeLabels",  counts.keySet().stream().collect(Collectors.toList()));
        model.addAttribute("typeValues",  counts.values().stream().collect(Collectors.toList()));

        return "health/health-workouts";
    }

    // ── Kardio ─────────────────────────────────────────────────────────────────

    @GetMapping("/cardio")
    public String cardio(Model model) {
        addMetricSeries(model, "vo2max",     "VO2Max");
        addMetricSeries(model, "restingHr",  "RestingHeartRate");

        // Tägliche Aggregate für Schlaf und HRV (letzte 90 Tage)
        List<HealthDailyMetric> recent = dashboardService.getRecentDailyMetrics(90);
        model.addAttribute("dailyLabels",  toDateLabels(recent));
        model.addAttribute("sleepValues",  recent.stream().map(HealthDailyMetric::getSleepHours).collect(Collectors.toList()));
        model.addAttribute("hrvValues",    recent.stream().map(HealthDailyMetric::getAvgHrv).collect(Collectors.toList()));
        model.addAttribute("avgHrValues",  recent.stream().map(HealthDailyMetric::getAvgHeartRate).collect(Collectors.toList()));

        return "health/health-cardio";
    }

    // ── Ernährung ──────────────────────────────────────────────────────────────

    @GetMapping("/nutrition")
    public String nutrition(Model model) {
        List<HealthDailyMetric> recent = dashboardService.getRecentDailyMetrics(30);
        model.addAttribute("recentDays",    recent);
        model.addAttribute("dailyLabels",   toDateLabels(recent));
        model.addAttribute("kcalValues",    recent.stream().map(HealthDailyMetric::getDietaryKcal).collect(Collectors.toList()));
        model.addAttribute("proteinValues", recent.stream().map(HealthDailyMetric::getDietaryProteinG).collect(Collectors.toList()));
        model.addAttribute("carbsValues",   recent.stream().map(HealthDailyMetric::getDietaryCarbsG).collect(Collectors.toList()));
        model.addAttribute("fatValues",     recent.stream().map(HealthDailyMetric::getDietaryFatG).collect(Collectors.toList()));
        return "health/health-nutrition";
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────────

    private void addMetricSeries(Model model, String prefix, String type) {
        List<HealthMetric> series = dashboardService.getAllMetricSeries(type);
        model.addAttribute(prefix + "Labels", series.stream().map(m -> m.getDate().toString()).collect(Collectors.toList()));
        model.addAttribute(prefix + "Values", series.stream().map(HealthMetric::getValue).collect(Collectors.toList()));
    }

    private List<String> toDateLabels(List<HealthDailyMetric> days) {
        return days.stream().map(HealthDailyMetric::getDate).collect(Collectors.toList());
    }
}
