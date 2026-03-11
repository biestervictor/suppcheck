package org.example.suppcheck.service;

import java.time.LocalDate;
import java.util.List;
import org.example.suppcheck.model.CostSnapshot;
import org.example.suppcheck.repository.CostSnapshotRepository;
import org.springframework.stereotype.Service;

/**
 * Service zum Speichern und Abrufen von {@link CostSnapshot}-Einträgen.
 */
@Service
public class CostSnapshotService {

  private final CostSnapshotRepository costSnapshotRepository;

  public CostSnapshotService(CostSnapshotRepository costSnapshotRepository) {
    this.costSnapshotRepository = costSnapshotRepository;
  }

  /**
   * Speichert einen neuen Kosten-Snapshot für das aktuelle Datum.
   *
   * @param preisProTag          Preis pro Tag (BASIC-Supplements)
   * @param preisProTagWhey      Preis pro Tag Whey (2,5 Portionen)
   * @param preisProTagExtended  Preis pro Tag Extended Supplements
   * @param preisProWorkout      Preis pro Workout-Tag
   * @param preisProMonat        Gesamtpreis pro Monat
   * @return der gespeicherte Snapshot
   */
  public CostSnapshot saveSnapshot(double preisProTag,
                                   double preisProTagWhey,
                                   double preisProTagExtended,
                                   double preisProWorkout,
                                   double preisProMonat) {
    CostSnapshot snapshot = new CostSnapshot();
    LocalDate today = LocalDate.now();
    snapshot.setDate(today);
    snapshot.setMonth(today.getYear() + "-" + String.format("%02d", today.getMonthValue()));
    snapshot.setPreisProTag(preisProTag);
    snapshot.setPreisProTagWhey(preisProTagWhey);
    snapshot.setPreisProTagExtended(preisProTagExtended);
    snapshot.setPreisProWorkout(preisProWorkout);
    snapshot.setPreisProMonat(preisProMonat);
    return costSnapshotRepository.save(snapshot);
  }

  /**
   * Gibt alle gespeicherten Snapshots chronologisch sortiert zurück.
   */
  public List<CostSnapshot> getAllSnapshots() {
    return costSnapshotRepository.findAllByOrderByDateAsc();
  }
}

