package org.example.suppcheck.health.controller;

import org.example.suppcheck.gymbook.model.GymSession;
import org.example.suppcheck.gymbook.service.GymBookDashboardService;
import org.example.suppcheck.health.model.HealthDailyMetric;
import org.example.suppcheck.health.model.HealthMetric;
import org.example.suppcheck.health.model.HealthWorkout;
import org.example.suppcheck.health.model.WorkoutRow;
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
import java.util.*;
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

    private final HealthDashboardService  dashboardService;
    private final HealthImportService     importService;
    private final GymBookDashboardService gymBookDashboardService;

    public HealthController(HealthDashboardService dashboardService,
                            HealthImportService importService,
                            GymBookDashboardService gymBookDashboardService) {
        this.dashboardService        = dashboardService;
        this.importService           = importService;
        this.gymBookDashboardService = gymBookDashboardService;
    }

    // ── Dashboard ──────────────────────────────────────────────────────────────

    @GetMapping
    public String dashboard(Model model) {
        Map<String, HealthMetric> latest = dashboardService.getLatestBodyMetrics();
        List<HealthDailyMetric>   recent = dashboardService.getAllDailyMetrics();
        var topEx = gymBookDashboardService.getTopExercises(8, 7);

        model.addAttribute("latestMetrics",   latest);
        model.addAttribute("recentDays",      recent);
        model.addAttribute("workoutRows",     buildWorkoutRows());
        model.addAttribute("gymHeatmap",      gymBookDashboardService.getMuscleHeatmap(7));
        model.addAttribute("totalWorkouts",   dashboardService.getTotalWorkoutCount());
        model.addAttribute("avgSleep30",      dashboardService.getAvgSleepHours(30));
        model.addAttribute("avgSteps30",      dashboardService.getAvgSteps(30));
        model.addAttribute("topExLabels",     new ArrayList<>(topEx.keySet()));
        model.addAttribute("topExValues",     new ArrayList<>(topEx.values()));

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
        model.addAttribute("typeLabels",  new ArrayList<>(counts.keySet()));
        model.addAttribute("typeValues",  new ArrayList<>(counts.values()));

        return "health/health-workouts";
    }

    // ── Kardio ─────────────────────────────────────────────────────────────────

    @GetMapping("/cardio")
    public String cardio(Model model) {
        addMetricSeries(model, "vo2max",     "VO2Max");
        addMetricSeries(model, "restingHr",  "RestingHeartRate");

        // Tägliche Aggregate für Schlaf und HRV (alle verfügbaren Daten)
        List<HealthDailyMetric> recent = dashboardService.getAllDailyMetrics();
        model.addAttribute("dailyLabels",  toDateLabels(recent));
        model.addAttribute("sleepValues",  recent.stream().map(HealthDailyMetric::getSleepHours).collect(Collectors.toList()));
        model.addAttribute("hrvValues",    recent.stream().map(HealthDailyMetric::getAvgHrv).collect(Collectors.toList()));
        model.addAttribute("avgHrValues",  recent.stream().map(HealthDailyMetric::getAvgHeartRate).collect(Collectors.toList()));

        return "health/health-cardio";
    }

    // ── Ernährung ──────────────────────────────────────────────────────────────

    @GetMapping("/nutrition")
    public String nutrition(Model model) {
        List<HealthDailyMetric> recent = dashboardService.getAllDailyMetrics();
        model.addAttribute("recentDays",    recent);
        model.addAttribute("dailyLabels",   toDateLabels(recent));
        model.addAttribute("kcalValues",    recent.stream().map(HealthDailyMetric::getDietaryKcal).collect(Collectors.toList()));
        model.addAttribute("proteinValues", recent.stream().map(HealthDailyMetric::getDietaryProteinG).collect(Collectors.toList()));
        model.addAttribute("carbsValues",   recent.stream().map(HealthDailyMetric::getDietaryCarbsG).collect(Collectors.toList()));
        model.addAttribute("fatValues",     recent.stream().map(HealthDailyMetric::getDietaryFatG).collect(Collectors.toList()));
        return "health/health-nutrition";
    }

    // ── Chart-Daten AJAX (Zeitraum-gefiltert) ─────────────────────────────────

    /**
     * Liefert Top-Übungen und Muskel-Heatmap für einen wählbaren Zeitraum als JSON.
     * Wird vom Dashboard per fetch() aufgerufen wenn der User die Zeitspanne wechselt.
     *
     * @param days Anzahl Tage zurück; verwende 9999 für All-Time
     */
    @GetMapping(value = "/api/chart-data", produces = "application/json")
    @ResponseBody
    public Map<String, Object> chartData(@RequestParam(defaultValue = "9999") int days) {
        var topEx   = gymBookDashboardService.getTopExercises(8, days);
        var heatmap = gymBookDashboardService.getMuscleHeatmap(days);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topExLabels", new ArrayList<>(topEx.keySet()));
        result.put("topExValues", new ArrayList<>(topEx.values()));
        result.put("heatmap",     heatmap);
        return result;
    }

    // ── Gemischte Workout-Zeilen (GymBook + Health) ────────────────────────────

    /**
     * Erstellt eine nach Datum absteigende Liste der letzten 25 Workout-Einheiten,
     * gemischt aus GymBook-Sessions und Apple-Health-Workouts.
     *
     * <p>Fallback-Logik:
     * <ul>
     *   <li>GymBook-Session hat Vorrang für Krafttraining – Apple-Health-Strength-Workouts
     *       werden für Tage mit GymBook-Session unterdrückt.</li>
     *   <li>Nicht-Kraft-Workouts aus Apple Health werden immer einbezogen.</li>
     * </ul>
     * </p>
     */
    private List<WorkoutRow> buildWorkoutRows() {
        // 1. GymBook-Sessions (bereits top 30, absteigend)
        List<GymSession> gymSessions = gymBookDashboardService.getRecentSessions();
        Set<String> gymDates = gymSessions.stream()
                .map(GymSession::getDate)
                .collect(Collectors.toSet());

        List<WorkoutRow> rows = new ArrayList<>();

        // 2. GymBook-Zeilen hinzufügen
        for (GymSession s : gymSessions) {
            String tag = switch (s.getTag() != null ? s.getTag() : "Sonstige") {
                case "Push"  -> "Krafttraining (Push)";
                case "Pull"  -> "Krafttraining (Pull)";
                case "Beine" -> "Krafttraining (Beine)";
                default      -> "Krafttraining";
            };
            String exNames = s.getExercises().stream()
                    .map(e -> e.getName())
                    .filter(n -> n != null && !n.isBlank())
                    .distinct()
                    .collect(Collectors.joining(", "));
            rows.add(new WorkoutRow(s.getDate(), tag, 0, 0, 0, exNames, true));
        }

        // 3. Health-Workouts hinzufügen (Strength wird unterdrückt wenn GymBook vorhanden)
        List<HealthWorkout> healthWorkouts = dashboardService.getAllWorkouts();

        // Dauer-Lookup für GymBook-Zeilen (Krafttraining selben Tags)
        Map<String, Double> healthDurByDate = new HashMap<>();
        for (HealthWorkout w : healthWorkouts) {
            boolean isS = "TraditionalStrengthTraining".equals(w.getActivityType())
                       || "FunctionalStrengthTraining".equals(w.getActivityType());
            if (isS && w.getDurationMinutes() > 0) {
                healthDurByDate.put(w.getDate().toString(), w.getDurationMinutes());
            }
        }

        // GymBook-Zeilen mit Dauer aus Apple Health anreichern
        rows.replaceAll(r -> r.isStrength()
                ? new WorkoutRow(r.getDate(), r.getTag(),
                        healthDurByDate.getOrDefault(r.getDate(), 0.0),
                        r.getKcal(), r.getDistKm(), r.getExercises(), true)
                : r);

        for (HealthWorkout w : healthWorkouts) {
            boolean isStrength = "TraditionalStrengthTraining".equals(w.getActivityType())
                              || "FunctionalStrengthTraining".equals(w.getActivityType());
            if (isStrength && gymDates.contains(w.getDate().toString())) continue;

            // Fallback-Klassifikation für Einträge ohne workoutTag (vor Migrations-Import)
            String tag;
            if (w.getWorkoutTag() != null && !w.getWorkoutTag().isBlank()) {
                tag = w.getWorkoutTag();
            } else {
                tag = switch (w.getActivityType() != null ? w.getActivityType() : "") {
                    case "Running"                                        -> "Joggen";
                    case "Walking", "Hiking"                              -> "Gehen";
                    case "TraditionalStrengthTraining",
                         "FunctionalStrengthTraining"                     -> "Krafttraining";
                    default                                               -> w.getActivityLabel();
                };
            }
            double distKm = isStrength ? 0 : w.getDistanceKm();
            rows.add(new WorkoutRow(
                    w.getDate().toString(), tag,
                    w.getDurationMinutes(), w.getCaloriesBurned(),
                    distKm, null, false));
        }

        // 4. Nach Datum absteigend sortieren, auf 500 begrenzen
        return rows.stream()
                .sorted(Comparator.comparing(WorkoutRow::getDate).reversed())
                .limit(500)
                .collect(Collectors.toList());
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
