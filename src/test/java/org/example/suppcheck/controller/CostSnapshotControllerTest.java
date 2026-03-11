package org.example.suppcheck.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import org.example.suppcheck.model.CostSnapshot;
import org.example.suppcheck.service.CostSnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

class CostSnapshotControllerTest {

  private CostSnapshotService service;
  private CostSnapshotController controller;

  @BeforeEach
  void setUp() {
    service = mock(CostSnapshotService.class);
    controller = new CostSnapshotController(service);
  }

  @Test
  void saveSnapshot_callsServiceAndRedirects() {
    CostSnapshot snap = new CostSnapshot();
    when(service.saveSnapshot(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
        .thenReturn(snap);

    String view = controller.saveSnapshot(1.67, 1.92, 2.87, 2.32, 189.19);

    assertEquals("redirect:/supplements?saved", view);
    verify(service).saveSnapshot(1.67, 1.92, 2.87, 2.32, 189.19);
  }

  @Test
  void showHistory_populatesModelAndReturnsView() {
    CostSnapshot s1 = new CostSnapshot();
    s1.setDate(LocalDate.of(2026, 1, 15));
    s1.setPreisProMonat(180.0);

    CostSnapshot s2 = new CostSnapshot();
    s2.setDate(LocalDate.of(2026, 3, 11));
    s2.setPreisProMonat(189.19);

    when(service.getAllSnapshots()).thenReturn(List.of(s1, s2));

    ConcurrentModel model = new ConcurrentModel();
    String view = controller.showHistory(model);

    assertEquals("cost_history", view);
    assertTrue(model.containsAttribute("snapshots"));
    @SuppressWarnings("unchecked")
    List<CostSnapshot> snapshots = (List<CostSnapshot>) model.getAttribute("snapshots");
    assertNotNull(snapshots);
    assertEquals(2, snapshots.size());
  }

  @Test
  void showHistory_emptyList_stillReturnsView() {
    when(service.getAllSnapshots()).thenReturn(List.of());

    ConcurrentModel model = new ConcurrentModel();
    String view = controller.showHistory(model);

    assertEquals("cost_history", view);
    @SuppressWarnings("unchecked")
    List<CostSnapshot> snapshots = (List<CostSnapshot>) model.getAttribute("snapshots");
    assertNotNull(snapshots);
    assertTrue(snapshots.isEmpty());
  }
}

