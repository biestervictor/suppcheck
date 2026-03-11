package org.example.suppcheck.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import org.example.suppcheck.model.CostSnapshot;
import org.example.suppcheck.repository.CostSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CostSnapshotServiceTest {

  private CostSnapshotRepository repository;
  private CostSnapshotService service;

  @BeforeEach
  void setUp() {
    repository = mock(CostSnapshotRepository.class);
    service = new CostSnapshotService(repository);
  }

  @Test
  void saveSnapshot_persistsAllValues() {
    CostSnapshot saved = new CostSnapshot();
    saved.setId("snap-1");
    saved.setDate(LocalDate.now());
    saved.setPreisProTag(1.67);
    saved.setPreisProTagWhey(1.92);
    saved.setPreisProTagExtended(2.87);
    saved.setPreisProWorkout(2.32);
    saved.setPreisProMonat(189.19);

    when(repository.save(any(CostSnapshot.class))).thenReturn(saved);

    CostSnapshot result = service.saveSnapshot(1.67, 1.92, 2.87, 2.32, 189.19);

    assertNotNull(result);
    assertEquals(1.67, result.getPreisProTag(), 0.001);
    assertEquals(1.92, result.getPreisProTagWhey(), 0.001);
    assertEquals(2.87, result.getPreisProTagExtended(), 0.001);
    assertEquals(2.32, result.getPreisProWorkout(), 0.001);
    assertEquals(189.19, result.getPreisProMonat(), 0.001);
    verify(repository, times(1)).save(any(CostSnapshot.class));
  }

  @Test
  void saveSnapshot_setsCurrentDateAndMonth() {
    LocalDate today = LocalDate.now();
    String expectedMonth = today.getYear() + "-" + String.format("%02d", today.getMonthValue());

    when(repository.save(any(CostSnapshot.class))).thenAnswer(inv -> inv.getArgument(0));

    CostSnapshot result = service.saveSnapshot(1.0, 2.0, 3.0, 4.0, 100.0);

    assertEquals(today, result.getDate());
    assertEquals(expectedMonth, result.getMonth());
  }

  @Test
  void getAllSnapshots_delegatesToRepository() {
    CostSnapshot s1 = new CostSnapshot();
    s1.setDate(LocalDate.of(2026, 1, 1));
    CostSnapshot s2 = new CostSnapshot();
    s2.setDate(LocalDate.of(2026, 3, 1));

    when(repository.findAllByOrderByDateAsc()).thenReturn(List.of(s1, s2));

    List<CostSnapshot> result = service.getAllSnapshots();

    assertEquals(2, result.size());
    assertSame(s1, result.get(0));
    assertSame(s2, result.get(1));
  }

  @Test
  void getAllSnapshots_returnsEmptyListWhenNoneExist() {
    when(repository.findAllByOrderByDateAsc()).thenReturn(List.of());

    List<CostSnapshot> result = service.getAllSnapshots();

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }
}

