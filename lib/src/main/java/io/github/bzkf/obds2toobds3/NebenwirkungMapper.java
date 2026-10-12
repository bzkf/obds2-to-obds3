package io.github.bzkf.obds2toobds3;

import de.basisdatensatz.obds.v3.NebenwirkungTyp;
import de.basisdatensatz.obds.v3.NebenwirkungTyp.MengeNebenwirkung.Nebenwirkung;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mappt die Nebenwirkungen einer Strahlen- oder Systemtherapie. In oBDS v2 sind die Elemente {@code
 * ST_Nebenwirkung} und {@code SYST_Nebenwirkung} vom selben Typ und werden daher identisch nach
 * oBDS v3 gemappt.
 */
class NebenwirkungMapper {
  private static final Logger LOG = LoggerFactory.getLogger(NebenwirkungMapper.class);

  private static final String DEFAULT_VERSION_OR_ART = "Sonstige";

  private NebenwirkungMapper() {}

  /**
   * In oBDS v3 sind die Nebenwirkungen einer ST und SYST verpflichtend, in oBDS v2 hingegen
   * optional. Fehlen sie, ist der maximale Grad unbekannt.
   */
  static NebenwirkungTyp unbekannt() {
    var result = new NebenwirkungTyp();
    result.setGradMaximal2OderUnbekannt("U");
    return result;
  }

  /**
   * @param nebenwirkungenV2 Die oBDS v2 Nebenwirkungen der Therapie
   * @param therapie Kürzel der Therapie ("ST" oder "SYST"), wird nur für Log-Meldungen verwendet
   */
  static NebenwirkungTyp map(
      List<de.basisdatensatz.obds.v2.NebenwirkungTyp> nebenwirkungenV2, String therapie) {
    var mengeNebenwirkungV3 = new NebenwirkungTyp.MengeNebenwirkung();

    var result = new NebenwirkungTyp();

    for (var nebenwirkungV2 : nebenwirkungenV2) {
      var nebenwirkungV3 = new Nebenwirkung();

      // In oBDS v3 ist der Grad einer einzelnen Nebenwirkung verpflichtend (3, 4 oder 5). Ohne
      // Grad kann sie nicht übernommen werden; der Grad der Nebenwirkungen ist dann unbekannt.
      if (nebenwirkungV2.getNebenwirkungGrad() == null
          || nebenwirkungV2.getNebenwirkungGrad().isBlank()) {
        LOG.warn(
            "Nebenwirkung_Grad of a {} Nebenwirkung is unset but required in v3. The Nebenwirkung (Art={}) is not mapped.",
            therapie,
            nebenwirkungV2.getNebenwirkungArt());
        continue;
      }

      if (nebenwirkungV2.getNebenwirkungGrad() != null
          && (nebenwirkungV2.getNebenwirkungGrad().equals("K")
              || nebenwirkungV2.getNebenwirkungGrad().equals("U"))) {
        if (nebenwirkungenV2.size() > 1) {
          LOG.warn(
              "{} has multiple Nebenwirkungen, but should only contain one if set to 'K' or 'U'.",
              therapie);
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
        LOG.warn("Nebenwirkung_Art is unset. Defaulting to '{}'.", DEFAULT_VERSION_OR_ART);
        art.setBezeichnung(DEFAULT_VERSION_OR_ART);
      }

      nebenwirkungV3.setArt(art);

      if (nebenwirkungV2.getNebenwirkungVersion() != null) {
        nebenwirkungV3.setVersion(nebenwirkungV2.getNebenwirkungVersion());
      } else {
        LOG.warn("Nebenwirkung_Version is unset. Defaulting to '{}'.", DEFAULT_VERSION_OR_ART);
        nebenwirkungV3.setVersion(DEFAULT_VERSION_OR_ART);
      }

      nebenwirkungV3.setGrad(nebenwirkungV2.getNebenwirkungGrad());

      mengeNebenwirkungV3.getNebenwirkung().add(nebenwirkungV3);
    }

    // Es konnte keine Nebenwirkung übernommen werden, da keine einen Grad hat
    if (mengeNebenwirkungV3.getNebenwirkung().isEmpty()) {
      return unbekannt();
    }

    result.setMengeNebenwirkung(mengeNebenwirkungV3);
    return result;
  }
}
