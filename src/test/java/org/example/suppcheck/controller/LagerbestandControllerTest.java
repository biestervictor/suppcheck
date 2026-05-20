package org.example.suppcheck.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.example.suppcheck.model.Supplement;
import org.example.suppcheck.service.SupplementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

class LagerbestandControllerTest {

    private SupplementService supplementService;
    private LagerbestandController controller;

    @BeforeEach
    void setUp() {
        supplementService = mock(SupplementService.class);
        controller = new LagerbestandController(supplementService);
    }

    @Test
    void showPage_returnsLagerbestandView() {
        when(supplementService.getAllSupplements()).thenReturn(List.of());

        ConcurrentModel model = new ConcurrentModel();
        String view = controller.showPage(model);

        assertEquals("lagerbestand", view);
    }

    @Test
    void showPage_addsSupplementsToModel() {
        Supplement s1 = new Supplement();
        s1.setName("Whey Pro");
        Supplement s2 = new Supplement();
        s2.setName("Creatine");
        when(supplementService.getAllSupplements()).thenReturn(List.of(s1, s2));

        ConcurrentModel model = new ConcurrentModel();
        controller.showPage(model);

        @SuppressWarnings("unchecked")
        List<Supplement> supps = (List<Supplement>) model.getAttribute("supplements");
        assertNotNull(supps);
        assertEquals(2, supps.size());
        assertEquals("Whey Pro", supps.get(0).getName());
    }

    @Test
    void showPage_callsGetAllSupplements() {
        when(supplementService.getAllSupplements()).thenReturn(List.of());

        controller.showPage(new ConcurrentModel());

        verify(supplementService, times(1)).getAllSupplements();
    }

    @Test
    void showPage_emptySupplementList_stillReturnsView() {
        when(supplementService.getAllSupplements()).thenReturn(List.of());

        ConcurrentModel model = new ConcurrentModel();
        String view = controller.showPage(model);

        assertEquals("lagerbestand", view);

        @SuppressWarnings("unchecked")
        List<Supplement> supps = (List<Supplement>) model.getAttribute("supplements");
        assertNotNull(supps);
        assertTrue(supps.isEmpty());
    }
}
