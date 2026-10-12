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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.basisdatensatz.obds.v2.DatumNUTyp;
import de.basisdatensatz.obds.v2.HormonrezeptorTyp;
import de.basisdatensatz.obds.v2.JNUTyp;
import de.basisdatensatz.obds.v2.ModulAllgemeinTyp;
import de.basisdatensatz.obds.v2.ModulDarmTyp;
import de.basisdatensatz.obds.v2.ModulMalignesMelanomTyp;
import de.basisdatensatz.obds.v2.ModulMammaTyp;
import de.basisdatensatz.obds.v2.ModulProstataTyp;
import de.basisdatensatz.obds.v3.AnlassGleasonScoreTyp;
import de.basisdatensatz.obds.v3.JNU;
import de.basisdatensatz.obds.v3.RASMutationTyp;
import java.math.BigDecimal;
import java.util.GregorianCalendar;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ModulMapperTest {

  @Test
  void testShouldMapModuleProstata() throws DatatypeConfigurationException {
    var source = new ModulProstataTyp();
    source.setAnlassGleasonScore("S");
    source.setAnzahlStanzen(2);
    source.setAnzahlPosStanzen(3);

    var calendarPSA = new GregorianCalendar();
    calendarPSA.clear();
    calendarPSA.set(2024, 9, 21);
    source.setDatumPSA(DatatypeFactory.newInstance().newXMLGregorianCalendar(calendarPSA));

    var calendarStanzen = new GregorianCalendar();
    calendarStanzen.clear();
    calendarStanzen.set(2024, 9, 22);
    source.setDatumStanzen(DatatypeFactory.newInstance().newXMLGregorianCalendar(calendarStanzen));

    var gleasonScore = new ModulProstataTyp.GleasonScore();
    gleasonScore.setGleasonGradPrimaer("4");
    gleasonScore.setGleasonGradSekundaer("5");
    gleasonScore.setGleasonScoreErgebnis("9");
    source.setGleasonScore(gleasonScore);

    source.setKomplPostOPClavienDindo(JNUTyp.J);

    var caBefallStanze = new ModulProstataTyp.CaBefallStanze();
    caBefallStanze.setProzentzahl(50.123f);
    source.setCaBefallStanze(caBefallStanze);

    var actual = ModulMapper.map(source);

    assertThat(actual.getAnlassGleasonScore()).isEqualTo(AnlassGleasonScoreTyp.S);
    assertThat(actual.getAnzahlStanzen()).isEqualTo(2);
    assertThat(actual.getAnzahlPosStanzen()).isEqualTo(3);
    assertThat(actual.getDatumPSA().getValue().toXMLFormat()).startsWith("2024-10-21");
    assertThat(actual.getDatumStanzen().toXMLFormat()).startsWith("2024-10-22");
    assertThat(actual.getGleasonScore().getGradPrimaer()).isEqualTo("4");
    assertThat(actual.getGleasonScore().getGradSekundaer()).isEqualTo("5");
    assertThat(actual.getGleasonScore().getScoreErgebnis()).isEqualTo("9");
    assertThat(actual.getKomplPostOPClavienDindo()).isEqualTo(de.basisdatensatz.obds.v3.JNU.J);
    assertThat(actual.getCaBefallStanze().getProzentzahl()).isEqualTo(50);
  }

  @Test
  void shouldMapAnlassGleasonScoreWithoutGleasonScore() {
    var source = new ModulProstataTyp();
    source.setAnlassGleasonScore("O");

    var actual = ModulMapper.map(source);

    assertThat(actual.getGleasonScore()).isNull();
    assertThat(actual.getAnlassGleasonScore()).isEqualTo(AnlassGleasonScoreTyp.O);
  }

  @Test
  void shouldMapGleasonScoreWithoutAnlassGleasonScore() {
    var source = new ModulProstataTyp();
    var gleasonScore = new ModulProstataTyp.GleasonScore();
    gleasonScore.setGleasonScoreErgebnis("6");
    source.setGleasonScore(gleasonScore);

    var actual = ModulMapper.map(source);

    assertThat(actual.getGleasonScore().getScoreErgebnis()).isEqualTo("6");
    assertThat(actual.getAnlassGleasonScore()).isNull();
  }

  @Test
  void shouldRejectMissingSource() {
    assertThrows(IllegalArgumentException.class, () -> ModulMapper.map((ModulMammaTyp) null));
    assertThrows(IllegalArgumentException.class, () -> ModulMapper.map((ModulDarmTyp) null));
    assertThrows(
        IllegalArgumentException.class, () -> ModulMapper.map((ModulMalignesMelanomTyp) null));
    assertThrows(IllegalArgumentException.class, () -> ModulMapper.map((ModulAllgemeinTyp) null));
  }

  private static ModulMammaTyp mammaSource() {
    var source = new ModulMammaTyp();
    source.setPraetherapeutischerMenopausenstatus("3");
    source.setHormonrezeptorStatusOestrogen(HormonrezeptorTyp.P);
    source.setHormonrezeptorStatusProgesteron(HormonrezeptorTyp.N);
    source.setHer2NeuStatus(HormonrezeptorTyp.U);
    source.setPraeopDrahtmarkierung("M");
    source.setIntraopPraeparatkontrolle("S");
    source.setTumorgroesseInvasiv("25");
    source.setTumorgroesseDCIS("U");
    return source;
  }

  private static ModulDarmTyp darmSource() {
    var source = new ModulDarmTyp();
    source.setRektumAbstandAnokutanlinie("8");
    source.setRektumAbstandAboralerResektionsrand("12");
    source.setRektumAbstandCircResektionsebene("3");
    source.setRektumQualitaetTME("2");
    source.setRektumMRTDuennschichtAngabemesorektaleFaszie("D");
    source.setArtEingriff("E");
    source.setRektumAnzeichnungStomaposition("K");
    source.setGradRektumAnastomoseninsuffizienz("B");
    source.setASA("2");
    source.setRASMutation("W");
    return source;
  }

  private static ModulMalignesMelanomTyp melanomSource() {
    var source = new ModulMalignesMelanomTyp();
    source.setSicherheitsabstandPrimaertumor(10);
    source.setTumordicke(new BigDecimal("1.4"));
    source.setLDH(250);
    source.setUlzeration(JNUTyp.J);
    return source;
  }

  private static XMLGregorianCalendar date(int year, int month, int day)
      throws DatatypeConfigurationException {
    return DatatypeFactory.newInstance()
        .newXMLGregorianCalendarDate(
            year, month, day, javax.xml.datatype.DatatypeConstants.FIELD_UNDEFINED);
  }

  @Test
  void shouldMapModulMamma() {
    var actual = ModulMapper.map(mammaSource());

    assertThat(actual.getPraetherapeutischerMenopausenstatus()).isEqualTo("3");
    assertThat(actual.getHormonrezeptorStatusOestrogen())
        .isEqualTo(de.basisdatensatz.obds.v3.HormonrezeptorTyp.P);
    assertThat(actual.getHormonrezeptorStatusProgesteron())
        .isEqualTo(de.basisdatensatz.obds.v3.HormonrezeptorTyp.N);
    assertThat(actual.getHer2NeuStatus()).isEqualTo(de.basisdatensatz.obds.v3.HormonrezeptorTyp.U);
    assertThat(actual.getPraeopDrahtmarkierung()).isEqualTo("M");
    assertThat(actual.getIntraopPraeparatkontrolle()).isEqualTo("S");
    assertThat(actual.getTumorgroesseInvasiv()).isEqualTo("25");
    assertThat(actual.getTumorgroesseDCIS()).isEqualTo("U");
  }

  @Test
  void shouldMapModulMammaWithUnsetHormonrezeptoren() {
    var actual = ModulMapper.map(new ModulMammaTyp());

    assertThat(actual.getHormonrezeptorStatusOestrogen()).isNull();
    assertThat(actual.getHormonrezeptorStatusProgesteron()).isNull();
    assertThat(actual.getHer2NeuStatus()).isNull();
  }

  @Test
  void shouldMapModulDarm() {
    var actual = ModulMapper.map(darmSource());

    assertThat(actual.getRektumAbstandAnokutanlinie()).isEqualTo("8");
    assertThat(actual.getRektumAbstandAboralerResektionsrand()).isEqualTo("12");
    assertThat(actual.getRektumAbstandCircResektionsebene()).isEqualTo("3");
    assertThat(actual.getRektumQualitaetTME()).isEqualTo("2");
    assertThat(actual.getRektumMRTDuennschichtAngabemesorektaleFaszie()).isEqualTo("D");
    assertThat(actual.getArtEingriff()).isEqualTo("E");
    assertThat(actual.getRektumAnzeichnungStomaposition()).isEqualTo("K");
    assertThat(actual.getGradRektumAnastomoseninsuffizienz()).isEqualTo("B");
    assertThat(actual.getASA()).isEqualTo("2");
    assertThat(actual.getRASMutation()).isEqualTo(RASMutationTyp.W);
  }

  @Test
  void shouldMapModulDarmWithoutRasMutation() {
    assertThat(ModulMapper.map(new ModulDarmTyp()).getRASMutation()).isNull();
  }

  @Test
  void shouldMapModulMalignesMelanom() {
    var actual = ModulMapper.map(melanomSource());

    assertThat(actual.getSicherheitsabstandPrimaertumor()).isEqualTo(10);
    assertThat(actual.getTumordicke()).isEqualByComparingTo("1.4");
    assertThat(actual.getLDH()).isEqualTo(250);
    assertThat(actual.getUlzeration()).isEqualTo(JNU.J);
  }

  @Nested
  class ModulAllgemeinTest {

    private DatumNUTyp datum(XMLGregorianCalendar datum) {
      var result = new DatumNUTyp();
      result.setDatum(datum);
      return result;
    }

    private DatumNUTyp nu(String nu) {
      var result = new DatumNUTyp();
      result.setNU(nu);
      return result;
    }

    @Test
    void shouldMapDatumToSozialdienstkontaktAndStudienteilnahme()
        throws DatatypeConfigurationException {
      var source = new ModulAllgemeinTyp();
      source.setDatumSozialdienstkontakt(datum(date(2024, 6, 12)));
      source.setDatumStudienrekrutierung(datum(date(2024, 6, 20)));

      var actual = ModulMapper.map(source);

      assertThat(actual.getSozialdienstkontakt().getDatum().toXMLFormat()).startsWith("2024-06-12");
      assertThat(actual.getSozialdienstkontakt().getN()).isNull();
      assertThat(actual.getStudienteilnahme().getDatum().toXMLFormat()).startsWith("2024-06-20");
      assertThat(actual.getStudienteilnahme().getNU()).isNull();
      // gibt es in oBDS v2 nicht
      assertThat(actual.getPsychoonkologiekontakt()).isNull();
    }

    @Test
    void shouldMapNoContactAndNoStudy() {
      var source = new ModulAllgemeinTyp();
      source.setDatumSozialdienstkontakt(nu("N"));
      source.setDatumStudienrekrutierung(nu("N"));

      var actual = ModulMapper.map(source);

      assertThat(actual.getSozialdienstkontakt().getN()).isEqualTo("N");
      assertThat(actual.getSozialdienstkontakt().getDatum()).isNull();
      assertThat(actual.getStudienteilnahme().getNU())
          .isEqualTo(de.basisdatensatz.obds.v3.DatumNUTyp.NeinOderUnbekannt.N);
    }

    @Test
    void shouldOmitUnknownSozialdienstkontaktAsTheTypeHasNoUnknown() {
      var source = new ModulAllgemeinTyp();
      source.setDatumSozialdienstkontakt(nu("U"));
      source.setDatumStudienrekrutierung(nu("U"));

      var actual = ModulMapper.map(source);

      assertThat(actual.getSozialdienstkontakt()).isNull();
      assertThat(actual.getStudienteilnahme().getNU())
          .isEqualTo(de.basisdatensatz.obds.v3.DatumNUTyp.NeinOderUnbekannt.U);
    }
  }

  /** In der Pathologie sind in oBDS v3 nur Teile der Module erlaubt. */
  @Nested
  class PathoTest {

    @Test
    void shouldOnlyMapAllowedFieldsOfModulMamma() {
      var actual = ModulMapper.mapPatho(mammaSource());

      assertThat(actual.getHormonrezeptorStatusOestrogen())
          .isEqualTo(de.basisdatensatz.obds.v3.HormonrezeptorTyp.P);
      assertThat(actual.getHormonrezeptorStatusProgesteron())
          .isEqualTo(de.basisdatensatz.obds.v3.HormonrezeptorTyp.N);
      assertThat(actual.getHer2NeuStatus())
          .isEqualTo(de.basisdatensatz.obds.v3.HormonrezeptorTyp.U);
      assertThat(actual.getTumorgroesseInvasiv()).isEqualTo("25");
      assertThat(actual.getTumorgroesseDCIS()).isEqualTo("U");
      assertThat(actual.getPraetherapeutischerMenopausenstatus()).isNull();
      assertThat(actual.getPraeopDrahtmarkierung()).isNull();
      assertThat(actual.getIntraopPraeparatkontrolle()).isNull();
    }

    @Test
    void shouldOnlyMapAllowedFieldsOfModulDarm() {
      var actual = ModulMapper.mapPatho(darmSource());

      assertThat(actual.getRektumAbstandAboralerResektionsrand()).isEqualTo("12");
      assertThat(actual.getRektumAbstandCircResektionsebene()).isEqualTo("3");
      assertThat(actual.getRektumQualitaetTME()).isEqualTo("2");
      assertThat(actual.getRASMutation()).isEqualTo(RASMutationTyp.W);
      assertThat(actual.getRektumAbstandAnokutanlinie()).isNull();
      assertThat(actual.getRektumMRTDuennschichtAngabemesorektaleFaszie()).isNull();
      assertThat(actual.getArtEingriff()).isNull();
      assertThat(actual.getRektumAnzeichnungStomaposition()).isNull();
      assertThat(actual.getGradRektumAnastomoseninsuffizienz()).isNull();
      assertThat(actual.getASA()).isNull();
    }

    @Test
    void shouldOnlyMapAllowedFieldsOfModulProstata() throws DatatypeConfigurationException {
      var source = new ModulProstataTyp();
      source.setAnlassGleasonScore("S");
      source.setAnzahlStanzen(12);
      source.setAnzahlPosStanzen(3);
      source.setDatumStanzen(date(2024, 6, 5));
      source.setDatumPSA(date(2024, 6, 6));
      source.setPSA(new BigDecimal("6.5"));
      source.setKomplPostOPClavienDindo(JNUTyp.N);

      var actual = ModulMapper.mapPatho(source);

      assertThat(actual.getAnlassGleasonScore()).isEqualTo(AnlassGleasonScoreTyp.S);
      assertThat(actual.getAnzahlStanzen()).isEqualTo(12);
      assertThat(actual.getAnzahlPosStanzen()).isEqualTo(3);
      assertThat(actual.getDatumStanzen().toXMLFormat()).startsWith("2024-06-05");
      assertThat(actual.getPSA()).isNull();
      assertThat(actual.getDatumPSA()).isNull();
      assertThat(actual.getKomplPostOPClavienDindo()).isNull();
    }

    @Test
    void shouldOnlyMapAllowedFieldsOfModulMalignesMelanom() {
      var actual = ModulMapper.mapPatho(melanomSource());

      assertThat(actual.getSicherheitsabstandPrimaertumor()).isEqualTo(10);
      assertThat(actual.getTumordicke()).isEqualByComparingTo("1.4");
      assertThat(actual.getUlzeration()).isEqualTo(JNU.J);
      assertThat(actual.getLDH()).isNull();
    }
  }
}
