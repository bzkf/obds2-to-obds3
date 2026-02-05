package io.github.bzkf.obds2toobds3;

import de.basisdatensatz.obds.v3.HistologieTyp;
import de.basisdatensatz.obds.v3.MorphologieICDOTyp;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HistologieMapper {
  private static final Logger LOG = LoggerFactory.getLogger(HistologieMapper.class);

  private HistologieMapper() {}

  public static Optional<HistologieTyp> map(List<de.basisdatensatz.obds.v2.HistologieTyp> source) {
    if (source == null || source.isEmpty()) {
      return Optional.empty();
    }

    var mappedHisto = new HistologieTyp();

    var byDateDesc =
        Comparator.comparing(
                (de.basisdatensatz.obds.v2.HistologieTyp h) ->
                    MapperUtils.mapDateString(h.getTumorHistologiedatum())
                        .map(d -> d.getValue().toGregorianCalendar())
                        .map(GregorianCalendar::toInstant)
                        .orElse(null),
                Comparator.nullsLast(Comparator.naturalOrder()))
            .reversed();

    var sorted = source.stream().sorted(byDateDesc).toList();

    for (var v2Histo : sorted) {
      if (v2Histo.getHistologieID() != null && mappedHisto.getHistologieID() == null) {
        mappedHisto.setHistologieID(v2Histo.getHistologieID());

        if (v2Histo.getHistologieEinsendeNr() != null) {
          mappedHisto.setHistologieEinsendeNr(v2Histo.getHistologieEinsendeNr());
          LOG.warn(
              "The Histologie_ID={} is set for the current element, but the EinsendeNr isn't. "
                  + "No EinsendeNr is mapped to avoid inconsistencies between the ID and the Nr",
              v2Histo.getHistologieID());
        }

        MapperUtils.mapDateString(v2Histo.getTumorHistologiedatum())
            .ifPresent(mappedHisto::setTumorHistologiedatum);
      }

      if (v2Histo.getSentinelLKUntersucht() != null
          && v2Histo.getSentinelLKBefallen() != null
          && mappedHisto.getSentinelLKUntersucht() == null
          && mappedHisto.getSentinelLKBefallen() == null) {
        mappedHisto.setSentinelLKUntersucht(v2Histo.getSentinelLKUntersucht());
        mappedHisto.setSentinelLKBefallen(v2Histo.getSentinelLKBefallen());
      }

      if (v2Histo.getLKUntersucht() != null
          && v2Histo.getLKBefallen() != null
          && mappedHisto.getLKUntersucht() == null
          && mappedHisto.getLKBefallen() == null) {
        mappedHisto.setLKUntersucht(v2Histo.getLKUntersucht());
        mappedHisto.setLKBefallen(v2Histo.getLKBefallen());
      }

      if (v2Histo.getMorphologieCode() != null) {
        var morphologieCode = new MorphologieICDOTyp();
        morphologieCode.setCode(v2Histo.getMorphologieCode());
        morphologieCode.setVersion(v2Histo.getMorphologieICDOVersion());
        // this may add duplicate ICDO3 codes, but oBDS-to-FHIR handles that
        mappedHisto.getMorphologieICDO().add(morphologieCode);
      }

      if (v2Histo.getMorphologieFreitext() != null
          && mappedHisto.getMorphologieFreitext() == null) {
        mappedHisto.setMorphologieFreitext(v2Histo.getMorphologieFreitext());
      }

      if (mappedHisto.getHistologieID() == null) {
        MapperUtils.mapDateString(v2Histo.getTumorHistologiedatum())
            .ifPresent(mappedHisto::setTumorHistologiedatum);
      }
    }

    var worstGrading =
        source.stream()
            .map(de.basisdatensatz.obds.v2.HistologieTyp::getGrading)
            .filter(Objects::nonNull)
            .max(Comparator.comparingInt(HistologieMapper::gradingSeverity));

    worstGrading.ifPresent(mappedHisto::setGrading);

    var hasB =
        source.stream()
            .map(de.basisdatensatz.obds.v2.HistologieTyp::getGrading)
            .anyMatch("B"::equals);

    var hasAnyNonB =
        source.stream()
            .map(de.basisdatensatz.obds.v2.HistologieTyp::getGrading)
            .filter(Objects::nonNull)
            .anyMatch(g -> !"B".equals(g));

    if (hasB && hasAnyNonB) {
      var gradings =
          source.stream()
              .map(de.basisdatensatz.obds.v2.HistologieTyp::getGrading)
              .filter(Objects::nonNull)
              .distinct()
              .sorted()
              .toList();

      LOG.warn("Histologie-Grading inconsistent: 'B' combined with {}", gradings);
    }

    return Optional.of(mappedHisto);
  }

  private static int gradingSeverity(String grading) {
    if (grading == null) {
      return Integer.MIN_VALUE;
    }

    return switch (grading) {
      case "4" -> 100; // Undifferenziert
      case "H" -> 90; // High grade (G3/G4)
      case "3" -> 80; // Schlecht differenziert
      case "M" -> 70; // Intermediate
      case "2" -> 60; // Mäßig differenziert
      case "L" -> 50; // Low grade
      case "1" -> 40; // Gut differenziert
      case "B" -> 30; // Borderline
      case "0" -> 20; // Melanom Konjunktiva
      case "X" -> 10; // Nicht bestimmbar
      case "U" -> 5; // Unbekannt
      case "T" -> 0; // Trifft nicht zu
      default -> -1;
    };
  }
}
