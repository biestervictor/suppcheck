package org.example.suppcheck.gymbook.service;

import org.example.suppcheck.gymbook.model.GymExerciseEntry;
import org.example.suppcheck.gymbook.model.GymSession;
import org.example.suppcheck.gymbook.model.GymSetEntry;
import org.example.suppcheck.gymbook.repository.GymSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Importiert ein GymBook-Backup (.database = SQLite) in die MongoDB-Collection
 * {@code gym_sessions}.
 *
 * <p>Die CoreData-Zeitstempel in GymBook sind Sekunden seit 2001-01-01 00:00:00 UTC.
 * Umrechnung: {@code epoch = timestamp + 978307200}
 */
@Service
public class GymBookImportService {

    private static final Logger log = LoggerFactory.getLogger(GymBookImportService.class);

    /** Sekunden zwischen Unix-Epoch (1970-01-01) und CoreData-Epoch (2001-01-01) */
    private static final long COREDATA_EPOCH_OFFSET = 978307200L;

    private static final String SQL = """
            SELECT
              date(el.ZDATE + %d, 'unixepoch') AS workout_date,
              e.ZNAME                           AS exercise_name,
              e.ZTARGETMUSCLESPRIMARY           AS primary_muscles,
              e.ZTARGETMUSCLESSECONDARY         AS secondary_muscles,
              e.ZTARGETREGION                   AS region,
              COALESCE(el.ZQUANTITYVALUE, 0)    AS weight,
              COALESCE(el.ZFREQUENCYVALUE, 0)   AS reps,
              COALESCE(el.ZTYPE, 'default')     AS set_type
            FROM ZEXERCISELOG el
            JOIN ZEXERCISE e ON el.ZEXERCISE = e.Z_PK
            WHERE el.ZDATE IS NOT NULL
            ORDER BY el.ZDATE ASC
            """.formatted(COREDATA_EPOCH_OFFSET);

    private final GymSessionRepository sessionRepo;

    // ── Status ────────────────────────────────────────────────────────────────
    private final AtomicReference<String> importStatus  = new AtomicReference<>("idle");
    private final AtomicReference<String> importError   = new AtomicReference<>(null);
    private final AtomicLong importedSessions           = new AtomicLong(0);
    private final AtomicLong importedSets               = new AtomicLong(0);

    public GymBookImportService(GymSessionRepository sessionRepo) {
        this.sessionRepo = sessionRepo;
    }

    // ── Status-API ────────────────────────────────────────────────────────────

    public String  getStatus()           { return importStatus.get(); }
    public String  getImportError()      { return importError.get(); }
    public long    getImportedSessions() { return importedSessions.get(); }
    public long    getImportedSets()     { return importedSets.get(); }
    public boolean isRunning()           { return "running".equals(importStatus.get()); }

    // ── Import starten ────────────────────────────────────────────────────────

    public void startImportAsync(File file, boolean deleteTempFile) {
        if (isRunning()) throw new IllegalStateException("Import is already running");
        importStatus.set("running");
        importError.set(null);
        importedSessions.set(0);
        importedSets.set(0);

        Thread t = new Thread(() -> {
            try {
                runImport(file.getAbsolutePath());
                importStatus.set("done");
            } catch (Exception e) {
                log.error("GymBook import failed", e);
                importError.set(e.getMessage());
                importStatus.set("error");
            } finally {
                if (deleteTempFile && file.exists()) {
                    if (!file.delete()) log.warn("Temp-Datei nicht gelöscht: {}", file.getAbsolutePath());
                }
            }
        }, "gymbook-import");
        t.setDaemon(true);
        t.start();
    }

    // ── Core-Import-Logik ─────────────────────────────────────────────────────

    private void runImport(String filePath) throws Exception {
        log.info("GymBook Import gestartet: {}", filePath);
        sessionRepo.deleteAll();

        // Build sessions in memory, then bulk-save
        Map<String, GymSession> sessionMap = new LinkedHashMap<>();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + filePath);
             Statement  stmt = conn.createStatement();
             ResultSet  rs   = stmt.executeQuery(SQL)) {

            while (rs.next()) {
                String date         = rs.getString("workout_date");
                String exerciseName = rs.getString("exercise_name");
                String primary      = rs.getString("primary_muscles");
                String secondary    = rs.getString("secondary_muscles");
                String region       = rs.getString("region");
                double weight       = rs.getDouble("weight");
                int    reps         = rs.getInt("reps");
                String setType      = rs.getString("set_type");

                if (date == null || exerciseName == null) continue;

                GymSession session = sessionMap.computeIfAbsent(date, GymSession::new);

                // find or create exercise entry for this session
                GymExerciseEntry exercise = session.getExercises().stream()
                        .filter(ex -> ex.getName().equals(exerciseName))
                        .findFirst()
                        .orElseGet(() -> {
                            GymExerciseEntry e = new GymExerciseEntry();
                            e.setName(exerciseName);
                            e.setPrimaryMuscles(primary != null ? primary : "");
                            e.setSecondaryMuscles(secondary != null ? secondary : "");
                            e.setRegion(region != null ? region : "");
                            session.addExercise(e);
                            return e;
                        });

                if (reps > 0) {
                    exercise.addSet(new GymSetEntry(weight, reps, setType));
                    importedSets.incrementAndGet();
                }
            }
        }

        sessionRepo.saveAll(sessionMap.values());
        importedSessions.set(sessionMap.size());
        log.info("GymBook Import abgeschlossen. Sessions={}, Sets={}", importedSessions.get(), importedSets.get());
    }
}
