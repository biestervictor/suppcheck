package org.example.suppcheck.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
public class SupplementSaveDto {

  private String id;
  private String shop;
  private String name;
  private List<IngredientDto> ingredients = new ArrayList<>();
  private boolean isInactive;
  private double price;
  private int portionSize;
  private String supplementType;

}
