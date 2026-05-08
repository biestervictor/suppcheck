package org.example.suppcheck.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.example.suppcheck.dto.IntakeChangeSummary;
import org.example.suppcheck.model.DailyIntakeSnapshot;
import org.example.suppcheck.service.DailyIntakeSnapshotService;
import org.example.suppcheck.service.SupplementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

class DailyIntakeSnapshotControllerTest {

  private DailyIntakeSnapshotService snapshotService;
  private SupplementService supplementService;
  private DailyIntakeSnapshotController controller;

  @BeforeEach
  void setUp() {
    snapshotService  = mock(DailyIntakeSnapshotService.class);
    supplementService = mock(SupplementService.class);
    controller = new DailyIntakeSnapshotController(snapshotService, supplementService);
    when(supplementService.getAllSupplements()).thenReturn(List.of());
  }

  @Test
  void saveSnapshot_callsServiceAndRedirects() {
    DailyIntakeSnapshot snap = new DailyIntakeSnapshot();
    when(snapshotService.saveSnapshot(anyList())).thenReturn(snap);

    String view = controller.saveSnapshot();

    assertEquals("redirect:/supplements/ingredients/summary?snapshotSaved", view);
    verify(snapshotService).saveSnapshot(anyList());
  }

  @Test
  void showHistory_emptySnapshots_returnsView() {
    when(snapshotService.getAllSnapshots()).thenReturn(List.of());
    when(snapshotService.computeChangeSummaries()).thenReturn(List.of());

    ConcurrentModel model = new ConcurrentModel();
    String view = controller.showHistory(model);

    assertEquals("intake_history", view);
    @SuppressWarnings("unchecked")
    List<DailyIntakeSnapshot> snapshots = (List<DailyIntakeSnapshot>) model.getAttribute("snapshots");
    assertNotNull(snapshots);
    assertTrue(snapshots.isEmpty());
  }

  @Test
  void showHistory_populatesModelWithMatricesAndNames() {
    DailyIntakeSnapshot s1 = new DailyIntakeSnapshot();
    s1.setDate(LocalDate.of(2026, 1, 1));
    s1.setRestDay(Map.of("Vitamin D3", 5000.0));
    s1.setWorkoutDay(Map.of("Vitamin D3", 5000.0, "L-Leucin", 3000.0));
    s1.setActiveSupplementNames(List.of("VitD", "BCAA"));

    DailyIntakeSnapshot s2 = new DailyIntakeSnapshot();
    s2.setDate(LocalDate.of(2026, 2, 1));
    s2.setRestDay(Map.of("Vitamin D3", 6000.0, "Magnesium", 200.0));
    s2.setWorkoutDay(Map.of("Vitamin D3", 6000.0, "Magnesium", 200.0, "L-Leucin", 3000.0));
    s2.setActiveSupplementNames(List.of("VitD2", "Magnesium", "BCAA"));

    IntakeChangeSummary change = new IntakeChangeSummary(
        List.of("VitD2", "Magnesium"), List.of("VitD"),
        Map.of("Vitamin D3", 1000.0, "Magnesium", 200.0),
        List.of("Änderungen von 2026-01-01 → 2026-02-01:")
    );

    when(snapshotService.getAllSnapshots()).thenReturn(List.of(s1, s2));
    when(snapshotService.computeChangeSummaries()).thenReturn(List.of(change));

    ConcurrentModel model = new ConcurrentModel();
    String view = controller.showHistory(model);

    assertEquals("intake_history", view);
    @SuppressWarnings("unchecked")
    List<String> names = (List<String>) model.getAttribute("allIngredientNames");
    assertNotNull(names);
    assertTrue(names.contains("Vitamin D3"));
    assertTrue(names.contains("Magnesium"));
    assertTrue(names.contains("L-Leucin"));

    assertNotNull(model.getAttribute("restDayMatrix"));
    assertNotNull(model.getAttribute("workoutDayMatrix"));
    assertNotNull(model.getAttribute("changes"));
  }
}
