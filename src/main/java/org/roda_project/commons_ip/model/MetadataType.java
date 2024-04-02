/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/commons-ip
 */
package org.roda_project.commons_ip.model;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

public class MetadataType implements Serializable {
  private static final long serialVersionUID = 9052247527983339112L;

  public enum MetadataTypeEnum {
    MARC("MARC"), MODS("MODS"), EAD("EAD"),
    DC("DC"), // the Dublin Core metadata
    NISOIMG("NISOIMG"), LCAV("LC-AV"), VRA("VRA"), TEIHDR("TEIHDR"),
    DDI("DDI"), FGDC("FGDC"), LOM("LOM"), PREMIS("PREMIS"), PREMISOBJECT("PREMIS:OBJECT"), PREMISAGENT("PREMIS:AGENT"),
    PREMISRIGHTS("PREMIS:RIGHTS"), PREMISEVENT("PREMIS:EVENT"), TEXTMD("TEXTMD"), METSRIGHTS("METSRIGHTS"),
    ISO191152003("ISO 19115:2003"), NAP("NAP"), EACCPF("EAC-CPF"), LIDO("LIDO"),
    /* iArxiu file Documents document MIMETYPE="text/xml" */
    I_ARXIU_VOC_DOC("Voc_document"), I_ARXIU_VOC_DOC_EXP("Voc_document_exp"), I_ARXIU_VOC_UPF("Voc_UPF"),
    /* iArxiu Expedients MIMETYPE="text/xml" */
    I_ARXIU_VOC_EXP("Voc_expedient"),
    /* Roda iArxiu mappings: for Dublin Core, Documents and Expedients */
    I_ARXIU_DC("dc_SimpleDC20021212"), I_ARXIU_DOC("iArxiu-doc"), I_ARXIU_EXP("iArxiu-exp"),
    // Other types
    OTHER("OTHER");

    protected static final Map<String, MetadataTypeEnum> typeToEnum = new HashMap<>();
    static {
      typeToEnum.put("LC-AV", MetadataTypeEnum.LCAV);
      typeToEnum.put("PREMIS:OBJECT", MetadataTypeEnum.PREMISOBJECT);
      typeToEnum.put("PREMIS:AGENT", MetadataTypeEnum.PREMISAGENT);
      typeToEnum.put("PREMIS:RIGHTS", MetadataTypeEnum.PREMISRIGHTS);
      typeToEnum.put("PREMIS:EVENT", MetadataTypeEnum.PREMISEVENT);
      typeToEnum.put("ISO 19115:2003", MetadataTypeEnum.ISO191152003);
      typeToEnum.put("EAC-CPF", MetadataTypeEnum.EACCPF);
      /* iArxiu (DC arrives as such) document and expedient types */
      typeToEnum.put("URN:IARXIU:2.0:VOCABULARIES:CESCA:VOC_DOCUMENT", MetadataTypeEnum.I_ARXIU_VOC_DOC);
      typeToEnum.put("URN:IARXIU:2.0:VOCABULARIES:CESCA:VOC_DOCUMENT", MetadataTypeEnum.I_ARXIU_VOC_DOC);
      typeToEnum.put("URN:IARXIU:2.0:VOCABULARIES:CESCA:VOC_DOCUMENT_EXP", MetadataTypeEnum.I_ARXIU_VOC_DOC_EXP);
      typeToEnum.put("URN:IARXIU:2.0:VOCABULARIES:CESCA:VOC_UPF", MetadataTypeEnum.I_ARXIU_VOC_UPF); // normally informed in SIP type 'PL_EXP_UPF'
      typeToEnum.put("URN:IARXIU:2.0:VOCABULARIES:CESCA:VOC_EXPEDIENT", MetadataTypeEnum.I_ARXIU_VOC_EXP);
    }

    private final String type;

    MetadataTypeEnum(final String type) {
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
    try { // by name...
      return MetadataTypeEnum.valueOf(type);
    } catch (IllegalArgumentException | NullPointerException e) { }

    // by type...
    for (MetadataTypeEnum value : MetadataTypeEnum.values()){
      if (value.getType().equalsIgnoreCase(type)){
        return value;
      }
    }

    // by file metadata mapping
    final String typeKey = type.toUpperCase();
    if (MetadataTypeEnum.typeToEnum.containsKey(typeKey)) {
      return MetadataTypeEnum.typeToEnum.get(typeKey);
    } else {
      return null;
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
