package org.example.suppcheck.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.example.suppcheck.model.PriceEntry;
import org.example.suppcheck.model.Supplement;
import org.example.suppcheck.service.SupplementService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

class SupplementPriceHistoryPageTest {

  @Test
  void showPriceHistory_setsSupplementInModel_andReturnsViewName() {
    SupplementService supplementService = org.mockito.Mockito.mock(SupplementService.class);
    SupplementController controller = new SupplementController(supplementService);

    Supplement supp = new Supplement();
    supp.setId("id-1");
    supp.setShop("Bodylab24");
    supp.setName("TestSupp");

    PriceEntry p1 = new PriceEntry();
    p1.setDate(LocalDate.now().minusDays(2));
    p1.setPrice(10.0);

    PriceEntry p2 = new PriceEntry();
    p2.setDate(LocalDate.now().minusDays(1));
    p2.setPrice(11.0);

    supp.setPrices(List.of(p1, p2));

    when(supplementService.getSupplementById("id-1")).thenReturn(Optional.of(supp));

    ConcurrentModel model = new ConcurrentModel();
    String view = controller.showPriceHistory("id-1", model);

    assertEquals("supplement_prices", view);
    assertTrue(model.containsAttribute("supplement"));
    assertSame(supp, model.getAttribute("supplement"));
  }
}
