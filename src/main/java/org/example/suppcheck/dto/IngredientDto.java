package org.example.suppcheck.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
public class IngredientDto {

  private String name;
  private double mg;
  private List<IngredientDto> subIngredients = new ArrayList<>();

}
