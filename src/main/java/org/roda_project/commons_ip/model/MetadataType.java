/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/commons-ip
 */
package org.roda_project.commons_ip.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

public class MetadataType implements Serializable {
  private static final long serialVersionUID = 9052247527983339112L;

  public enum MetadataTypeEnum {
    MARC("MARC"), MODS("MODS"), EAD("EAD"), DC("DC"), NISOIMG("NISOIMG"), LCAV("LC-AV"), VRA("VRA"), TEIHDR("TEIHDR"),
    DDI("DDI"), FGDC("FGDC"), LOM("LOM"), PREMIS("PREMIS"), PREMISOBJECT("PREMIS:OBJECT"), PREMISAGENT("PREMIS:AGENT"),
    PREMISRIGHTS("PREMIS:RIGHTS"), PREMISEVENT("PREMIS:EVENT"), TEXTMD("TEXTMD"), METSRIGHTS("METSRIGHTS"),
    ISO191152003("ISO 19115:2003"), NAP("NAP"), EACCPF("EAC-CPF"), LIDO("LIDO"),
    OTHER("OTHER"),
    /*  Other iArxiu Md Types: MIMETYPE="text/xml"
      - OTHERMDTYPE="urn:iarxiu:2.0:vocabularies:cesca:Voc_document_exp"
      - OTHERMDTYPE="urn:iarxiu:2.0:vocabularies:cesca:Voc_expedient"
     */
    OTHER_VOC_EXP("Voc_expedient"), OTHER_VOC_DOC_EXP("Voc_document_exp");

    protected static final Map<String, MetadataTypeEnum> typeToEnum = new HashMap<>();
    static {
      typeToEnum.put("LC-AV", MetadataTypeEnum.LCAV);
      typeToEnum.put("PREMIS:OBJECT", MetadataTypeEnum.PREMISOBJECT);
      typeToEnum.put("PREMIS:AGENT", MetadataTypeEnum.PREMISAGENT);
      typeToEnum.put("PREMIS:RIGHTS", MetadataTypeEnum.PREMISRIGHTS);
      typeToEnum.put("PREMIS:EVENT", MetadataTypeEnum.PREMISEVENT);
      typeToEnum.put("ISO 19115:2003", MetadataTypeEnum.ISO191152003);
      typeToEnum.put("EAC-CPF", MetadataTypeEnum.EACCPF);

      typeToEnum.put("URN:IARXIU:2.0:VOCABULARIES:CESCA:VOC_EXPEDIENT", MetadataTypeEnum.OTHER_VOC_EXP);
      typeToEnum.put("URN:IARXIU:2.0:VOCABULARIES:CESCA:VOC_DOCUMENT_EXP", MetadataTypeEnum.OTHER_VOC_DOC_EXP);

    }

    private final String type;

    private MetadataTypeEnum(final String type) {
      this.type = type;
    }

    public String getType() {
      return type;
    }

    @Override
    public String toString() {
      return type;
    }
  }

  private MetadataTypeEnum type;
  private String otherType;

  public MetadataType(String type) {
    final MetadataTypeEnum typeEnum = match(type);
    if (typeEnum != null){
      this.type = typeEnum;
      this.otherType = "";
    } else {
      this.type = MetadataTypeEnum.OTHER;
      this.otherType = type;
    }
  }

  public static MetadataTypeEnum match(String type){
    if (type == null){
      return null;
    }
    try {
      return MetadataTypeEnum.valueOf(type);
    } catch (IllegalArgumentException | NullPointerException e) {
      if (MetadataTypeEnum.typeToEnum.containsKey(type.toUpperCase())) {
        return MetadataTypeEnum.typeToEnum.get(type.toUpperCase());
      } else {
        return null;
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

  public String asString() {
    String ret = type.getType();

    if (type == MetadataTypeEnum.OTHER && StringUtils.isNotBlank(otherType)) {
      ret = otherType;
    }

    return ret;
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

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((otherType == null) ? 0 : otherType.hashCode());
    result = prime * result + ((type == null) ? 0 : type.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (!(obj instanceof MetadataType))
      return false;
    MetadataType other = (MetadataType) obj;
    return this.type == other.getType() && this.otherType.equals(other.getOtherType());
  }

  public static MetadataType OTHER() {
    return new MetadataType(MetadataTypeEnum.OTHER);
  }

}
