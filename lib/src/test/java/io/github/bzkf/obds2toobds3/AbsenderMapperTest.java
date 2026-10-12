package io.github.bzkf.obds2toobds3;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v2.ADTGEKID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class AbsenderMapperTest {

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "  "})
  void shouldFallBackToUnknownVersionWithoutManifestEntry(String implementationVersion) {
    assertThat(AbsenderMapper.resolveSoftwareVersion(implementationVersion))
        .isEqualTo(AbsenderMapper.UNKNOWN_SOFTWARE_VERSION);
  }

  @Test
  void shouldUseVersionFromManifestEntry() {
    assertThat(AbsenderMapper.resolveSoftwareVersion("0.1.22")).isEqualTo("0.1.22");
    assertThat(AbsenderMapper.resolveSoftwareVersion(" 0.1.22 ")).isEqualTo("0.1.22");
  }

  @Test
  void shouldSetSoftwareIdAndVersion() {
    var source = new ADTGEKID.Absender();
    source.setAbsenderID("ABSENDER");

    var actual = AbsenderMapper.map(source);

    assertThat(actual.getAbsenderID()).isEqualTo("ABSENDER");
    assertThat(actual.getSoftwareID()).isEqualTo(AbsenderMapper.SOFTWARE_ID);
    assertThat(actual.getSoftwareVersion()).isNotBlank();
  }
}
