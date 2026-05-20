package org.example.suppcheck.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.example.suppcheck.model.PriceEntry;
import org.example.suppcheck.model.Supplement;
import org.example.suppcheck.repository.SupplementRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SupplementServicePriceHistoryTest {

  @Test
  void saveSupplement_migratesExistingWithoutPrices_toOneEntryWithToday() {
    SupplementRepository repo = mock(SupplementRepository.class);
    SupplementService service = new SupplementService(repo, new IngredientHistoryService());

    // Existing DB document (legacy): no prices list
    Supplement existing = new Supplement();
    existing.setId("id-1");
    existing.setShop("Bodylab24");
    existing.setName("LegacySupp");
    existing.setPortionSize(10);
    existing.setSupplementType("BASIC");
    existing.setInactive(false);


    // legacy state: prices not present
    existing.setPrices(null);

    when(repo.findById("id-1")).thenReturn(Optional.of(existing));

    Supplement incomingUpdate = new Supplement();
    incomingUpdate.setId("id-1");
    incomingUpdate.setShop("Bodylab24");
    incomingUpdate.setName("LegacySupp");
    incomingUpdate.setPortionSize(10);
    incomingUpdate.setSupplementType("BASIC");
    incomingUpdate.setInactive(false);
    incomingUpdate.setPrice(12.34);
    incomingUpdate.setOvp(19.99);

    service.saveSupplement(incomingUpdate);

    ArgumentCaptor<Supplement> captor = ArgumentCaptor.forClass(Supplement.class);
    verify(repo).save(captor.capture());

    Supplement saved = captor.getValue();
    assertNotNull(saved.getPrices());
    assertEquals(1, saved.getPrices().size(), "Migration should create exactly one price entry");

    PriceEntry entry = saved.getPrices().getFirst();
    assertEquals(LocalDate.now(), entry.getDate());
    assertEquals(12.34, entry.getPrice(), 0.00001);
    assertEquals(19.99, entry.getOvp(), 0.00001);
  }

  @Test
  void saveSupplement_onUpdateWithDifferentPrice_appendsNewEntryForToday() {
    SupplementRepository repo = mock(SupplementRepository.class);
    SupplementService service = new SupplementService(repo, new IngredientHistoryService());

    Supplement existing = new Supplement();
    existing.setId("id-2");
    existing.setShop("Bodylab24");
    existing.setName("Supp");
    existing.setPortionSize(10);
    existing.setSupplementType("BASIC");
    existing.setInactive(false);

    PriceEntry old = new PriceEntry();
    old.setDate(LocalDate.now().minusDays(1));
    old.setPrice(10.0);
    old.setOvp(25.0);
    existing.setPrices(new ArrayList<>(List.of(old)));

    when(repo.findById("id-2")).thenReturn(Optional.of(existing));

    Supplement incomingUpdate = new Supplement();
    incomingUpdate.setId("id-2");
    incomingUpdate.setShop("Bodylab24");
    incomingUpdate.setName("Supp");
    incomingUpdate.setPortionSize(10);
    incomingUpdate.setSupplementType("BASIC");
    incomingUpdate.setInactive(false);
    incomingUpdate.setPrice(11.0);
    incomingUpdate.setOvp(30.0);

    service.saveSupplement(incomingUpdate);

    ArgumentCaptor<Supplement> captor = ArgumentCaptor.forClass(Supplement.class);
    verify(repo).save(captor.capture());

    Supplement saved = captor.getValue();
    assertNotNull(saved.getPrices());
    assertEquals(2, saved.getPrices().size());

    PriceEntry latest = saved.getPrices().getLast();
    assertEquals(LocalDate.now(), latest.getDate());
    assertEquals(11.0, latest.getPrice(), 0.00001);
    assertEquals(30.0, latest.getOvp(), 0.00001);
  }

  @Test
  void saveSupplement_onUpdateWithSamePrice_doesNotAppendNewEntry() {
    SupplementRepository repo = mock(SupplementRepository.class);
    SupplementService service = new SupplementService(repo, new IngredientHistoryService());

    Supplement existing = new Supplement();
    existing.setId("id-3");
    existing.setShop("Bodylab24");
    existing.setName("Supp");
    existing.setPortionSize(10);
    existing.setSupplementType("BASIC");
    existing.setInactive(false);

    PriceEntry old = new PriceEntry();
    old.setDate(LocalDate.now().minusDays(1));
    old.setPrice(10.0);
    old.setOvp(25.0);
    existing.setPrices(new ArrayList<>(List.of(old)));

    when(repo.findById("id-3")).thenReturn(Optional.of(existing));

    Supplement incomingUpdate = new Supplement();
    incomingUpdate.setId("id-3");
    incomingUpdate.setShop("Bodylab24");
    incomingUpdate.setName("Supp");
    incomingUpdate.setPortionSize(10);
    incomingUpdate.setSupplementType("BASIC");
    incomingUpdate.setInactive(false);
    incomingUpdate.setPrice(10.0);
    incomingUpdate.setOvp(25.0);

    service.saveSupplement(incomingUpdate);

    ArgumentCaptor<Supplement> captor = ArgumentCaptor.forClass(Supplement.class);
    verify(repo).save(captor.capture());

    Supplement saved = captor.getValue();
    assertNotNull(saved.getPrices());
    assertEquals(1, saved.getPrices().size(), "Price+OVP unchanged -> must not add a new entry");
  }

  @Test
  void saveSupplement_newSupplement_createsSingleEntryForToday() {
    SupplementRepository repo = mock(SupplementRepository.class);
    SupplementService service = new SupplementService(repo, new IngredientHistoryService());

    when(repo.findById(any())).thenReturn(Optional.empty());

    Supplement supp = new Supplement();
    supp.setShop("Bodylab24");
    supp.setName("NewSupp");
    supp.setPortionSize(10);
    supp.setSupplementType("BASIC");
    supp.setInactive(false);
    supp.setPrice(20.0);
    supp.setOvp(35.0);

    service.saveSupplement(supp);

    ArgumentCaptor<Supplement> captor = ArgumentCaptor.forClass(Supplement.class);
    verify(repo).save(captor.capture());

    Supplement saved = captor.getValue();
    assertNotNull(saved.getPrices());
    assertEquals(1, saved.getPrices().size());
    assertEquals(LocalDate.now(), saved.getPrices().getFirst().getDate());
    assertEquals(20.0, saved.getPrices().getFirst().getPrice(), 0.00001);
    assertEquals(35.0, saved.getPrices().getFirst().getOvp(), 0.00001);
  }

  // ── addHistoricalPrice ────────────────────────────────────────────────────

  @Test
  void addHistoricalPrice_insertsEntryInChronologicalOrder() {
    SupplementRepository repo = mock(SupplementRepository.class);
    SupplementService service = new SupplementService(repo, new IngredientHistoryService());

    Supplement supp = new Supplement();
    supp.setId("id-h1");
    PriceEntry e1 = new PriceEntry(); e1.setDate(LocalDate.of(2026, 1, 1)); e1.setPrice(10.0); e1.setOvp(20.0);
    PriceEntry e3 = new PriceEntry(); e3.setDate(LocalDate.of(2026, 3, 1)); e3.setPrice(12.0); e3.setOvp(22.0);
    supp.setPrices(new ArrayList<>(List.of(e1, e3)));

    when(repo.findById("id-h1")).thenReturn(Optional.of(supp));

    service.addHistoricalPrice("id-h1", LocalDate.of(2026, 2, 1), 11.0, 21.0);

    ArgumentCaptor<Supplement> captor = ArgumentCaptor.forClass(Supplement.class);
    verify(repo).save(captor.capture());

    List<PriceEntry> prices = captor.getValue().getPrices();
    assertEquals(3, prices.size());
    assertEquals(LocalDate.of(2026, 1, 1), prices.get(0).getDate());
    assertEquals(LocalDate.of(2026, 2, 1), prices.get(1).getDate());
    assertEquals(11.0, prices.get(1).getPrice(), 0.00001);
    assertEquals(21.0, prices.get(1).getOvp(), 0.00001);
    assertEquals(LocalDate.of(2026, 3, 1), prices.get(2).getDate());
  }

  @Test
  void addHistoricalPrice_appendsEntryWhenNewest() {
    SupplementRepository repo = mock(SupplementRepository.class);
    SupplementService service = new SupplementService(repo, new IngredientHistoryService());

    Supplement supp = new Supplement();
    supp.setId("id-h2");
    PriceEntry e1 = new PriceEntry(); e1.setDate(LocalDate.of(2026, 1, 1)); e1.setPrice(10.0); e1.setOvp(20.0);
    supp.setPrices(new ArrayList<>(List.of(e1)));

    when(repo.findById("id-h2")).thenReturn(Optional.of(supp));

    service.addHistoricalPrice("id-h2", LocalDate.of(2026, 6, 1), 15.0, 25.0);

    ArgumentCaptor<Supplement> captor = ArgumentCaptor.forClass(Supplement.class);
    verify(repo).save(captor.capture());

    List<PriceEntry> prices = captor.getValue().getPrices();
    assertEquals(2, prices.size());
    assertEquals(LocalDate.of(2026, 6, 1), prices.get(1).getDate());
    assertEquals(15.0, prices.get(1).getPrice(), 0.00001);
  }

  @Test
  void addHistoricalPrice_insertsAtBeginningWhenOldest() {
    SupplementRepository repo = mock(SupplementRepository.class);
    SupplementService service = new SupplementService(repo, new IngredientHistoryService());

    Supplement supp = new Supplement();
    supp.setId("id-h3");
    PriceEntry e1 = new PriceEntry(); e1.setDate(LocalDate.of(2026, 6, 1)); e1.setPrice(15.0); e1.setOvp(25.0);
    supp.setPrices(new ArrayList<>(List.of(e1)));

    when(repo.findById("id-h3")).thenReturn(Optional.of(supp));

    service.addHistoricalPrice("id-h3", LocalDate.of(2025, 1, 1), 8.0, 18.0);

    ArgumentCaptor<Supplement> captor = ArgumentCaptor.forClass(Supplement.class);
    verify(repo).save(captor.capture());

    List<PriceEntry> prices = captor.getValue().getPrices();
    assertEquals(2, prices.size());
    assertEquals(LocalDate.of(2025, 1, 1), prices.get(0).getDate());
    assertEquals(8.0, prices.get(0).getPrice(), 0.00001);
  }

  @Test
  void addHistoricalPrice_replacesExistingEntryOnSameDate() {
    SupplementRepository repo = mock(SupplementRepository.class);
    SupplementService service = new SupplementService(repo, new IngredientHistoryService());

    Supplement supp = new Supplement();
    supp.setId("id-h4");
    PriceEntry e1 = new PriceEntry(); e1.setDate(LocalDate.of(2026, 3, 15)); e1.setPrice(10.0); e1.setOvp(20.0);
    supp.setPrices(new ArrayList<>(List.of(e1)));

    when(repo.findById("id-h4")).thenReturn(Optional.of(supp));

    service.addHistoricalPrice("id-h4", LocalDate.of(2026, 3, 15), 12.5, 22.0);

    ArgumentCaptor<Supplement> captor = ArgumentCaptor.forClass(Supplement.class);
    verify(repo).save(captor.capture());

    List<PriceEntry> prices = captor.getValue().getPrices();
    assertEquals(1, prices.size(), "Same date -> replace, not duplicate");
    assertEquals(12.5, prices.get(0).getPrice(), 0.00001);
    assertEquals(22.0, prices.get(0).getOvp(), 0.00001);
  }

  @Test
  void addHistoricalPrice_worksOnEmptyPricesList() {
    SupplementRepository repo = mock(SupplementRepository.class);
    SupplementService service = new SupplementService(repo, new IngredientHistoryService());

    Supplement supp = new Supplement();
    supp.setId("id-h5");
    supp.setPrices(new ArrayList<>());

    when(repo.findById("id-h5")).thenReturn(Optional.of(supp));

    service.addHistoricalPrice("id-h5", LocalDate.of(2025, 6, 1), 9.99, 14.99);

    ArgumentCaptor<Supplement> captor = ArgumentCaptor.forClass(Supplement.class);
    verify(repo).save(captor.capture());

    List<PriceEntry> prices = captor.getValue().getPrices();
    assertEquals(1, prices.size());
    assertEquals(9.99, prices.get(0).getPrice(), 0.00001);
  }

  @Test
  void addHistoricalPrice_throwsWhenSupplementNotFound() {
    SupplementRepository repo = mock(SupplementRepository.class);
    SupplementService service = new SupplementService(repo, new IngredientHistoryService());

    when(repo.findById("not-existing")).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class,
        () -> service.addHistoricalPrice("not-existing", LocalDate.now(), 10.0, 20.0));
  }
}
