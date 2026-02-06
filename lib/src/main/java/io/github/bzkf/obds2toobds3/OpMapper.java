package io.github.bzkf.obds2toobds3;

import de.basisdatensatz.obds.v2.ADTGEKID.MengePatient.Patient.MengeMeldung.Meldung.MengeOP.OP;
import de.basisdatensatz.obds.v3.OPTyp;
import de.basisdatensatz.obds.v3.OPTyp.Komplikationen;
import de.basisdatensatz.obds.v3.OPTyp.MengeOPS;
import de.basisdatensatz.obds.v3.OPTyp.MengeOPS.OPS;
import de.basisdatensatz.obds.v3.RTyp;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class OpMapper {
  private static final Logger LOG = LoggerFactory.getLogger(OpMapper.class);

  private OpMapper() {}

  public static List<OPTyp> map(
      de.basisdatensatz.obds.v2.ADTGEKID.MengePatient.Patient.MengeMeldung.Meldung.MengeOP
          mengeOp) {
    return mengeOp.getOP().stream().map(OpMapper::mapToOPType).toList();
  }

  private static OPTyp mapToOPType(OP source) {
    MDC.put("OPID", source.getOPID());
    var opTyp = new OPTyp();
    opTyp.setOPID(source.getOPID());
    opTyp.setIntention(source.getOPIntention());

    MapperUtils.mapDateString(source.getOPDatum()).ifPresent(d -> opTyp.setDatum(d.getValue()));

    if (source.getMengeOPS() != null) {
      var mengeOPS = new MengeOPS();
      for (var ops : source.getMengeOPS().getOPOPS()) {
        var opsv3 = new OPS();
        opsv3.setCode(ops);
        opsv3.setVersion(source.getOPOPSVersion());

        mengeOPS.getOPS().add(opsv3);
      }

      opTyp.setMengeOPS(mengeOPS);
    }

    if (source.getHistologie() != null) {
      HistologieMapper.map(List.of(source.getHistologie())).ifPresent(opTyp::setHistologie);
    }

    MeldungMapper.mapTnmType(source.getTNM()).ifPresent(opTyp::setTNM);

    if (source.getMengeKomplikation() != null) {
      opTyp.setKomplikationen(mapToKomplikationen(source));
    }

    // v2: R0, R1, R1(is), R1(cy+), R2, RX, RU
    // v3: R0, R1, R1(is), R1(cy+), R2, RX, U
    if (source.getResidualstatus() != null) {
      var residualstatus = new de.basisdatensatz.obds.v3.ResidualstatusTyp();

      var gesamtbeurteilung = source.getResidualstatus().getGesamtbeurteilungResidualstatus();
      if (gesamtbeurteilung != null) {
        if (gesamtbeurteilung == de.basisdatensatz.obds.v2.RTyp.RU) {
          residualstatus.setGesamtbeurteilungResidualstatus(RTyp.U);
        } else {
          residualstatus.setGesamtbeurteilungResidualstatus(
              RTyp.fromValue(gesamtbeurteilung.value()));
        }
      } else {
        LOG.debug("OP has no GesamtbeurteilungResidualstatus set in v2 residualstatus");
      }

      var lokaleBeurteilung = source.getResidualstatus().getLokaleBeurteilungResidualstatus();
      if (lokaleBeurteilung != null) {
        if (lokaleBeurteilung == de.basisdatensatz.obds.v2.RTyp.RU) {
          residualstatus.setLokaleBeurteilungResidualstatus(RTyp.U);
        } else {
          residualstatus.setLokaleBeurteilungResidualstatus(
              RTyp.fromValue(lokaleBeurteilung.value()));
        }
      } else {
        LOG.debug("OP has no LokaleBeurteilungResidualstatus set in v2 residualstatus");
      }

      opTyp.setResidualstatus(residualstatus);
    }

    if (source.getModulProstata() != null) {
      opTyp.setModulProstata(ModulMapper.map(source.getModulProstata()));
    }

    MDC.remove("OPID");

    return opTyp;
  }

  private static Komplikationen mapToKomplikationen(OP source) {
    var komplikationen = new Komplikationen();
    var mengeKomplikation = new OPTyp.Komplikationen.MengeKomplikation();

    for (var komp : source.getMengeKomplikation().getOPKomplikation()) {
      if (komp.equals("N") || komp.equals("U")) {
        komplikationen.setKomplikationNeinOderUnbekannt(komp);
        if (source.getMengeKomplikation().getOPKomplikation().size() > 1) {
          LOG.warn(
              "OP {} has multiple Komplikationen, but should only contain one if set to 'N' or 'U'.",
              source.getOPID());
        }
      } else {
        var komplikationTyp = new de.basisdatensatz.obds.v3.KomplikationTyp();
        komplikationTyp.setKuerzel(komp);

        mengeKomplikation.getKomplikation().add(komplikationTyp);
      }
    }

    // currently, if both "N"/"U" and other komplikationen are set,
    // we keep both in v3 which isn't strictly schema-compliant,
    // but preserves all information.
    if (!mengeKomplikation.getKomplikation().isEmpty()) {
      komplikationen.setMengeKomplikation(mengeKomplikation);
    }

    return komplikationen;
  }
}
