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
    SupplementService service = new SupplementService(repo);

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

    service.saveSupplement(incomingUpdate);

    ArgumentCaptor<Supplement> captor = ArgumentCaptor.forClass(Supplement.class);
    verify(repo).save(captor.capture());

    Supplement saved = captor.getValue();
    assertNotNull(saved.getPrices());
    assertEquals(1, saved.getPrices().size(), "Migration should create exactly one price entry");

    PriceEntry entry = saved.getPrices().getFirst();
    assertEquals(LocalDate.now(), entry.getDate());
    assertEquals(12.34, entry.getPrice(), 0.00001);
  }

  @Test
  void saveSupplement_migratesExistingWithOldPriceWithoutPrices_toOneEntryWithToday() {
    SupplementRepository repo = mock(SupplementRepository.class);
    SupplementService service = new SupplementService(repo);

    // Existing DB document (legacy): no prices list
    Supplement existing = new Supplement();
    existing.setId("id-1");
    existing.setShop("Bodylab24");
    existing.setName("LegacySupp");
    existing.setPortionSize(10);
    existing.setSupplementType("BASIC");
    existing.setInactive(false);
    //During the Migration the date for the new entery should me 31.12.2025
    existing.setPrice(12.00);

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

    service.saveSupplement(incomingUpdate);

    ArgumentCaptor<Supplement> captor = ArgumentCaptor.forClass(Supplement.class);
    verify(repo).save(captor.capture());

    Supplement saved = captor.getValue();
    assertNotNull(saved.getPrices());
    assertEquals(2, saved.getPrices().size(), "Migration should create two price entry, becaue in the old dto was a price field and the service should create an entry for this price and then another for the new price");

    PriceEntry entry = saved.getPrices().getFirst();
    assertEquals(LocalDate.of(2025,12,31), entry.getDate());
    assertEquals(12.00, entry.getPrice(), 0.00001);
     entry = saved.getPrices().getLast();
    assertEquals(LocalDate.now(), entry.getDate());
    assertEquals(12.34, entry.getPrice(), 0.00001);
  }
  @Test
  void saveSupplement_onUpdateWithDifferentPrice_appendsNewEntryForToday() {
    SupplementRepository repo = mock(SupplementRepository.class);
    SupplementService service = new SupplementService(repo);

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

    service.saveSupplement(incomingUpdate);

    ArgumentCaptor<Supplement> captor = ArgumentCaptor.forClass(Supplement.class);
    verify(repo).save(captor.capture());

    Supplement saved = captor.getValue();
    assertNotNull(saved.getPrices());
    assertEquals(2, saved.getPrices().size());

    PriceEntry latest = saved.getPrices().getLast();
    assertEquals(LocalDate.now(), latest.getDate());
    assertEquals(11.0, latest.getPrice(), 0.00001);
  }

  @Test
  void saveSupplement_onUpdateWithSamePrice_doesNotAppendNewEntry() {
    SupplementRepository repo = mock(SupplementRepository.class);
    SupplementService service = new SupplementService(repo);

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

    service.saveSupplement(incomingUpdate);

    ArgumentCaptor<Supplement> captor = ArgumentCaptor.forClass(Supplement.class);
    verify(repo).save(captor.capture());

    Supplement saved = captor.getValue();
    assertNotNull(saved.getPrices());
    assertEquals(1, saved.getPrices().size(), "Price unchanged -> must not add a new entry");
  }

  @Test
  void saveSupplement_newSupplement_createsSingleEntryForToday() {
    SupplementRepository repo = mock(SupplementRepository.class);
    SupplementService service = new SupplementService(repo);

    when(repo.findById(any())).thenReturn(Optional.empty());

    Supplement supp = new Supplement();
    supp.setShop("Bodylab24");
    supp.setName("NewSupp");
    supp.setPortionSize(10);
    supp.setSupplementType("BASIC");
    supp.setInactive(false);
    supp.setPrice(20.0);

    service.saveSupplement(supp);

    ArgumentCaptor<Supplement> captor = ArgumentCaptor.forClass(Supplement.class);
    verify(repo).save(captor.capture());

    Supplement saved = captor.getValue();
    assertNotNull(saved.getPrices());
    assertEquals(1, saved.getPrices().size());
    assertEquals(LocalDate.now(), saved.getPrices().getFirst().getDate());
    assertEquals(20.0, saved.getPrices().getFirst().getPrice(), 0.00001);
  }
}
