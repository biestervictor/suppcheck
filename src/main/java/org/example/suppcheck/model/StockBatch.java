package org.example.suppcheck.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Eine einzelne Restock-Einheit: wann wurde was (welcher Flavor, welches MHD) hinzugefügt.
 */
@Getter
@Setter
@NoArgsConstructor
public class StockBatch {

    /** Flavor/Geschmacksrichtung – optional, null wenn nicht relevant. */
    private String flavor;

    /** Mindesthaltbarkeitsdatum – optional, als ISO-String "yyyy-MM-dd" serialisiert. */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate expiryDate;

    /** Datum, an dem dieser Batch hinzugefügt wurde, als ISO-String "yyyy-MM-dd". */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate addedDate;

    /** Anzahl Packungen/Portionen in diesem Batch (unveränderter Original-Wert). */
    private int quantity;

    /**
     * Verbleibende Menge in diesem Batch.
     * {@code null} = Legacy-Batch (kein Tracking) → treat as {@code quantity}.
     */
    private Integer remaining;

    /**
     * True wenn dieser Batch/Flavor aktuell in Benutzung ist.
     * Global darf immer nur ein Batch dieses Flag tragen.
     */
    private boolean inBenutzung = false;

    /**
     * Erstellt einen neuen Batch; {@code remaining} wird auf {@code quantity} gesetzt.
     */
    public StockBatch(String flavor, LocalDate expiryDate, LocalDate addedDate, int quantity) {
        this.flavor = flavor;
        this.expiryDate = expiryDate;
        this.addedDate = addedDate;
        this.quantity = quantity;
        this.remaining = quantity;
    }
}
