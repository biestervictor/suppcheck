package org.example.suppcheck.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.example.suppcheck.dto.IntakeChangeSummary;
import org.example.suppcheck.model.DailyIntakeSnapshot;
import org.example.suppcheck.model.Ingredient;
import org.example.suppcheck.model.Supplement;
import org.example.suppcheck.model.SupplementType;
import org.example.suppcheck.repository.DailyIntakeSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DailyIntakeSnapshotServiceTest {

  private DailyIntakeSnapshotRepository repository;
  private DailyIntakeSnapshotService service;

  @BeforeEach
  void setUp() {
    repository = mock(DailyIntakeSnapshotRepository.class);
    service = new DailyIntakeSnapshotService(repository);
    when(repository.save(any(DailyIntakeSnapshot.class))).thenAnswer(inv -> inv.getArgument(0));
    when(repository.findByDate(any(LocalDate.class))).thenReturn(Optional.empty());
  }

  // ── helpers ──────────────────────────────────────────────────────────────────

  private Supplement supplement(String name, String type, boolean inactive, String ingName, double mg) {
    Supplement s = new Supplement();
    s.setName(name);
    s.setSupplementType(type);
    s.setInactive(inactive);
    Ingredient ing = new Ingredient();
    ing.setName(ingName);
    ing.setMg(mg);
    ing.setSubIngredients(new ArrayList<>());
    s.setIngredients(List.of(ing));
    return s;
  }

  // ── saveSnapshot ─────────────────────────────────────────────────────────────

  @Test
  void saveSnapshot_setsDateAndCallsRepository() {
    Supplement s = supplement("VitD", SupplementType.BASIC.name(), false, "Vitamin D3", 5000);

    DailyIntakeSnapshot result = service.saveSnapshot(List.of(s));

    assertEquals(LocalDate.now(), result.getDate());
    assertNotNull(result.getRestDay());
    assertNotNull(result.getWorkoutDay());
    verify(repository).save(any(DailyIntakeSnapshot.class));
  }

  @Test
  void saveSnapshot_excludesInactiveSupplements() {
    Supplement active   = supplement("VitD",  SupplementType.BASIC.name(), false, "Vitamin D3", 5000);
    Supplement inactive = supplement("Whey",  SupplementType.WHEY.name(),  true,  "Protein",    30000);

    DailyIntakeSnapshot result = service.saveSnapshot(List.of(active, inactive));

    assertTrue(result.getRestDay().containsKey("Vitamin D3"));
    assertFalse(result.getRestDay().containsKey("Protein"), "Inactive supplement must be excluded");
  }

  @Test
  void saveSnapshot_excludesSportOnRestDay_includesOnWorkoutDay() {
    Supplement sport = supplement("BCAA", SupplementType.SPORT.name(), false, "L-Leucin", 3000);

    DailyIntakeSnapshot result = service.saveSnapshot(List.of(sport));

    assertFalse(result.getRestDay().containsKey("L-Leucin"), "SPORT must be absent on rest day");
    assertTrue(result.getWorkoutDay().containsKey("L-Leucin"), "SPORT must be present on workout day");
  }

  @Test
  void saveSnapshot_overwritesTodayIfAlreadyExists() {
    DailyIntakeSnapshot existing = new DailyIntakeSnapshot();
    existing.setId("existing-id");
    existing.setDate(LocalDate.now());
    when(repository.findByDate(LocalDate.now())).thenReturn(Optional.of(existing));

    service.saveSnapshot(List.of());

    verify(repository, times(1)).save(existing);
  }

  @Test
  void saveSnapshot_storesActiveSupplementNames() {
    Supplement a = supplement("VitD",  SupplementType.BASIC.name(), false, "Vitamin D3", 5000);
    Supplement b = supplement("Magnesium", SupplementType.BASIC.name(), false, "Magnesium", 200);
    Supplement c = supplement("Inaktiv", SupplementType.BASIC.name(), true, "X", 1);

    DailyIntakeSnapshot result = service.saveSnapshot(List.of(a, b, c));

    assertTrue(result.getActiveSupplementNames().contains("VitD"));
    assertTrue(result.getActiveSupplementNames().contains("Magnesium"));
    assertFalse(result.getActiveSupplementNames().contains("Inaktiv"));
  }

  // ── computeChangeSummaries ───────────────────────────────────────────────────

  @Test
  void computeChangeSummaries_emptyWhenOnlyOneSnapshot() {
    DailyIntakeSnapshot snap = new DailyIntakeSnapshot();
    snap.setDate(LocalDate.now());
    snap.setRestDay(java.util.Map.of("Vitamin D3", 5000.0));
    snap.setWorkoutDay(java.util.Map.of("Vitamin D3", 5000.0));
    snap.setActiveSupplementNames(List.of("VitD"));

    when(repository.findAllByOrderByDateAsc()).thenReturn(List.of(snap));

    List<IntakeChangeSummary> summaries = service.computeChangeSummaries();
    assertTrue(summaries.isEmpty());
  }

  @Test
  void computeChangeSummaries_detectsRemovedSupplement() {
    DailyIntakeSnapshot older = new DailyIntakeSnapshot();
    older.setDate(LocalDate.now().minusDays(1));
    older.setRestDay(java.util.Map.of("Vitamin D3", 10000.0));
    older.setWorkoutDay(java.util.Map.of("Vitamin D3", 10000.0));
    older.setActiveSupplementNames(List.of("D3 10000"));

    DailyIntakeSnapshot newer = new DailyIntakeSnapshot();
    newer.setDate(LocalDate.now());
    newer.setRestDay(java.util.Map.of("Vitamin D3", 6000.0));
    newer.setWorkoutDay(java.util.Map.of("Vitamin D3", 6000.0));
    newer.setActiveSupplementNames(List.of("D3 6000"));

    when(repository.findAllByOrderByDateAsc()).thenReturn(List.of(older, newer));

    List<IntakeChangeSummary> summaries = service.computeChangeSummaries();

    assertEquals(1, summaries.size());
    IntakeChangeSummary summary = summaries.get(0);
    assertTrue(summary.getRemovedSupplements().contains("D3 10000"));
    assertTrue(summary.getAddedSupplements().contains("D3 6000"));
    assertEquals(-4000.0, summary.getIngredientDeltas().get("Vitamin D3"), 0.001,
        "Net change should be -4000 mg");
  }

  @Test
  void computeChangeSummaries_noChangesWhenSnapshotsIdentical() {
    DailyIntakeSnapshot older = new DailyIntakeSnapshot();
    older.setDate(LocalDate.now().minusDays(1));
    older.setRestDay(java.util.Map.of("Vitamin C", 1000.0));
    older.setWorkoutDay(java.util.Map.of("Vitamin C", 1000.0));
    older.setActiveSupplementNames(List.of("VitC"));

    DailyIntakeSnapshot newer = new DailyIntakeSnapshot();
    newer.setDate(LocalDate.now());
    newer.setRestDay(java.util.Map.of("Vitamin C", 1000.0));
    newer.setWorkoutDay(java.util.Map.of("Vitamin C", 1000.0));
    newer.setActiveSupplementNames(List.of("VitC"));

    when(repository.findAllByOrderByDateAsc()).thenReturn(List.of(older, newer));

    List<IntakeChangeSummary> summaries = service.computeChangeSummaries();

    assertEquals(1, summaries.size());
    assertTrue(summaries.get(0).getIngredientDeltas().isEmpty());
    assertTrue(summaries.get(0).getRemovedSupplements().isEmpty());
    assertTrue(summaries.get(0).getAddedSupplements().isEmpty());
  }
}
