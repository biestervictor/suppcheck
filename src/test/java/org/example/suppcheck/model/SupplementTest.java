package org.example.suppcheck.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class SupplementTest {

    // --- getPrice ---

    @Test
    void getPrice_noPrices_returnsZero() {
        Supplement supp = new Supplement();
        supp.setPrices(new ArrayList<>());

        assertEquals(0d, supp.getPrice());
    }

    @Test
    void getPrice_nullPrices_returnsZero() {
        Supplement supp = new Supplement();
        supp.setPrices(null);

        assertEquals(0d, supp.getPrice());
    }

    @Test
    void getPrice_returnsLatestEntry() {
        PriceEntry p1 = new PriceEntry();
        p1.setDate(LocalDate.of(2026, 1, 1));
        p1.setPrice(10.0);

        PriceEntry p2 = new PriceEntry();
        p2.setDate(LocalDate.of(2026, 3, 1));
        p2.setPrice(15.0);

        Supplement supp = new Supplement();
        supp.setPrices(new ArrayList<>(List.of(p1, p2)));

        assertEquals(15.0, supp.getPrice(), 0.001);
    }

    // --- getOvp ---

    @Test
    void getOvp_noPrices_returnsZero() {
        Supplement supp = new Supplement();
        supp.setPrices(new ArrayList<>());

        assertEquals(0d, supp.getOvp());
    }

    @Test
    void getOvp_nullPrices_returnsZero() {
        Supplement supp = new Supplement();
        supp.setPrices(null);

        assertEquals(0d, supp.getOvp());
    }

    @Test
    void getOvp_returnsLatestEntry() {
        PriceEntry p1 = new PriceEntry();
        p1.setOvp(30.0);

        PriceEntry p2 = new PriceEntry();
        p2.setOvp(40.0);

        Supplement supp = new Supplement();
        supp.setPrices(new ArrayList<>(List.of(p1, p2)));

        assertEquals(40.0, supp.getOvp(), 0.001);
    }

    // --- setPrice / setOvp -> transient fields ---

    @Test
    void setPrice_setsCurrentPrice() {
        Supplement supp = new Supplement();
        supp.setPrice(12.34);

        assertEquals(12.34, supp.getCurrentPrice(), 0.001);
        // prices list is still empty -> getPrice() returns 0
        assertEquals(0d, supp.getPrice());
    }

    @Test
    void setOvp_setsCurrentOvp() {
        Supplement supp = new Supplement();
        supp.setOvp(19.99);

        assertEquals(19.99, supp.getCurrentOvp(), 0.001);
        assertEquals(0d, supp.getOvp());
    }

    // --- Defaults ---

    @Test
    void defaults_areCorrect() {
        Supplement supp = new Supplement();

        assertEquals("BASIC", supp.getSupplementType());
        assertFalse(supp.isInactive());
        assertFalse(supp.isMhdProdukt());
        assertNull(supp.getDiscount());
        assertNotNull(supp.getIngredients());
        assertTrue(supp.getIngredients().isEmpty());
        assertNotNull(supp.getPrices());
        assertTrue(supp.getPrices().isEmpty());
        assertEquals(0, supp.getPortionSize());
    }
}

