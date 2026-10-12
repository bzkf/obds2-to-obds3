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

import de.basisdatensatz.obds.v2.ADTGEKID;
import de.basisdatensatz.obds.v3.AbsenderTyp;

class AbsenderMapper {

  static final String SOFTWARE_ID = "obds2-to-obds3";

  /**
   * Wird verwendet, wenn kein {@code Implementation-Version} Eintrag im Manifest vorhanden ist,
   * z.B. beim Ausführen der Tests oder aus der IDE heraus.
   */
  static final String UNKNOWN_SOFTWARE_VERSION = "unknown";

  private static final String SOFTWARE_VERSION =
      resolveSoftwareVersion(AbsenderMapper.class.getPackage().getImplementationVersion());

  private AbsenderMapper() {}

  /**
   * @param implementationVersion Wert des {@code Implementation-Version} Eintrags aus dem Manifest
   *     des Jars, falls vorhanden
   * @return die Version oder {@link #UNKNOWN_SOFTWARE_VERSION}, wenn keine Version bekannt ist
   */
  static String resolveSoftwareVersion(String implementationVersion) {
    if (null == implementationVersion || implementationVersion.isBlank()) {
      return UNKNOWN_SOFTWARE_VERSION;
    }
    return implementationVersion.trim();
  }

  public static AbsenderTyp map(ADTGEKID.Absender source) {
    if (null == source) {
      throw new IllegalArgumentException("Source cannot be null");
    }

    var absender = new AbsenderTyp();
    absender.setAbsenderID(source.getAbsenderID());
    absender.setInstallationsID(source.getInstallationsID());
    absender.setAnschrift(source.getAbsenderAnschrift());
    absender.setAnsprechpartner(source.getAbsenderAnsprechpartner());
    absender.setBezeichnung(source.getAbsenderBezeichnung());
    absender.setEMail(source.getAbsenderEMail());
    absender.setTelefon(source.getAbsenderTelefon());

    absender.setSoftwareID(SOFTWARE_ID);
    absender.setSoftwareVersion(SOFTWARE_VERSION);

    return absender;
  }
}
