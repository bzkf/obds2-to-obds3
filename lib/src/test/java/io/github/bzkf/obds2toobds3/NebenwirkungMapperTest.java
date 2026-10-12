package io.github.bzkf.obds2toobds3;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v2.NebenwirkungTyp;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NebenwirkungMapperTest {

  private static NebenwirkungTyp nebenwirkung(String grad, String art, String version) {
    var result = new NebenwirkungTyp();
    result.setNebenwirkungGrad(grad);
    result.setNebenwirkungArt(art);
    result.setNebenwirkungVersion(version);
    return result;
  }

  @ParameterizedTest
  @ValueSource(strings = {"ST", "SYST"})
  void shouldMapGradKAndUToGradMaximal2OderUnbekannt(String therapie) {
    for (var grad : List.of("K", "U")) {
      var actual = NebenwirkungMapper.map(List.of(nebenwirkung(grad, null, null)), therapie);

      assertThat(actual.getGradMaximal2OderUnbekannt()).isEqualTo(grad);
      assertThat(actual.getMengeNebenwirkung()).isNull();
    }
  }

  @Test
  void shouldDropArtIfGradIsKOrU() {
    var actual = NebenwirkungMapper.map(List.of(nebenwirkung("U", "10055599", "4.03")), "SYST");

    assertThat(actual.getGradMaximal2OderUnbekannt()).isEqualTo("U");
    assertThat(actual.getMengeNebenwirkung()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"ST", "SYST"})
  void shouldMapNebenwirkungen(String therapie) {
    var actual =
        NebenwirkungMapper.map(
            List.of(nebenwirkung("3", "Übelkeit", "5.0"), nebenwirkung("4", "Anämie", "4.03")),
            therapie);

    assertThat(actual.getGradMaximal2OderUnbekannt()).isNull();
    assertThat(actual.getMengeNebenwirkung().getNebenwirkung())
        .satisfiesExactly(
            first -> {
              assertThat(first.getGrad()).isEqualTo("3");
              assertThat(first.getArt().getBezeichnung()).isEqualTo("Übelkeit");
              assertThat(first.getVersion()).isEqualTo("5.0");
            },
            second -> {
              assertThat(second.getGrad()).isEqualTo("4");
              assertThat(second.getArt().getBezeichnung()).isEqualTo("Anämie");
              assertThat(second.getVersion()).isEqualTo("4.03");
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"ST", "SYST"})
  void shouldDefaultMissingArtAndVersionToSonstige(String therapie) {
    var actual = NebenwirkungMapper.map(List.of(nebenwirkung("3", null, null)), therapie);

    assertThat(actual.getMengeNebenwirkung().getNebenwirkung())
        .singleElement()
        .satisfies(
            nebenwirkung -> {
              assertThat(nebenwirkung.getArt().getBezeichnung()).isEqualTo("Sonstige");
              assertThat(nebenwirkung.getVersion()).isEqualTo("Sonstige");
            });
  }
}
