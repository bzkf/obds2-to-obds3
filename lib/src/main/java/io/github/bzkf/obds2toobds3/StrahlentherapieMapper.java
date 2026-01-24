package io.github.bzkf.obds2toobds3;

import de.basisdatensatz.obds.v2.ADTGEKID.MengePatient.Patient.MengeMeldung.Meldung.MengeST.ST.MengeBestrahlung.Bestrahlung;
import de.basisdatensatz.obds.v2.ADTGEKID.MengePatient.Patient.MengeMeldung.Meldung.MengeST.ST.MengeNebenwirkung;
import de.basisdatensatz.obds.v3.NebenwirkungTyp;
import de.basisdatensatz.obds.v3.NebenwirkungTyp.MengeNebenwirkung.Nebenwirkung;
import de.basisdatensatz.obds.v3.STTyp;
import de.basisdatensatz.obds.v3.STTyp.Meldeanlass;
import de.basisdatensatz.obds.v3.STTyp.MengeBestrahlung;
import de.basisdatensatz.obds.v3.STTyp.MengeBestrahlung.Bestrahlung.Applikationsart;
import de.basisdatensatz.obds.v3.SeiteZielgebietTyp;
import de.basisdatensatz.obds.v3.StrahlendosisTyp;
import de.basisdatensatz.obds.v3.ZielgebietTyp;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class StrahlentherapieMapper {
  private static final Logger LOG = LoggerFactory.getLogger(StrahlentherapieMapper.class);

  private StrahlentherapieMapper() {}

  public static List<STTyp> map(
      de.basisdatensatz.obds.v2.ADTGEKID.MengePatient.Patient.MengeMeldung.Meldung.MengeST mengeST,
      String meldeanlass) {
    var result = new ArrayList<STTyp>();
    for (var source : mengeST.getST()) {
      MDC.put("STID", source.getSTID());
      var stTyp = new STTyp();
      stTyp.setSTID(source.getSTID());

      stTyp.setMeldeanlass(Meldeanlass.fromValue(meldeanlass));

      // v2: K, P, S, X
      // v3: K, P, S, X, O
      stTyp.setIntention(source.getSTIntention());

      // v2: O, A, N, I, S
      // v3: O, A, N, I, S, Z
      stTyp.setStellungOP(source.getSTStellungOP());

      // v2: A, E, V, P, S, U
      // v3: A, E, V, P, S, U, F, T
      stTyp.setEndeGrund(source.getSTEndeGrund());

      var mengeBestrahlung = new MengeBestrahlung();

      for (var bestrahlungV2 : source.getMengeBestrahlung().getBestrahlung()) {
        var bestrahlungV3 = new STTyp.MengeBestrahlung.Bestrahlung();

        var mappedApplikationsart = mapApplikationsart(bestrahlungV2);
        bestrahlungV3.setApplikationsart(mappedApplikationsart);

        MapperUtils.mapDateString(bestrahlungV2.getSTBeginnDatum())
            .ifPresent(d -> bestrahlungV3.setBeginn(d.getValue()));
        MapperUtils.mapDateString(bestrahlungV2.getSTEndeDatum())
            .ifPresent(d -> bestrahlungV3.setEnde(d.getValue()));

        mengeBestrahlung.getBestrahlung().add(bestrahlungV3);
      }

      stTyp.setMengeBestrahlung(mengeBestrahlung);

      if (source.getMengeNebenwirkung() != null) {
        var mappedNebenwirkungen = mapNebenwirkungen(source.getMengeNebenwirkung());
        stTyp.setNebenwirkungen(mappedNebenwirkungen);
      }

      result.add(stTyp);
      MDC.remove("STID");
    }

    return result;
  }

  private static NebenwirkungTyp mapNebenwirkungen(MengeNebenwirkung mengeNebenwirkung) {
    var mengeNebenwirkungV3 = new de.basisdatensatz.obds.v3.NebenwirkungTyp.MengeNebenwirkung();

    var result = new NebenwirkungTyp();

    for (var nebenwirkungV2 : mengeNebenwirkung.getSTNebenwirkung()) {
      var nebenwirkungV3 = new Nebenwirkung();

      if (nebenwirkungV2.getNebenwirkungGrad() != null
          && (nebenwirkungV2.getNebenwirkungGrad().equals("K")
              || nebenwirkungV2.getNebenwirkungGrad().equals("U"))) {
        if (mengeNebenwirkung.getSTNebenwirkung().size() > 1) {
          LOG.warn(
              "ST has multiple Nebenwirkungen, but should only contain one if set to 'K' or 'U'.");
        }

        if (nebenwirkungV2.getNebenwirkungArt() != null) {
          LOG.warn(
              "Nebenwirkung Grad is 'K' or 'U', but the Art is set. This information is lost after the mapping.");
        }

        result.setGradMaximal2OderUnbekannt(nebenwirkungV2.getNebenwirkungGrad());
        return result;
      }

      var art = new NebenwirkungTyp.MengeNebenwirkung.Nebenwirkung.Art();
      if (nebenwirkungV2.getNebenwirkungArt() != null) {
        art.setBezeichnung(nebenwirkungV2.getNebenwirkungArt());
      } else {
        LOG.warn("Nebenwirkung_Art is unset. Defaulting to 'Sonstige'.");
        art.setBezeichnung("Sonstige");
      }

      nebenwirkungV3.setArt(art);

      if (nebenwirkungV2.getNebenwirkungVersion() != null) {
        nebenwirkungV3.setVersion(nebenwirkungV2.getNebenwirkungVersion());
      } else {
        LOG.warn("Nebenwirkung_Version is unset. Defaulting to 'Sonstige'.");
        nebenwirkungV3.setVersion("Sonstige");
      }

      nebenwirkungV3.setGrad(nebenwirkungV2.getNebenwirkungGrad());

      mengeNebenwirkungV3.getNebenwirkung().add(nebenwirkungV3);
    }

    result.setMengeNebenwirkung(mengeNebenwirkungV3);
    return result;
  }

  private static Applikationsart mapApplikationsart(Bestrahlung bestrahlung) {
    var result = new Applikationsart();

    SeiteZielgebietTyp seiteZielgebiet = null;

    if (bestrahlung.getSTSeiteZielgebiet() != null) {
      seiteZielgebiet = SeiteZielgebietTyp.fromValue(bestrahlung.getSTSeiteZielgebiet());
    }

    StrahlendosisTyp einzeldosis = null;
    if (bestrahlung.getSTEinzeldosis() != null) {
      einzeldosis = new StrahlendosisTyp();
      einzeldosis.setDosis(bestrahlung.getSTEinzeldosis().getDosis());
      einzeldosis.setEinheit(bestrahlung.getSTEinzeldosis().getEinheit());
    }

    StrahlendosisTyp gesamtdosis = null;
    if (bestrahlung.getSTGesamtdosis() != null) {
      gesamtdosis = new StrahlendosisTyp();
      gesamtdosis.setDosis(bestrahlung.getSTGesamtdosis().getDosis());
      gesamtdosis.setEinheit(bestrahlung.getSTGesamtdosis().getEinheit());
    }

    var applikationsart = bestrahlung.getSTApplikationsart();
    if (applikationsart == null) {
      LOG.warn("Applikationsart is unset. Defaulting to 'Sonstige'");
      applikationsart = "S";
    }

    var zielgebiet = new ZielgebietTyp();
    zielgebiet.setCodeVersion2014(bestrahlung.getSTZielgebiet());

    if (applikationsart.startsWith("P")) {
      var perkutan = new Applikationsart.Perkutan();

      if (bestrahlung.getSTZielgebiet() != null) {
        perkutan.setZielgebiet(zielgebiet);
      } else {
        LOG.warn("Zielgebiet is unset in v2 but required in v3 for Perkutan.");
      }

      if (seiteZielgebiet != null) {
        perkutan.setSeiteZielgebiet(seiteZielgebiet);
      } else {
        LOG.warn(
            "Seite_Zielgebiet is unset in v2 but required for Perkutan in v3. Defaulting to 'U'.");
        perkutan.setSeiteZielgebiet(SeiteZielgebietTyp.U);
      }

      perkutan.setEinzeldosis(einzeldosis);
      perkutan.setGesamtdosis(gesamtdosis);
      switch (applikationsart) {
        case "P":
          break;
        case "PRCJ":
          perkutan.setRadiochemo("RCJ");
          break;
        case "PRCN":
          perkutan.setRadiochemo("RCN");
          break;
        default:
          LOG.warn("Unknown Perkutan radiation type: {}", applikationsart);
      }

      result.setPerkutan(perkutan);
      return result;
    }

    if (applikationsart.startsWith("K")) {
      var kontakt = new Applikationsart.Kontakt();
      kontakt.setZielgebiet(zielgebiet);

      if (bestrahlung.getSTZielgebiet() != null) {
        kontakt.setZielgebiet(zielgebiet);
      } else {
        LOG.warn("Zielgebiet is unset in v2 but required in v3.");
      }

      if (seiteZielgebiet != null) {
        kontakt.setSeiteZielgebiet(seiteZielgebiet);
      } else {
        LOG.warn(
            "Seite_Zielgebiet is unset in v2 but required for Kontakt in v3. Defaulting to 'U'.");
        kontakt.setSeiteZielgebiet(SeiteZielgebietTyp.U);
      }

      kontakt.setEinzeldosis(einzeldosis);
      kontakt.setGesamtdosis(gesamtdosis);
      kontakt.setInterstitiellEndokavitaer("K");

      switch (applikationsart) {
        case "K":
          LOG.warn(
              "Missing required information on Rate_Type in v3 for applikationsart {}",
              applikationsart);
          break;
        case "KHDR":
          kontakt.setRateType("HDR");
          break;
        case "KPDR":
          kontakt.setRateType("PDR");
          break;
        case "KLDR":
          kontakt.setRateType("LDR");
          break;
        default:
          LOG.warn("Unknown Kontakt radiation type: {}", applikationsart);
      }

      result.setKontakt(kontakt);
      return result;
    }

    if (applikationsart.startsWith("I")) {
      var kontakt = new Applikationsart.Kontakt();

      if (bestrahlung.getSTZielgebiet() != null) {
        kontakt.setZielgebiet(zielgebiet);
      } else {
        LOG.warn("Zielgebiet is unset in v2 but required in v3.");
      }

      if (seiteZielgebiet != null) {
        kontakt.setSeiteZielgebiet(seiteZielgebiet);
      } else {
        LOG.warn(
            "Seite_Zielgebiet is unset in v2 but required for Kontakt in v3. Defaulting to 'U'.");
        kontakt.setSeiteZielgebiet(SeiteZielgebietTyp.U);
      }

      kontakt.setEinzeldosis(einzeldosis);
      kontakt.setGesamtdosis(gesamtdosis);
      kontakt.setInterstitiellEndokavitaer("I");
      switch (applikationsart) {
        case "I":
          LOG.warn(
              "Missing required information on Rate_Type in v3 for applikationsart {}",
              applikationsart);
          break;
        case "IHDR":
          kontakt.setRateType("HDR");
          break;
        case "IPDR":
          kontakt.setRateType("PDR");
          break;
        case "ILDR":
          kontakt.setRateType("LDR");
          break;
        default:
          LOG.warn("Unknown Interstitiell radiation type: {}", applikationsart);
      }

      result.setKontakt(kontakt);
      return result;
    }

    if (applikationsart.startsWith("M")) {
      var metabolisch = new Applikationsart.Metabolisch();

      // For 'M' and 'S' it's fine if Seite_Zielgebiet and Zielgebiet are unset
      if (bestrahlung.getSTZielgebiet() != null) {
        metabolisch.setZielgebiet(zielgebiet);
      }

      if (seiteZielgebiet != null) {
        metabolisch.setSeiteZielgebiet(seiteZielgebiet);
      }

      switch (applikationsart) {
        case "M":
          metabolisch.setMetabolischTyp("M");
          break;
        case "MSIRT":
          metabolisch.setMetabolischTyp("SIRT");
          break;
        case "MPRRT":
          metabolisch.setMetabolischTyp("PRRT");
          break;
        default:
          LOG.warn("Unknown Metabolisch radiation type: {}", applikationsart);
      }

      result.setMetabolisch(metabolisch);
      return result;
    }

    if (applikationsart.equals("S")) {
      var sonstige = new Applikationsart.Sonstige();
      sonstige.setZielgebiet(zielgebiet);

      if (bestrahlung.getSTZielgebiet() != null) {
        sonstige.setZielgebiet(zielgebiet);
      }

      if (seiteZielgebiet != null) {
        sonstige.setSeiteZielgebiet(seiteZielgebiet);
      }

      sonstige.setEinzeldosis(einzeldosis);
      sonstige.setGesamtdosis(gesamtdosis);
      result.setSonstige(sonstige);
      return result;
    }

    LOG.warn("Unexpected radiation type {}", applikationsart);

    return null;
  }
}
