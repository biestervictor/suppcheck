package org.example.suppcheck.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.example.suppcheck.dto.IngredientDto;
import org.example.suppcheck.dto.SupplementSaveDto;
import org.example.suppcheck.model.Supplement;
import org.junit.jupiter.api.Test;

class SupplementMapperTest {

  @Test
  void toEntity_mapsNestedIngredients() {
    IngredientDto sub = new IngredientDto();
    sub.setName("Sub");
    sub.setMg(44.0);

    IngredientDto ing = new IngredientDto();
    ing.setName("Main");
    ing.setMg(33.0);
    ing.setSubIngredients(List.of(sub));

    SupplementSaveDto dto = new SupplementSaveDto();
    dto.setId(" 123 ");
    dto.setShop("Bodylab24");
    dto.setName("TestSupp");
    dto.setPrice(12.88);
    dto.setPortionSize(2);
    dto.setSupplementType("BASIC");
    dto.setInactive(false);
    dto.setIngredients(List.of(ing));

    Supplement entity = SupplementMapper.toEntity(dto);

    assertEquals("123", entity.getId());
    assertEquals("Bodylab24", entity.getShop());
    assertEquals("TestSupp", entity.getName());
    assertEquals(12.88, entity.getPrice(), 0.00001);
    assertEquals(2, entity.getPortionSize());
    assertEquals("BASIC", entity.getSupplementType());
    assertFalse(entity.isInactive());

    assertNotNull(entity.getIngredients());
    assertEquals(1, entity.getIngredients().size());
    assertEquals("Main", entity.getIngredients().getFirst().getName());
    assertEquals(33.0, entity.getIngredients().getFirst().getMg(), 0.00001);

    assertNotNull(entity.getIngredients().getFirst().getSubIngredients());
    assertEquals(1, entity.getIngredients().getFirst().getSubIngredients().size());
    assertEquals("Sub", entity.getIngredients().getFirst().getSubIngredients().getFirst().getName());
    assertEquals(44.0, entity.getIngredients().getFirst().getSubIngredients().getFirst().getMg(), 0.00001);
  }
}

