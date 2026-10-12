package io.github.bzkf.obds2toobds3;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v2.ADTGEKID.MengePatient.Patient.MengeMeldung.Meldung.MengeST;
import de.basisdatensatz.obds.v2.StrahlendosisTyp;
import de.basisdatensatz.obds.v3.STTyp;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class StrahlentherapieMapperTest {

  private static StrahlendosisTyp dosis(String dosis, String einheit) {
    var result = new StrahlendosisTyp();
    result.setDosis(new BigDecimal(dosis));
    result.setEinheit(einheit);
    return result;
  }

  private static STTyp.MengeBestrahlung.Bestrahlung mapBestrahlung(
      String applikationsart, StrahlendosisTyp gesamtdosis, StrahlendosisTyp einzeldosis) {
    var bestrahlung = new MengeST.ST.MengeBestrahlung.Bestrahlung();
    bestrahlung.setSTApplikationsart(applikationsart);
    bestrahlung.setSTZielgebiet("1.");
    bestrahlung.setSTSeiteZielgebiet("L");
    bestrahlung.setSTGesamtdosis(gesamtdosis);
    bestrahlung.setSTEinzeldosis(einzeldosis);

    var mengeBestrahlung = new MengeST.ST.MengeBestrahlung();
    mengeBestrahlung.getBestrahlung().add(bestrahlung);
    var st = new MengeST.ST();
    st.setSTID("1");
    st.setMengeBestrahlung(mengeBestrahlung);
    var mengeST = new MengeST();
    mengeST.getST().add(st);

    var result = StrahlentherapieMapper.map(mengeST, "behandlungsende");
    return result.get(0).getMengeBestrahlung().getBestrahlung().get(0);
  }

  @Test
  void shouldMapDosesOfMetabolicTherapyAsAktivitaet() {
    var actual = mapBestrahlung("MPRRT", dosis("29.6", "GBq"), dosis("7.4", "GBq"));

    var metabolisch = actual.getApplikationsart().getMetabolisch();
    assertThat(metabolisch.getMetabolischTyp()).isEqualTo("PRRT");
    assertThat(metabolisch.getGesamtdosis().getDosis()).isEqualByComparingTo("29.6");
    assertThat(metabolisch.getGesamtdosis().getEinheit()).isEqualTo("GBq");
    assertThat(metabolisch.getEinzeldosis().getDosis()).isEqualByComparingTo("7.4");
    assertThat(metabolisch.getEinzeldosis().getEinheit()).isEqualTo("GBq");
  }

  @Test
  void shouldNotMapGrayDosesOfMetabolicTherapyAsTheyAreNotAnAktivitaet() {
    var actual = mapBestrahlung("M", dosis("50", "Gy"), null);

    var metabolisch = actual.getApplikationsart().getMetabolisch();
    assertThat(metabolisch.getGesamtdosis()).isNull();
    assertThat(metabolisch.getEinzeldosis()).isNull();
  }

  @Test
  void shouldMapGrayDosesOfPercutaneousTherapy() {
    var actual = mapBestrahlung("P", dosis("50", "Gy"), dosis("2", "Gy"));

    var perkutan = actual.getApplikationsart().getPerkutan();
    assertThat(perkutan.getGesamtdosis().getDosis()).isEqualByComparingTo("50");
    assertThat(perkutan.getGesamtdosis().getEinheit()).isEqualTo("Gy");
    assertThat(perkutan.getEinzeldosis().getDosis()).isEqualByComparingTo("2");
  }

  @Test
  void shouldNotMapDosesInGbqForOtherThanMetabolicTherapy() {
    var perkutan = mapBestrahlung("P", dosis("50", "Gy"), dosis("2", "GBq"));
    assertThat(perkutan.getApplikationsart().getPerkutan().getGesamtdosis()).isNotNull();
    assertThat(perkutan.getApplikationsart().getPerkutan().getEinzeldosis()).isNull();

    var kontakt = mapBestrahlung("KHDR", dosis("7", "GBq"), null);
    assertThat(kontakt.getApplikationsart().getKontakt().getGesamtdosis()).isNull();

    var sonstige = mapBestrahlung("S", null, dosis("1", "GBq"));
    assertThat(sonstige.getApplikationsart().getSonstige().getEinzeldosis()).isNull();
  }

  @Test
  void shouldDefaultNebenwirkungenToUnknownIfUnset() {
    var st = new MengeST.ST();
    st.setSTID("1");
    var mengeST = new MengeST();
    mengeST.getST().add(st);

    var actual = StrahlentherapieMapper.map(mengeST, "behandlungsende");

    assertThat(actual).singleElement();
    assertThat(actual.get(0).getNebenwirkungen().getGradMaximal2OderUnbekannt()).isEqualTo("U");
  }
}
