package io.github.bzkf.obds2toobds3;

import de.basisdatensatz.obds.v3.SYSTTyp;
import de.basisdatensatz.obds.v3.SYSTTyp.EndeGrund;
import de.basisdatensatz.obds.v3.SYSTTyp.Meldeanlass;
import de.basisdatensatz.obds.v3.SYSTTyp.Therapieart;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class SystemtherapieMapper {
  private static final Logger LOG = LoggerFactory.getLogger(SystemtherapieMapper.class);

  private SystemtherapieMapper() {}

  public static List<SYSTTyp> map(
      de.basisdatensatz.obds.v2.ADTGEKID.MengePatient.Patient.MengeMeldung.Meldung.MengeSYST
          mengeSyst,
      String meldeanlass) {
    var result = new ArrayList<SYSTTyp>();
    for (var source : mengeSyst.getSYST()) {
      MDC.put("SYSTID", source.getSYSTID());
      var systTyp = new SYSTTyp();

      systTyp.setSYSTID(source.getSYSTID());
      systTyp.setIntention(source.getSYSTIntention());
      systTyp.setStellungOP(source.getSYSTStellungOP());
      systTyp.setMeldeanlass(Meldeanlass.fromValue(meldeanlass));

      if (source.getMengeTherapieart() != null) {
        var therapieart = mapTherapieart(source.getMengeTherapieart().getSYSTTherapieart());
        systTyp.setTherapieart(therapieart);
      }

      systTyp.setProtokoll(source.getSYSTProtokoll());

      MapperUtils.mapDateStringGenau(source.getSYSTBeginnDatum()).ifPresent(systTyp::setBeginn);
      MapperUtils.mapDateString(source.getSYSTEndeDatum())
          .ifPresent(d -> systTyp.setEnde(d.getValue()));

      if (source.getMengeSubstanz() != null) {
        var mengeSubstanz = new SYSTTyp.MengeSubstanz();
        for (var substanz : source.getMengeSubstanz().getSYSTSubstanz()) {
          var substanzv3 = new SYSTTyp.MengeSubstanz.Substanz();
          substanzv3.setBezeichnung(substanz);
          mengeSubstanz.getSubstanz().add(substanzv3);
        }

        systTyp.setMengeSubstanz(mengeSubstanz);
      }

      if (source.getSYSTEndeGrund() != null) {
        systTyp.setEndeGrund(EndeGrund.fromValue(source.getSYSTEndeGrund()));
      }

      if (source.getMengeNebenwirkung() != null) {
        var mappedNebenwirkungen =
            NebenwirkungMapper.map(source.getMengeNebenwirkung().getSYSTNebenwirkung(), "SYST");
        systTyp.setNebenwirkungen(mappedNebenwirkungen);
      } else {
        // In oBDS v3 verpflichtend, in oBDS v2 optional
        LOG.debug("Menge_Nebenwirkung is unset in SYST but required in v3. Defaulting to 'U'.");
        systTyp.setNebenwirkungen(NebenwirkungMapper.unbekannt());
      }

      MapperUtils.ifNotNull(
          source.getModulAllgemein(), m -> systTyp.setModulAllgemein(ModulMapper.map(m)));

      if (source.getResidualstatus() != null) {
        LOG.warn("Residualstatus is not available for a SYST in oBDS v3 and is not mapped.");
      }

      result.add(systTyp);
      MDC.remove("SYSTID");
    }

    return result;
  }

  private static Therapieart mapTherapieart(List<String> therapieartV2) {
    // v2: CH, HO, IM, KM, WS, AS, ZS, SO
    // v3: CH, HO, IM, ??, WS, AS, ZS, SO, CI, CZ, CIZ, IZ, SZ, WW
    // v3: SZ = Stammzelltransplantation (inkl. Knochenmarktransplantation)

    if (therapieartV2.size() == 1) {
      var code = therapieartV2.get(0);
      if (code.equals("KM")) {
        return Therapieart.SZ;
      }
      return Therapieart.fromValue(code);
    }

    var distinctCodes = new ArrayList<>(therapieartV2.stream().distinct().toList());
    if (distinctCodes.size() < therapieartV2.size()) {
      LOG.warn(
          "Duplicate Therapieart codes found: {}. Using distinct codes: {}",
          therapieartV2,
          distinctCodes);
    }

    Collections.sort(distinctCodes);
    var code = String.join("", distinctCodes);

    // gibt es eine eine Kombination mit SO-Sonstige, so fällt das Sonstige einfach raus
    // und es wird die übrig gebliebene Therapieart, bzw. Therapieartkombination gemeldet.
    if (distinctCodes.contains("SO")) {
      distinctCodes.remove("SO");
      return mapTherapieart(distinctCodes);
    }

    // Ausnahme: wenn nur zwischen HO und ZS entschieden werden muss,
    // dann „gewinnt“ HO  - ein bisschen unlogisch, aber auf irgendwas
    //  musste man sich wohl einigen und so macht es Onkostar.
    if (code.equalsIgnoreCase("HOZS")) {
      distinctCodes.remove("ZS");
      return mapTherapieart(distinctCodes);
    }

    // Gleiches gilt für die HO-Hormontherapie -> diese fällt innerhalb einer Kombi raus
    if (distinctCodes.contains("HO")) {
      distinctCodes.remove("HO");
      return mapTherapieart(distinctCodes);
    }

    return switch (code) {
      case "CHIM" -> Therapieart.CI;
      case "CHZS" -> Therapieart.CZ;
      case "CHIMZS" -> Therapieart.CIZ;
      case "IMZS" -> Therapieart.IZ;
      default -> throw new IllegalStateException("Unsupported Therapieart: " + code);
    };
  }
}
