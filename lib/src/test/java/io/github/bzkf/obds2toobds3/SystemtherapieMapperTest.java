package io.github.bzkf.obds2toobds3;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v2.ADTGEKID;
import de.basisdatensatz.obds.v3.SYSTTyp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SystemtherapieMapperTest {

  @ParameterizedTest
  @CsvSource({
    "CH,CH",
    "HO,HO",
    "IM,IM",
    "WS,WS",
    "AS,AS",
    "ZS,ZS",
    "SO,SO",
    "CH;IM,CI",
    "CH;ZS,CZ",
    "CH;IM;ZS,CIZ",
    "IM;ZS,IZ",
    "KM,SZ",
    "CH;IM;SO,CI",
    "CH;SO,CH",
    "CH;HO,CH",
    "CH;HO;IM,CI",
    "CH;HO;IM;SO,CI",
    "CH;HO;IM;SO;ZS,CIZ",
    "CH;HO;IM;ZS,CIZ",
    "CH;HO;SO,CH",
    "CH;HO;SO;ZS,CZ",
    "CH;HO;ZS,CZ",
    "CH;IM;SO;ZS,CIZ",
    "CH;SO;ZS,CZ",
    "HO;IM,IM",
    "HO;IM;ZS,IZ",
    "HO;SO,HO",
    "HO;SO;ZS,HO",
    "HO;ZS,HO",
    "IM;SO,IM",
    "IM;SO;ZS,IZ",
    "SO;ZS,ZS",
    "ZS;SO;IM;CH,CIZ",
  })
  void shouldMapTherapieart(String in, String out) {
    var menge = new ADTGEKID.MengePatient.Patient.MengeMeldung.Meldung.MengeSYST();
    var syst = new ADTGEKID.MengePatient.Patient.MengeMeldung.Meldung.MengeSYST.SYST();
    menge.getSYST().add(syst);

    var mengeTherapieart =
        new ADTGEKID.MengePatient.Patient.MengeMeldung.Meldung.MengeSYST.SYST.MengeTherapieart();
    for (var art : in.split(";")) {
      mengeTherapieart.getSYSTTherapieart().add(art);
    }
    syst.setMengeTherapieart(mengeTherapieart);

    var actual = SystemtherapieMapper.map(menge, SYSTTyp.Meldeanlass.BEHANDLUNGSENDE.value());

    assertThat(actual).hasSize(1);
    assertThat(actual.get(0).getTherapieart()).isEqualTo(SYSTTyp.Therapieart.fromValue(out));
  }

  @Test
  void shouldDefaultNebenwirkungenToUnknownIfUnset() {
    var menge = new ADTGEKID.MengePatient.Patient.MengeMeldung.Meldung.MengeSYST();
    var syst = new ADTGEKID.MengePatient.Patient.MengeMeldung.Meldung.MengeSYST.SYST();
    syst.setSYSTID("1");
    menge.getSYST().add(syst);

    var actual = SystemtherapieMapper.map(menge, SYSTTyp.Meldeanlass.BEHANDLUNGSENDE.value());

    assertThat(actual).singleElement();
    assertThat(actual.get(0).getNebenwirkungen().getGradMaximal2OderUnbekannt()).isEqualTo("U");
  }
}
