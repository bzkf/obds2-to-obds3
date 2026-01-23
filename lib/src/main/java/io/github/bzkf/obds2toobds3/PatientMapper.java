/*
 * This file is part of obds2-to-obds3
 *
 * Copyright (c) 2024 the original author or authors.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.github.bzkf.obds2toobds3;

import de.basisdatensatz.obds.v2.ADTGEKID;
import de.basisdatensatz.obds.v3.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PatientMapper {
  private static final Logger LOG = LoggerFactory.getLogger(PatientMapper.class);

  private final MeldungMapper meldungMapper;

  PatientMapper(boolean ignoreUnmappableMessages, boolean fixMissingId) {
    this.meldungMapper = new MeldungMapper(ignoreUnmappableMessages, fixMissingId);
  }

  public OBDS.MengePatient.Patient map(ADTGEKID.MengePatient.Patient source) {
    if (null == source) {
      throw new IllegalArgumentException("Source cannot be null");
    }

    var stammdaten = source.getPatientenStammdaten();
    assert stammdaten != null;

    var patient = new OBDS.MengePatient.Patient();
    // PatientenID in oBDS v2 in Stammdaten
    patient.setPatientID(stammdaten.getPatientID());
    patient.setAnmerkung(source.getAnmerkung());
    patient.setPatientenStammdaten(getMappedStammdaten(stammdaten));

    // Meldungen
    var mappedMeldungen =
        source.getMengeMeldung().getMeldung().stream()
            .map(m -> meldungMapper.map(m, source))
            .flatMap(List::stream)
            .toList();

    patient.setMengeMeldung(new OBDS.MengePatient.Patient.MengeMeldung());
    patient.getMengeMeldung().getMeldung().addAll(mappedMeldungen);

    return patient;
  }

  /** If */
  static boolean aktuellGueltigeAdresse(
      ADTGEKID.MengePatient.Patient.PatientenStammdaten.MengeAdresse.Adresse adresse) {
    if (null == adresse) {
      throw new IllegalArgumentException("Address should not be null at this point");
    }

    var start = MapperUtils.parseDate(adresse.getGueltigVon());
    var end = MapperUtils.parseDate(adresse.getGueltigBis());

    // Keine Angabe über Gültigkeit - Adresse unbegrenzt gültig
    if (adresse.getGueltigVon() == null && adresse.getGueltigBis() == null) {
      return true;
    }
    // Adresse gültig bis
    else if (adresse.getGueltigVon() == null && adresse.getGueltigBis() != null) {
      return end.isPresent() && end.get().isAfter(LocalDate.now());
    }
    // Adresse gültig ab
    else if (adresse.getGueltigVon() != null && adresse.getGueltigBis() == null) {
      return start.isPresent() && start.get().isBefore(LocalDate.now());
    }
    // Adresse gültig von bis
    else if (adresse.getGueltigVon() != null && adresse.getGueltigBis() != null) {
      return start.isPresent()
          && start.get().isBefore(LocalDate.now())
          && end.isPresent()
          && end.get().isAfter(LocalDate.now());
    }

    return false;
  }

  private static PatientenStammdatenMelderTyp getMappedStammdaten(
      ADTGEKID.MengePatient.Patient.PatientenStammdaten stammdaten) {
    // Stammdaten
    var mappedStammdaten = new PatientenStammdatenMelderTyp();

    // Erste aktuell gültige Adresse
    if (null != stammdaten.getMengeAdresse()) {
      stammdaten.getMengeAdresse().getAdresse().stream()
          .filter(Objects::nonNull)
          .filter(PatientMapper::aktuellGueltigeAdresse)
          .findFirst()
          .ifPresent(
              adresse -> {
                var mappedAdresse = new PatientenAdresseMelderTyp();
                mappedAdresse.setHausnummer(adresse.getPatientenHausnummer());
                mappedAdresse.setStrasse(adresse.getPatientenStrasse());
                mappedAdresse.setPLZ(adresse.getPatientenPLZ());
                mappedAdresse.setOrt(adresse.getPatientenOrt());
                mappedAdresse.setLand(adresse.getPatientenLand());
                mappedStammdaten.setAdresse(mappedAdresse);
              });
    } else {
      // Use empty address, if no address available in V2
      mappedStammdaten.setAdresse(new PatientenAdresseMelderTyp());
    }

    if (stammdaten.getKrankenkassenNr() != null && !stammdaten.getKrankenkassenNr().isBlank()) {
      // Stammdaten - Krankenkasse: In oBDS v2 keine Unterscheidung GKV/PKV
      // See:
      // https://www.krebsregister-sh.de/wp-content/uploads/2023/02/son_2023-02-01_KRSH_Ersatzkodes_Krankenkassennummer.pdf
      if (List.of("970000011", "970001001", "970100001", "970000022", "970000099")
          .contains(stammdaten.getKrankenkassenNr())) {
        var versichertendaten = new VersichertendatenSonstigeTyp();
        versichertendaten.setErsatzkode(stammdaten.getKrankenkassenNr());
        mappedStammdaten.setVersichertendatenSonstige(versichertendaten);
      } else if (null != stammdaten.getKrankenversichertenNr()
          && !stammdaten.getKrankenversichertenNr().isBlank()
          && stammdaten.getKrankenkassenNr().matches("16\\d{7}|950\\d{6}")) {
        var versichertendaten = new VersichertendatenPKVTyp();
        versichertendaten.setIKNR(stammdaten.getKrankenkassenNr());
        versichertendaten.setPKVVersichertennummer(stammdaten.getKrankenversichertenNr());
        mappedStammdaten.setVersichertendatenPKV(versichertendaten);
      } else {
        if (null == stammdaten.getKrankenversichertenNr()
            || !stammdaten.getKrankenversichertenNr().matches("[A-Z]\\d{9}")
            || !stammdaten.getKrankenkassenNr().matches("10\\d{7}")) {
          LOG.debug(
              "KrankenversichertenNr or KrankenkassenNr doesn't match the expected format. Defaulting to GKV element.");
        }

        // Andere Werte: Aktuell als GKV behandelt
        var versichertendatenGkv = new VersichertendatenGKVTyp();
        versichertendatenGkv.setIKNR(stammdaten.getKrankenkassenNr());
        versichertendatenGkv.setGKVVersichertennummer(stammdaten.getKrankenversichertenNr());
        mappedStammdaten.setVersichertendatenGKV(versichertendatenGkv);
      }
    }

    // Geburtsdatum
    var geburtsdatum = MapperUtils.mapDateString(stammdaten.getPatientenGeburtsdatum());
    assert geburtsdatum.isPresent();
    mappedStammdaten.setGeburtsdatum(geburtsdatum.get());

    // Geschlecht
    // oBDS v2 kennt noch "S", oBDS v3 jedoch "D" und "X" - daher "U" für unbekannt
    var geschlecht = stammdaten.getPatientenGeschlecht();
    if (geschlecht != null && List.of("M", "W").contains(geschlecht)) {
      mappedStammdaten.setGeschlecht(PatientenStammdatenMelderTyp.Geschlecht.fromValue(geschlecht));
    } else {
      mappedStammdaten.setGeschlecht(PatientenStammdatenMelderTyp.Geschlecht.U);
    }

    // Name(n)
    mappedStammdaten.setGeburtsname(stammdaten.getPatientenGeburtsname());
    // Namensvorsatz nicht in oBDS v2
    mappedStammdaten.setNamenszusatz(stammdaten.getPatientenNamenszusatz());
    mappedStammdaten.setTitel(stammdaten.getPatientenTitel());
    mappedStammdaten.setNachname(stammdaten.getPatientenNachname());
    mappedStammdaten.setVornamen(stammdaten.getPatientenVornamen());
    // Frühere Namen
    var mappedFruehereNamen = new PatientenStammdatenMelderTyp.MengeFruehererName();
    if (stammdaten.getMengeFruehererName() != null) {
      mappedFruehereNamen
          .getFruehererName()
          .addAll(stammdaten.getMengeFruehererName().getPatientenFruehererName());
      mappedStammdaten.setMengeFruehererName(mappedFruehereNamen);
    }

    return mappedStammdaten;
  }
}
