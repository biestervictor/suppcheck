package org.example.suppcheck.controller;

import java.util.*;
import java.util.stream.Collectors;

import org.example.suppcheck.dto.SupplementSaveDto;
import org.example.suppcheck.mapper.SupplementMapper;
import org.example.suppcheck.model.Ingredient;
import org.example.suppcheck.model.Shop;
import org.example.suppcheck.model.Supplement;
import org.example.suppcheck.model.SupplementType;
import org.example.suppcheck.service.SupplementService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for the Supplement entity.
 */

@Controller
@RequestMapping("/supplements")
public class SupplementController {

    public static final String SHOPS = "shops";
    private final SupplementService supplementService;

    /**
     * Constructor for SupplementController.
     *
     * @param supplementService the SupplementService instance
     */
    @SuppressWarnings("java:S2384")
    public SupplementController(SupplementService supplementService) {
        this.supplementService = supplementService;
    }

    /**
     * Shows a Form to create a new Supplement.
     *
     * @param model the model to add attributes to
     * @return the name of the view to render
     */
    @GetMapping("/new")
    public String showCreateForm(Model model) {
        Supplement supplement = new Supplement();
        supplement.setIngredients(new ArrayList<>());
        Ingredient ingredient = new Ingredient();

        supplement.getIngredients().add(ingredient);
        model.addAttribute("supplement", supplement);
        List<String> types = Arrays.stream(SupplementType.values())
                .map(SupplementType::name)
                .toList();
        model.addAttribute("types", types);
        List<String> shops = Arrays.stream(Shop.values())
                .map(Shop::name)
                .toList();
        model.addAttribute(SHOPS, shops);
        return "supplement_form";
    }

    /**
     * Shows the edit Supplemtn page.
     *
     * @param name  the name of the supplement to edit
     * @param model the model to add attributes to
     * @return the name of the view to render
     */
    @GetMapping("/edit/{name}")
    public String editSupplement(@PathVariable String name, Model model) {
        Supplement supplement = supplementService.getSupplementById(name).orElseThrow();
        model.addAttribute("supplement", supplement);
        List<String> types = Arrays.stream(SupplementType.values())
                .map(SupplementType::name)
                .toList();
        model.addAttribute("types", types);

        List<String> shops = Arrays.stream(Shop.values())
                .map(Shop::name)
                .toList();
        model.addAttribute(SHOPS, shops);
        return "supplement_form";
    }

    /**
     * Delete a supplement.
     *
     * @param id the id of the supplement to delete
     * @return a redirect to the supplements list
     */
    @PostMapping("/delete/{id}")
    public String deleteSupplement(@PathVariable String id) {
        supplementService.deleteSupplementById(id);
        return "redirect:/supplements";
    }

    /**
     * Shows the all supplements.
     *
     * @param model the model to add attributes to
     * @return the name of the view to render
     */
    @GetMapping
    public String showSupplementsList(Model model) {
        List<Supplement> supplements = supplementService.getAllSupplements();

        double preisProTag = 0;
        double preisWorkout = 0;
        double preisProTagExtended = 0;
        double avgWheyPrice = 0;
        int wheyCount = 0;
        for (Supplement supp : supplements) {


             if( !supp.isInactive() && supp.getSupplementType().equals(SupplementType.BASIC.name())) {
                preisProTag += supp.getPrice() / supp.getPortionSize();
            }else  if( !supp.isInactive() && supp.getSupplementType().equals(SupplementType.EXTENDED.name())) {
                 preisProTagExtended += supp.getPrice() / supp.getPortionSize();
             }else  if( !supp.isInactive() && supp.getSupplementType().equals(SupplementType.WHEY.name())) {
                 avgWheyPrice += supp.getPrice() / supp.getPortionSize();
                 wheyCount++;
             }else if (!supp.isInactive()){
                preisWorkout += supp.getPrice() / supp.getPortionSize();
            }




        }
        if(wheyCount==0) wheyCount=1; // Division durch 0 verhindern
        avgWheyPrice= avgWheyPrice/wheyCount;
        int daysMonth = 30;
        int dayWorkout = 15;
        // Whey an  normalen 2 Portionen, an Trainingstagen + 1 Portion also avg ist 2.5
        double preisProTagWhey = 2* avgWheyPrice;
        double preisProMonat = preisProTag* daysMonth +
                (avgWheyPrice+preisWorkout)* dayWorkout +
                preisProTagExtended* daysMonth
                +2* preisProTagWhey;
        model.addAttribute("preisProMonat", preisProMonat);
        model.addAttribute("preisProTagExtended", preisProTagExtended);
        model.addAttribute("preisProTag", preisProTag);
        model.addAttribute("preisProTagWhey", preisProTagWhey);
        model.addAttribute("preisProWorkout", preisWorkout);
        model.addAttribute("supplements", supplements);

        return "supplements_list";
    }

    /**
     * Shows daily Intake.
     *
     * @param model the model to add attributes to
     * @return the name of the view to render
     */
    @GetMapping("/ingredients/summary")
    public String showIngredientsSummaryWithWorkout(@RequestParam(defaultValue = "false")boolean isWorkoutDay, Model model){
        List<Supplement> supplements = supplementService.getAllSupplements();
        List<Ingredient> summedIngredients = supplementService.getSummedIngredients(supplements,isWorkoutDay);
        model.addAttribute("summedIngredients", summedIngredients);
        model.addAttribute("isWorkoutDay", isWorkoutDay);
        return "ingredients_summary";
    }

    /**
     * Stores or edits supplements.
     *
     * @param supplementDto the supplement data to save
     * @return a redirect to the new supplement form with a success message
     */
    @PostMapping("/save")
    public String saveSupplement(@ModelAttribute SupplementSaveDto supplementDto) {
        Supplement supplement = SupplementMapper.toEntity(supplementDto);
        supplementService.saveSupplement(supplement);
        return "redirect:/supplements/new?success";
    }

    /**
     * Zeigt die Vergleichsseite für Supplements an.
     */
    @GetMapping("/compare")
    public String showComparePage(Model model) {
        List<Supplement> supplements = supplementService.getAllSupplements();
        // Zutaten jedes Supplements alphabetisch sortieren
        for (Supplement supp : supplements) {
            if (supp.getIngredients() != null) {
                supp.getIngredients().sort(Comparator.comparing(ing -> ing.getName() != null ? ing.getName().toLowerCase() : ""));
            }
        }
        // Extrahiere alle Shops und Namen für die Dropdowns
        Set<String> shops = supplements.stream().map(Supplement::getShop).collect(Collectors.toSet());
        Set<String> names = supplements.stream().map(Supplement::getName).collect(Collectors.toSet());
        model.addAttribute("supplements", supplements);
        model.addAttribute(SHOPS, shops);
        model.addAttribute("names", names);
        return "supplements_compare";
    }
}