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

import de.basisdatensatz.obds.v2.ADTGEKID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ObdsMapperTest {

  @Nested
  class DefaultObdsMapperTest {

    private ObdsMapper mapper;

    @BeforeEach
    void setUp() {
      mapper = ObdsMapper.builder().build();
    }

    @ParameterizedTest
    @CsvSource({
      "testdaten/obdsv2_1.xml,testdaten/obdsv3_1.xml",
      "testdaten/obdsv2_gueltige-adresse.xml,testdaten/obdsv3_1.xml",
      "testdaten/obdsv2_verlauf.xml,testdaten/obdsv3_verlauf.xml",
      "testdaten/obdsv2_tumorkonferenz.xml,testdaten/obdsv3_tumorkonferenz.xml",
      "testdaten/obdsv2_modul_prostata.xml,testdaten/obdsv3_modul_prostata.xml",
      "testdaten/obdsv2_zusatzitems.xml,testdaten/obdsv3_zusatzitems.xml",
      "testdaten/obdsv2_histologie.xml,testdaten/obdsv3_histologie.xml",
      "testdaten/obdsv2_ohne-adresse.xml,testdaten/obdsv3_ohne-adresse.xml",
      "testdaten/obdsv2_keine-fruehere-namen.xml,testdaten/obdsv3_keine-fruehere-namen.xml",
      "testdaten/obdsv2_diagnose-zu-tumorzuordung.xml,testdaten/obdsv3_diagnose-zu-tumorzuordung.xml",
      "testdaten/obdsv2_kv-ersatzcode.xml,testdaten/obdsv3_kv-ersatzcode.xml",
      "testdaten/obdsv2_pkv.xml,testdaten/obdsv3_pkv.xml",
      "testdaten/obdsv2_keine-diagnose.xml,testdaten/obdsv3_keine-diagnose.xml",
      "testdaten/obdsv2_op_1.xml,testdaten/obdsv3_op_1.xml",
      "testdaten/obdsv2_st_1.xml,testdaten/obdsv3_st_1.xml",
      "testdaten/obdsv2_syst_1.xml,testdaten/obdsv3_syst_1.xml",
      "testdaten/obdsv2_verlauf_histologie.xml,testdaten/obdsv3_verlauf_histologie.xml",
      "testdaten/obdsv2_missing-meldung-id.xml,testdaten/obdsv3_missing-meldung-id.xml",
      "testdaten/obdsv2_module.xml,testdaten/obdsv3_module.xml",
      "testdaten/obdsv2_module_patho.xml,testdaten/obdsv3_module_patho.xml",
    })
    void shouldMapObdsFile(String obdsV2File, String obdsV3File) throws Exception {
      var obdsV2String =
          new String(
              getClass().getClassLoader().getResource(obdsV2File).openStream().readAllBytes());
      var obdsV3String =
          new String(
              getClass().getClassLoader().getResource(obdsV3File).openStream().readAllBytes());

      var obdsv2 = mapper.readValue(obdsV2String, ADTGEKID.class);
      assertThat(obdsv2).isNotNull();

      var obdsV3MappedString = mapper.writeMappedXmlString(obdsv2);

      assertThat(obdsV3MappedString).isEqualTo(obdsV3String);
    }

    @ParameterizedTest
    @CsvSource({
      "testdaten/obdsv2_keine-tumorzuordung.xml,ADT_GEKID tumorzuordung should not be null at this point - required for oBDS v3",
      "testdaten/obdsv2_nicht-mappbarer-patient.xml,ADT_GEKID tumorzuordung should not be null at this point - required for oBDS v3",
      "testdaten/obdsv2_diagnose-zu-tumorzuordung-keinetumorid.xml,ADT_GEKID attribute 'Tumor_ID' must not be null at this point - required for oBDS v3",
    })
    void shouldNotMapObdsFileAndThrowUnmappableItemException(String obdsV2File, String message)
        throws Exception {
      var obdsV2String =
          new String(
              getClass().getClassLoader().getResource(obdsV2File).openStream().readAllBytes());

      var obdsv2 = mapper.readValue(obdsV2String, ADTGEKID.class);
      assertThat(obdsv2).isNotNull();

      var exception =
          assertThrows(UnmappableItemException.class, () -> mapper.writeMappedXmlString(obdsv2));
      assertThat(exception).hasMessage(message);
    }
  }

  @Nested
  class IgnoreUnmappableObdsMapperTest {

    private ObdsMapper mapper;

    @BeforeEach
    void setUp() {
      mapper = ObdsMapper.builder().ignoreUnmappable(true).build();
    }

    @ParameterizedTest
    @CsvSource({
      "testdaten/obdsv2_keine-tumorzuordung.xml,testdaten/obdsv3_keine-tumorzuordung.xml",
      "testdaten/obdsv2_nicht-mappbarer-patient.xml,testdaten/obdsv3_nicht-mappbarer-patient.xml"
    })
    void shouldMapObdsFileIgnoringUnmappableItems(String obdsV2File, String obdsV3File)
        throws Exception {
      var obdsV2String =
          new String(
              getClass().getClassLoader().getResource(obdsV2File).openStream().readAllBytes());
      var obdsV3String =
          new String(
              getClass().getClassLoader().getResource(obdsV3File).openStream().readAllBytes());

      var obdsv2 = mapper.readValue(obdsV2String, ADTGEKID.class);
      assertThat(obdsv2).isNotNull();

      assertThat(mapper.writeMappedXmlString(obdsv2)).isEqualTo(obdsV3String);
    }
  }

  @Nested
  class FixMissingIdObdsMapperTest {
    private ObdsMapper mapper;

    @BeforeEach
    void setUp() {
      mapper = ObdsMapper.builder().fixMissingId(true).build();
    }

    @ParameterizedTest
    @CsvSource({
      "testdaten/obdsv2_diagnose-zu-tumorzuordung-keinetumorid.xml,testdaten/obdsv3_diagnose-zu-tumorzuordung-keinetumorid.xml",
      "testdaten/obdsv2_patho.xml,testdaten/obdsv3_patho.xml"
    })
    void shouldMapObdsFileWithGeneratedIds(String obdsV2File, String obdsV3File) throws Exception {
      var obdsV2String =
          new String(
              getClass().getClassLoader().getResource(obdsV2File).openStream().readAllBytes());
      var obdsV3String =
          new String(
              getClass().getClassLoader().getResource(obdsV3File).openStream().readAllBytes());

      var obdsv2 = mapper.readValue(obdsV2String, ADTGEKID.class);
      assertThat(obdsv2).isNotNull();

      var mappedObdsv3String = mapper.writeMappedXmlString(obdsv2);

      assertThat(mappedObdsv3String).isEqualTo(obdsV3String);
    }
  }

  @Nested
  class DisabledSchemaValidationObdsMapperTest {
    private ObdsMapper mapper;

    @BeforeEach
    void setUp() {
      mapper = ObdsMapper.builder().disableSchemaValidation().build();
    }

    @ParameterizedTest
    @CsvSource({
      "testdaten/obdsv2_invalid-schema.xml,testdaten/obdsv3_invalid-schema.xml",
      "testdaten/obdsv2_invalid-kv.xml,testdaten/obdsv3_invalid-kv.xml",
      "testdaten/obdsv2_st_strahlenart.xml,testdaten/obdsv3_st_strahlenart.xml",
      "testdaten/obdsv2_tod.xml,testdaten/obdsv3_tod.xml",
      "testdaten/obdsv2_missing_meldungen.xml,testdaten/obdsv3_missing_meldungen.xml",
    })
    void shouldMapObdsFileWithoutSchemaValidation(String obdsV2File, String obdsV3File)
        throws Exception {
      var obdsV2String =
          new String(
              getClass().getClassLoader().getResource(obdsV2File).openStream().readAllBytes());
      var obdsV3String =
          new String(
              getClass().getClassLoader().getResource(obdsV3File).openStream().readAllBytes());

      var obdsv2 = mapper.readValue(obdsV2String, ADTGEKID.class);
      assertThat(obdsv2).isNotNull();

      var mappedObdsv3String = mapper.writeMappedXmlString(obdsv2);

      assertThat(mappedObdsv3String).isEqualTo(obdsV3String);
    }
  }

  /**
   * In oBDS v2 sind diese Elemente optional, in oBDS v3 werden sie benötigt. Die Prüfung darf nicht
   * von aktivierten Assertions abhängen.
   */
  @Nested
  class MissingRequiredElementsObdsMapperTest {

    private ADTGEKID adtgekid;

    @BeforeEach
    void setUp() {
      adtgekid = new ADTGEKID();
      adtgekid.setAbsender(new ADTGEKID.Absender());
      adtgekid.setMengePatient(new ADTGEKID.MengePatient());
      adtgekid.setMengeMelder(new ADTGEKID.MengeMelder());
    }

    private void addPatient(
        Consumer<ADTGEKID.MengePatient.Patient.PatientenStammdaten> customizer) {
      var stammdaten = new ADTGEKID.MengePatient.Patient.PatientenStammdaten();
      stammdaten.setPatientID("P1");
      stammdaten.setPatientenGeburtsdatum("01.01.1980");
      customizer.accept(stammdaten);

      var patient = new ADTGEKID.MengePatient.Patient();
      patient.setPatientenStammdaten(stammdaten);
      patient.setMengeMeldung(new ADTGEKID.MengePatient.Patient.MengeMeldung());
      adtgekid.getMengePatient().getPatient().add(patient);
    }

    @Test
    void shouldMapMinimalDocument() {
      addPatient(stammdaten -> {});

      var actual = ObdsMapper.builder().build().map(adtgekid);

      assertThat(actual.getMengePatient().getPatient()).hasSize(1);
    }

    @Test
    void shouldThrowIfAbsenderIsMissing() {
      adtgekid.setAbsender(null);

      var exception =
          assertThrows(
              UnmappableItemException.class, () -> ObdsMapper.builder().build().map(adtgekid));
      assertThat(exception).hasMessageContaining("Absender");
    }

    @Test
    void shouldThrowIfMengePatientIsMissing() {
      adtgekid.setMengePatient(null);

      var exception =
          assertThrows(
              UnmappableItemException.class, () -> ObdsMapper.builder().build().map(adtgekid));
      assertThat(exception).hasMessageContaining("Menge_Patient");
    }

    @Test
    void shouldThrowIfMengeMelderIsMissing() {
      adtgekid.setMengeMelder(null);

      var exception =
          assertThrows(
              UnmappableItemException.class, () -> ObdsMapper.builder().build().map(adtgekid));
      assertThat(exception).hasMessageContaining("Menge_Melder");
    }

    @Test
    void shouldThrowIfAbsenderIsMissingEvenIfIgnoringUnmappablePatients() {
      adtgekid.setAbsender(null);

      assertThrows(
          UnmappableItemException.class,
          () -> ObdsMapper.builder().ignoreUnmappable(true).build().map(adtgekid));
    }

    @Test
    void shouldThrowIfPatientenStammdatenAreMissing() {
      var patient = new ADTGEKID.MengePatient.Patient();
      patient.setMengeMeldung(new ADTGEKID.MengePatient.Patient.MengeMeldung());
      adtgekid.getMengePatient().getPatient().add(patient);

      var exception =
          assertThrows(
              UnmappableItemException.class, () -> ObdsMapper.builder().build().map(adtgekid));
      assertThat(exception).hasMessageContaining("Patienten_Stammdaten");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"not a date", "99.99.1980"})
    void shouldThrowIfGeburtsdatumIsMissingOrInvalid(String geburtsdatum) {
      addPatient(stammdaten -> stammdaten.setPatientenGeburtsdatum(geburtsdatum));

      var exception =
          assertThrows(
              UnmappableItemException.class, () -> ObdsMapper.builder().build().map(adtgekid));
      assertThat(exception).hasMessageContaining("Patienten_Geburtsdatum");
      // Patientendaten dürfen nicht in der Meldung der Exception landen
      assertThat(exception.getMessage()).doesNotContain("not a date").doesNotContain("99.99");
    }

    @Test
    void shouldSkipPatientWithoutValidGeburtsdatumIfIgnoringUnmappablePatients() {
      addPatient(stammdaten -> stammdaten.setPatientenGeburtsdatum(null));

      var actual = ObdsMapper.builder().ignoreUnmappable(true).build().map(adtgekid);

      assertThat(actual.getMengePatient().getPatient()).isEmpty();
    }
  }
}
