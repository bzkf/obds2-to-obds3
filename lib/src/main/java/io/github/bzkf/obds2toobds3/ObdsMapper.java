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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;
import de.basisdatensatz.obds.v2.ADTGEKID;
import de.basisdatensatz.obds.v3.OBDS;
import java.text.SimpleDateFormat;
import java.util.Objects;
import java.util.Optional;

public class ObdsMapper {

  public static final XmlMapper XML_MAPPER =
      XmlMapper.builder()
          .defaultUseWrapper(false)
          .defaultDateFormat(new SimpleDateFormat("yyyy-MM-dd"))
          .addModule(new JakartaXmlBindAnnotationModule())
          .addModule(new Jdk8Module())
          .enable(SerializationFeature.INDENT_OUTPUT)
          .serializationInclusion(JsonInclude.Include.NON_EMPTY)
          .build();

  private final PatientMapper patientMapper;

  private final boolean ignoreUnmappable;
  private final boolean disableSchemaValidation;

  private ObdsMapper(
      boolean ignoreUnmappable, boolean fixMissingId, boolean disableSchemaValidation) {
    this.ignoreUnmappable = ignoreUnmappable;
    this.disableSchemaValidation = disableSchemaValidation;
    patientMapper = new PatientMapper(ignoreUnmappable, fixMissingId);
  }

  public static Builder builder() {
    return new Builder();
  }

  public OBDS map(ADTGEKID adtgekid) {
    if (null == adtgekid) {
      throw new IllegalArgumentException("ADT_GEKID source cannot be null");
    }

    OBDS obds = new OBDS();
    obds.setSchemaVersion("3.0.4");

    // In oBDSv2 nicht so definiert
    // Letztes Meldedatum aus oBDSv2 Menge_Meldung?
    adtgekid.getMengePatient().getPatient().stream()
        .map(
            patient ->
                patient.getMengeMeldung().getMeldung().stream()
                    .map(ADTGEKID.MengePatient.Patient.MengeMeldung.Meldung::getMeldedatum)
                    .map(MapperUtils::mapDateString)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst())
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst()
        .ifPresent(
            datumTagOderMonatOderJahrOderNichtGenauTyp ->
                obds.setMeldedatum(datumTagOderMonatOderJahrOderNichtGenauTyp.getValue()));

    // Absender der Meldung
    var absender = adtgekid.getAbsender();
    assert absender != null;
    obds.setAbsender(AbsenderMapper.map(absender));

    // Menge Patient
    var mengePatient = adtgekid.getMengePatient();
    assert mengePatient != null;

    var mappedMengePatient = new OBDS.MengePatient();
    mappedMengePatient
        .getPatient()
        .addAll(
            mengePatient.getPatient().stream()
                .map(
                    patient -> {
                      try {
                        return patientMapper.map(patient);
                      } catch (UnmappableItemException e) {
                        if (!ignoreUnmappable) {
                          throw e;
                        }
                        return null;
                      }
                    })
                .filter(Objects::nonNull)
                .filter(
                    patient ->
                        !ignoreUnmappable
                            || (null != patient.getMengeMeldung()
                                && !patient.getMengeMeldung().getMeldung().isEmpty()))
                .toList());
    obds.setMengePatient(mappedMengePatient);

    // Menge Melder
    var mengeMelder = adtgekid.getMengeMelder();
    assert mengeMelder != null;

    var mappedMengeMelder = new OBDS.MengeMelder();
    mappedMengeMelder
        .getMelder()
        .addAll(mengeMelder.getMelder().stream().map(MelderMapper::map).toList());
    obds.setMengeMelder(mappedMengeMelder);

    return obds;
  }

  public <T> T readValue(String str, Class<T> clazz) throws JsonProcessingException {
    if (!this.disableSchemaValidation && ADTGEKID.class == clazz) {
      SchemaValidator.isValid(str, SchemaValidator.SchemaVersion.ADT_GEKID_2_2_3);
    } else if (!this.disableSchemaValidation && OBDS.class == clazz) {
      SchemaValidator.isValid(str, SchemaValidator.SchemaVersion.OBDS_3_0_4);
    }

    if (clazz == ADTGEKID.class || clazz == OBDS.class) {
      return XML_MAPPER.readValue(str, clazz);
    }
    throw new IllegalArgumentException("Allowed classes are ADTGEKID and OBDS");
  }

  public String writeMappedXmlString(ADTGEKID obj) throws JsonProcessingException {
    return writeXmlString(map(obj));
  }

  public String writeXmlString(OBDS obj) throws JsonProcessingException {
    var xmlString = XML_MAPPER.writeValueAsString(obj);
    var result =
        String.format(
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>%s%s",
            System.lineSeparator(),
            xmlString.replace("<oBDS ", "<oBDS xmlns=\"http://www.basisdatensatz.de/oBDS/XML\" "));

    if (this.disableSchemaValidation
        || SchemaValidator.isValid(result, SchemaValidator.SchemaVersion.OBDS_3_0_4)) {
      return result;
    }

    throw new SchemaValidatorException("Cannot validate result using oBDS schema");
  }

  public static class Builder {
    private boolean ignoreUnmappable;
    private boolean fixMissingId;
    private boolean disableSchemaValidation = false;

    public Builder ignoreUnmappable(boolean ignoreUnmappable) {
      this.ignoreUnmappable = ignoreUnmappable;
      return this;
    }

    public Builder fixMissingId(boolean fixMissingId) {
      this.fixMissingId = fixMissingId;
      return this;
    }

    /**
     * This disables schema validation on input and output
     *
     * @param disableSchemaValidation Weather to disable schema validation or not
     * @return the configured builder
     */
    public Builder disableSchemaValidation(boolean disableSchemaValidation) {
      this.disableSchemaValidation = disableSchemaValidation;
      return this;
    }

    /**
     * This disables schema validation on input and output
     *
     * @return the configured builder
     */
    public Builder disableSchemaValidation() {
      return disableSchemaValidation(true);
    }

    public ObdsMapper build() {
      return new ObdsMapper(ignoreUnmappable, fixMissingId, disableSchemaValidation);
    }
  }
}
