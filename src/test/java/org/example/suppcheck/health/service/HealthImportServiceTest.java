package org.example.suppcheck.health.service;

import org.example.suppcheck.gymbook.model.GymSession;
import org.example.suppcheck.gymbook.repository.GymSessionRepository;
import org.example.suppcheck.health.model.HealthDailyMetric;
import org.example.suppcheck.health.model.HealthMetric;
import org.example.suppcheck.health.model.HealthWorkout;
import org.example.suppcheck.health.repository.HealthDailyMetricRepository;
import org.example.suppcheck.health.repository.HealthMetricRepository;
import org.example.suppcheck.health.repository.HealthWorkoutRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für HealthImportService.
 *
 * Testet insbesondere, dass Records innerhalb von Correlation-Elementen
 * NICHT doppelt gezählt werden (Apple Health DTD-Bug-Workaround).
 */
class HealthImportServiceTest {

    @TempDir
    Path tempDir;

    private MongoTemplate         mongo;
    private GymSessionRepository  gymRepo;
    private HealthImportService   service;

    // Captured upsert calls
    private final List<Update> capturedUpdates = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mongo   = mock(MongoTemplate.class);
        gymRepo = mock(GymSessionRepository.class);
        doAnswer(inv -> { capturedUpdates.add(inv.getArgument(1)); return null; })
                .when(mongo).upsert(any(Query.class), any(Update.class), any(Class.class));
        doNothing().when(mongo).dropCollection(anyString());

        service = new HealthImportService(
                mock(HealthMetricRepository.class),
                mock(HealthDailyMetricRepository.class),
                mock(HealthWorkoutRepository.class),
                mongo,
                gymRepo
        );
    }

    // ── Correlation-Doppelzählung ─────────────────────────────────────────────

    /**
     * Kerntest: eine Mahlzeit wird einmal als eigenständiger Record und einmal
     * als Kind-Record einer Correlation exportiert. Nur der Top-Level-Record
     * darf gezählt werden → kcal = 500 (nicht 1000).
     */
    @Test
    void correlationChildRecords_areNotDoubleCounted(@TempDir Path tmp) throws Exception {
        String xml = """
                <?xml version="1.0"?>
                <!DOCTYPE HealthData [<!ELEMENT HealthData ANY>]>
                <HealthData locale="de_DE">
                  <ExportDate value="2026-05-18 10:00:00 +0200"/>
                  <Me HKCharacteristicTypeIdentifierDateOfBirth=""
                      HKCharacteristicTypeIdentifierBiologicalSex=""
                      HKCharacteristicTypeIdentifierBloodType=""
                      HKCharacteristicTypeIdentifierFitzpatrickSkinType=""
                      HKCharacteristicTypeIdentifierCardioFitnessMedicationsUse=""/>

                  <!-- Correlation wraps the child records – they are also emitted top-level -->
                  <Correlation type="HKCorrelationTypeIdentifierFood"
                               sourceName="Yazio" sourceVersion="1"
                               startDate="2026-05-17 12:00:00 +0200"
                               endDate="2026-05-17 12:00:00 +0200">
                    <Record type="HKQuantityTypeIdentifierDietaryEnergyConsumed"
                            sourceName="Yazio" unit="kcal" value="500"
                            startDate="2026-05-17 12:00:00 +0200"
                            endDate="2026-05-17 12:00:00 +0200"/>
                  </Correlation>

                  <!-- Same record again as top-level (as Apple Health always does) -->
                  <Record type="HKQuantityTypeIdentifierDietaryEnergyConsumed"
                          sourceName="Yazio" unit="kcal" value="500"
                          startDate="2026-05-17 12:00:00 +0200"
                          endDate="2026-05-17 12:00:00 +0200"/>
                </HealthData>
                """;
        File f = tmp.resolve("export.xml").toFile();
        try (FileWriter w = new FileWriter(f)) { w.write(xml); }

        service.startImportAsync(f, false);
        waitForImport();

        assertEquals("done", service.getStatus(), "Import should finish successfully");
        // Should have exactly one upsert for 2026-05-17
        assertEquals(1, capturedUpdates.size(), "Should produce exactly one daily upsert");

        // The dietaryKcal increment should be 500, not 1000
        Update update = capturedUpdates.get(0);
        Object kcalInc = getIncValue(update, "dietaryKcal");
        assertNotNull(kcalInc, "dietaryKcal should be in the update");
        assertEquals(500.0, ((Number) kcalInc).doubleValue(), 0.01,
                "Calories must NOT be doubled by Correlation child records");
    }

    @Test
    void nonYazioNutrition_isIgnored(@TempDir Path tmp) throws Exception {
        String xml = """
                <?xml version="1.0"?>
                <!DOCTYPE HealthData [<!ELEMENT HealthData ANY>]>
                <HealthData locale="de_DE">
                  <ExportDate value="2026-05-18 10:00:00 +0200"/>
                  <Me HKCharacteristicTypeIdentifierDateOfBirth=""
                      HKCharacteristicTypeIdentifierBiologicalSex=""
                      HKCharacteristicTypeIdentifierBloodType=""
                      HKCharacteristicTypeIdentifierFitzpatrickSkinType=""
                      HKCharacteristicTypeIdentifierCardioFitnessMedicationsUse=""/>
                  <Record type="HKQuantityTypeIdentifierDietaryEnergyConsumed"
                          sourceName="MyFitnessPal" unit="kcal" value="800"
                          startDate="2026-05-17 12:00:00 +0200"
                          endDate="2026-05-17 12:00:00 +0200"/>
                  <Record type="HKQuantityTypeIdentifierDietaryEnergyConsumed"
                          sourceName="Yazio" unit="kcal" value="500"
                          startDate="2026-05-17 12:00:00 +0200"
                          endDate="2026-05-17 12:00:00 +0200"/>
                </HealthData>
                """;
        File f = tmp.resolve("export.xml").toFile();
        try (FileWriter w = new FileWriter(f)) { w.write(xml); }

        service.startImportAsync(f, false);
        waitForImport();

        assertEquals(1, capturedUpdates.size());
        Object kcalInc = getIncValue(capturedUpdates.get(0), "dietaryKcal");
        assertEquals(500.0, ((Number) kcalInc).doubleValue(), 0.01,
                "Only Yazio calories should be counted, not MyFitnessPal");
    }

    @Test
    void qRingSteps_areIgnored(@TempDir Path tmp) throws Exception {
        String xml = """
                <?xml version="1.0"?>
                <!DOCTYPE HealthData [<!ELEMENT HealthData ANY>]>
                <HealthData locale="de_DE">
                  <ExportDate value="2026-05-18 10:00:00 +0200"/>
                  <Me HKCharacteristicTypeIdentifierDateOfBirth=""
                      HKCharacteristicTypeIdentifierBiologicalSex=""
                      HKCharacteristicTypeIdentifierBloodType=""
                      HKCharacteristicTypeIdentifierFitzpatrickSkinType=""
                      HKCharacteristicTypeIdentifierCardioFitnessMedicationsUse=""/>
                  <Record type="HKQuantityTypeIdentifierStepCount"
                          sourceName="QRing" unit="count" value="3000"
                          startDate="2026-05-17 08:00:00 +0200"
                          endDate="2026-05-17 08:05:00 +0200"/>
                  <Record type="HKQuantityTypeIdentifierStepCount"
                          sourceName="iPhone (7)" unit="count" value="500"
                          startDate="2026-05-17 08:00:00 +0200"
                          endDate="2026-05-17 08:05:00 +0200"/>
                  <Record type="HKQuantityTypeIdentifierStepCount"
                          sourceName="Apple\u00a0Watch von Victor" unit="count" value="1500"
                          startDate="2026-05-17 08:00:00 +0200"
                          endDate="2026-05-17 08:05:00 +0200"/>
                </HealthData>
                """;
        File f = tmp.resolve("export.xml").toFile();
        try (FileWriter w = new FileWriter(f)) { w.write(xml); }

        service.startImportAsync(f, false);
        waitForImport();

        assertEquals(1, capturedUpdates.size());
        Object stepsInc = getIncValue(capturedUpdates.get(0), "steps");
        assertEquals(1500.0, ((Number) stepsInc).doubleValue(), 0.01,
                "Only Apple Watch steps counted – QRing and iPhone excluded");
    }

    // ── classifyWorkoutTag ────────────────────────────────────────────────────

    @Test
    void classifyWorkoutTag_walking_returnsGehen() {
        assertEquals("Gehen", service.classifyWorkoutTag("Walking", LocalDate.of(2026, 5, 1)));
    }

    @Test
    void classifyWorkoutTag_hiking_returnsGehen() {
        assertEquals("Gehen", service.classifyWorkoutTag("Hiking", LocalDate.of(2026, 5, 1)));
    }

    @Test
    void classifyWorkoutTag_running_returnsJoggen() {
        assertEquals("Joggen", service.classifyWorkoutTag("Running", LocalDate.of(2026, 5, 1)));
    }

    @Test
    void classifyWorkoutTag_unknown_returnsSonstiges() {
        assertEquals("Sonstiges", service.classifyWorkoutTag("CoreTraining", LocalDate.of(2026, 5, 1)));
    }

    @Test
    void classifyWorkoutTag_strengthWithGymBookPush_returnsKrafttrainingPush() {
        GymSession gs = new GymSession("2026-05-01");
        gs.setTag("Push");
        when(gymRepo.findById("2026-05-01")).thenReturn(Optional.of(gs));
        assertEquals("Krafttraining (Push)",
                service.classifyWorkoutTag("TraditionalStrengthTraining", LocalDate.of(2026, 5, 1)));
    }

    @Test
    void classifyWorkoutTag_strengthWithGymBookPull_returnsKrafttrainingPull() {
        GymSession gs = new GymSession("2026-05-02");
        gs.setTag("Pull");
        when(gymRepo.findById("2026-05-02")).thenReturn(Optional.of(gs));
        assertEquals("Krafttraining (Pull)",
                service.classifyWorkoutTag("FunctionalStrengthTraining", LocalDate.of(2026, 5, 2)));
    }

    @Test
    void classifyWorkoutTag_strengthWithGymBookBeine_returnsKrafttrainingBeine() {
        GymSession gs = new GymSession("2026-05-03");
        gs.setTag("Beine");
        when(gymRepo.findById("2026-05-03")).thenReturn(Optional.of(gs));
        assertEquals("Krafttraining (Beine)",
                service.classifyWorkoutTag("TraditionalStrengthTraining", LocalDate.of(2026, 5, 3)));
    }

    @Test
    void classifyWorkoutTag_strengthWithoutGymBook_returnsKrafttraining() {
        when(gymRepo.findById("2026-05-04")).thenReturn(Optional.empty());
        assertEquals("Krafttraining",
                service.classifyWorkoutTag("TraditionalStrengthTraining", LocalDate.of(2026, 5, 4)));
    }

    @Test
    void classifyWorkoutTag_strengthWithGymBookSonstige_returnsKrafttraining() {
        GymSession gs = new GymSession("2026-05-05");
        gs.setTag("Sonstige");
        when(gymRepo.findById("2026-05-05")).thenReturn(Optional.of(gs));
        assertEquals("Krafttraining",
                service.classifyWorkoutTag("TraditionalStrengthTraining", LocalDate.of(2026, 5, 5)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void waitForImport() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (service.isRunning() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        if (service.isRunning()) fail("Import did not finish within 5 s");
    }

    /** Extract the $inc value for a given field from a Spring Data MongoDB Update object. */
    @SuppressWarnings("unchecked")
    private Object getIncValue(Update update, String field) {
        // Update.getUpdateObject() returns a Document like { "$inc": { "steps": 1500, ... }, ... }
        var doc = update.getUpdateObject();
        var incMap = (java.util.Map<String, Object>) doc.get("$inc");
        return incMap == null ? null : incMap.get(field);
    }
}
