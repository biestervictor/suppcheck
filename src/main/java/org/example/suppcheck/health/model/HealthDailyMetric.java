package org.example.suppcheck.health.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Voraggregierte tägliche Health-Statistiken.
 * Dokument-ID ist der ISO-Datumsstring ("yyyy-MM-dd"), damit genau ein Dokument pro Tag existiert.
 */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = "health_daily")
public class HealthDailyMetric {

    @Id
    private String date; // "yyyy-MM-dd"

    private long   steps;
    private double activeEnergyKcal;
    private double basalEnergyKcal;
    private double sleepHours;
    private double avgHeartRate;
    private double minHeartRate;
    private double maxHeartRate;
    private int    heartRateSamples;
    private double avgHrv;
    private int    hrvSamples;
    private double avgRespiratoryRate;
    private int    respiratorySamples;
    private double avgOxygenSaturation;
    private int    oxygenSamples;
    private double daylightMinutes;

    // ── Ernährung (täglich summiert) ────────────────────────────────────────
    private double dietaryKcal;
    private double dietaryProteinG;
    private double dietaryCarbsG;
    private double dietaryFatG;
    private double dietarySugarG;
    private double dietaryFiberG;
    private double dietaryWaterMl;

    public HealthDailyMetric(String date) { this.date = date; }

    // ── Merge-Helpers ────────────────────────────────────────────────────────

    public void addSteps(double val)         { this.steps += (long) val; }
    public void addActiveEnergy(double val)  { this.activeEnergyKcal += val; }
    public void addBasalEnergy(double val)   { this.basalEnergyKcal  += val; }
    public void addDaylight(double val)      { this.daylightMinutes  += val; }
    public void addDietaryKcal(double val)   { this.dietaryKcal      += val; }
    public void addDietaryProtein(double val){ this.dietaryProteinG  += val; }
    public void addDietaryCarbs(double val)  { this.dietaryCarbsG    += val; }
    public void addDietaryFat(double val)    { this.dietaryFatG      += val; }
    public void addDietarySugar(double val)  { this.dietarySugarG    += val; }
    public void addDietaryFiber(double val)  { this.dietaryFiberG    += val; }
    public void addDietaryWater(double val)  { this.dietaryWaterMl   += val; }

    public void addSleepMinutes(double minutes) { this.sleepHours += minutes / 60.0; }

    public void addHeartRate(double val) {
        avgHeartRate = (avgHeartRate * heartRateSamples + val) / (heartRateSamples + 1);
        if (heartRateSamples == 0 || val < minHeartRate) minHeartRate = val;
        if (val > maxHeartRate) maxHeartRate = val;
        heartRateSamples++;
    }

    public void addHrv(double val) {
        avgHrv = (avgHrv * hrvSamples + val) / (hrvSamples + 1);
        hrvSamples++;
    }

    public void addRespiratoryRate(double val) {
        avgRespiratoryRate = (avgRespiratoryRate * respiratorySamples + val) / (respiratorySamples + 1);
        respiratorySamples++;
    }

    public void addOxygenSaturation(double val) {
        avgOxygenSaturation = (avgOxygenSaturation * oxygenSamples + val) / (oxygenSamples + 1);
        oxygenSamples++;
    }
}
