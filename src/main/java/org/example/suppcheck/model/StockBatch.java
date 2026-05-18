package org.example.suppcheck.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
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
@AllArgsConstructor
public class StockBatch {

    /** Flavor/Geschmacksrichtung – optional, null wenn nicht relevant. */
    private String flavor;

    /** Mindesthaltbarkeitsdatum – optional, als ISO-String "yyyy-MM-dd" serialisiert. */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate expiryDate;

    /** Datum, an dem dieser Batch hinzugefügt wurde, als ISO-String "yyyy-MM-dd". */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate addedDate;

    /** Anzahl Packungen/Portionen in diesem Batch. */
    private int quantity;
}
