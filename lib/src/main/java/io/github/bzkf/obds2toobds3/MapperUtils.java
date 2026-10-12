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

import de.basisdatensatz.obds.v3.DatumTagOderMonatGenauTyp;
import de.basisdatensatz.obds.v3.DatumTagOderMonatGenauTypSchaetzOptional;
import de.basisdatensatz.obds.v3.DatumTagOderMonatOderJahrOderNichtGenauTyp;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MapperUtils {

  private static final Logger LOG = LoggerFactory.getLogger(MapperUtils.class);

  /**
   * Creates a plain calendar date without time part and without time zone. Unknown (0) month or day
   * default to 1, the precision is tracked separately in the Datumsgenauigkeit. Avoiding
   * GregorianCalendar here is intentional: it would use the JVM's default time zone and make the
   * result depend on where the conversion runs.
   */
  static XMLGregorianCalendar newDate(int year, int month, int day)
      throws DatatypeConfigurationException {
    return DatatypeFactory.newInstance()
        .newXMLGregorianCalendarDate(
            year, Math.max(month, 1), Math.max(day, 1), DatatypeConstants.FIELD_UNDEFINED);
  }

  public static final String OBDS2_DATE_REGEX =
      "(?<day>([0-2]\\d)|(3[01]))\\.(?<month>(0\\d)|(1[0-2]))\\.(?<year>(18|19|20)\\d\\d)";

  /** Maximale Länge einer Anmerkung in oBDS v3 (<code>FreitextAnmerkung_Typ</code>) */
  static final int MAX_ANMERKUNG_LENGTH = 2000;

  private MapperUtils() {}

  /**
   * Wandelt oBDS v2 Datumsstring in oBDS v3 "nicht genau" Datum um.
   *
   * @param date
   * @return
   * @link <a
   *     href="https://plattform65c.atlassian.net/wiki/spaces/UMK/pages/15532303/Datum_Tag_oder_Monat_oder_Jahr_oder_nicht_genau_Typ">Spezifikation</a>
   */
  public static Optional<DatumTagOderMonatOderJahrOderNichtGenauTyp> mapDateString(String date) {
    if (null == date) {
      return Optional.empty();
    }

    var result = new DatumTagOderMonatOderJahrOderNichtGenauTyp();

    if ("00.00.0000".equals(date)) {
      result.setDatumsgenauigkeit(
          DatumTagOderMonatOderJahrOderNichtGenauTyp
              .DatumsgenauigkeitTagOderMonatOderJahrOderNichtGenau.V);

      try {
        result.setValue(newDate(1900, 1, 1));
        return Optional.of(result);
      } catch (DatatypeConfigurationException e) {
        return Optional.empty();
      }
    }

    var obdsV2datePattern = Pattern.compile(OBDS2_DATE_REGEX);
    var matcher = obdsV2datePattern.matcher(date);
    if (matcher.matches()) {
      var day = Integer.parseInt(matcher.group("day"));
      var month = Integer.parseInt(matcher.group("month"));
      var year = Integer.parseInt(matcher.group("year"));

      result.setDatumsgenauigkeit(
          DatumTagOderMonatOderJahrOderNichtGenauTyp
              .DatumsgenauigkeitTagOderMonatOderJahrOderNichtGenau.M);

      if (month > 0) {
        result.setDatumsgenauigkeit(
            DatumTagOderMonatOderJahrOderNichtGenauTyp
                .DatumsgenauigkeitTagOderMonatOderJahrOderNichtGenau.T);
      }

      if (day > 0) {
        result.setDatumsgenauigkeit(
            DatumTagOderMonatOderJahrOderNichtGenauTyp
                .DatumsgenauigkeitTagOderMonatOderJahrOderNichtGenau.E);
      }

      try {
        result.setValue(newDate(year, month, day));
        return Optional.of(result);
      } catch (DatatypeConfigurationException e) {
        return Optional.empty();
      }
    }

    // Schätze kein Datum (Genauigkeit "V") bei Fehler
    return Optional.empty();
  }

  /**
   * Wandelt oBDS v2 Datumsstring in oBDS v3 "genau" Datum um.
   *
   * @param date
   * @return
   * @link <a
   *     href="https://plattform65c.atlassian.net/wiki/spaces/UMK/pages/15532315/Datum_Tag_oder_Monat_genau_Typ">Spezifikation</a>
   */
  public static Optional<DatumTagOderMonatGenauTyp> mapDateStringGenau(String date) {
    if (null == date) {
      return Optional.empty();
    }

    var result = new DatumTagOderMonatGenauTyp();

    var obdsV2datePattern = Pattern.compile(OBDS2_DATE_REGEX);
    var matcher = obdsV2datePattern.matcher(date);
    if (matcher.matches()) {
      var day = Integer.parseInt(matcher.group("day"));
      var month = Integer.parseInt(matcher.group("month"));
      var year = Integer.parseInt(matcher.group("year"));

      if (month == 0 && day == 0) {
        // For DatumTagOderMonatGenauTyp, Datumsgenauigkeit is a required field.
        // If both month and day are unknown, our only choice is to set the
        // least precise enum
        LOG.warn(
            "Both the month and day are unset, "
                + "but DatumTagOderMonatGenauTyp requires at least the month. Defaulting to precision of 'T'");
        result.setDatumsgenauigkeit(DatumTagOderMonatGenauTyp.DatumsgenauigkeitTagOderMonatGenau.T);
      }

      if (month > 0) {
        result.setDatumsgenauigkeit(DatumTagOderMonatGenauTyp.DatumsgenauigkeitTagOderMonatGenau.T);
      }

      if (day > 0) {
        result.setDatumsgenauigkeit(DatumTagOderMonatGenauTyp.DatumsgenauigkeitTagOderMonatGenau.E);
      }

      try {
        result.setValue(newDate(year, month, day));
        return Optional.of(result);
      } catch (DatatypeConfigurationException e) {
        return Optional.empty();
      }
    }

    // Schätze kein Datum (Genauigkeit "V")
    return Optional.empty();
  }

  /**
   * Wandelt XMLGregorianCalendar in oBDS v3 "schaetz" Datum mit optionaler Genauigkeitsangabe um.
   *
   * @param calendar
   * @return
   */
  public static Optional<DatumTagOderMonatGenauTypSchaetzOptional>
      mapDatumTagOderMonatGenauTypSchaetzOptional(XMLGregorianCalendar calendar) {
    if (null == calendar) {
      return Optional.empty();
    }

    var result = new DatumTagOderMonatGenauTypSchaetzOptional();
    result.setValue(calendar);
    // as per the schema, the default value of Datum_Tag_oder_Monat_genau_Typ_Schaetz_optional
    // should be 'E'
    result.setDatumsgenauigkeit(
        DatumTagOderMonatGenauTypSchaetzOptional
            .DatumsgenauigkeitTagOderMonatGenauTypSchaetzOptional.E);

    if (calendar.getMonth() > 0) {
      result.setDatumsgenauigkeit(
          DatumTagOderMonatGenauTypSchaetzOptional
              .DatumsgenauigkeitTagOderMonatGenauTypSchaetzOptional.T);
    }

    if (calendar.getDay() > 0) {
      result.setDatumsgenauigkeit(
          DatumTagOderMonatGenauTypSchaetzOptional
              .DatumsgenauigkeitTagOderMonatGenauTypSchaetzOptional.E);
    }

    return Optional.of(result);
  }

  public static Optional<LocalDate> parseDate(String dateString) {
    if (null == dateString || dateString.trim().isEmpty()) {
      return Optional.empty();
    }

    var obdsV2datePattern = Pattern.compile(OBDS2_DATE_REGEX);
    var matcher = obdsV2datePattern.matcher(dateString.trim());
    if (matcher.matches()) {
      var day = Integer.parseInt(matcher.group("day"));
      var month = Integer.parseInt(matcher.group("month"));
      var year = Integer.parseInt(matcher.group("year"));

      return Optional.of(LocalDate.of(year, month, day));
    }
    return Optional.empty();
  }

  /**
   * Entferne alle nicht erlaubten Zeichen aus String
   *
   * @param datatypeName Name des Datentyps gemäß oBDS 3 Schema
   * @param string Eingabestring
   * @return String mit entfernten unerlaubten Zeichen oder empty Optional
   */
  public static Optional<String> trimToMatchDatatype(String datatypeName, String string) {
    return SchemaValidator.regexpPattern(datatypeName, SchemaValidator.SchemaVersion.OBDS_3_0_4)
        .map(pattern -> string.replaceAll(String.format("[^%s]", pattern.pattern()), ""));
  }

  /**
   * Fügt Anmerkungen zu einem Text zusammen. Leere Anmerkungen werden ignoriert, die übrigen
   * getrimmt und durch einen Zeilenumbruch getrennt. Ist das Ergebnis länger als in oBDS v3
   * erlaubt, wird es gekürzt und eine Warnung ausgegeben, da die Meldung sonst nicht schemakonform
   * wäre.
   *
   * @return die zusammengefügte Anmerkung oder <code>null</code>, wenn keine Anmerkung vorhanden
   *     ist
   */
  public static String joinAnmerkungen(String... anmerkungen) {
    var joined =
        Arrays.stream(anmerkungen)
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(anmerkung -> !anmerkung.isEmpty())
            .collect(Collectors.joining("\n"));

    if (joined.isEmpty()) {
      return null;
    }

    if (joined.length() > MAX_ANMERKUNG_LENGTH) {
      LOG.warn(
          "Anmerkung has {} characters, but only {} are allowed in oBDS v3. The text is truncated.",
          joined.length(),
          MAX_ANMERKUNG_LENGTH);
      var end = MAX_ANMERKUNG_LENGTH;
      // keine Surrogate-Paare zerschneiden
      if (Character.isHighSurrogate(joined.charAt(end - 1))) {
        end--;
      }
      return joined.substring(0, end);
    }

    return joined;
  }

  /**
   * Stellt sicher, dass ein für oBDS v3 benötigter Wert vorhanden ist.
   *
   * @param value Der zu prüfende Wert
   * @param message Die Meldung der Exception, wenn der Wert fehlt
   * @return der übergebene Wert, wenn er nicht {@code null} ist
   * @throws UnmappableItemException wenn der Wert {@code null} ist
   */
  public static <T> T requireMappable(T value, String message) {
    if (null == value) {
      throw new UnmappableItemException(message);
    }
    return value;
  }

  /**
   * Runs given function if value is not null
   *
   * @param value The value to operate on
   * @param f The function to be used
   * @param <T> The type of the given value
   */
  public static <T> void ifNotNull(T value, Consumer<T> f) {
    if (null != value) {
      f.accept(value);
    }
  }
}
