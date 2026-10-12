package io.github.bzkf.obds2toobds3;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * (De-)serializes {@link XMLGregorianCalendar} values as plain text, without converting them to a
 * {@link java.util.Date} first. oBDS dates are calendar dates, a round trip through an instant
 * would make the result depend on the JVM's default time zone.
 */
public class PlainDateModule extends SimpleModule {

  public PlainDateModule() {
    super("PlainDateModule");
    addSerializer(XMLGregorianCalendar.class, new Serializer());
    addDeserializer(XMLGregorianCalendar.class, new Deserializer());
  }

  private static boolean isDateOnly(XMLGregorianCalendar calendar) {
    return calendar.getHour() == DatatypeConstants.FIELD_UNDEFINED
        || (calendar.getHour() == 0
            && calendar.getMinute() == 0
            && calendar.getSecond() == 0
            && calendar.getMillisecond() <= 0);
  }

  private static class Serializer extends StdSerializer<XMLGregorianCalendar> {
    Serializer() {
      super(XMLGregorianCalendar.class);
    }

    @Override
    public void serialize(XMLGregorianCalendar value, JsonGenerator gen, SerializationContext ctxt)
        throws JacksonException {
      if (isDateOnly(value)) {
        // yyyy-MM-dd, any time zone is dropped on purpose
        gen.writeString(
            String.format("%04d-%02d-%02d", value.getYear(), value.getMonth(), value.getDay()));
      } else {
        gen.writeString(value.toXMLFormat());
      }
    }
  }

  private static class Deserializer extends StdDeserializer<XMLGregorianCalendar> {
    Deserializer() {
      super(XMLGregorianCalendar.class);
    }

    @Override
    public XMLGregorianCalendar deserialize(JsonParser p, DeserializationContext ctxt)
        throws JacksonException {
      var text = p.getValueAsString();
      if (text == null || text.isBlank()) {
        return null;
      }
      try {
        var calendar = DatatypeFactory.newInstance().newXMLGregorianCalendar(text.trim());
        if (isDateOnly(calendar)) {
          // xs:date allows an optional offset, e.g. 2019-05-02+02:00. It's a calendar date,
          // the offset must not shift it.
          calendar.setTimezone(DatatypeConstants.FIELD_UNDEFINED);
        }
        return calendar;
      } catch (DatatypeConfigurationException | IllegalArgumentException e) {
        return (XMLGregorianCalendar)
            ctxt.handleWeirdStringValue(
                XMLGregorianCalendar.class, text, "not a valid xs:date or xs:dateTime");
      }
    }
  }
}
