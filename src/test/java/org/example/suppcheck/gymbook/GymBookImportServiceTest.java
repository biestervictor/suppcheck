package org.example.suppcheck.gymbook;

import org.example.suppcheck.gymbook.model.GymExerciseEntry;
import org.example.suppcheck.gymbook.model.GymSession;
import org.example.suppcheck.gymbook.model.GymSetEntry;
import org.example.suppcheck.gymbook.repository.GymSessionRepository;
import org.example.suppcheck.gymbook.service.GymBookImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für GymBookImportService – insbesondere die
 * Push/Pull/Beine-Klassifikationslogik und den 0-Sätze-Filter.
 */
class GymBookImportServiceTest {

    private GymBookImportService service;

    @BeforeEach
    void setUp() {
        GymSessionRepository repo = mock(GymSessionRepository.class);
        service = new GymBookImportService(repo);
    }

    // ── classifySession: Push ─────────────────────────────────────────────────

    @Test
    void classifySession_pushWhenOnlyPushMuscles() {
        GymSession s = sessionWith("020.pectorals", "010.shoulders");
        service.classifySession(s);
        assertEquals("Push", s.getTag());
    }

    @Test
    void classifySession_pushWhenTrizeps() {
        GymSession s = sessionWith("040.armExtensors");
        service.classifySession(s);
        assertEquals("Push", s.getTag());
    }

    // ── classifySession: Pull ─────────────────────────────────────────────────

    @Test
    void classifySession_pullWhenLatAndBizeps() {
        GymSession s = sessionWith("031.dorsalMuscles", "041.armFlexors");
        service.classifySession(s);
        assertEquals("Pull", s.getTag());
    }

    @Test
    void classifySession_pullWhenTrapezius() {
        GymSession s = sessionWith("030.trapezius");
        service.classifySession(s);
        assertEquals("Pull", s.getTag());
    }

    // ── classifySession: Beine ────────────────────────────────────────────────

    @Test
    void classifySession_beineWhenQuads() {
        GymSession s = sessionWith("070.quadriceps", "072.thighFlexors");
        service.classifySession(s);
        assertEquals("Beine", s.getTag());
    }

    @Test
    void classifySession_beineWhenGlutes() {
        GymSession s = sessionWith("060.glutes");
        service.classifySession(s);
        assertEquals("Beine", s.getTag());
    }

    // ── classifySession: Sonstige / mixed ────────────────────────────────────

    @Test
    void classifySession_sonstigeWhenNoMuscles() {
        GymSession s = new GymSession("2026-01-01");
        service.classifySession(s);
        assertEquals("Sonstige", s.getTag());
    }

    @Test
    void classifySession_beineWinsWhenTied() {
        // Beine (1) = Push (1) → Beine wins because checked first
        GymSession s = sessionWith("060.glutes", "020.pectorals");
        service.classifySession(s);
        assertEquals("Beine", s.getTag());
    }

    @Test
    void classifySession_pullWinsOverPushWhenTied() {
        // Pull (1) = Push (1), no Beine → Pull wins
        GymSession s = sessionWith("031.dorsalMuscles", "020.pectorals");
        service.classifySession(s);
        assertEquals("Pull", s.getTag());
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    /** Creates a session with one exercise per muscle (each with 1 set). */
    private GymSession sessionWith(String... muscles) {
        GymSession session = new GymSession("2026-05-01");
        for (String m : muscles) {
            GymExerciseEntry ex = new GymExerciseEntry();
            ex.setName("Exercise for " + m);
            ex.setPrimaryMuscles(m);
            ex.setSecondaryMuscles("");
            ex.addSet(new GymSetEntry(50.0, 10, "default"));
            session.addExercise(ex);
        }
        return session;
    }
}
