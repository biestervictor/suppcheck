package org.example.suppcheck.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.example.suppcheck.dto.IngredientDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IngredientTranslationServiceTest {

    private IngredientTranslationService service;

    @BeforeEach
    void setUp() {
        service = new IngredientTranslationService();
    }

    // --- translate(): basic cases ---

    @Test
    void translate_null_returnsNull() {
        assertNull(service.translate(null));
    }

    @Test
    void translate_blank_returnsBlank() {
        assertEquals("   ", service.translate("   "));
    }

    @Test
    void translate_noMatch_returnsOriginal() {
        assertEquals("Magnesium", service.translate("Magnesium")); // same in both languages
        assertEquals("Kreatin",   service.translate("Kreatin"));   // already German
    }

    // --- Amino acids ---

    @Test
    void translate_lCitrullineMalate_translatesCompound() {
        assertEquals("L-Citrullin Malat", service.translate("L-Citrulline Malate"));
    }

    @Test
    void translate_lCitrulline_translates() {
        assertEquals("L-Citrullin", service.translate("L-Citrulline"));
    }

    @Test
    void translate_lIsoleucine_translates() {
        assertEquals("L-Isoleucin", service.translate("L-Isoleucine"));
    }

    @Test
    void translate_lLeucine_translates() {
        assertEquals("L-Leucin", service.translate("L-Leucine"));
    }

    @Test
    void translate_lValine_translates() {
        assertEquals("L-Valin", service.translate("L-Valine"));
    }

    @Test
    void translate_lGlutamine_translates() {
        assertEquals("L-Glutamin", service.translate("L-Glutamine"));
    }

    @Test
    void translate_lArginine_translates() {
        assertEquals("L-Arginin", service.translate("L-Arginine"));
    }

    @Test
    void translate_lCarnitine_translates() {
        assertEquals("L-Carnitin", service.translate("L-Carnitine"));
    }

    @Test
    void translate_lTheanine_translates() {
        assertEquals("L-Theanin", service.translate("L-Theanine"));
    }

    @Test
    void translate_betaAlanine_translates() {
        assertEquals("Beta-Alanin", service.translate("Beta-Alanine"));
    }

    @Test
    void translate_taurine_translates() {
        assertEquals("Taurin", service.translate("Taurine"));
    }

    @Test
    void translate_creatineMonohydrate_translatesCompound() {
        assertEquals("Kreatin Monohydrat", service.translate("Creatine Monohydrate"));
    }

    @Test
    void translate_creatine_translates() {
        assertEquals("Kreatin", service.translate("Creatine"));
    }

    // --- Minerals / vitamins ---

    @Test
    void translate_caffeine_translates() {
        assertEquals("Koffein", service.translate("Caffeine"));
    }

    @Test
    void translate_iodine_translates() {
        assertEquals("Jod", service.translate("Iodine"));
    }

    @Test
    void translate_iron_translates() {
        assertEquals("Eisen", service.translate("Iron"));
    }

    @Test
    void translate_zinc_translates() {
        assertEquals("Zink", service.translate("Zinc"));
    }

    @Test
    void translate_potassium_translates() {
        assertEquals("Kalium", service.translate("Potassium"));
    }

    @Test
    void translate_sodium_translates() {
        assertEquals("Natrium", service.translate("Sodium"));
    }

    @Test
    void translate_selenium_translates() {
        assertEquals("Selen", service.translate("Selenium"));
    }

    @Test
    void translate_copper_translates() {
        assertEquals("Kupfer", service.translate("Copper"));
    }

    @Test
    void translate_manganese_translates() {
        assertEquals("Mangan", service.translate("Manganese"));
    }

    @Test
    void translate_chromium_translates() {
        assertEquals("Chrom", service.translate("Chromium"));
    }

    @Test
    void translate_molybdenum_translates() {
        assertEquals("Molybdän", service.translate("Molybdenum"));
    }

    @Test
    void translate_thiamine_translates() {
        assertEquals("Thiamin", service.translate("Thiamine"));
    }

    @Test
    void translate_folate_translates() {
        assertEquals("Folat", service.translate("Folate"));
    }

    @Test
    void translate_folicAcid_translates() {
        assertEquals("Folsäure", service.translate("Folic Acid"));
    }

    // --- Macros ---

    @Test
    void translate_carbohydrates_translates() {
        assertEquals("Kohlenhydrate", service.translate("Carbohydrates"));
    }

    @Test
    void translate_fat_translates() {
        assertEquals("Fett", service.translate("Fat"));
    }

    @Test
    void translate_saturatedFat_translatesCompound() {
        assertEquals("gesättigte Fettsäuren", service.translate("Saturated Fat"));
    }

    @Test
    void translate_fiber_translates() {
        assertEquals("Ballaststoffe", service.translate("Fiber"));
    }

    @Test
    void translate_fibre_translates() {
        assertEquals("Ballaststoffe", service.translate("Fibre"));
    }

    @Test
    void translate_salt_translates() {
        assertEquals("Salz", service.translate("Salt"));
    }

    // --- Case-insensitive matching ---

    @Test
    void translate_caseInsensitive_lowercase() {
        assertEquals("Kreatin", service.translate("creatine"));
    }

    @Test
    void translate_caseInsensitive_mixed() {
        assertEquals("Taurin", service.translate("TAURINE"));
    }

    // --- Compound names with translation term as substring ---

    @Test
    void translate_compoundNameWithLCitrullineMalate_replacesCompoundFirst() {
        // Must replace "L-Citrulline Malate" as a whole, not "L-Citrulline" + " Malate" separately
        assertEquals("L-Citrullin Malat", service.translate("L-Citrulline Malate"));
    }

    @Test
    void translate_compoundNameWithCreatineMonohydrate_replacesCompoundFirst() {
        assertEquals("Kreatin Monohydrat", service.translate("Creatine Monohydrate"));
    }

    // --- Word-boundary safety: substring must not be replaced inside a word ---

    @Test
    void translate_fatInsideLongerWord_notReplaced() {
        // "Saturated Fat" → should match "Saturated Fat" → "gesättigte Fettsäuren"
        // but standalone "Fat" within "Fatty" must not be replaced
        String result = service.translate("Fatty Acids");
        // "Fat" is at start of "Fatty" — preceded/followed by letters → no replacement
        assertFalse(result.contains("Fett"), "Fat inside Fatty must not be replaced");
    }

    @Test
    void translate_ironInsideIronWort_notReplaced() {
        // "Iron" must not match inside "Ironwort"
        String result = service.translate("Ironwort 100 mg");
        assertFalse(result.contains("Eisen"), "Iron inside Ironwort must not be replaced");
    }

    // --- translateAll() ---

    @Test
    void translateAll_translatesTopLevelNames() {
        IngredientDto ing = dto("Taurine", 2000);
        List<IngredientDto> result = service.translateAll(new ArrayList<>(List.of(ing)));

        assertEquals("Taurin", result.getFirst().getName());
        assertEquals(2000.0, result.getFirst().getMg(), 0.001);
    }

    @Test
    void translateAll_translatesSubIngredientNames() {
        IngredientDto sub = dto("L-Leucine", 2100);
        IngredientDto parent = dto("BCAA", 6000);
        parent.getSubIngredients().add(sub);

        service.translateAll(new ArrayList<>(List.of(parent)));

        assertEquals("BCAA",     parent.getName());             // unchanged (no translation)
        assertEquals("L-Leucin", parent.getSubIngredients().getFirst().getName());
    }

    @Test
    void translateAll_emptyList_returnsEmpty() {
        assertTrue(service.translateAll(new ArrayList<>()).isEmpty());
    }

    @Test
    void translateAll_noMatchingNames_unchanged() {
        IngredientDto ing = dto("Magnesium", 75);
        service.translateAll(new ArrayList<>(List.of(ing)));
        assertEquals("Magnesium", ing.getName());
    }

    // --- Helper ---

    private static IngredientDto dto(String name, double mg) {
        IngredientDto d = new IngredientDto();
        d.setName(name);
        d.setMg(mg);
        return d;
    }
}
