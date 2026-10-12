package io.github.bzkf.obds2toobds3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.basisdatensatz.obds.v2.ADTGEKID.MengePatient.Patient;
import de.basisdatensatz.obds.v2.ADTGEKID.MengePatient.Patient.MengeMeldung.Meldung;
import de.basisdatensatz.obds.v2.SeitenlokalisationTyp;
import de.basisdatensatz.obds.v3.TumorzuordnungTyp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
}
