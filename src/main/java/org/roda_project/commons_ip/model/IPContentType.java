/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/commons-ip
 */
package org.roda_project.commons_ip.model;

import java.io.Serializable;

import org.apache.commons.lang3.StringUtils;

public class IPContentType implements Serializable {
  private static final long serialVersionUID = 1191075605637022551L;

  public enum IPContentTypeEnum {
    SFSB, RDB, ERMS, GEODATA, MIXED,
    /** default iArxiu Expedient type: urn:iarxiu:2.0:templates:cesca:PL_Expedient */
    PL_EXPEDIENT,
    /** iArxiu UPF expedient: urn:iarxiu:2.0:templates:cesca:PL_EXP_UPF" */
    PL_EXP_UPF,
    /* iArxiu PL document: urn:iarxiu:2.0:templates:cesca:PL_document; */
    PL_DOCUMENT,
    /** other for any SIP ingest */
    OTHER
  }

  private IPContentTypeEnum type;
  private String otherType;

  /**
   * Constructs a new object, trying to use 'type' parameter as the 'type' value
   * and if it does not match any of the enum values, 'othertype' will be set to
   * 'type' parameter
   */
  public IPContentType(final String type) {
    this(type, IPContentTypeEnum.OTHER);
  }

  public IPContentType(final String type, IPContentTypeEnum defaultValue) {
    try {
      this.type = IPContentTypeEnum.valueOf(type.toUpperCase());
      this.otherType = "";
    } catch (IllegalArgumentException | NullPointerException e) {
      this.type = defaultValue;
      this.otherType = type;
    }
  }

  public IPContentType(final IPContentTypeEnum type) {
    this.type = type;
    this.otherType = "";
  }

  public IPContentTypeEnum getType() {
    return type;
  }

  public String getOtherType() {
    return otherType;
  }

  public IPContentType setOtherType(final String otherType) {
    this.otherType = otherType;
    return this;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("type: ").append(type);
    if (StringUtils.isNotBlank(otherType)) {
      sb.append("; othertype: ").append(otherType);
    }

    return sb.toString();
  }

  public String asString() {
    String ret = type.toString();

    if (type == IPContentTypeEnum.OTHER && StringUtils.isNotBlank(otherType)) {
      ret = otherType;
    }

    return ret;
  }

  public static IPContentType getMIXED() {
    return new IPContentType(IPContentTypeEnum.MIXED);
  }
}
