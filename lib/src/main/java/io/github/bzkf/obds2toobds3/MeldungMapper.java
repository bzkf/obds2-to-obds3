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

import com.fasterxml.jackson.core.JsonProcessingException;
import de.basisdatensatz.obds.v2.ADTGEKID;
import de.basisdatensatz.obds.v3.*;
import de.basisdatensatz.obds.v3.DiagnoseTyp.MengeFruehereTumorerkrankung.FruehereTumorerkrankung;
import de.basisdatensatz.obds.v3.OBDS.MengePatient.Patient.MengeMeldung.Meldung;
import java.util.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class MeldungMapper {

  private static final Logger LOG = LoggerFactory.getLogger(MeldungMapper.class);

  private static final String MUST_NOT_BE_NULL = "ADT_GEKID must not be null at this point";
  private static final String DIAGNOSE_SHOULD_NOT_BE_NULL =
      "ADT_GEKID diagnose should not be null at this point";
  private static final String DIAGNOSESICHERUNG_MUST_NOT_BE_NULL =
      "ADT_GEKID diagnosesicherung must not be null at this point";
  private static final String TUMORZUORDUNG_SHOULD_NOT_BE_NULL =
      "ADT_GEKID tumorzuordung should not be null at this point - required for oBDS v3";
  private static final String TUMORID_MUST_NOT_BE_NULL =
      "ADT_GEKID attribute 'Tumor_ID' must not be null at this point - required for oBDS v3";

  private final boolean ignoreUnmappableMessages;
  private final boolean fixMissingId;

  MeldungMapper(boolean ignoreUnmappableMessages, boolean fixMissingId) {
    this.ignoreUnmappableMessages = ignoreUnmappableMessages;
    this.fixMissingId = fixMissingId;
  }

  /**
   * Mappe eine ADT_GEKID oBDS v2 Meldung in eine Liste von oBDS v3 Meldungen
   *
   * @param source The source message
   * @return
   */
  public List<Meldung> map(ADTGEKID.MengePatient.Patient.MengeMeldung.Meldung source) {
    if (null == source) {
      throw new IllegalArgumentException(MUST_NOT_BE_NULL);
    }

    if (null == source.getMeldungID() || source.getMeldungID().isBlank()) {
      LOG.warn("Meldung ID is unset or blank. Replacing with hash value of the content.");
      try {
        var meldungXml = ObdsMapper.XML_MAPPER.writeValueAsString(source);
        // Meldung_ID can be 50 chars max
        var digest = DigestUtils.sha256Hex(meldungXml).substring(0, 49);
        source.setMeldungID(digest);
      } catch (JsonProcessingException e) {
        throw new IllegalArgumentException(e);
      }
    }

    MDC.put("meldungId", source.getMeldungID());

    if (null == source.getTumorzuordnung()
        && (null == source.getDiagnose()
            || null == source.getDiagnose().getPrimaertumorICDCode()
            || null == source.getDiagnose().getPrimaertumorICDVersion())) {

      LOG.warn("Meldung has incomplete Diagnose data: ICD_Code, ICD_Version may be null.");

      if (ignoreUnmappableMessages) {
        return new ArrayList<>();
      }
      throw new UnmappableItemException(TUMORZUORDUNG_SHOULD_NOT_BE_NULL);
    }

    var result = new ArrayList<Meldung>();

    // im Falle von externen Meldungen wie Tumorkonferenzen existiert
    // kein Diagnose-Element.
    if (null != source.getDiagnose()) {
      if ("histologie_zytologie".equals(source.getMeldeanlass())) {
        var pathomeldung = getMeldungPathologie(source);
        // Füge Zusatzitems zur Diagnosemeldung hinzu, wenn vorhanden und nicht
        // "Einsender"
        getMengeZusatzitemTyp(source)
            .ifPresent(
                zusatzitems -> {
                  var filtered =
                      zusatzitems.getZusatzitem().stream()
                          .filter(zusatzitem -> !zusatzitem.getArt().startsWith("Einsender"))
                          .toList();
                  var mengeZusatzitemTyp = new MengeZusatzitemTyp();
                  mengeZusatzitemTyp.getZusatzitem().addAll(filtered);
                  pathomeldung.setMengeZusatzitem(mengeZusatzitemTyp);
                });
        result.add(pathomeldung);
      } else {
        try {
          // Diagnose als einzelne Meldung
          var diagnosemeldung = getMeldungDiagnose(source);
          // Füge Zusatzitems zur Diagnosemeldung hinzu, wenn vorhanden
          getMengeZusatzitemTyp(source).ifPresent(diagnosemeldung::setMengeZusatzitem);
          result.add(diagnosemeldung);
        } catch (UnmappableItemException e) {
          LOG.error("Failed to map Diagnosemeldung", e);
          if (!ignoreUnmappableMessages) {
            throw e;
          }
        }
      }
    }

    // SYSTs als einzelne Meldungen
    result.addAll(getMappedSYSTs(source));
    // STs als einzelne Meldung
    result.addAll(getMappedSTs(source));
    // OPs als einzelne Meldung
    result.addAll(getMappedOPs(source));
    // Tumorkonferenzen als einzelne Meldung
    result.addAll(getMappedTumorkonferenzen(source));
    // Verlauf - Ohne: oOBDS v2 Verlauf - Tod
    result.addAll(getMappedVerlauf(source));
    // Verlauf - Hier: oBDS v2 Verlauf - Tod
    getMeldungTod(source).ifPresent(result::add);

    // Für jede Meldung: Ergänze Tumorzuordnung aus Diagnose, wenn möglich und noch
    // nicht vorhanden
    result.forEach(
        meldung -> {
          if (null == meldung.getTumorzuordnung()) {
            try {
              getMappedTumorzuordung(source).ifPresent(meldung::setTumorzuordnung);
            } catch (UnmappableItemException e) {
              LOG.error("Failed to map Tumorzuordnung", e);
              if (!ignoreUnmappableMessages) {
                throw e;
              }
            }
          }
        });

    if (result.isEmpty()) {
      LOG.warn("Couldn't map any Meldung for source. Adding empty Meldung with Tumorzuordnung.");
      var meldung = getMeldungsRumpf(source);
      meldung.setMeldungID(source.getMeldungID());
      meldung.setMelderID(source.getMelderID());
      getMappedTumorzuordung(source).ifPresent(meldung::setTumorzuordnung);
      result.add(meldung);
    }

    MDC.remove("meldungId");

    return result.stream()
        .filter(
            meldung ->
                !ignoreUnmappableMessages
                    || (null != meldung.getTumorzuordnung()
                        && null != meldung.getTumorzuordnung().getTumorID()))
        .toList();
  }

  private Optional<TumorzuordnungTyp> getMappedTumorzuordung(
      ADTGEKID.MengePatient.Patient.MengeMeldung.Meldung meldung) {
    if (null == meldung) {
      return Optional.empty();
    }

    var source = meldung.getDiagnose();

    if (null == source
        || null == source.getDiagnosedatum()
        || null == source.getPrimaertumorICDCode()
        || null == source.getPrimaertumorICDVersion()) {
      LOG.warn("Tumorzuordnung Diagnosedatum, ICD_Code or ICD_Version are null");
      return Optional.empty();
    }

    var mappedTumorzuordnung = new TumorzuordnungTyp();
    // Übernehme Tumor-ID, wenn vorhanden - ansonsten generiere ID, wenn verlangt
    if (this.fixMissingId
        && null == source.getTumorID()
        && null != source.getDiagnosedatum()
        && null != source.getPrimaertumorICDCode()
        && null != meldung.getMeldungID()) {
      var generatedTumorId =
          DigestUtils.sha1Hex(
                  String.format(
                      "%s_%s_%s",
                      source.getDiagnosedatum(),
                      source.getPrimaertumorICDCode(),
                      meldung.getMeldungID()))
              .subSequence(0, 16);
      mappedTumorzuordnung.setTumorID(String.format("TID_%s", generatedTumorId));
    } else if (null != source.getTumorID()) {
      mappedTumorzuordnung.setTumorID(source.getTumorID());
    } else {
      LOG.error("TumorID is null");
      throw new UnmappableItemException(TUMORID_MUST_NOT_BE_NULL);
    }
    // Datum
    MapperUtils.mapDateString(source.getDiagnosedatum())
        .ifPresent(mappedTumorzuordnung::setDiagnosedatum);
    // ICD10
    var icd10 = new TumorICDTyp();
    icd10.setCode(source.getPrimaertumorICDCode());
    icd10.setVersion(source.getPrimaertumorICDVersion());
    mappedTumorzuordnung.setPrimaertumorICD(icd10);
    // Morphologie nicht in oBDS v2 ?
    // Seitenlokalisation: mapping über Enum - beide {'L'|'R'|'B'|'M'|'U'|'T'}
    if (null != source.getSeitenlokalisation()) {
      mappedTumorzuordnung.setSeitenlokalisation(
          SeitenlokalisationTyp.fromValue(source.getSeitenlokalisation().value()));
    }

    return Optional.of(mappedTumorzuordnung);
  }

  private List<Meldung> getMappedSYSTs(ADTGEKID.MengePatient.Patient.MengeMeldung.Meldung source) {
    var mengeSYST = source.getMengeSYST();
    if (mengeSYST == null || mengeSYST.getSYST().isEmpty()) {
      return List.of();
    }

    var mappedSyst = SystemtherapieMapper.map(source.getMengeSYST(), source.getMeldeanlass());

    return mappedSyst.stream()
        .map(
            mappedSysTyp -> {
              var meldung = getMeldungsRumpf(source);
              meldung.setMeldungID(
                  String.format("%s_%s", source.getMeldungID(), mappedSysTyp.getSYSTID()));
              meldung.setSYST(mappedSysTyp);
              return meldung;
            })
        .toList();
  }

  private List<Meldung> getMappedOPs(ADTGEKID.MengePatient.Patient.MengeMeldung.Meldung source) {
    var mengeOP = source.getMengeOP();
    if (mengeOP == null || mengeOP.getOP().isEmpty()) {
      return List.of();
    }

    var mappedOps = OpMapper.map(source.getMengeOP());

    return mappedOps.stream()
        .map(
            mappedOpTyp -> {
              var meldung = getMeldungsRumpf(source);
              meldung.setMeldungID(
                  String.format("%s_%s", source.getMeldungID(), mappedOpTyp.getOPID()));
              meldung.setOP(mappedOpTyp);
              return meldung;
            })
        .toList();
  }

  private List<Meldung> getMappedSTs(ADTGEKID.MengePatient.Patient.MengeMeldung.Meldung source) {
    var mengeOP = source.getMengeST();
    if (mengeOP == null || mengeOP.getST().isEmpty()) {
      return List.of();
    }

    var mappedSTs = StrahlentherapieMapper.map(source.getMengeST(), source.getMeldeanlass());

    return mappedSTs.stream()
        .map(
            mappedSTTyp -> {
              var meldung = getMeldungsRumpf(source);
              meldung.setMeldungID(
                  String.format("%s_%s", source.getMeldungID(), mappedSTTyp.getSTID()));
              meldung.setST(mappedSTTyp);
              return meldung;
            })
        .toList();
  }

  /**
   * Liefert ein Optional mit MengeZusatzitems, wenn im oBDS v2 enthalten und nicht leer
   *
   * @param source
   * @return
   */
  private static Optional<MengeZusatzitemTyp> getMengeZusatzitemTyp(
      ADTGEKID.MengePatient.Patient.MengeMeldung.Meldung source) {
    if (null == source) {
      throw new IllegalArgumentException(MUST_NOT_BE_NULL);
    }

    var mengeZusatzitem = source.getMengeZusatzitem();
    if (null == mengeZusatzitem) {
      return Optional.empty();
    }

    var mappedZusatzitems =
        mengeZusatzitem.getZusatzitem().stream()
            .map(
                zusatzitem -> {
                  var mappedZusatzitem = new MengeZusatzitemTyp.Zusatzitem();
                  mappedZusatzitem.setArt(
                      null != zusatzitem.getArt() ? zusatzitem.getArt().trim() : null);
                  // Nur, wenn in oBDSv2 vorhanden und mappbar
                  MapperUtils.mapDateString(zusatzitem.getDatum())
                      .ifPresent(mappedZusatzitem::setDatum);
                  mappedZusatzitem.setBemerkung(
                      null != zusatzitem.getBemerkung() ? zusatzitem.getBemerkung().trim() : null);
                  mappedZusatzitem.setWert(
                      null != zusatzitem.getWert() ? zusatzitem.getWert().trim() : null);
                  return mappedZusatzitem;
                })
            .toList();

    if (mappedZusatzitems.isEmpty()) {
      return Optional.empty();
    }

    var result = new MengeZusatzitemTyp();
    result.getZusatzitem().addAll(mappedZusatzitems);
    return Optional.of(result);
  }

  /**
   * Liefert eine Meldung mit Item "tod", wenn im oBDS v2-Verlauf eine Meldung "tod" enthalten ist
   * Die ID der Meldung hat den Suffix "__D"
   *
   * @param source
   * @return
   */
  private Optional<Meldung> getMeldungTod(
      ADTGEKID.MengePatient.Patient.MengeMeldung.Meldung source) {
    if (null == source) {
      throw new IllegalArgumentException(MUST_NOT_BE_NULL);
    }

    var meldung = getMeldungsRumpf(source);
    meldung.setMeldungID(String.format("%s__D", source.getMeldungID()));

    var mengeVerlauf = source.getMengeVerlauf();
    // it's also possible for the Verlauf to be empty but the meldeanlass to be
    // "tod"
    if (null == mengeVerlauf || mengeVerlauf.getVerlauf().isEmpty()) {
      if (source.getMeldeanlass().equalsIgnoreCase("tod")) {
        LOG.info(
            "The verlauf for this Meldung with Meldeanlass=tod is unset. "
                + "Assuming a death report with no further information.");
        var tod = new TodTyp();
        tod.setTodTumorbedingt(JNU.U);
        meldung.setTod(tod);
        return Optional.of(meldung);
      }

      return Optional.empty();
    }

    var mengeTod =
        mengeVerlauf.getVerlauf().stream()
            .map(ADTGEKID.MengePatient.Patient.MengeMeldung.Meldung.MengeVerlauf.Verlauf::getTod)
            .filter(Objects::nonNull)
            .map(
                verlaufTod -> {
                  var tod = new TodTyp();
                  // Nicht in oBDS v2?
                  // tod.setAbschlussID();
                  if (verlaufTod.getTodTumorbedingt() != null) {
                    tod.setTodTumorbedingt(JNU.fromValue(verlaufTod.getTodTumorbedingt().value()));
                  }

                  // Sterbedatum
                  MapperUtils.mapDateString(verlaufTod.getSterbedatum())
                      .ifPresent(datum -> tod.setSterbedatum(datum.getValue()));
                  // Nicht in oBDS v2?
                  // tod.setModulDKKR(..);
                  if (null != verlaufTod.getMengeTodesursache()
                      && null != verlaufTod.getMengeTodesursache().getTodesursacheICD()) {
                    var mengeTodesursachen = new TodTyp.MengeTodesursachen();
                    mengeTodesursachen
                        .getTodesursacheICD()
                        .addAll(
                            verlaufTod.getMengeTodesursache().getTodesursacheICD().stream()
                                .map(
                                    icd10 -> {
                                      var result = new AllgemeinICDTyp();
                                      result.setCode(icd10);
                                      result.setVersion(
                                          verlaufTod
                                              .getMengeTodesursache()
                                              .getTodesursacheICDVersion());
                                      return result;
                                    })
                                .toList());
                    tod.setMengeTodesursachen(mengeTodesursachen);
                  }
                  return tod;
                })
            .toList();

    if (mengeTod.size() > 1) {
      LOG.warn("Multiple 'Tod' entries found in Verlauf. Only the first entry can be mapped!");
    }

    mengeTod.stream().findFirst().ifPresent(meldung::setTod);

    if (null != meldung.getTod()) {
      return Optional.of(meldung);
    }
    return Optional.empty();
  }

  private List<Meldung> getMappedTumorkonferenzen(
      ADTGEKID.MengePatient.Patient.MengeMeldung.Meldung source) {
    if (null == source) {
      throw new IllegalArgumentException(MUST_NOT_BE_NULL);
    }

    var mengeTumorkonferenz = source.getMengeTumorkonferenz();
    if (null == mengeTumorkonferenz || mengeTumorkonferenz.getTumorkonferenz().isEmpty()) {
      return List.of();
    }

    return mengeTumorkonferenz.getTumorkonferenz().stream()
        .map(
            tumorkonferenz -> {
              var mappedTumorkonferenz = new TumorkonferenzTyp();
              MapperUtils.mapDateString(tumorkonferenz.getTumorkonferenzDatum())
                  .ifPresent(
                      datum -> {
                        mappedTumorkonferenz.setDatum(datum);
                        // oBDS v2 Meldung-Meldeanlass ist Quelle!
                        mappedTumorkonferenz.setMeldeanlass(
                            TumorkonferenzTyp.Meldeanlass.fromValue(source.getMeldeanlass()));
                        mappedTumorkonferenz.setTumorkonferenzID(
                            tumorkonferenz.getTumorkonferenzID());
                        mappedTumorkonferenz.setTyp(tumorkonferenz.getTumorkonferenzTyp());
                        // Therapieempfehlung nicht in oBDS v2?
                        // mappedTumorkonferenz.setTherapieempfehlung();
                      });
              return new AbstractMap.SimpleEntry<String, TumorkonferenzTyp>(
                  tumorkonferenz.getAnmerkung(), mappedTumorkonferenz);
            })
        .map(
            anmerkungAndMappedTumorkonferenz -> {
              var anmerkung = anmerkungAndMappedTumorkonferenz.getKey();
              var mappedTumorkonferenz = anmerkungAndMappedTumorkonferenz.getValue();
              var meldung = getMeldungsRumpf(source);
              meldung.setMeldungID(
                  String.format(
                      "%s_%s", source.getMeldungID(), mappedTumorkonferenz.getTumorkonferenzID()));
              meldung.setTumorkonferenz(mappedTumorkonferenz);
              // Anmerkung übernommen aus oBDS v2 -> Meldung-Tumorkonferenz
              meldung.setAnmerkung(anmerkung);
              return meldung;
            })
        .toList();
  }

  private List<Meldung> getMappedVerlauf(
      ADTGEKID.MengePatient.Patient.MengeMeldung.Meldung source) {
    if (null == source) {
      throw new IllegalArgumentException(MUST_NOT_BE_NULL);
    }

    var mengeVerlauf = source.getMengeVerlauf();
    if (null == mengeVerlauf || mengeVerlauf.getVerlauf().isEmpty()) {
      return List.of();
    }

    return mengeVerlauf.getVerlauf().stream()
        // Ohne Verlauf - Tod!
        .filter(verlauf -> verlauf.getTod() == null)
        .map(
            verlauf -> {
              var mappedVerlauf = new VerlaufTyp();
              mappedVerlauf.setVerlaufID(verlauf.getVerlaufID());

              // oBDS v2 Meldung->Meldeanlass wird in oBDS v3 für Verlauf verwendet
              if (source.getMeldeanlass() != null) {
                mappedVerlauf.setMeldeanlass(
                    VerlaufTyp.Meldeanlass.fromValue(source.getMeldeanlass()));
              } else {
                LOG.warn("Meldeanlass is null for Verlauf={}", verlauf.getVerlaufID());
              }

              // AllgemeinerLeistungszustand ist nicht verpflicchtend oBDS v2. In v3 schon.
              // Default: "U" verwenden
              if (verlauf.getAllgemeinerLeistungszustand() != null) {
                mappedVerlauf.setAllgemeinerLeistungszustand(
                    AllgemeinerLeistungszustand.fromValue(
                        verlauf.getAllgemeinerLeistungszustand()));
              } else {
                mappedVerlauf.setAllgemeinerLeistungszustand(AllgemeinerLeistungszustand.U);
              }

              mappedVerlauf.setVerlaufLokalerTumorstatus(verlauf.getVerlaufLokalerTumorstatus());

              if ("M".equals(verlauf.getVerlaufTumorstatusFernmetastasen())) {
                // "M" ist nicht mehr in oBDS3 enthalten. Equivalent "T" verwendet.
                mappedVerlauf.setVerlaufTumorstatusFernmetastasen("T");
              } else {
                mappedVerlauf.setVerlaufTumorstatusFernmetastasen(
                    verlauf.getVerlaufTumorstatusFernmetastasen());
              }

              mappedVerlauf.setVerlaufTumorstatusLymphknoten(
                  verlauf.getVerlaufTumorstatusLymphknoten());

              mapHistologie(verlauf.getHistologie()).ifPresent(mappedVerlauf::setHistologie);

              // Nur verwendet, wenn Datumstring mappbar
              MapperUtils.mapDateString(verlauf.getUntersuchungsdatumVerlauf())
                  .ifPresent(datum -> mappedVerlauf.setUntersuchungsdatumVerlauf(datum.getValue()));
              mappedVerlauf.setGesamtbeurteilungTumorstatus(
                  verlauf.getGesamtbeurteilungTumorstatus());

              // Fernmetastasen
              if (verlauf.getMengeFM() != null) {
                mappedVerlauf.setMengeFM(new MengeFMTyp());
                mappedVerlauf
                    .getMengeFM()
                    .getFernmetastase()
                    .addAll(
                        verlauf.getMengeFM().getFernmetastase().stream()
                            .map(MeldungMapper::mapFernmetastase)
                            .toList());
              }

              // TNPM
              mapTnmType(verlauf.getTNM()).ifPresent(mappedVerlauf::setTNM);

              // Weitere Klassifikationen
              if (verlauf.getMengeWeitereKlassifikation() != null) {
                mappedVerlauf.setMengeWeitereKlassifikation(
                    mapMengeWeitereKlassifikation(verlauf.getMengeWeitereKlassifikation()));
              }

              // Nicht in oBDS v2 enthalten ?
              // mappedVerlauf.setMengeGenetik(..);

              // TODO Weitere Module ...
              return mappedVerlauf;
            })
        .map(
            mappedVerlauf -> {
              var meldung = getMeldungsRumpf(source);
              meldung.setMeldungID(
                  String.format("%s_%s", source.getMeldungID(), mappedVerlauf.getVerlaufID()));
              meldung.setVerlauf(mappedVerlauf);
              return meldung;
            })
        .toList();
  }

  private Meldung getMeldungDiagnose(ADTGEKID.MengePatient.Patient.MengeMeldung.Meldung source) {
    var diagnose = source.getDiagnose();
    if (null == diagnose) {
      throw new IllegalArgumentException(DIAGNOSE_SHOULD_NOT_BE_NULL);
    }

    var mappedDiagnose = new DiagnoseTyp();
    mappedDiagnose.setPrimaertumorDiagnosetext(diagnose.getPrimaertumorDiagnosetext());

    // ICDO Topographie
    if (diagnose.getPrimaertumorTopographieICDO() != null) {
      var icdo = new TopographieICDOTyp();
      icdo.setCode(diagnose.getPrimaertumorTopographieICDO());
      icdo.setVersion(diagnose.getPrimaertumorTopographieICDOVersion());
      mappedDiagnose.setPrimaertumorTopographieFreitext(
          diagnose.getPrimaertumorTopographieICDOFreitext());
      mappedDiagnose.setPrimaertumorTopographieICDO(icdo);
    }

    // oBDS v3 kennt auch 7.1, 7.2 ... als Untertyp von 7 für Diagnosesicherung
    if (diagnose.getDiagnosesicherung() != null && !diagnose.getDiagnosesicherung().isBlank()) {
      mappedDiagnose.setDiagnosesicherung(
          DiagnoseTyp.DiagnoseTypDiagnosesicherung.fromValue(diagnose.getDiagnosesicherung()));
    } else {
      LOG.error("Diagnosesicherung is unset in v2 but required in v3.");
    }

    if (diagnose.getMengeFruehereTumorerkrankung() != null) {
      var fruehereTumorerkrankungen =
          diagnose.getMengeFruehereTumorerkrankung().getFruehereTumorerkrankung().stream()
              .map(MeldungMapper::mapFruehereTumorerkrankung)
              .toList();
      var mengeFruehereTumorerkrankung = new DiagnoseTyp.MengeFruehereTumorerkrankung();
      mengeFruehereTumorerkrankung.getFruehereTumorerkrankung().addAll(fruehereTumorerkrankungen);
      mappedDiagnose.setMengeFruehereTumorerkrankung(mengeFruehereTumorerkrankung);
    }

    // Menge Histologie lässt sich nicht direkt auf einen Wert mappen in oBDS v3 -
    // mehrere Diagnose-Meldungen?
    // mappedDiagnose.setHistologie(..);
    // Aktuell: Immer nur erste Histologie verwendet!
    if (diagnose.getMengeHistologie() != null) {

      if (diagnose.getMengeHistologie().getHistologie().size() > 1) {
        var allSame =
            diagnose.getMengeHistologie().getHistologie().stream()
                    .map(
                        h -> {
                          try {
                            return ObdsMapper.XML_MAPPER.writeValueAsString(h);
                          } catch (JsonProcessingException e) {
                            LOG.error("Failed to serialize Histologie for comparison", e);
                            return "";
                          }
                        })
                    .distinct()
                    .count()
                == 1;
        if (!allSame) {
          LOG.warn(
              "Multiple entries for 'Diagnose.Histologie' found that differ in content. "
                  + "Only the first entry can be mapped! Meldung: {}",
              source.getMeldungID());
        }
      }

      var firstHistologie = diagnose.getMengeHistologie().getHistologie().stream().findFirst();
      if (firstHistologie.isPresent()) {
        MeldungMapper.mapHistologie(firstHistologie.get()).ifPresent(mappedDiagnose::setHistologie);
      }
    }

    // Fernmetastasen
    if (diagnose.getMengeFM() != null) {
      mappedDiagnose.setMengeFM(new MengeFMTyp());
      mappedDiagnose
          .getMengeFM()
          .getFernmetastase()
          .addAll(
              diagnose.getMengeFM().getFernmetastase().stream()
                  .map(MeldungMapper::mapFernmetastase)
                  .toList());
    }

    // cTNPM
    mapTnmType(diagnose.getCTNM()).ifPresent(mappedDiagnose::setCTNM);

    // pTNPM
    mapTnmType(diagnose.getPTNM()).ifPresent(mappedDiagnose::setPTNM);

    // Weitere Klassifikationen
    if (diagnose.getMengeWeitereKlassifikation() != null) {
      mappedDiagnose.setMengeWeitereKlassifikation(
          mapMengeWeitereKlassifikation(diagnose.getMengeWeitereKlassifikation()));
    }

    // Modul Prostata
    if (diagnose.getModulProstata() != null) {
      mappedDiagnose.setModulProstata(ModulMapper.map(diagnose.getModulProstata()));
    }

    // Nicht in oBDS v2 ?
    // mappedDiagnose.setMengeGenetik(..);

    // Direkt übernommen - sofern vorhanden, sonst 'U'
    // Beide nutzen
    // {'0'|'1'|'2'|'3'|'4'|'U'|'10%'|'20%'|'30%'|'40%'|'50%'|'60%'|'70%'|'80%'|'90%'|'100%'}
    if (diagnose.getAllgemeinerLeistungszustand() != null) {
      mappedDiagnose.setAllgemeinerLeistungszustand(
          AllgemeinerLeistungszustand.fromValue(diagnose.getAllgemeinerLeistungszustand()));
    } else {
      mappedDiagnose.setAllgemeinerLeistungszustand(AllgemeinerLeistungszustand.U);
    }

    var mappedMeldung = getMeldungsRumpf(source);
    mappedMeldung.setMeldungID(source.getMeldungID());
    mappedMeldung.setDiagnose(mappedDiagnose);
    return mappedMeldung;
  }

  private Meldung getMeldungPathologie(ADTGEKID.MengePatient.Patient.MengeMeldung.Meldung source) {
    var diagnose = source.getDiagnose();
    if (null == diagnose) {
      throw new IllegalArgumentException(DIAGNOSE_SHOULD_NOT_BE_NULL);
    }

    var mappedPathologie = new PathologieTyp();
    mappedPathologie.setPrimaertumorDiagnosetext(diagnose.getPrimaertumorDiagnosetext());
    mappedPathologie.setBefundtext(diagnose.getAnmerkung());
    mappedPathologie.setDiagnosesicherung(diagnose.getDiagnosesicherung());

    // Menge Histologie lässt sich nicht direkt auf einen Wert mappen in oBDS v3 -
    // mehrere Diagnose-Meldungen?
    // mappedDiagnose.setHistologie(..);
    // Aktuell: Immer nur erste Histologie verwendet!
    if (diagnose.getMengeHistologie() != null) {
      if (diagnose.getMengeHistologie().getHistologie().size() > 1) {
        LOG.warn(
            "Multiple entries for 'Diagnose.Histologie' found. Only the first entry can be mapped!");
      }

      var firstHistologie = diagnose.getMengeHistologie().getHistologie().stream().findFirst();
      // Wenn Histo vorhanden - erste Histo
      if (firstHistologie.isPresent()) {
        MeldungMapper.mapHistologie(firstHistologie.get())
            .ifPresent(mappedPathologie::setHistologie);
      }
    }

    // Fernmetastasen
    if (diagnose.getMengeFM() != null) {
      var fm =
          diagnose.getMengeFM().getFernmetastase().stream()
              .map(MeldungMapper::mapFernmetastase)
              .toList();
      var mengeFM = new MengeFMTyp();
      mengeFM.getFernmetastase().addAll(fm);
      mappedPathologie.setMengeFM(mengeFM);
    }

    // cTNPM
    mapTnmType(diagnose.getCTNM()).ifPresent(mappedPathologie::setCTNM);

    // pTNPM
    mapTnmType(diagnose.getPTNM()).ifPresent(mappedPathologie::setPTNM);

    // Weitere Klassifikationen
    if (diagnose.getMengeWeitereKlassifikation() != null) {
      mappedPathologie.setMengeWeitereKlassifikation(
          mapMengeWeitereKlassifikation(diagnose.getMengeWeitereKlassifikation()));
    }

    // Einsender aus Zusatzitems
    getMengeZusatzitemTyp(source)
        .ifPresent(
            zusatzitems -> {
              var einsender = new PathologieTyp.Einsender();
              var strukturiert = new PathologieTyp.Einsender.Strukturiert();
              zusatzitems.getZusatzitem().stream()
                  .filter(item -> "Einsender_Einrichtung".equals(item.getArt()))
                  .findFirst()
                  .ifPresent(item -> strukturiert.setEinrichtung(item.getWert()));
              zusatzitems.getZusatzitem().stream()
                  .filter(item -> "Einsender_Klinik_Abteilung_Praxis".equals(item.getArt()))
                  .findFirst()
                  .ifPresent(item -> strukturiert.setAbteilung(item.getWert()));
              zusatzitems.getZusatzitem().stream()
                  .filter(item -> "Einsender_Strasse".equals(item.getArt()))
                  .findFirst()
                  .ifPresent(item -> strukturiert.setStrasse(item.getWert()));
              zusatzitems.getZusatzitem().stream()
                  .filter(item -> "Einsender_Hausnummer".equals(item.getArt()))
                  .findFirst()
                  .ifPresent(item -> strukturiert.setHausnummer(item.getWert()));
              zusatzitems.getZusatzitem().stream()
                  .filter(item -> "Einsender_PLZ".equals(item.getArt()))
                  .findFirst()
                  .ifPresent(item -> strukturiert.setPLZ(item.getWert()));
              zusatzitems.getZusatzitem().stream()
                  .filter(item -> "Einsender_Ort".equals(item.getArt()))
                  .findFirst()
                  .ifPresent(item -> strukturiert.setOrt(item.getWert()));
              // Not in obds2!
              strukturiert.setLand("DE");
              einsender.setStrukturiert(strukturiert);
              mappedPathologie.setEinsender(einsender);
            });

    var mappedMeldung = getMeldungsRumpf(source);
    mappedMeldung.setMeldungID(source.getMeldungID());
    mappedMeldung.setPathologie(mappedPathologie);

    return mappedMeldung;
  }

  private Meldung getMeldungsRumpf(ADTGEKID.MengePatient.Patient.MengeMeldung.Meldung source) {
    if (null == source) {
      throw new IllegalArgumentException("Source cannot be null");
    }

    var mappedMeldung = new Meldung();
    mappedMeldung.setMelderID(source.getMelderID());
    mappedMeldung.setAnmerkung(source.getAnmerkung());

    if (source.getMeldebegruendung() != null) {
      mappedMeldung.setMeldebegruendung(
          MeldebegruendungTyp.fromValue(source.getMeldebegruendung()));
    } else {
      LOG.warn("Meldebegruendung is unset but required in v3. Ignoring.");
    }

    // Nicht in oBDS v2: Eigene Leistung J/N
    // For now: Wenn Melder-ID keine "9999" enthält => "J" -> Onkostar-Konvention
    // für "Extern"
    if (source.getMelderID().contains("9999")) {
      mappedMeldung.setEigeneLeistung(JNU.N);
    } else {
      mappedMeldung.setEigeneLeistung(JNU.J);
    }

    // In jeder (Unter-)Meldung
    var tumorzuordnung = source.getTumorzuordnung();
    if (tumorzuordnung != null) {
      var mappedTumorzuordnung = new TumorzuordnungTyp();
      // Übernehme Tumor-ID, wenn vorhanden
      if (fixMissingId
          && null == tumorzuordnung.getTumorID()
          && null != tumorzuordnung.getDiagnosedatum()
          && null != tumorzuordnung.getPrimaertumorICDCode()
          && null != source.getMeldungID()) {
        var generatedTumorId =
            DigestUtils.sha1Hex(
                    String.format(
                        "%s_%s_%s",
                        tumorzuordnung.getDiagnosedatum(),
                        tumorzuordnung.getPrimaertumorICDCode(),
                        source.getMeldungID()))
                .subSequence(0, 16);
        mappedTumorzuordnung.setTumorID(String.format("TID_%s", generatedTumorId));
      } else if (null != tumorzuordnung.getTumorID()) {
        mappedTumorzuordnung.setTumorID(tumorzuordnung.getTumorID());
      } else {
        throw new UnmappableItemException(TUMORID_MUST_NOT_BE_NULL);
      }
      // Datum
      MapperUtils.mapDateString(tumorzuordnung.getDiagnosedatum())
          .ifPresent(mappedTumorzuordnung::setDiagnosedatum);
      // ICD10
      var icd10 = new TumorICDTyp();
      icd10.setCode(tumorzuordnung.getPrimaertumorICDCode());
      icd10.setVersion(tumorzuordnung.getPrimaertumorICDVersion());
      mappedTumorzuordnung.setPrimaertumorICD(icd10);

      // Seitenlokalisation: mapping über Enum - beide {'L'|'R'|'B'|'M'|'U'|'T'}
      if (null != tumorzuordnung.getSeitenlokalisation()) {
        mappedTumorzuordnung.setSeitenlokalisation(
            SeitenlokalisationTyp.fromValue(tumorzuordnung.getSeitenlokalisation().value()));
      } else {
        mappedTumorzuordnung.setSeitenlokalisation(SeitenlokalisationTyp.U);
      }

      mappedMeldung.setTumorzuordnung(mappedTumorzuordnung);
    }

    return mappedMeldung;
  }

  private static MengeWeitereKlassifikationTyp mapMengeWeitereKlassifikation(
      de.basisdatensatz.obds.v2.MengeWeitereKlassifikationTyp mengeWeitereKlassifikation) {
    var mappedMengeWeitereKlassifikation = new MengeWeitereKlassifikationTyp();
    for (var source : mengeWeitereKlassifikation.getWeitereKlassifikation()) {
      var mappedWeitereKlassifikation = new MengeWeitereKlassifikationTyp.WeitereKlassifikation();

      mappedWeitereKlassifikation.setName(source.getName());
      mappedWeitereKlassifikation.setStadium(source.getStadium());

      var date = MapperUtils.mapDateStringGenau(source.getDatum());
      if (date.isPresent()) {
        mappedWeitereKlassifikation.setDatum(date.get());
      }

      mappedMengeWeitereKlassifikation.getWeitereKlassifikation().add(mappedWeitereKlassifikation);
    }

    return mappedMengeWeitereKlassifikation;
  }

  private static MengeFMTyp.Fernmetastase mapFernmetastase(
      de.basisdatensatz.obds.v2.MengeFMTyp.Fernmetastase source) {
    var result = new MengeFMTyp.Fernmetastase();
    // Diagnosedatum
    var date = MapperUtils.mapDateString(source.getFMDiagnosedatum());
    if (date.isPresent()) {
      result.setDiagnosedatum(date.get());
    } else {
      LOG.warn(
          "Unable to map FM Diagnosedatum but required in v3: {}", source.getFMDiagnosedatum());
    }

    // Lokalisation - direkt übernommen
    // oBDS v2 kennt
    // 'PUL'|'OSS'|'HEP'|'BRA'|'LYM'|'MAR'|'PLE'|'PER'|'ADR'|'SKI'|'OTH'|'GEN'
    // oBDS v3 kennt
    // 'PUL'|'OSS'|'HEP'|'BRA'|'LYM'|'MAR'|'PLE'|'PER'|'ADR'|'SKI'|'OTH'|'GEN'
    result.setLokalisation(source.getFMLokalisation());
    return result;
  }

  private static FruehereTumorerkrankung mapFruehereTumorerkrankung(
      ADTGEKID.MengePatient.Patient.MengeMeldung.Meldung.Diagnose.MengeFruehereTumorerkrankung
              .FruehereTumorerkrankung
          source) {
    var result = new FruehereTumorerkrankung();
    // Diagnosedatum
    var date = MapperUtils.mapDateString(source.getDiagnosedatum());
    if (date.isPresent()) {
      result.setDiagnosedatum(date.get().getValue());
    } else {
      LOG.warn(
          "Unable to map FruehereTumorerkrankung Diagnosedatum but required in v3: {}",
          source.getDiagnosedatum());
    }

    // ICD10
    var icd10 = new TumorICDTyp();
    icd10.setCode(source.getICDCode());
    icd10.setVersion(source.getICDVersion());
    result.setICD(icd10);
    // Freitext
    result.setFreitext(source.getFreitext());
    return result;
  }

  public static Optional<TNMTyp> mapTnmType(de.basisdatensatz.obds.v2.TNMTyp source) {
    if (source == null) {
      return Optional.empty();
    }
    var result = new TNMTyp();
    var date = MapperUtils.mapDateString(source.getTNMDatum());
    // das Datum ist optional, selbst wenn es nicht in oBDS v2 gesetzt ist,
    // können wir also korrekte oBDS v3 TNM Elemente erzeugen
    if (date.isPresent()) {
      result.setDatum(date.get().getValue());
    }

    result.setVersion(source.getTNMVersion());
    result.setYSymbol(source.getTNMYSymbol());
    result.setRSymbol(source.getTNMRSymbol());
    result.setASymbol(source.getTNMASymbol());
    result.setCPUPraefixT(source.getTNMCPUPraefixT());
    result.setT(source.getTNMT());
    result.setMSymbol(source.getTNMMSymbol());
    result.setCPUPraefixN(source.getTNMCPUPraefixN());
    result.setN(source.getTNMN());
    result.setCPUPraefixM(source.getTNMCPUPraefixM());
    result.setM(source.getTNMM());
    result.setL(source.getTNML());
    result.setV(source.getTNMV());
    result.setPn(source.getTNMPn());
    result.setS(source.getTNMS());

    result.setID(source.getTNMID());

    // Nicht in oBDSv2?
    // result.setUICCStadium();
    return Optional.of(result);
  }

  public static Optional<HistologieTyp> mapHistologie(
      de.basisdatensatz.obds.v2.HistologieTyp source) {
    if (source == null) {
      return Optional.empty();
    }

    var mappedHisto = new HistologieTyp();
    mappedHisto.setGrading(source.getGrading());
    mappedHisto.setHistologieID(source.getHistologieID());
    mappedHisto.setHistologieEinsendeNr(source.getHistologieEinsendeNr());
    mappedHisto.setLKBefallen(source.getLKBefallen());
    mappedHisto.setLKUntersucht(source.getLKUntersucht());
    mappedHisto.setMorphologieFreitext(source.getMorphologieFreitext());
    mappedHisto.setSentinelLKBefallen(source.getSentinelLKBefallen());
    mappedHisto.setSentinelLKUntersucht(source.getSentinelLKUntersucht());

    if (source.getMorphologieCode() != null) {
      var morphologieCode = new MorphologieICDOTyp();
      morphologieCode.setCode(source.getMorphologieCode());
      morphologieCode.setVersion(source.getMorphologieICDOVersion());
      mappedHisto.getMorphologieICDO().add(morphologieCode);
    }

    // Nur wenn vorhanden und mappbar
    MapperUtils.mapDateString(source.getTumorHistologiedatum())
        .ifPresent(mappedHisto::setTumorHistologiedatum);
    return Optional.of(mappedHisto);
  }
}
