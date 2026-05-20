package org.example.suppcheck.controller;

import java.util.*;
import java.time.LocalDate;

import org.example.suppcheck.dto.CheckResult;
import org.example.suppcheck.dto.IngredientDto;
import org.example.suppcheck.dto.IngredientWithSources;
import org.example.suppcheck.dto.OcrResult;
import org.example.suppcheck.dto.SupplementSaveDto;
import org.example.suppcheck.mapper.SupplementMapper;
import org.example.suppcheck.model.Ingredient;
import org.example.suppcheck.model.IngredientHistoryEntry;
import org.example.suppcheck.model.StockBatch;
import org.example.suppcheck.model.Supplement;
import org.example.suppcheck.model.SupplementType;
import org.example.suppcheck.service.DailyIntakeSnapshotService;
import org.example.suppcheck.service.CheckService;
import org.example.suppcheck.service.HerstellerService;
import org.example.suppcheck.service.OcrService;
import org.example.suppcheck.service.SupplementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

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
    private final CheckService checkService;
    private final HerstellerService herstellerService;

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
                                OcrService ocrService,
                                CheckService checkService,
                                HerstellerService herstellerService) {
        this.supplementService = supplementService;
        this.snapshotService = snapshotService;
        this.ocrService = ocrService;
        this.checkService = checkService;
        this.herstellerService = herstellerService;
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

    private void addFormAttributes(Model model) {
        model.addAttribute("types", Arrays.stream(SupplementType.values())
                 .map(SupplementType::name)
                 .toList());
        model.addAttribute(SHOPS, herstellerService.findAllNames());
        model.addAttribute("allSupplements", supplementService.getAllSupplements());
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
        // Vorgänger dieses Supplements (falls vorhanden) für die Formular-Vorauswahl
        supplementService.findVorgaenger(id).ifPresent(v -> model.addAttribute("vorgaenger", v));
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
        // Ingredient history in reverse chronological order (newest first)
        List<IngredientHistoryEntry> historyReversed = new ArrayList<>(
                supplement.getIngredientHistory() != null ? supplement.getIngredientHistory() : List.of());
        Collections.reverse(historyReversed);
        model.addAttribute("ingredientHistoryReversed", historyReversed);

        // ── Nachfolger (V2) ──────────────────────────────────────────────────
        if (supplement.getNachfolgerId() != null) {
            supplementService.getSupplementById(supplement.getNachfolgerId()).ifPresent(nachfolger -> {
                model.addAttribute("nachfolger", nachfolger);
                // Release-Datum = erstes Preis-Eintrag des Nachfolgers
                if (nachfolger.getPrices() != null && !nachfolger.getPrices().isEmpty()) {
                    model.addAttribute("nachfolgerReleaseDate",
                            nachfolger.getPrices().get(0).getDate().toString());
                }
                // Zutaten-Diff V1 vs V2
                supplementService.computeVersionDiff(supplement, nachfolger)
                        .ifPresent(diff -> model.addAttribute("v1v2Diff", diff));
            });
        }

        // ── Vorgänger (V1) ───────────────────────────────────────────────────
        supplementService.findVorgaenger(id).ifPresent(v -> model.addAttribute("vorgaenger", v));

        // Auto-Snapshot: speichert den aktuellen Intake-Stand (max. 1× pro Tag)
        snapshotService.saveSnapshot(supplementService.getAllSupplements());
        return "supplement_prices";
    }

    /**
     * Fügt einen historischen Preiseintrag für ein Supplement nachträglich ein.
     * Der Eintrag wird chronologisch sortiert eingebaut; ein Eintrag mit demselben
     * Datum wird ersetzt.
     */
    @PostMapping("/{id}/prices/add")
    public String addHistoricalPrice(
            @PathVariable String id,
            @RequestParam LocalDate date,
            @RequestParam double price,
            @RequestParam double ovp) {
        supplementService.addHistoricalPrice(id, date, price, ovp);
        return "redirect:/supplements/" + id + "/prices";
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
        model.addAttribute(SHOPS, herstellerService.findAllNames());
        Map<String, Supplement> supplementById = supplements.stream()
            .filter(s -> s.getId() != null)
            .collect(java.util.stream.Collectors.toMap(Supplement::getId, s -> s, (a, b) -> a));
        model.addAttribute("supplementById", supplementById);

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
     * @return a redirect to the supplements overview
     */
    @PostMapping("/save")
    public String saveSupplement(@ModelAttribute SupplementSaveDto supplementDto) {
        boolean isNew = supplementDto.getId() == null || supplementDto.getId().isBlank();
        Supplement supplement = SupplementMapper.toEntity(supplementDto);
        supplementService.saveSupplement(supplement);

        // Initialen Batch anlegen wenn:
        //   a) Neuanlage (isNew) und MongoDB hat eine ID vergeben, ODER
        //   b) Bearbeitung eines Supplements das noch keine Bestände hat
        boolean noBatchesYet = supplementDto.getStockBatches() == null || supplementDto.getStockBatches().isEmpty();
        String targetId = isNew ? supplement.getId() : supplementDto.getId();
        if (noBatchesYet && supplementDto.getInitialQty() > 0 && targetId != null && !targetId.isBlank()) {
            LocalDate mhd = (supplementDto.getInitialMhd() != null && !supplementDto.getInitialMhd().isBlank())
                    ? LocalDate.parse(supplementDto.getInitialMhd()) : null;
            String flavor = (supplementDto.getInitialFlavor() != null && !supplementDto.getInitialFlavor().isBlank())
                    ? supplementDto.getInitialFlavor().trim() : null;
            StockBatch batch = new StockBatch(flavor, mhd, LocalDate.now(), supplementDto.getInitialQty());
            supplementService.addStockBatch(targetId, batch);
        }

        // Vorgänger-Verknüpfung: wenn ein Vorgänger angegeben wurde, dessen
        // nachfolgerId auf die ID des gerade gespeicherten Supplements setzen.
        String vorgaengerId = supplementDto.getVorgaengerId();
        if (vorgaengerId != null && !vorgaengerId.isBlank() && supplement.getId() != null) {
            supplementService.setNachfolgerOf(vorgaengerId, supplement.getId());
        }

        return "redirect:/supplements";
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
     * Parses a raw ingredient text directly (no image/OCR needed).
     * Used when the user pastes label text into the "Text-Eingabe" tab.
     *
     * @param text the raw ingredient text
     * @return 200 with parsed ingredient list, or 500 on failure
     */
    @PostMapping(value = "/ocr/text", produces = "application/json")
    @ResponseBody
    public ResponseEntity<OcrResult> ocrText(@RequestParam String text) {
        try {
            OcrResult result = ocrService.parseText(text);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Accepts one or more image uploads, runs Tesseract OCR on each, and returns
     * the merged + deduplicated ingredient list as JSON so the form can
     * auto-populate the ingredient rows.
     *
     * <p>When multiple images are provided (e.g. a wide-angle shot and a
     * zoomed-in crop of the small-print section), OCR accuracy improves because
     * Tesseract performs better on larger characters.  Duplicate ingredient
     * names that appear in several images are silently collapsed into one entry.</p>
     *
     * @param files one or more uploaded nutrition-label images
     * @return 200 with merged ingredient list, or 500 on OCR failure
     */
    @PostMapping(value = "/ocr-extract", produces = "application/json")
    @ResponseBody
    public ResponseEntity<OcrResult> ocrExtract(
            @RequestParam("image") List<MultipartFile> files) {
        try {
            OcrResult result = ocrService.extractIngredients(files);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Runs OCR on the uploaded image(s) and compares the result against the
     * supplement's stored ingredient list.  Nothing is saved – the response is
     * purely informational so the UI can highlight discrepancies.
     *
     * @param id    the supplement to check
     * @param files one or more nutrition-label images
     * @return 200 with {@link CheckResult} JSON, or 500 on failure
     */
    @PostMapping(value = "/check/{id}", produces = "application/json")
    @ResponseBody
    public ResponseEntity<CheckResult> checkSupplement(
            @PathVariable String id,
            @RequestParam("image") List<MultipartFile> files) {
        try {
            Supplement supplement = supplementService.getSupplementById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Supplement mit ID " + id + " nicht gefunden"));
            OcrResult ocrResult = ocrService.extractIngredients(files);
            CheckResult result = checkService.compare(supplement, ocrResult);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Adjusts the stock of a supplement by delta (positive = add, negative = remove).
     * Stock is floored at 0.
     *
     * @param id    the supplement id
     * @param delta the amount to add or remove
     * @return JSON with the new stock value, e.g. {"stock": 3}
     */
    @PostMapping(value = "/{id}/stock", produces = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Integer>> adjustStock(
            @PathVariable String id,
            @RequestParam int delta) {
        try {
            int newStock = supplementService.adjustStock(id, delta);
            return ResponseEntity.ok(Map.of("stock", newStock));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * Fügt einen Restock-Batch hinzu (Flavor + MHD + Menge) und erhöht den Lagerbestand.
     *
     * @param id         die Supplement-ID
     * @param flavor     Geschmacksrichtung (optional)
     * @param expiryDate MHD als ISO-String yyyy-MM-dd (optional)
     * @param quantity   Anzahl Packungen/Portionen (min. 1)
     * @return JSON mit neuem Bestand, z.B. {"stock": 3}
     */
    @PostMapping(value = "/{id}/stock/batch", produces = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Integer>> addStockBatch(
            @PathVariable String id,
            @RequestParam(required = false) String flavor,
            @RequestParam(required = false) String expiryDate,
            @RequestParam(defaultValue = "1") int quantity,
            @RequestParam(defaultValue = "false") boolean inBenutzung) {
        try {
            LocalDate expiry = (expiryDate != null && !expiryDate.isBlank())
                    ? LocalDate.parse(expiryDate) : null;
            StockBatch batch = new StockBatch(
                    (flavor != null && !flavor.isBlank()) ? flavor : null,
                    expiry,
                    LocalDate.now(),
                    Math.max(1, quantity));
            batch.setInBenutzung(inBenutzung);
            int newStock = supplementService.addStockBatch(id, batch, inBenutzung);
            return ResponseEntity.ok(Map.of("stock", newStock));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * Gibt alle StockBatches eines Supplements zurück (für das Restock-Modal).
     *
     * @param id die Supplement-ID
     * @return JSON-Liste aller Batches (flavor, expiryDate, addedDate, quantity)
     */
    @GetMapping(value = "/{id}/stock/batches", produces = "application/json")
    @ResponseBody
    public ResponseEntity<List<StockBatch>> getStockBatches(@PathVariable String id) {
        return supplementService.getSupplementById(id)
                .<ResponseEntity<List<StockBatch>>>map(s -> ResponseEntity.ok(
                        s.getStockBatches() != null ? s.getStockBatches() : List.of()))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    /**
     * Entnimmt eine Menge aus einem bestimmten Batch (Flavor + MHD) und reduziert
     * sowohl den Batch-Restbestand als auch den Gesamt-Lagerbestand.
     *
     * @param id         die Supplement-ID
     * @param flavor     Flavor/Geschmacksrichtung (optional)
     * @param expiryDate MHD als ISO-String yyyy-MM-dd (optional)
     * @param quantity   Anzahl zu entnehmender Packungen/Portionen (min. 1)
     * @return JSON mit neuem Bestand, z.B. {"stock": 2}
     */
    @PostMapping(value = "/{id}/stock/consume", produces = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> consumeFromBatch(
            @PathVariable String id,
            @RequestParam(required = false) String flavor,
            @RequestParam(required = false) String expiryDate,
            @RequestParam(defaultValue = "1") int quantity) {
        try {
            Map<String, Object> result = supplementService.consumeFromBatch(id, flavor, expiryDate, quantity);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * Returns the full ingredient-change history for a supplement, newest last.
     *
     * @param id the supplement id
     * @return JSON list of {@link IngredientHistoryEntry}
     */
    @GetMapping(value = "/{id}/ingredient-history", produces = "application/json")
    @ResponseBody
    public ResponseEntity<List<IngredientHistoryEntry>> getIngredientHistory(
            @PathVariable String id) {
        return supplementService.getSupplementById(id)
                .map(s -> {
                    List<IngredientHistoryEntry> hist = s.getIngredientHistory() != null
                            ? s.getIngredientHistory()
                            : List.of();
                    return ResponseEntity.ok(hist);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    /**
     * Setzt den inBenutzung-Flag auf einem bestimmten Batch und löscht ihn global von allen anderen.
     *
     * @param id         die Supplement-ID
     * @param flavor     Flavor des Batches (optional)
     * @param expiryDate MHD des Batches als ISO-String yyyy-MM-dd (optional)
     * @return 200 OK bei Erfolg, 404 wenn Supplement oder Batch nicht gefunden
     */
    @PostMapping(value = "/{id}/stock/batch/setInBenutzung", produces = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> setInBenutzungBatch(
            @PathVariable String id,
            @RequestParam(required = false) String flavor,
            @RequestParam(required = false) String expiryDate) {
        try {
            supplementService.setInBenutzungBatch(id, flavor, expiryDate);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}


