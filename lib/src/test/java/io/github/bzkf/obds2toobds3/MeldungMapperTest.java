package io.github.bzkf.obds2toobds3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.basisdatensatz.obds.v2.ADTGEKID.MengePatient.Patient;
import de.basisdatensatz.obds.v2.ADTGEKID.MengePatient.Patient.MengeMeldung.Meldung;
import de.basisdatensatz.obds.v2.SeitenlokalisationTyp;
import de.basisdatensatz.obds.v3.TumorzuordnungTyp;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MeldungMapperTest {

  MeldungMapper mapper;

  @BeforeEach
  void setUp() {
    this.mapper = new MeldungMapper(false, false);
  }

  /**
   * Die Tumorzuordnung wird entweder aus dem Element Tumorzuordnung oder, falls dieses fehlt, aus
   * dem Element Diagnose gemappt. Beide Wege müssen zum selben Ergebnis führen.
   */
  @Nested
  class TumorzuordnungTest {

    private static final String MELDUNG_ID = "M1";

    private Patient patient;

    @BeforeEach
    void setUp() {
      var stammdaten = new Patient.PatientenStammdaten();
      stammdaten.setPatientID("P1");
      patient = new Patient();
      patient.setPatientenStammdaten(stammdaten);
    }

    private Meldung meldungMitTumorzuordnungsElement(
        String tumorId, SeitenlokalisationTyp seitenlokalisation) {
      var tumorzuordnung = new Meldung.Tumorzuordnung();
      tumorzuordnung.setTumorID(tumorId);
      tumorzuordnung.setPrimaertumorICDCode("C17.1");
      tumorzuordnung.setPrimaertumorICDVersion("10 2021 GM");
      tumorzuordnung.setDiagnosedatum("10.06.2024");
      tumorzuordnung.setSeitenlokalisation(seitenlokalisation);

      var meldung = leereMeldung();
      meldung.setTumorzuordnung(tumorzuordnung);
      return meldung;
    }

    private Meldung meldungNurMitDiagnose(
        String tumorId, SeitenlokalisationTyp seitenlokalisation) {
      var diagnose = new Meldung.Diagnose();
      diagnose.setTumorID(tumorId);
      diagnose.setPrimaertumorICDCode("C17.1");
      diagnose.setPrimaertumorICDVersion("10 2021 GM");
      diagnose.setDiagnosedatum("10.06.2024");
      diagnose.setSeitenlokalisation(seitenlokalisation);
      diagnose.setDiagnosesicherung("7");

      var meldung = leereMeldung();
      meldung.setDiagnose(diagnose);
      return meldung;
    }

    private Meldung leereMeldung() {
      var meldung = new Meldung();
      meldung.setMeldungID(MELDUNG_ID);
      meldung.setMelderID("MELDER");
      meldung.setMeldeanlass("diagnose");
      meldung.setMeldebegruendung("I");
      return meldung;
    }

    private TumorzuordnungTyp mappeTumorzuordnung(MeldungMapper mapper, Meldung source) {
      var result = mapper.map(source, patient);
      assertThat(result).isNotEmpty();
      assertThat(result).allSatisfy(m -> assertThat(m.getTumorzuordnung()).isNotNull());
      return result.get(0).getTumorzuordnung();
    }

    @Test
    void shouldMapTumorzuordnungElementAndDiagnoseFallbackIdentically() {
      var aus =
          mappeTumorzuordnung(
              mapper, meldungMitTumorzuordnungsElement("T1", SeitenlokalisationTyp.L));
      var ausDiagnose =
          mappeTumorzuordnung(mapper, meldungNurMitDiagnose("T1", SeitenlokalisationTyp.L));

      assertThat(ausDiagnose.getTumorID()).isEqualTo(aus.getTumorID()).isEqualTo("T1");
      assertThat(ausDiagnose.getPrimaertumorICD().getCode())
          .isEqualTo(aus.getPrimaertumorICD().getCode())
          .isEqualTo("C17.1");
      assertThat(ausDiagnose.getPrimaertumorICD().getVersion())
          .isEqualTo(aus.getPrimaertumorICD().getVersion())
          .isEqualTo("10 2021 GM");
      assertThat(ausDiagnose.getDiagnosedatum().getValue().toXMLFormat())
          .startsWith("2024-06-10")
          .isEqualTo(aus.getDiagnosedatum().getValue().toXMLFormat());
      assertThat(ausDiagnose.getSeitenlokalisation())
          .isEqualTo(aus.getSeitenlokalisation())
          .isEqualTo(de.basisdatensatz.obds.v3.SeitenlokalisationTyp.L);
    }

    @Test
    void shouldDefaultSeitenlokalisationToUnknownForTumorzuordnungElement() {
      var actual = mappeTumorzuordnung(mapper, meldungMitTumorzuordnungsElement("T1", null));
      assertThat(actual.getSeitenlokalisation())
          .isEqualTo(de.basisdatensatz.obds.v3.SeitenlokalisationTyp.U);
    }

    @Test
    void shouldDefaultSeitenlokalisationToUnknownForDiagnoseFallback() {
      var actual = mappeTumorzuordnung(mapper, meldungNurMitDiagnose("T1", null));
      assertThat(actual.getSeitenlokalisation())
          .isEqualTo(de.basisdatensatz.obds.v3.SeitenlokalisationTyp.U);
    }

    @Test
    void shouldGenerateTheSameTumorIdForBothSourcesIfRequested() {
      var fixingMapper = new MeldungMapper(false, true);

      var aus = mappeTumorzuordnung(fixingMapper, meldungMitTumorzuordnungsElement(null, null));
      var ausDiagnose = mappeTumorzuordnung(fixingMapper, meldungNurMitDiagnose(null, null));

      assertThat(aus.getTumorID()).startsWith("TID_");
      assertThat(ausDiagnose.getTumorID()).isEqualTo(aus.getTumorID());
    }

    @Test
    void shouldThrowForMissingTumorIdIfNotRequestedToGenerateIt() {
      var mitTumorzuordnungsElement = meldungMitTumorzuordnungsElement(null, null);
      var nurMitDiagnose = meldungNurMitDiagnose(null, null);

      assertThrows(
          UnmappableItemException.class, () -> mapper.map(mitTumorzuordnungsElement, patient));
      assertThrows(UnmappableItemException.class, () -> mapper.map(nurMitDiagnose, patient));
    }
  }

  private static Patient patientMitId() {
    var stammdaten = new Patient.PatientenStammdaten();
    stammdaten.setPatientID("P1");
    var patient = new Patient();
    patient.setPatientenStammdaten(stammdaten);
    return patient;
  }

  private static Meldung meldungMitTumorzuordnung(String meldeanlass) {
    var tumorzuordnung = new Meldung.Tumorzuordnung();
    tumorzuordnung.setTumorID("T1");
    tumorzuordnung.setPrimaertumorICDCode("C17.1");
    tumorzuordnung.setPrimaertumorICDVersion("10 2021 GM");
    tumorzuordnung.setDiagnosedatum("10.06.2024");

    var meldung = new Meldung();
    meldung.setMeldungID("M1");
    meldung.setMelderID("MELDER");
    meldung.setMeldeanlass(meldeanlass);
    meldung.setMeldebegruendung("I");
    meldung.setTumorzuordnung(tumorzuordnung);
    return meldung;
  }

  private static Meldung.MengeVerlauf.Verlauf verlauf(
      String id,
      String untersuchungsdatum,
      String gesamtbeurteilung,
      String leistungszustand,
      boolean mitTod) {
    var verlauf = verlauf(id, untersuchungsdatum, gesamtbeurteilung, mitTod);
    verlauf.setAllgemeinerLeistungszustand(leistungszustand);
    return verlauf;
  }

  private static Meldung.MengeVerlauf.Verlauf verlauf(
      String id, String untersuchungsdatum, String gesamtbeurteilung, boolean mitTod) {
    var verlauf = new Meldung.MengeVerlauf.Verlauf();
    verlauf.setVerlaufID(id);
    verlauf.setUntersuchungsdatumVerlauf(untersuchungsdatum);
    verlauf.setGesamtbeurteilungTumorstatus(gesamtbeurteilung);
    verlauf.setAnmerkung("Anmerkung " + id);
    if (mitTod) {
      var tod = new Meldung.MengeVerlauf.Verlauf.Tod();
      tod.setSterbedatum("16.10.2024");
      tod.setTodTumorbedingt(de.basisdatensatz.obds.v2.JNUTyp.J);
      verlauf.setTod(tod);
    }
    return verlauf;
  }

  private List<de.basisdatensatz.obds.v3.OBDS.MengePatient.Patient.MengeMeldung.Meldung>
      mappeVerlauf(String meldeanlass, Meldung.MengeVerlauf.Verlauf... verlaeufe) {
    var meldung = meldungMitTumorzuordnung(meldeanlass);
    var mengeVerlauf = new Meldung.MengeVerlauf();
    mengeVerlauf.getVerlauf().addAll(List.of(verlaeufe));
    meldung.setMengeVerlauf(mengeVerlauf);
    return mapper.map(meldung, patientMitId());
  }

  /**
   * Ein Verlauf mit Tod wird zusätzlich als Verlauf gemappt, wenn in oBDS v2 die Gesamtbeurteilung
   * oder der allgemeine Leistungszustand angegeben ist. Ein Datum wird nicht benötigt. Übernommen
   * wird nur, was angegeben ist: Platzhalter ("X" und "U") gibt es für diese Verläufe nicht, sie
   * würden z.B. zu einem ECOG-Wert "unbekannt" führen.
   */
  @Nested
  class VerlaufMitTodTest {

    private de.basisdatensatz.obds.v3.OBDS.MengePatient.Patient.MengeMeldung.Meldung tod(
        List<de.basisdatensatz.obds.v3.OBDS.MengePatient.Patient.MengeMeldung.Meldung> result) {
      return result.stream().filter(m -> m.getTod() != null).findFirst().orElseThrow();
    }

    private void assertOnlyTod(
        List<de.basisdatensatz.obds.v3.OBDS.MengePatient.Patient.MengeMeldung.Meldung> result) {
      assertThat(result).singleElement().satisfies(m -> assertThat(m.getTod()).isNotNull());
    }

    @Test
    void shouldMapVerlaufWithTodAsVerlaufAndTod() {
      var result = mappeVerlauf("statusaenderung", verlauf("V1", "14.10.2024", "P", "2", true));

      assertThat(result)
          .extracting(m -> m.getMeldungID())
          .containsExactlyInAnyOrder("M1_V1", "M1__D");
      var verlauf = result.stream().filter(m -> m.getVerlauf() != null).findFirst().orElseThrow();
      assertThat(verlauf.getVerlauf().getGesamtbeurteilungTumorstatus()).isEqualTo("P");
      assertThat(verlauf.getVerlauf().getAllgemeinerLeistungszustand())
          .isEqualTo(de.basisdatensatz.obds.v3.AllgemeinerLeistungszustand.ECOG_2);
      assertThat(verlauf.getVerlauf().getUntersuchungsdatumVerlauf().toXMLFormat())
          .startsWith("2024-10-14");
      assertThat(tod(result).getTod().getSterbedatum().toXMLFormat()).startsWith("2024-10-16");
    }

    @Test
    void shouldNotRequireUntersuchungsdatum() {
      var result = mappeVerlauf("statusaenderung", verlauf("V1", null, "P", "2", true));

      assertThat(result).hasSize(2);
      var verlauf = result.stream().filter(m -> m.getVerlauf() != null).findFirst().orElseThrow();
      assertThat(verlauf.getVerlauf().getUntersuchungsdatumVerlauf()).isNull();
      assertThat(verlauf.getVerlauf().getGesamtbeurteilungTumorstatus()).isEqualTo("P");
    }

    @Test
    void shouldAttachAnmerkungOfVerlaufWithTodToTodOnly() {
      var result = mappeVerlauf("statusaenderung", verlauf("V1", "14.10.2024", "P", "2", true));

      assertThat(tod(result).getAnmerkung()).isEqualTo("Anmerkung V1");
      assertThat(
              result.stream()
                  .filter(m -> m.getVerlauf() != null)
                  .findFirst()
                  .orElseThrow()
                  .getAnmerkung())
          .isNull();
    }

    @Test
    void shouldAttachAnmerkungOfVerlaufWithoutTodToVerlauf() {
      var result = mappeVerlauf("statusaenderung", verlauf("V1", "14.10.2024", "P", false));

      assertThat(result)
          .singleElement()
          .satisfies(m -> assertThat(m.getAnmerkung()).isEqualTo("Anmerkung V1"));
    }

    @Test
    void shouldOnlyMapTodIfThereIsNoFurtherData() {
      var nurTod = verlauf("V1", null, null, true);
      nurTod.setAnmerkung(null);

      assertOnlyTod(mappeVerlauf("tod", nurTod));
    }

    @Test
    void shouldMapVerlaufWithTodWithoutPlaceholderIfOnlyTheGesamtbeurteilungIsGiven() {
      var result = mappeVerlauf("tod", verlauf("V1", "14.10.2024", "P", null, true));

      assertThat(result).hasSize(2);
      var verlauf = result.stream().filter(m -> m.getVerlauf() != null).findFirst().orElseThrow();
      assertThat(verlauf.getVerlauf().getGesamtbeurteilungTumorstatus()).isEqualTo("P");
      // kein "U" als Platzhalter
      assertThat(verlauf.getVerlauf().getAllgemeinerLeistungszustand()).isNull();
    }

    @Test
    void shouldMapVerlaufWithTodWithoutPlaceholderIfOnlyTheLeistungszustandIsGiven() {
      var result = mappeVerlauf("tod", verlauf("V1", "14.10.2024", null, "2", true));

      assertThat(result).hasSize(2);
      var verlauf = result.stream().filter(m -> m.getVerlauf() != null).findFirst().orElseThrow();
      assertThat(verlauf.getVerlauf().getAllgemeinerLeistungszustand())
          .isEqualTo(de.basisdatensatz.obds.v3.AllgemeinerLeistungszustand.ECOG_2);
      // weder "X" noch "U" als Platzhalter
      assertThat(verlauf.getVerlauf().getGesamtbeurteilungTumorstatus()).isNull();
    }

    @Test
    void shouldOnlyMapTodIfNeitherGesamtbeurteilungNorLeistungszustandIsGiven() {
      var nurDatum = verlauf("V1", "14.10.2024", null, null, true);
      assertOnlyTod(mappeVerlauf("tod", nurDatum));

      var nurLokalerStatus = verlauf("V1", null, null, null, true);
      nurLokalerStatus.setVerlaufLokalerTumorstatus("K");
      assertOnlyTod(mappeVerlauf("tod", nurLokalerStatus));
    }

    @Test
    void shouldUseStatusmeldungForMeldeanlassTod() {
      var result =
          mappeVerlauf(
              "tod",
              verlauf("V1", "14.10.2024", "P", "2", true),
              verlauf("V2", "13.10.2024", "K", false));

      assertThat(result.stream().filter(m -> m.getVerlauf() != null))
          .hasSize(2)
          .allSatisfy(
              m ->
                  assertThat(m.getVerlauf().getMeldeanlass())
                      .isEqualTo(
                          de.basisdatensatz.obds.v3.VerlaufTyp.Meldeanlass.fromValue(
                              "statusmeldung")));
    }

    @Test
    void shouldOnlyMapTodIfMeldeanlassIsNotAvailableForVerlauf() {
      assertOnlyTod(mappeVerlauf("behandlungsende", verlauf("V1", "14.10.2024", "P", "2", true)));
    }
  }

  /** Verläufe ohne Tod sind ein eigener Status, die in oBDS v3 verpflichtenden Angaben fehlen. */
  @Nested
  class LeistungszustandTest {

    private de.basisdatensatz.obds.v3.AllgemeinerLeistungszustand mappeLeistungszustand(
        String leistungszustand) {
      var result =
          mappeVerlauf(
              "statusaenderung", verlauf("V1", "14.10.2024", "P", leistungszustand, false));
      assertThat(result).singleElement();
      return result.get(0).getVerlauf().getAllgemeinerLeistungszustand();
    }

    @Test
    void shouldUseUnknownIfLeistungszustandIsMissingInVerlaufWithoutTod() {
      assertThat(mappeLeistungszustand(null))
          .isEqualTo(de.basisdatensatz.obds.v3.AllgemeinerLeistungszustand.U);
    }

    @Test
    void shouldKeepLeistungszustandFromObdsV2() {
      assertThat(mappeLeistungszustand("3"))
          .isEqualTo(de.basisdatensatz.obds.v3.AllgemeinerLeistungszustand.ECOG_3);
    }
  }

  /**
   * Die Gesamtbeurteilung ist in oBDS v3 verpflichtend. "U" bedeutet "Beurteilung unmöglich" und
   * ist damit eine inhaltliche Aussage, fehlt die Angabe, ist sie "X" (fehlende Angaben).
   */
  @Nested
  class GesamtbeurteilungTumorstatusTest {

    private String mappeGesamtbeurteilung(String gesamtbeurteilung) {
      var result =
          mappeVerlauf("statusaenderung", verlauf("V1", "14.10.2024", gesamtbeurteilung, false));
      assertThat(result).singleElement();
      return result.get(0).getVerlauf().getGesamtbeurteilungTumorstatus();
    }

    @Test
    void shouldUseFehlendeAngabeIfGesamtbeurteilungIsMissing() {
      assertThat(mappeGesamtbeurteilung(null)).isEqualTo("X").isNotEqualTo("U");
    }

    @ParameterizedTest
    @ValueSource(strings = {"V", "T", "K", "P", "D", "B", "R", "U", "X"})
    void shouldKeepGesamtbeurteilungFromObdsV2(String gesamtbeurteilung) {
      // insbesondere "U" (Beurteilung unmöglich) wird nicht verändert
      assertThat(mappeGesamtbeurteilung(gesamtbeurteilung)).isEqualTo(gesamtbeurteilung);
    }
  }

  @Nested
  class ZusatzitemTest {

    @Test
    void shouldNotFailForZusatzitemWithoutArt() {
      var meldung = meldungMitTumorzuordnung("histologie_zytologie");
      var diagnose = new Meldung.Diagnose();
      diagnose.setDiagnosesicherung("7");
      meldung.setDiagnose(diagnose);
      var zusatzitem = new Meldung.MengeZusatzitem.Zusatzitem();
      zusatzitem.setWert("Wert ohne Art");
      var mengeZusatzitem = new Meldung.MengeZusatzitem();
      mengeZusatzitem.getZusatzitem().add(zusatzitem);
      meldung.setMengeZusatzitem(mengeZusatzitem);

      var result = mapper.map(meldung, patientMitId());

      assertThat(result).singleElement().satisfies(m -> assertThat(m.getPathologie()).isNotNull());
    }
  }
}
