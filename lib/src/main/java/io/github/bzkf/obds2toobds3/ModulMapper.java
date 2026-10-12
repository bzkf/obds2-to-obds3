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

import de.basisdatensatz.obds.v3.*;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mappt die oBDS v2 Module (Mamma, Darm, Prostata, Malignes Melanom, Allgemein) nach oBDS v3.
 *
 * <p>In oBDS v3 gibt es für Pathologie-Meldungen eingeschränkte Varianten der Module (Mamma, Darm,
 * Prostata, Malignes Melanom). Diese sind über {@code mapPatho} verfügbar; Felder, die dort nicht
 * erlaubt sind, werden verworfen und im Log gemeldet.
 */
class ModulMapper {
  private static final Logger LOG = LoggerFactory.getLogger(ModulMapper.class);

  private ModulMapper() {}

  public static ModulProstataTyp map(de.basisdatensatz.obds.v2.ModulProstataTyp source) {
    requireSource(source, "ModulProstata");

    var result = new ModulProstataTyp();
    if (source.getGleasonScore() != null) {
      var gleasonScore = new GleasonScoreTyp();
      gleasonScore.setGradPrimaer(source.getGleasonScore().getGleasonGradPrimaer());
      gleasonScore.setGradSekundaer(source.getGleasonScore().getGleasonGradSekundaer());
      gleasonScore.setScoreErgebnis(source.getGleasonScore().getGleasonScoreErgebnis());
      result.setGleasonScore(gleasonScore);
    }
    // Der Anlass ist unabhängig vom Gleason Score optional und wird daher separat gemappt
    if (source.getAnlassGleasonScore() != null) {
      result.setAnlassGleasonScore(AnlassGleasonScoreTyp.fromValue(source.getAnlassGleasonScore()));
    }
    // oBDS v2 verwendet hier abweichend Format yyyy-MM-dd! Daher direktes Mapping
    result.setDatumStanzen(source.getDatumStanzen());
    result.setAnzahlStanzen(source.getAnzahlStanzen());
    result.setAnzahlPosStanzen(source.getAnzahlPosStanzen());
    // oBDS v2 verwendet hier abweichend Format yyyy-MM-dd! Daher direktes Mapping
    MapperUtils.mapDatumTagOderMonatGenauTypSchaetzOptional(source.getDatumPSA())
        .ifPresent(result::setDatumPSA);
    result.setPSA(source.getPSA());
    if (source.getCaBefallStanze() != null) {
      var caBefallStanze = new CaBefallStanzeTyp();

      if (source.getCaBefallStanze().getProzentzahl() != null) {
        var prozentzahl = source.getCaBefallStanze().getProzentzahl();
        if (prozentzahl % 1 != 0) {
          LOG.warn("Prozentzahl has decimal places. Ignored when mapping to v3.");
        }
        caBefallStanze.setProzentzahl(source.getCaBefallStanze().getProzentzahl().intValue());
      }

      caBefallStanze.setU(source.getCaBefallStanze().getU());
      result.setCaBefallStanze(caBefallStanze);
    }
    if (source.getKomplPostOPClavienDindo() != null) {
      result.setKomplPostOPClavienDindo(JNU.fromValue(source.getKomplPostOPClavienDindo().value()));
    }
    return result;
  }

  public static ModulMammaTyp map(de.basisdatensatz.obds.v2.ModulMammaTyp source) {
    requireSource(source, "ModulMamma");

    // Die Felder und Wertebereiche sind in oBDS v2 und v3 identisch
    var result = new ModulMammaTyp();
    result.setPraetherapeutischerMenopausenstatus(source.getPraetherapeutischerMenopausenstatus());
    result.setHormonrezeptorStatusOestrogen(
        mapHormonrezeptor(source.getHormonrezeptorStatusOestrogen()));
    result.setHormonrezeptorStatusProgesteron(
        mapHormonrezeptor(source.getHormonrezeptorStatusProgesteron()));
    result.setHer2NeuStatus(mapHormonrezeptor(source.getHer2NeuStatus()));
    result.setPraeopDrahtmarkierung(source.getPraeopDrahtmarkierung());
    result.setIntraopPraeparatkontrolle(source.getIntraopPraeparatkontrolle());
    result.setTumorgroesseInvasiv(source.getTumorgroesseInvasiv());
    result.setTumorgroesseDCIS(source.getTumorgroesseDCIS());
    return result;
  }

  public static ModulDarmTyp map(de.basisdatensatz.obds.v2.ModulDarmTyp source) {
    requireSource(source, "ModulDarm");

    // Die Felder und Wertebereiche sind in oBDS v2 und v3 identisch
    var result = new ModulDarmTyp();
    result.setRektumAbstandAnokutanlinie(source.getRektumAbstandAnokutanlinie());
    result.setRektumAbstandAboralerResektionsrand(source.getRektumAbstandAboralerResektionsrand());
    result.setRektumAbstandCircResektionsebene(source.getRektumAbstandCircResektionsebene());
    result.setRektumQualitaetTME(source.getRektumQualitaetTME());
    result.setRektumMRTDuennschichtAngabemesorektaleFaszie(
        source.getRektumMRTDuennschichtAngabemesorektaleFaszie());
    result.setArtEingriff(source.getArtEingriff());
    result.setRektumAnzeichnungStomaposition(source.getRektumAnzeichnungStomaposition());
    result.setGradRektumAnastomoseninsuffizienz(source.getGradRektumAnastomoseninsuffizienz());
    result.setASA(source.getASA());
    if (source.getRASMutation() != null) {
      result.setRASMutation(RASMutationTyp.fromValue(source.getRASMutation()));
    }
    return result;
  }

  public static ModulMalignesMelanomTyp map(
      de.basisdatensatz.obds.v2.ModulMalignesMelanomTyp source) {
    requireSource(source, "ModulMalignesMelanom");

    // Die Felder sind in oBDS v2 und v3 identisch. Die Tumordicke erlaubt in oBDS v3 mehr
    // Nachkommastellen und einen kleineren Minimalwert, jeder v2 Wert ist daher auch in v3 gültig.
    var result = new ModulMalignesMelanomTyp();
    result.setSicherheitsabstandPrimaertumor(source.getSicherheitsabstandPrimaertumor());
    result.setTumordicke(source.getTumordicke());
    result.setLDH(source.getLDH());
    if (source.getUlzeration() != null) {
      result.setUlzeration(JNU.fromValue(source.getUlzeration().value()));
    }
    return result;
  }

  /**
   * oBDS v2: <code>DatumSozialdienstkontakt</code> und <code>DatumStudienrekrutierung</code>; oBDS
   * v3: <code>Sozialdienstkontakt</code>, <code>Psychoonkologiekontakt</code> (nicht in oBDS v2)
   * und <code>Studienteilnahme</code>.
   */
  public static ModulAllgemeinTyp map(de.basisdatensatz.obds.v2.ModulAllgemeinTyp source) {
    requireSource(source, "ModulAllgemein");

    var result = new ModulAllgemeinTyp();

    var sozialdienstkontakt = source.getDatumSozialdienstkontakt();
    if (sozialdienstkontakt != null) {
      var mapped = new DatumNTyp();
      if (sozialdienstkontakt.getDatum() != null) {
        mapped.setDatum(sozialdienstkontakt.getDatum());
        result.setSozialdienstkontakt(mapped);
      } else if ("N".equals(sozialdienstkontakt.getNU())) {
        mapped.setN("N");
        result.setSozialdienstkontakt(mapped);
      } else {
        // Der Typ in oBDS v3 kennt keine Ausprägung "Unbekannt" - das Element wird ausgelassen
        LOG.debug("Sozialdienstkontakt is 'U' (unbekannt) which is not available in oBDS v3.");
      }
    }

    var studienrekrutierung = source.getDatumStudienrekrutierung();
    if (studienrekrutierung != null) {
      var mapped = new DatumNUTyp();
      mapped.setDatum(studienrekrutierung.getDatum());
      if (studienrekrutierung.getNU() != null) {
        mapped.setNU(DatumNUTyp.NeinOderUnbekannt.fromValue(studienrekrutierung.getNU()));
      }
      result.setStudienteilnahme(mapped);
    }

    return result;
  }

  /** Mappt das Modul Mamma auf die eingeschränkte Variante für Pathologie-Meldungen. */
  public static ModulMammaPathoTyp mapPatho(de.basisdatensatz.obds.v2.ModulMammaTyp source) {
    var full = map(source);

    var result = new ModulMammaPathoTyp();
    result.setHormonrezeptorStatusOestrogen(full.getHormonrezeptorStatusOestrogen());
    result.setHormonrezeptorStatusProgesteron(full.getHormonrezeptorStatusProgesteron());
    result.setHer2NeuStatus(full.getHer2NeuStatus());
    result.setTumorgroesseInvasiv(full.getTumorgroesseInvasiv());
    result.setTumorgroesseDCIS(full.getTumorgroesseDCIS());

    warnAboutDroppedFields(
        "Modul_Mamma",
        droppedFields(
            "Praetherapeutischer_Menopausenstatus", full.getPraetherapeutischerMenopausenstatus(),
            "PraeopDrahtmarkierung", full.getPraeopDrahtmarkierung(),
            "IntraopPraeparatkontrolle", full.getIntraopPraeparatkontrolle()));
    return result;
  }

  /** Mappt das Modul Darm auf die eingeschränkte Variante für Pathologie-Meldungen. */
  public static ModulDarmPathoTyp mapPatho(de.basisdatensatz.obds.v2.ModulDarmTyp source) {
    var full = map(source);

    var result = new ModulDarmPathoTyp();
    result.setRektumAbstandAboralerResektionsrand(full.getRektumAbstandAboralerResektionsrand());
    result.setRektumAbstandCircResektionsebene(full.getRektumAbstandCircResektionsebene());
    result.setRektumQualitaetTME(full.getRektumQualitaetTME());
    result.setRASMutation(full.getRASMutation());

    warnAboutDroppedFields(
        "Modul_Darm",
        droppedFields(
            "RektumAbstandAnokutanlinie", full.getRektumAbstandAnokutanlinie(),
            "RektumMRTDuennschichtAngabemesorektaleFaszie",
                full.getRektumMRTDuennschichtAngabemesorektaleFaszie(),
            "ArtEingriff", full.getArtEingriff(),
            "RektumAnzeichnungStomaposition", full.getRektumAnzeichnungStomaposition(),
            "GradRektumAnastomoseninsuffizienz", full.getGradRektumAnastomoseninsuffizienz(),
            "ASA", full.getASA()));
    return result;
  }

  /** Mappt das Modul Prostata auf die eingeschränkte Variante für Pathologie-Meldungen. */
  public static ModulProstataPathoTyp mapPatho(de.basisdatensatz.obds.v2.ModulProstataTyp source) {
    var full = map(source);

    var result = new ModulProstataPathoTyp();
    result.setGleasonScore(full.getGleasonScore());
    result.setAnlassGleasonScore(full.getAnlassGleasonScore());
    result.setDatumStanzen(full.getDatumStanzen());
    result.setAnzahlStanzen(full.getAnzahlStanzen());
    result.setAnzahlPosStanzen(full.getAnzahlPosStanzen());
    result.setCaBefallStanze(full.getCaBefallStanze());

    warnAboutDroppedFields(
        "Modul_Prostata",
        droppedFields(
            "PSA", full.getPSA(),
            "DatumPSA", full.getDatumPSA(),
            "KomplPostOP_ClavienDindo", full.getKomplPostOPClavienDindo()));
    return result;
  }

  /** Mappt das Modul Malignes Melanom auf die eingeschränkte Variante für Pathologie-Meldungen. */
  public static ModulMalignesMelanomPathoTyp mapPatho(
      de.basisdatensatz.obds.v2.ModulMalignesMelanomTyp source) {
    var full = map(source);

    var result = new ModulMalignesMelanomPathoTyp();
    result.setSicherheitsabstandPrimaertumor(full.getSicherheitsabstandPrimaertumor());
    result.setTumordicke(full.getTumordicke());
    result.setUlzeration(full.getUlzeration());

    warnAboutDroppedFields("Modul_Malignes_Melanom", droppedFields("LDH", full.getLDH()));
    return result;
  }

  private static HormonrezeptorTyp mapHormonrezeptor(
      de.basisdatensatz.obds.v2.HormonrezeptorTyp source) {
    return null == source ? null : HormonrezeptorTyp.fromValue(source.value());
  }

  private static void requireSource(Object source, String modul) {
    if (null == source) {
      throw new IllegalArgumentException(
          "ADT_GEKID " + modul + " should not be null at this point");
    }
  }

  /**
   * @param namesAndValues abwechselnd der Name eines Feldes und sein Wert
   * @return die Namen der Felder, die einen Wert haben
   */
  private static List<String> droppedFields(Object... namesAndValues) {
    var result = new ArrayList<String>();
    for (var i = 0; i < namesAndValues.length; i += 2) {
      if (null != namesAndValues[i + 1]) {
        result.add((String) namesAndValues[i]);
      }
    }
    return result;
  }

  private static void warnAboutDroppedFields(String modul, List<String> fields) {
    if (!fields.isEmpty()) {
      LOG.warn(
          "{} fields {} are not allowed in a Pathologie in oBDS v3 and are dropped.",
          modul,
          fields);
    }
  }
}
