/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/commons-ip
 */
package org.roda_project.commons_ip.model;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

public class MetadataType {
  public enum MetadataTypeEnum {
    MARC("MARC"), MODS("MODS"), EAD("EAD"), DC("DC"), NISOIMG("NISOIMG"), LCAV("LC-AV"), VRA("VRA"), TEIHDR("TEIHDR"),
    DDI("DDI"), FGDC("FGDC"), LOM("LOM"), PREMIS("PREMIS"), PREMISOBJECT("PREMIS:OBJECT"), PREMISAGENT("PREMIS:AGENT"),
    PREMISRIGHTS("PREMIS:RIGHTS"), PREMISEVENT("PREMIS:EVENT"), TEXTMD("TEXTMD"), METSRIGHTS("METSRIGHTS"),
    ISO191152003("ISO 19115:2003"), NAP("NAP"), EACCPF("EAC-CPF"), LIDO("LIDO"), OTHER("OTHER");

    public static Map<String, MetadataTypeEnum> typeToEnum = new HashMap<>();
    static {
      typeToEnum.put("LC-AV", MetadataTypeEnum.LCAV);
      typeToEnum.put("PREMIS:OBJECT", MetadataTypeEnum.PREMISOBJECT);
      typeToEnum.put("PREMIS:AGENT", MetadataTypeEnum.PREMISAGENT);
      typeToEnum.put("PREMIS:RIGHTS", MetadataTypeEnum.PREMISRIGHTS);
      typeToEnum.put("PREMIS:EVENT", MetadataTypeEnum.PREMISEVENT);
      typeToEnum.put("ISO 19115:2003", MetadataTypeEnum.ISO191152003);
      typeToEnum.put("EAC-CPF", MetadataTypeEnum.EACCPF);
    }

    private final String type;

    private MetadataTypeEnum(final String type) {
      this.type = type;
    }

    public String getType() {
      return type;
    }

    public String toString() {
      return type;
    }
  }

  private MetadataTypeEnum type;
  private String otherType;

  public MetadataType(final String type) {
    try {
      this.type = MetadataTypeEnum.valueOf(type);
      this.otherType = "";
    } catch (IllegalArgumentException e) {
      if (MetadataTypeEnum.typeToEnum.containsKey(type)) {
        this.type = MetadataTypeEnum.typeToEnum.get(type);
        this.otherType = "";
      } else {
        this.type = MetadataTypeEnum.OTHER;
        this.otherType = type;
      }
    }
  }

  public MetadataType(final MetadataTypeEnum type) {
    this.type = type;
    this.otherType = "";
  }

  public MetadataTypeEnum getType() {
    return type;
  }

  public String getOtherType() {
    return otherType;
  }

  public MetadataType setOtherType(final String otherType) {
    this.otherType = otherType;
    return this;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("type: ").append(type);
    if (StringUtils.isNotBlank(otherType)) {
      sb.append("; othertype: ").append(otherType);
    }

    return sb.toString();
  }

  public String asString() {
    String ret = type.getType();

    if (type == MetadataTypeEnum.OTHER && StringUtils.isNotBlank(otherType)) {
      ret = otherType;
    }

    return ret;
  }

}
