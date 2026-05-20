package org.example.suppcheck.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.example.suppcheck.model.Hersteller;
import org.example.suppcheck.model.Shop;
import org.example.suppcheck.repository.HerstellerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link HerstellerService}.
 */
class HerstellerServiceTest {

    private HerstellerRepository repo;
    private HerstellerService service;

    @BeforeEach
    void setUp() {
        repo = mock(HerstellerRepository.class);
        service = new HerstellerService(repo);
    }

    // ── findAll / findAllNames ────────────────────────────────────────────────

    @Test
    void findAll_delegatesToRepository() {
        List<Hersteller> list = List.of(new Hersteller("ESN"), new Hersteller("Bodylab24"));
        when(repo.findAllByOrderByNameAsc()).thenReturn(list);

        assertEquals(list, service.findAll());
    }

    @Test
    void findAllNames_returnsNamesOnly() {
        when(repo.findAllByOrderByNameAsc()).thenReturn(
                List.of(new Hersteller("Bodylab24"), new Hersteller("ESN")));

        List<String> names = service.findAllNames();
        assertEquals(List.of("Bodylab24", "ESN"), names);
    }

    // ── add ───────────────────────────────────────────────────────────────────

    @Test
    void add_savesAndReturnsNewHersteller() {
        when(repo.findByName("MyProtein")).thenReturn(Optional.empty());
        Hersteller saved = new Hersteller("MyProtein");
        saved.setId("abc");
        when(repo.save(any())).thenReturn(saved);

        Hersteller result = service.add("MyProtein");
        assertEquals("MyProtein", result.getName());
        verify(repo).save(argThat(h -> "MyProtein".equals(h.getName())));
    }

    @Test
    void add_trimsWhitespace() {
        when(repo.findByName("Sinob")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Hersteller result = service.add("  Sinob  ");
        assertEquals("Sinob", result.getName());
    }

    @Test
    void add_throwsOnBlankName() {
        assertThrows(IllegalArgumentException.class, () -> service.add("  "));
        assertThrows(IllegalArgumentException.class, () -> service.add(""));
        assertThrows(IllegalArgumentException.class, () -> service.add(null));
        verify(repo, never()).save(any());
    }

    @Test
    void add_throwsIfNameAlreadyExists() {
        when(repo.findByName("ESN")).thenReturn(Optional.of(new Hersteller("ESN")));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> service.add("ESN"));
        assertTrue(ex.getMessage().contains("ESN"));
        verify(repo, never()).save(any());
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_callsRepositoryDeleteById() {
        service.delete("id-123");
        verify(repo).deleteById("id-123");
    }

    // ── seedFromShopEnum ──────────────────────────────────────────────────────

    @Test
    void seed_insertsAllEnumValuesWhenCollectionEmpty() {
        when(repo.count()).thenReturn(0L);

        service.seedFromShopEnum();

        int expectedCount = Shop.values().length;
        verify(repo, times(expectedCount)).save(any(Hersteller.class));
    }

    @Test
    void seed_doesNothingWhenCollectionNotEmpty() {
        when(repo.count()).thenReturn(5L);

        service.seedFromShopEnum();

        verify(repo, never()).save(any());
    }
}
