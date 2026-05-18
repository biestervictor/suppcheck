package org.example.suppcheck.health.service;

import org.example.suppcheck.health.model.HealthDailyMetric;
import org.example.suppcheck.health.model.HealthMetric;
import org.example.suppcheck.health.model.HealthWorkout;
import org.example.suppcheck.health.repository.HealthDailyMetricRepository;
import org.example.suppcheck.health.repository.HealthMetricRepository;
import org.example.suppcheck.health.repository.HealthWorkoutRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Importiert Apple Health Export.xml via SAX-Streaming in MongoDB.
 *
 * <p>Neu gegenüber der ursprünglichen MTG-Version:
 * <ul>
 *   <li>Schlaf: echte Dauer aus {@code endDate − startDate} statt 0,25 h pauschal</li>
 *   <li>Nutrition: DietaryEnergyConsumed, Protein, Carbs, Fat, Sugar, Fiber, Water werden
 *       täglich aufsummiert.</li>
 * </ul>
 * </p>
 */
@Service
public class HealthImportService {

    private static final Logger log = LoggerFactory.getLogger(HealthImportService.class);
    private static final DateTimeFormatter APPLE_DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z");
    private static final int BATCH = 500;

    // ── Körper-Metriken (als Einzeldokumente gespeichert) ─────────────────────
    private static final Set<String> BODY_METRIC_TYPES = Set.of(
            "HKQuantityTypeIdentifierBodyMass",
            "HKQuantityTypeIdentifierLeanBodyMass",
            "HKQuantityTypeIdentifierBodyFatPercentage",
            "HKQuantityTypeIdentifierBodyMassIndex",
            "HKQuantityTypeIdentifierRestingHeartRate",
            "HKQuantityTypeIdentifierWalkingHeartRateAverage",
            "HKQuantityTypeIdentifierVO2Max",
            "HKQuantityTypeIdentifierBloodPressureSystolic",
            "HKQuantityTypeIdentifierBloodPressureDiastolic",
            "HKQuantityTypeIdentifierHeight",
            "HKQuantityTypeIdentifierAppleWalkingSteadiness",
            "HKQuantityTypeIdentifierSixMinuteWalkTestDistance",
            "HKQuantityTypeIdentifierHeartRateRecoveryOneMinute",
            "HKQuantityTypeIdentifierAppleSleepingWristTemperature"
    );

    private static final Map<String, String> TYPE_NAMES = Map.ofEntries(
            Map.entry("HKQuantityTypeIdentifierBodyMass",                     "BodyMass"),
            Map.entry("HKQuantityTypeIdentifierLeanBodyMass",                 "LeanBodyMass"),
            Map.entry("HKQuantityTypeIdentifierBodyFatPercentage",            "BodyFatPercentage"),
            Map.entry("HKQuantityTypeIdentifierBodyMassIndex",                "BMI"),
            Map.entry("HKQuantityTypeIdentifierRestingHeartRate",             "RestingHeartRate"),
            Map.entry("HKQuantityTypeIdentifierWalkingHeartRateAverage",      "WalkingHeartRate"),
            Map.entry("HKQuantityTypeIdentifierVO2Max",                       "VO2Max"),
            Map.entry("HKQuantityTypeIdentifierBloodPressureSystolic",        "SystolicBP"),
            Map.entry("HKQuantityTypeIdentifierBloodPressureDiastolic",       "DiastolicBP"),
            Map.entry("HKQuantityTypeIdentifierHeight",                       "Height"),
            Map.entry("HKQuantityTypeIdentifierAppleWalkingSteadiness",       "WalkingSteadiness"),
            Map.entry("HKQuantityTypeIdentifierSixMinuteWalkTestDistance",    "SixMinuteWalkDistance"),
            Map.entry("HKQuantityTypeIdentifierHeartRateRecoveryOneMinute",   "HRRecovery"),
            Map.entry("HKQuantityTypeIdentifierAppleSleepingWristTemperature","WristTemperature")
    );

    private final HealthMetricRepository    metricRepo;
    private final HealthDailyMetricRepository dailyRepo;
    private final HealthWorkoutRepository   workoutRepo;
    private final MongoTemplate             mongoTemplate;

    // ── Status ────────────────────────────────────────────────────────────────
    private final AtomicLong processedRecords = new AtomicLong(0);
    private final AtomicReference<String> importStatus = new AtomicReference<>("idle");
    private final AtomicReference<String> importError  = new AtomicReference<>(null);
    private final AtomicLong importedMetrics  = new AtomicLong(0);
    private final AtomicLong importedWorkouts = new AtomicLong(0);
    private final AtomicLong importedDays     = new AtomicLong(0);

    public HealthImportService(HealthMetricRepository metricRepo,
                               HealthDailyMetricRepository dailyRepo,
                               HealthWorkoutRepository workoutRepo,
                               MongoTemplate mongoTemplate) {
        this.metricRepo    = metricRepo;
        this.dailyRepo     = dailyRepo;
        this.workoutRepo   = workoutRepo;
        this.mongoTemplate = mongoTemplate;
    }

    // ── Status-API ────────────────────────────────────────────────────────────

    public String  getStatus()           { return importStatus.get(); }
    public long    getProcessedRecords() { return processedRecords.get(); }
    public long    getImportedMetrics()  { return importedMetrics.get(); }
    public long    getImportedWorkouts() { return importedWorkouts.get(); }
    public long    getImportedDays()     { return importedDays.get(); }
    public String  getImportError()      { return importError.get(); }
    public boolean isRunning()           { return "running".equals(importStatus.get()); }

    // ── Import starten ────────────────────────────────────────────────────────

    public void startImportAsync(String filePath) {
        startImportAsync(new File(filePath), false);
    }

    public void startImportAsync(File file, boolean deleteTempFile) {
        if (isRunning()) throw new IllegalStateException("Import is already running");
        importStatus.set("running");
        importError.set(null);
        processedRecords.set(0);
        importedMetrics.set(0);
        importedWorkouts.set(0);
        importedDays.set(0);

        Thread t = new Thread(() -> {
            try {
                runImport(file.getAbsolutePath());
                importStatus.set("done");
            } catch (Exception e) {
                log.error("Health import failed", e);
                importError.set(e.getMessage());
                importStatus.set("error");
            } finally {
                if (deleteTempFile && file.exists()) {
                    boolean deleted = file.delete();
                    if (!deleted) log.warn("Temp-Datei konnte nicht gelöscht werden: {}", file.getAbsolutePath());
                }
            }
        }, "health-import");
        t.setDaemon(true);
        t.start();
    }

    // ── Core-Import-Logik ─────────────────────────────────────────────────────

    private void runImport(String filePath) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) throw new IllegalArgumentException("File not found: " + filePath);
        log.info("Apple Health Import gestartet: {}", filePath);

        mongoTemplate.dropCollection("health_metrics");
        mongoTemplate.dropCollection("health_daily");
        mongoTemplate.dropCollection("health_workouts");
        log.info("Bestehende Health-Collections gelöscht");

        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.newSAXParser().parse(file, new HealthSaxHandler());

        log.info("Import abgeschlossen. Metriken={}, Workouts={}, Tage={}",
                importedMetrics.get(), importedWorkouts.get(), importedDays.get());
    }

    // ── SAX-Handler ───────────────────────────────────────────────────────────

    private class HealthSaxHandler extends DefaultHandler {
        private final List<HealthMetric>  metricBatch  = new ArrayList<>(BATCH);
        private final List<HealthWorkout> workoutBatch = new ArrayList<>(BATCH);
        private final Map<String, HealthDailyMetric> daily = new LinkedHashMap<>();

        /**
         * Flag: befinden wir uns gerade innerhalb eines {@code <Correlation>}-Elements?
         *
         * <p>Apple Health exportiert jede Mahlzeit als {@code <Correlation type="...Food">}
         * mit Kind-{@code <Record>}-Elementen UND dieselben Records nochmal als
         * eigenständige Top-Level-Records (so dokumentiert im DTD-Kommentar der Export.xml).
         * Ohne dieses Flag würden alle Nutrition-Werte doppelt gezählt.</p>
         */
        private boolean insideCorrelation = false;

        @Override
        public void startElement(String uri, String local, String qName, Attributes attrs) {
            processedRecords.incrementAndGet();
            switch (qName) {
                case "Correlation" -> insideCorrelation = true;
                // Kind-Records einer Correlation überspringen – sie erscheinen auch als Top-Level
                case "Record"  -> { if (!insideCorrelation) handleRecord(attrs); }
                case "Workout" -> handleWorkout(attrs);
                default        -> {}
            }
            if (metricBatch.size()  >= BATCH)   flushMetrics();
            if (workoutBatch.size() >= BATCH)    flushWorkouts();
            if (daily.size()        >= 10_000)   flushDaily();
        }

        @Override
        public void endElement(String uri, String local, String qName) {
            if ("Correlation".equals(qName)) insideCorrelation = false;
        }

        @Override
        public void endDocument() {
            flushMetrics();
            flushWorkouts();
            flushDaily();
        }

        // ── Record-Verarbeitung ───────────────────────────────────────────────

        private void handleRecord(Attributes a) {
            String type      = a.getValue("type");
            String valueStr  = a.getValue("value");
            String startStr  = a.getValue("startDate");
            String endStr    = a.getValue("endDate");
            String unit      = a.getValue("unit");
            String source    = a.getValue("sourceName");

            if (type == null || startStr == null) return;

            LocalDate date;
            try { date = parseDate(startStr); } catch (Exception e) { return; }

            // ── Körper-Metriken (Einzeldokumente) ────────────────────────────
            if (BODY_METRIC_TYPES.contains(type)) {
                if (valueStr == null) return;
                double val;
                try { val = Double.parseDouble(valueStr); } catch (NumberFormatException e) { return; }
                String shortType = TYPE_NAMES.getOrDefault(type, type);
                metricBatch.add(new HealthMetric(shortType, date, val, unit != null ? unit : "", source != null ? source : ""));
                return;
            }

            HealthDailyMetric d = daily.computeIfAbsent(date.toString(), HealthDailyMetric::new);

            // ── Schlaf: echte Dauer ───────────────────────────────────────────
            if ("HKCategoryTypeIdentifierSleepAnalysis".equals(type) && endStr != null) {
                try {
                    LocalDateTime start = LocalDateTime.parse(startStr, APPLE_DT_FMT);
                    LocalDateTime end   = LocalDateTime.parse(endStr,   APPLE_DT_FMT);
                    long minutes = ChronoUnit.MINUTES.between(start, end);
                    // Nur sinnvolle Schlaf-Intervalle (1 min – 16 h)
                    if (minutes > 0 && minutes < 960) {
                        d.addSleepMinutes(minutes);
                    }
                } catch (Exception ignored) {}
                return;
            }

            if (valueStr == null) return;
            double val;
            try { val = Double.parseDouble(valueStr); } catch (NumberFormatException e) { return; }

            // ── Tägliche Aggregatmetriken ─────────────────────────────────────
            switch (type) {
                // Schritte: nur Apple-Geräte (Apple Watch, iPhone) — QRing etc. ausschließen
                case "HKQuantityTypeIdentifierStepCount" -> {
                    if (isAppleStepSource(source)) d.addSteps(val);
                }
                case "HKQuantityTypeIdentifierActiveEnergyBurned"       -> d.addActiveEnergy(val);
                case "HKQuantityTypeIdentifierBasalEnergyBurned"        -> d.addBasalEnergy(val);
                case "HKQuantityTypeIdentifierHeartRate"                 -> d.addHeartRate(val);
                case "HKQuantityTypeIdentifierHeartRateVariabilitySDNN" -> d.addHrv(val);
                case "HKQuantityTypeIdentifierRespiratoryRate"           -> d.addRespiratoryRate(val);
                case "HKQuantityTypeIdentifierOxygenSaturation"          -> d.addOxygenSaturation(val * 100.0);
                case "HKQuantityTypeIdentifierTimeInDaylight"            -> d.addDaylight(val);
                // ── Nutrition: nur Yazio als Quelle ──────────────────────────
                case "HKQuantityTypeIdentifierDietaryEnergyConsumed" -> {
                    if (isYazioSource(source)) d.addDietaryKcal(val);
                }
                case "HKQuantityTypeIdentifierDietaryProtein" -> {
                    if (isYazioSource(source)) d.addDietaryProtein(val);
                }
                case "HKQuantityTypeIdentifierDietaryCarbohydrates" -> {
                    if (isYazioSource(source)) d.addDietaryCarbs(val);
                }
                case "HKQuantityTypeIdentifierDietaryFatTotal" -> {
                    if (isYazioSource(source)) d.addDietaryFat(val);
                }
                case "HKQuantityTypeIdentifierDietarySugar" -> {
                    if (isYazioSource(source)) d.addDietarySugar(val);
                }
                case "HKQuantityTypeIdentifierDietaryFiber" -> {
                    if (isYazioSource(source)) d.addDietaryFiber(val);
                }
                case "HKQuantityTypeIdentifierDietaryWater" -> {
                    if (isYazioSource(source)) d.addDietaryWater(val);
                }
                default -> {}
            }
        }

        // ── Workout-Verarbeitung ──────────────────────────────────────────────

        private void handleWorkout(Attributes a) {
            String rawType  = a.getValue("workoutActivityType");
            String startStr = a.getValue("startDate");
            String endStr   = a.getValue("endDate");
            String duration = a.getValue("duration");
            String calories = a.getValue("totalEnergyBurned");
            String distance = a.getValue("totalDistance");
            String distUnit = a.getValue("totalDistanceUnit");
            String source   = a.getValue("sourceName");

            if (rawType == null || startStr == null) return;
            String type = rawType.replace("HKWorkoutActivityType", "");
            LocalDate date;
            try { date = parseDate(startStr); } catch (Exception e) { return; }

            HealthWorkout w = new HealthWorkout();
            w.setActivityType(type);
            w.setDate(date);
            w.setStartDate(startStr);
            w.setEndDate(endStr != null ? endStr : "");
            w.setSourceName(source != null ? source : "");
            if (duration != null) { try { w.setDurationMinutes(Double.parseDouble(duration)); } catch (NumberFormatException ignored) {} }
            if (calories != null) { try { w.setCaloriesBurned(Double.parseDouble(calories)); } catch (NumberFormatException ignored) {} }
            if (distance != null) {
                try {
                    double dist = Double.parseDouble(distance);
                    if ("mi".equalsIgnoreCase(distUnit)) dist *= 1.60934;
                    w.setDistanceKm(dist);
                } catch (NumberFormatException ignored) {}
            }
            workoutBatch.add(w);
        }

        // ── Flush-Helpers ─────────────────────────────────────────────────────

        private void flushMetrics() {
            if (metricBatch.isEmpty()) return;
            mongoTemplate.insertAll(metricBatch);
            importedMetrics.addAndGet(metricBatch.size());
            metricBatch.clear();
        }

        private void flushWorkouts() {
            if (workoutBatch.isEmpty()) return;
            mongoTemplate.insertAll(workoutBatch);
            importedWorkouts.addAndGet(workoutBatch.size());
            workoutBatch.clear();
        }

        private void flushDaily() {
            if (daily.isEmpty()) return;
            for (HealthDailyMetric m : daily.values()) {
                mongoTemplate.upsert(
                        Query.query(Criteria.where("_id").is(m.getDate())),
                        buildUpdate(m),
                        HealthDailyMetric.class
                );
            }
            importedDays.addAndGet(daily.size());
            daily.clear();
        }

        private Update buildUpdate(HealthDailyMetric m) {
            Update u = new Update();
            u.inc("steps",             (double) m.getSteps());
            u.inc("activeEnergyKcal",  m.getActiveEnergyKcal());
            u.inc("basalEnergyKcal",   m.getBasalEnergyKcal());
            u.inc("sleepHours",        m.getSleepHours());
            u.inc("daylightMinutes",   m.getDaylightMinutes());
            u.inc("dietaryKcal",       m.getDietaryKcal());
            u.inc("dietaryProteinG",   m.getDietaryProteinG());
            u.inc("dietaryCarbsG",     m.getDietaryCarbsG());
            u.inc("dietaryFatG",       m.getDietaryFatG());
            u.inc("dietarySugarG",     m.getDietarySugarG());
            u.inc("dietaryFiberG",     m.getDietaryFiberG());
            u.inc("dietaryWaterMl",    m.getDietaryWaterMl());
            if (m.getHeartRateSamples() > 0) {
                u.set("avgHeartRate",    m.getAvgHeartRate());
                u.set("minHeartRate",    m.getMinHeartRate());
                u.set("maxHeartRate",    m.getMaxHeartRate());
                u.set("heartRateSamples", m.getHeartRateSamples());
            }
            if (m.getHrvSamples() > 0)         u.set("avgHrv",              m.getAvgHrv());
            if (m.getRespiratorySamples() > 0)  u.set("avgRespiratoryRate",  m.getAvgRespiratoryRate());
            if (m.getOxygenSamples() > 0)       u.set("avgOxygenSaturation", m.getAvgOxygenSaturation());
            return u;
        }
    }

    // ── Quellen-Filter ────────────────────────────────────────────────────────

    /**
     * Nur Apple Watch als Schrittquelle.
     * iPhone und QRing überlappen mit Apple Watch zeitlich → Apple Health
     * dedupliziert intern und bevorzugt die Watch. In der rohen Export.xml
     * sind beide Quellen vorhanden, daher nur Watch-Records übernehmen.
     */
    private static boolean isAppleStepSource(String source) {
        if (source == null) return false;
        // "Apple\u00a0Watch von Victor" – non-breaking space im Export
        return source.startsWith("Apple");
    }

    /**
     * Nur Yazio-Einträge für Ernährungsdaten übernehmen.
     */
    private static boolean isYazioSource(String source) {
        return "Yazio".equalsIgnoreCase(source);
    }

    // ── Datum-Parsing ─────────────────────────────────────────────────────────

    private static LocalDate parseDate(String appleDate) {
        return LocalDate.parse(appleDate.substring(0, 10));
    }
}
