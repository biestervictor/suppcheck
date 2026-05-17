package org.example.suppcheck.health.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

/**
 * Einzelne Körper-Messung aus Apple Health (Withings-Waage, VO2max, Blutdruck etc.).
 * Hochfrequente Metriken (Schritte, Herzfrequenz, Energie) werden in {@link HealthDailyMetric}
 * voraggregiert.
 */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = "health_metrics")
@CompoundIndexes({
    @CompoundIndex(name = "type_date_idx", def = "{'type': 1, 'date': 1}")
})
public class HealthMetric {

    @Id
    private String id;

    /** Kurzname, z.B. "BodyMass", "LeanBodyMass", "VO2Max". */
    private String type;

    /** Messdatum (aus startDate-Attribut). */
    private LocalDate date;

    /** Numerischer Wert in der von Apple Health gespeicherten Einheit. */
    private double value;

    /** Einheit, z.B. "kg", "%", "mL/min·kg". */
    private String unit;

    /** Quell-App / Gerät, z.B. "Withings". */
    private String sourceName;

    public HealthMetric(String type, LocalDate date, double value, String unit, String sourceName) {
        this.type = type;
        this.date = date;
        this.value = value;
        this.unit = unit;
        this.sourceName = sourceName;
    }
}
