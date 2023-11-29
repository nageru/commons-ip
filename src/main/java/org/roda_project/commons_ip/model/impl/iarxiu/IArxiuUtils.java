/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree
 */
package org.roda_project.commons_ip.model.impl.iarxiu;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jdom2.Element;
import org.jdom2.IllegalDataException;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.roda_project.commons_ip.mets_v1_11.beans.Mets;
import org.roda_project.commons_ip.mets_v1_11.beans.StructMapType;
import org.roda_project.commons_ip.model.*;
import org.roda_project.commons_ip.utils.METSUtils;
import org.roda_project.commons_ip.utils.ValidationConstants;
import org.roda_project.commons_ip.utils.ValidationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static org.roda_project.commons_ip.model.IPConstants.COMMON_SPEC_STRUCTURAL_MAP_ID;

public final class IArxiuUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(IArxiuUtils.class);
  private static final String METADATA_TYPE = "key-value";

  private IArxiuUtils() {
    // do nothing
  }

  /** gets the main mets file from the root of the ip path
   * validates if mets file found
   * @param validation
   * @param ipPath
   * @return the main mets file path if found; null otherwise */
  public static Path getMainMetsFile(ValidationReport validation, Path ipPath) {
    final Path mainMETSFile = ipPath.resolve(IPConstants.METS_FILE);
    if (Files.exists(mainMETSFile)) {
      ValidationUtils.addInfo(validation, ValidationConstants.MAIN_METS_FILE_FOUND, ipPath, mainMETSFile);
      return mainMETSFile;
    } else {
      ValidationUtils.addIssue(validation, ValidationConstants.MAIN_METS_FILE_NOT_FOUND,
              ValidationEntry.LEVEL.ERROR, ipPath, mainMETSFile);
      return null;
    }
  }
  /**
   * extracts and validates the main mets from the main mets file
   *
   * @param validation updates the done validations
   * @param ipPath
   * @return if the mets parsed null otherwise
   */
  public static Mets parseMainMets(ValidationReport validation, Path ipPath, Path mainMETSFile) {
      try {
        return METSUtils.instantiateIArxiuMETSFromFile(mainMETSFile);
      } catch (JAXBException | SAXException e) {
        ValidationUtils.addIssue(validation, ValidationConstants.MAIN_METS_NOT_VALID,
                ValidationEntry.LEVEL.ERROR, e, ipPath, mainMETSFile);
        return null;
      }
  }

  public static IPContentType getSipContentType(Mets mets) throws ParseException {
    final String metsType = mets.getTYPE();

    if (StringUtils.isBlank(metsType)) {
      throw new ParseException("METS 'TYPE' attribute does not contain any value");
    }

    final String[] contentTypeParts = metsType.split(":");
    if (contentTypeParts.length == 0) {
      throw new ParseException("METS 'TYPE' attribute does not contain a valid value: " + metsType);
    }

    final String packageTye;
    if (contentTypeParts.length > 1) {
      packageTye = Arrays.toString(Arrays.copyOf(contentTypeParts, contentTypeParts.length - 1));
    } else {
      packageTye = null;
    }
    final String contentType = contentTypeParts[contentTypeParts.length - 1];
    final IPContentType ipContentType = new IPContentType(contentType, IPContentType.IPContentTypeEnum.PL_EXPEDIENT);
    if (StringUtils.isNotBlank(ipContentType.getOtherType())){
      LOGGER.warn("Unknown SIP content type; set default '{}' from '{}' package", ipContentType, packageTye);
    }

    return ipContentType;
  }


  protected static StructMapType getMainMetsStructMap(MetsWrapper metsWrapper, IPInterface ip) {
    final Mets mets = metsWrapper.getMets();

    final List<StructMapType> metsStructMapList = mets.getStructMap().stream().filter(structMapType -> {
      final String structMapTypeId = structMapType.getID();
      final boolean foundValidId = COMMON_SPEC_STRUCTURAL_MAP_ID.equalsIgnoreCase(structMapTypeId);
      if (!foundValidId) {
        LOGGER.warn("Main METS.xml file has not recognized structural map id: '{}'", structMapTypeId);
      }
      return foundValidId;
    }).collect(Collectors.toList());

    final StructMapType structMap;
    final long smCount = metsStructMapList.size();
    if (smCount == 0) {
      LOGGER.error("Main METS.xml file has no structural map for IArxiu '{}' ID", COMMON_SPEC_STRUCTURAL_MAP_ID);
      structMap = null;
      ValidationUtils.addIssue(ip.getValidationReport(),
              ValidationConstants.MAIN_METS_HAS_NO_I_ARXIU_STRUCT_MAP,
              ValidationEntry.LEVEL.ERROR, structMap, ip.getBasePath(), metsWrapper.getMetsPath());
    } else {
      structMap = metsStructMapList.remove(0);
      if (smCount > 1){
        LOGGER.warn("Main METS.xml file has too many ({}) structural map. Will take first only! Ignored: {}", smCount, metsStructMapList);
      }
      ValidationUtils.addInfo(ip.getValidationReport(),
              ValidationConstants.MAIN_METS_HAS_E_ARK_STRUCT_MAP,
              structMap, ip.getBasePath(), metsWrapper.getMetsPath());
    }
    return structMap;
  }

  public static IPDescriptiveMetadata createIArxiuMetadata(Map<String, String> metadata, Path metadataPath) {
    return createIArxiuMetadata(metadata, new ArrayList<>(), metadataPath);
  }

  public static IPDescriptiveMetadata createIArxiuMetadata(Map<String, String> metadata, List<String> ancestors,
    Path metadataPath) {
    try {
      FileBasedConfigurationBuilder<PropertiesConfiguration> builder = new Configurations()
        .propertiesBuilder(metadataPath.toFile());
      Files.createFile(metadataPath);
      PropertiesConfiguration config = builder.getConfiguration();

      for (Entry<String, String> entry : metadata.entrySet()) {
        config.setProperty(entry.getKey(), entry.getValue());
      }

      for (String ancestor : ancestors) {
        config.addProperty(IPConstants.PARENT_KEY, ancestor);
      }

      builder.save();
    } catch (IOException | ConfigurationException e) {
      LOGGER.error("Could not save IArxiu metadata content on file", e);
    }

    return new IPDescriptiveMetadata(metadataPath.getFileName().toString(), new IPFile(metadataPath),
      new MetadataType(METADATA_TYPE), "");
  }

  public static Map<String, String> getIArxiuInfo(Path metadataPath) {
    final Map<String, String> metadataList = new HashMap<>();
    try {
      PropertiesConfiguration config = new Configurations().properties(metadataPath.toFile());
      Iterator<String> keys = config.getKeys();

      while (keys.hasNext()) {
        String key = keys.next();
        metadataList.put(key, config.getString(key));
      }
    } catch (ConfigurationException e) {
      LOGGER.error("Could not load properties with iArxiu metadata", e);
    }

    return metadataList;
  }

  public static String generateMetadataFile(Path metadataPath) throws IllegalDataException {
    final Map<String, String> iArxiuInfo = getIArxiuInfo(metadataPath);
    Element root = new Element(IPConstants.METADATA_KEY);
    org.jdom2.Document doc = new org.jdom2.Document();

    for (Entry<String, String> entry : iArxiuInfo.entrySet()) {
      if (!IPConstants.PARENT_KEY.equalsIgnoreCase(entry.getKey())) {
        Element child = new Element(IPConstants.FIELD_KEY);
        child.setAttribute(IPConstants.NAME_KEY, StringEscapeUtils.escapeXml11(entry.getKey()));
        child.addContent(entry.getValue());
        root.addContent(child);
      }
    }

    doc.setRootElement(root);
    XMLOutputter outter = new XMLOutputter();
    outter.setFormat(Format.getPrettyFormat());
    return outter.outputString(doc);
  }
}
