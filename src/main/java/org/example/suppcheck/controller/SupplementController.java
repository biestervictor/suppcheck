package org.example.suppcheck.controller;

import java.util.*;

import org.example.suppcheck.dto.IngredientDto;
import org.example.suppcheck.dto.IngredientWithSources;
import org.example.suppcheck.dto.SupplementSaveDto;
import org.example.suppcheck.mapper.SupplementMapper;
import org.example.suppcheck.model.Ingredient;
import org.example.suppcheck.model.Shop;
import org.example.suppcheck.model.Supplement;
import org.example.suppcheck.model.SupplementType;
import org.example.suppcheck.service.DailyIntakeSnapshotService;
import org.example.suppcheck.service.OcrService;
import org.example.suppcheck.service.SupplementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller for the Supplement entity.
 */

@Controller
@RequestMapping("/supplements")
public class SupplementController {

    public static final String SHOPS = "shops";
    private static final String MODEL_SUPPLEMENT = "supplement";

    private final SupplementService supplementService;
    private final DailyIntakeSnapshotService snapshotService;
    private final OcrService ocrService;

    /**
     * Constructor for SupplementController.
     *
     * @param supplementService the SupplementService instance
     * @param snapshotService   the DailyIntakeSnapshotService instance
     * @param ocrService        the OcrService instance
     */
    @SuppressWarnings("java:S2384")
    public SupplementController(SupplementService supplementService,
                                DailyIntakeSnapshotService snapshotService,
                                OcrService ocrService) {
        this.supplementService = supplementService;
        this.snapshotService = snapshotService;
        this.ocrService = ocrService;
    }

    /**
     * Calculates the effective daily price for a supplement.
     * For non-daily supplements the per-portion cost is divided by the consumption interval.
     */
    private static double pricePerDay(Supplement supp) {
        double perPortion = supp.getPrice() / supp.getPortionSize();
        if (supp.isNonDaily() && supp.getConsumptionIntervalDays() > 1) {
            return perPortion / supp.getConsumptionIntervalDays();
        }
        return perPortion;
    }

    private void addFormAttributes(Model model) {        model.addAttribute("types", Arrays.stream(SupplementType.values())
                .map(SupplementType::name)
                .toList());
        model.addAttribute(SHOPS, Arrays.stream(Shop.values())
                .map(Shop::name)
                .toList());
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
        supplement.getIngredients().add(new Ingredient());
        model.addAttribute(MODEL_SUPPLEMENT, supplement);
        addFormAttributes(model);
        return "supplement_form";
    }

    /**
     * Shows the edit Supplement page.
     *
     * @param id    the id of the supplement to edit
     * @param model the model to add attributes to
     * @return the name of the view to render
     */
    @GetMapping("/edit/{id}")
    public String editSupplement(@PathVariable String id, Model model) {
        Supplement supplement = supplementService.getSupplementById(id)
                .orElseThrow(() -> new IllegalArgumentException("Supplement mit ID " + id + " nicht gefunden"));
        model.addAttribute(MODEL_SUPPLEMENT, supplement);
        addFormAttributes(model);
        return "supplement_form";
    }

    /**
     * Detailseite für Preisentwicklung eines Supplements.
     * Löst dabei auch einen Daily-Intake-Snapshot aus (idempotent: max. 1 pro Tag).
     */
    @GetMapping("/{id}/prices")
    public String showPriceHistory(@PathVariable String id, Model model) {
        Supplement supplement = supplementService.getSupplementById(id)
                .orElseThrow(() -> new IllegalArgumentException("Supplement mit ID " + id + " nicht gefunden"));
        model.addAttribute(MODEL_SUPPLEMENT, supplement);
        // Auto-Snapshot: speichert den aktuellen Intake-Stand (max. 1× pro Tag)
        snapshotService.saveSnapshot(supplementService.getAllSupplements());
        return "supplement_prices";
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


        if (!supp.isInactive() && supp.getSupplementType().equals(SupplementType.BASIC.name())) {
                preisProTag += pricePerDay(supp);
            } else if (!supp.isInactive() && supp.getSupplementType().equals(SupplementType.EXTENDED.name())) {
                preisProTagExtended += pricePerDay(supp);
            } else if (!supp.isInactive() && supp.getSupplementType().equals(SupplementType.WHEY.name())) {
                avgWheyPrice += pricePerDay(supp);
                wheyCount++;
            } else if (!supp.isInactive()) {
                preisWorkout += pricePerDay(supp);
            }


        }
        if (wheyCount == 0) wheyCount = 1; // Division durch 0 verhindern
        avgWheyPrice = avgWheyPrice / wheyCount;
        int daysMonth = 30;
        int dayWorkout = 15;
        // Whey an  normalen 2 Portionen, an Trainingstagen + 1 Portion also avg ist 2.5
        double preisProTagWhey = 2 * avgWheyPrice;
        double preisProMonat = preisProTag * daysMonth +
                (avgWheyPrice + preisWorkout) * dayWorkout +
                preisProTagExtended * daysMonth
                + 2 * preisProTagWhey;
        model.addAttribute("preisProMonat", preisProMonat);
        model.addAttribute("preisProTagExtended", preisProTagExtended);
        model.addAttribute("preisProTag", preisProTag);
        model.addAttribute("preisProTagWhey", preisProTagWhey);
        model.addAttribute("preisProWorkout", preisWorkout);
        model.addAttribute("supplements", supplements);
        model.addAttribute("types", Arrays.stream(SupplementType.values())
            .map(Enum::name).toList());
        model.addAttribute(SHOPS, Arrays.stream(Shop.values())
            .map(Enum::name).toList());

        return "supplements_list";
    }

    /**
     * Shows daily Intake with per-ingredient source breakdown.
     *
     * @param model the model to add attributes to
     * @return the name of the view to render
     */
    @GetMapping("/ingredients/summary")
    public String showIngredientsSummaryWithWorkout(@RequestParam(defaultValue = "false") boolean isWorkoutDay, Model model) {
        List<Supplement> supplements = supplementService.getAllSupplements();
        List<IngredientWithSources> summedIngredients =
                supplementService.getSummedIngredientsWithSources(supplements, isWorkoutDay);
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
        model.addAttribute("supplements", supplements);
        return "supplements_compare";
    }

    /**
     * Returns the union of all ingredient names from active WHEY supplements as
     * a JSON template (amounts set to 0).  Used by the form JS to pre-fill the
     * ingredient list when the user selects the WHEY type.
     *
     * @return list of ingredient DTOs with name populated and mg = 0
     */
    @GetMapping(value = "/api/whey-template", produces = "application/json")
    @ResponseBody
    public List<IngredientDto> getWheyTemplate() {
        return supplementService.getWheyIngredientTemplate();
    }

    /**
     * Accepts an image upload, runs Tesseract OCR, and returns the detected
     * ingredients as JSON so the form can auto-populate the ingredient rows.
     *
     * @param file the uploaded nutrition-label image
     * @return 200 with list of detected ingredients, or 500 on OCR failure
     */
    @PostMapping(value = "/ocr-extract", produces = "application/json")
    @ResponseBody
    public ResponseEntity<List<IngredientDto>> ocrExtract(
            @RequestParam("image") MultipartFile file) {
        try {
            List<IngredientDto> ingredients = ocrService.extractIngredients(file);
            return ResponseEntity.ok(ingredients);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

