package org.example.suppcheck.gymbook.controller;

import org.example.suppcheck.gymbook.model.GymExerciseEntry;
import org.example.suppcheck.gymbook.model.GymSession;
import org.example.suppcheck.gymbook.model.GymSetEntry;
import org.example.suppcheck.gymbook.service.GymBookDashboardService;
import org.example.suppcheck.gymbook.service.GymBookImportService;
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
 * HTTP-Endpunkte für das GymBook-Trainingslogbuch.
 *
 * <pre>
 * GET  /gymbook                    → Dashboard (letzte Sessions)
 * GET  /gymbook/body-map           → Muskel-Heatmap
 * GET  /gymbook/exercise/{name}    → Gewichtsverlauf einer Übung
 * GET  /gymbook/import             → Import-Seite
 * POST /gymbook/import/upload      → AJAX-Upload des .database-Backups
 * GET  /gymbook/import/status      → JSON-Status des Imports
 * </pre>
 */
@Controller
@RequestMapping("/gymbook")
public class GymBookController {

    private final GymBookDashboardService dashboardService;
    private final GymBookImportService    importService;

    public GymBookController(GymBookDashboardService dashboardService,
                             GymBookImportService importService) {
        this.dashboardService = dashboardService;
        this.importService    = importService;
    }

    // ── Dashboard ──────────────────────────────────────────────────────────────

    @GetMapping
    public String dashboard(Model model) {
        var sessions = dashboardService.getRecentSessions();
        var topEx    = dashboardService.getTopExercises(8);

        model.addAttribute("recentSessions",   sessions);
        model.addAttribute("totalSessions",    dashboardService.getTotalSessionCount());
        model.addAttribute("topExLabels",      List.copyOf(topEx.keySet()));
        model.addAttribute("topExValues",      List.copyOf(topEx.values()));

        return "gymbook/gymbook-dashboard";
    }

    // ── Muskel-Heatmap ─────────────────────────────────────────────────────────

    @GetMapping("/body-map")
    public String bodyMap(@RequestParam(defaultValue = "90") int days, Model model) {
        Map<String, Integer> heatmap = dashboardService.getMuscleHeatmap(days);
        model.addAttribute("heatmap",          heatmap);
        model.addAttribute("days",             days);
        model.addAttribute("muscleExercises",  dashboardService.getMuscleExercises(days));
        return "gymbook/gymbook-body-map";
    }

    // ── Session-Detail (AJAX JSON) ─────────────────────────────────────────────

    /**
     * Gibt die Muscle-Heatmap und Exercise-Liste für eine einzelne Session als JSON zurück.
     * Wird vom Dashboard per fetch() aufgerufen wenn ein Training angeklickt wird.
     */
    @GetMapping(value = "/session/{date}", produces = "application/json")
    @ResponseBody
    public Map<String, Object> sessionDetail(@PathVariable String date) {
        Optional<GymSession> opt = dashboardService.getSessionByDate(date);
        if (opt.isEmpty()) return Map.of("error", "Session not found");

        GymSession session = opt.get();

        // Muskel → Liste von Übungen (dedupliziert)
        Map<String, Set<String>> muscleExercises = new LinkedHashMap<>();
        // Muskel → Gesamtanzahl Sätze in dieser Session
        Map<String, Integer> muscleSets = new LinkedHashMap<>();

        for (GymExerciseEntry ex : session.getExercises()) {
            List<String> muscles = new ArrayList<>();
            if (ex.getPrimaryMuscles() != null)
                Arrays.stream(ex.getPrimaryMuscles().split("\\|")).map(String::trim)
                      .filter(s -> !s.isBlank()).forEach(muscles::add);

            for (String m : muscles) {
                muscleExercises.computeIfAbsent(m, k -> new LinkedHashSet<>()).add(ex.getName());
                muscleSets.merge(m, ex.getTotalSets(), Integer::sum);
            }
        }

        // Exercises als serialisierbare Liste
        List<Map<String, Object>> exercises = session.getExercises().stream()
                .map(ex -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name",      ex.getName());
                    m.put("primary",   ex.getPrimaryMuscles() != null ? ex.getPrimaryMuscles() : "");
                    m.put("secondary", ex.getSecondaryMuscles() != null ? ex.getSecondaryMuscles() : "");
                    m.put("region",    ex.getRegion() != null ? ex.getRegion() : "");
                    m.put("sets",      ex.getTotalSets());
                    m.put("reps",      ex.getTotalReps());
                    m.put("maxKg",     ex.getMaxWeightKg());
                    m.put("setDetails", ex.getSets().stream()
                            .map(s -> ex.getMaxWeightKg() > 0
                                    ? s.getWeightKg() + " kg × " + s.getReps()
                                    : s.getReps() + " Wdh.")
                            .collect(Collectors.toList()));
                    return m;
                }).collect(Collectors.toList());

        // muscleExercises als einfache Map<String,List> für JSON
        Map<String, List<String>> muscleExMap = new LinkedHashMap<>();
        muscleExercises.forEach((k, v) -> muscleExMap.put(k, new ArrayList<>(v)));

        return Map.of(
                "date",           date,
                "totalSets",      session.getTotalSets(),
                "totalReps",      session.getTotalReps(),
                "muscleSets",     muscleSets,
                "muscleExercises",muscleExMap,
                "exercises",      exercises
        );
    }

    // ── Gewichtsverlauf ────────────────────────────────────────────────────────

    @GetMapping("/exercise/{name}")
    public String exerciseProgress(@PathVariable String name, Model model) {
        var progress = dashboardService.getWeightProgression(name);

        model.addAttribute("exerciseName",  name);
        model.addAttribute("allExercises",  dashboardService.getAllExerciseNames());
        model.addAttribute("progressData",  progress);
        model.addAttribute("dateLabels",    progress.stream().map(GymBookDashboardService.ExerciseProgress::date).collect(Collectors.toList()));
        model.addAttribute("weightValues",  progress.stream().map(GymBookDashboardService.ExerciseProgress::maxWeightKg).collect(Collectors.toList()));

        return "gymbook/gymbook-exercise";
    }

    @GetMapping("/exercise")
    public String exerciseSelect(Model model) {
        model.addAttribute("allExercises", dashboardService.getAllExerciseNames());
        return "gymbook/gymbook-exercise";
    }

    // ── Import ─────────────────────────────────────────────────────────────────

    @GetMapping("/import")
    public String importPage(Model model) {
        model.addAttribute("status",           importService.getStatus());
        model.addAttribute("importedSessions", importService.getImportedSessions());
        model.addAttribute("importedSets",     importService.getImportedSets());
        model.addAttribute("importError",      importService.getImportError());
        return "gymbook/gymbook-import";
    }

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
            File tempFile = File.createTempFile("gymbook-", ".database");
            file.transferTo(tempFile);
            importService.startImportAsync(tempFile, true);
            return ResponseEntity.ok(Map.of("status", "started"));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Upload fehlgeschlagen: " + e.getMessage()));
        }
    }

    @GetMapping(value = "/import/status", produces = "application/json")
    @ResponseBody
    public Map<String, Object> importStatus() {
        return Map.of(
                "status",           importService.getStatus(),
                "importedSessions", importService.getImportedSessions(),
                "importedSets",     importService.getImportedSets(),
                "error",            importService.getImportError() != null ? importService.getImportError() : ""
        );
    }
}
