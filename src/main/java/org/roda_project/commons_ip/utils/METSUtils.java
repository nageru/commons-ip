/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/commons-ip
 */
package org.roda_project.commons_ip.utils;

import org.apache.commons.lang3.StringUtils;
import org.roda_project.commons_ip.mets_v1_11.beans.FileType;
import org.roda_project.commons_ip.mets_v1_11.beans.FileType.FLocat;
import org.roda_project.commons_ip.mets_v1_11.beans.MdSecType.MdRef;
import org.roda_project.commons_ip.mets_v1_11.beans.Mets;
import org.roda_project.commons_ip.mets_v1_11.beans.MetsType.MetsHdr.Agent;
import org.roda_project.commons_ip.model.*;
import org.roda_project.commons_ip.utils.METSEnums.LocType;
import org.slf4j.Logger;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public final class METSUtils {

  private METSUtils() {
    // do nothing
  }

  public static Mets instantiateMETS1_11FromFile(Path metsFile) throws JAXBException, SAXException {
    return instantiateMETSFromFile(metsFile, "/schemas/mets1_11.xsd");
  }

  public static Mets instantiateIArxiuMETSFromFile(Path metsFile) throws JAXBException, SAXException {
    return instantiateMETSFromFile(metsFile, "/schemas-iArxiu/mets.xsd" );
  }

  public static Mets instantiateMETSFromFile(Path metsFile, String... schemaFiles) throws JAXBException, SAXException {
    return unmarshallMETS(getMETSUnmarshaller(schemaFiles), metsFile);
  }
  public static Mets instantiateRelaxedMETSFromFile(Path metsFile) throws JAXBException, SAXException {
    return unmarshallMETS(getRelaxedMETSUnmarshaller(), metsFile);
  }

  private static Mets unmarshallMETS(Unmarshaller unmarshaller, Path metsFile) throws JAXBException {
    return (Mets) unmarshaller.unmarshal(metsFile.toFile());
  }

  private static Unmarshaller getMETSUnmarshaller(String... schemaFiles) throws JAXBException, SAXException {
    final Unmarshaller jaxbUnmarshaller = getRelaxedMETSUnmarshaller();

    final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    factory.setResourceResolver(new ResourceResolver());

    for (String schemaFile: schemaFiles) {
        final Source metsSchemaSource = new StreamSource(METSUtils.class.getResourceAsStream(schemaFile));
        jaxbUnmarshaller.setSchema(factory.newSchema(metsSchemaSource));
    }

    return jaxbUnmarshaller;
  }
  private static Unmarshaller getRelaxedMETSUnmarshaller() throws JAXBException {
    JAXBContext jaxbContext = JAXBContext.newInstance(Mets.class);
    Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
    return jaxbUnmarshaller;
  }

  public static Path marshallMETS(Mets mets, Path tempMETSFile, boolean rootMETS)
    throws JAXBException, IOException, IPException {
    JAXBContext context = JAXBContext.newInstance(Mets.class);
    Marshaller m = context.createMarshaller();
    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

    if (rootMETS) {
      m.setProperty(Marshaller.JAXB_SCHEMA_LOCATION,
        "http://www.loc.gov/METS/ schemas/IP.xsd http://www.w3.org/1999/xlink schemas/xlink.xsd");
    } else {
      m.setProperty(Marshaller.JAXB_SCHEMA_LOCATION,
        "http://www.loc.gov/METS/ ../../schemas/IP.xsd http://www.w3.org/1999/xlink ../../schemas/xlink.xsd");
    }

    try (OutputStream metsOutputStream = Files.newOutputStream(tempMETSFile)) {
      m.marshal(mets, metsOutputStream);
    }

    return tempMETSFile;
  }

  public static IPContentType getIPContentType(Mets mets, IPInterface ip) throws ParseException {
    String metsType = mets.getTYPE();

    if (StringUtils.isBlank(metsType)) {
      throw new ParseException("METS 'TYPE' attribute does not contain any value");
    }

    String[] contentTypeParts = metsType.split(":");
    if (contentTypeParts.length != 2 || StringUtils.isBlank(contentTypeParts[0])
            || StringUtils.isBlank(contentTypeParts[1])) {
      throw new ParseException("METS 'TYPE' attribute does not contain a valid value: " + metsType);
    }

    try {
      IPEnums.IPType packageType = IPEnums.IPType.valueOf(contentTypeParts[0]);

      if (ip instanceof SIP && IPEnums.IPType.SIP != packageType) {
        throw new ParseException("METS 'TYPE' attribute should start with 'SIP:'");
      } else if (ip instanceof AIP && IPEnums.IPType.AIP != packageType) {
        throw new ParseException("METS 'TYPE' attribute should start with 'AIP:'");
      }
    } catch (IllegalArgumentException e) {
      throw new ParseException("METS 'TYPE' attribute does not contain a valid package type");
    }

    return new IPContentType(contentTypeParts[1]);
  }

  public static List<IPAgent> getHeaderIpAgents(Mets mets) {
    final List<IPAgent> ipAgentList = new ArrayList<>();
    if (mets.getMetsHdr() != null && mets.getMetsHdr().getAgent() != null) {
      for (Agent agent : mets.getMetsHdr().getAgent()) {
        final IPAgent ipAgent = createIPAgent(agent);
        ipAgentList.add(ipAgent);
      }
    }
    return ipAgentList;
  }

  private static IPAgent createIPAgent(Agent agent) {
    IPAgent ipAgent = new IPAgent();
    METSEnums.CreatorType agentType;
    try {
      agentType = METSEnums.CreatorType.valueOf(agent.getTYPE());
    } catch (IllegalArgumentException e) {
      agentType = METSEnums.CreatorType.OTHER;
      // Setting agent type to OTHER <- add Sip Validation?
    }
    ipAgent.setName(agent.getName()).setRole(agent.getROLE()).setOtherRole(agent.getOTHERROLE()).setType(agentType)
            .setOtherType(agent.getOTHERTYPE());

    return ipAgent;
  }

  public static void addMainMETSToZip(Map<String, ZipEntryInfo> zipEntries, MetsWrapper metsWrapper, String metsPath,
    Path buildDir) throws IPException {
    try {
      addMETSToZip(zipEntries, metsWrapper, metsPath, buildDir, true);
    } catch (JAXBException | IOException e) {
      throw new IPException(e.getMessage(), e);
    }
  }

  public static void addMainMETSToZip(Map<String, ZipEntryInfo> zipEntries, MetsWrapper mainMETSWrapper, Path buildDir)
    throws IPException {
    addMainMETSToZip(zipEntries, mainMETSWrapper, IPConstants.METS_FILE, buildDir);
  }

  public static void addMETSToZip(Map<String, ZipEntryInfo> zipEntries, MetsWrapper metsWrapper, String metsPath,
    Path buildDir, boolean mainMets) throws JAXBException, IOException, IPException {
    Path temp = Files.createTempFile(buildDir, IPConstants.METS_FILE_NAME, IPConstants.METS_FILE_EXTENSION);
    ZIPUtils.addMETSFileToZip(zipEntries, temp, metsPath, metsWrapper.getMets(), mainMets);
  }

  public static Agent createMETSAgent(IPAgent ipAgent) {
    Agent agent = new Agent();
    agent.setName(ipAgent.getName());
    agent.getNote().add(ipAgent.getNote());
    agent.setROLE(ipAgent.getRole());
    agent.setOTHERROLE(ipAgent.getOtherRole());
    agent.setTYPE(ipAgent.getType().toString());
    agent.setOTHERTYPE(ipAgent.getOtherType());
    return agent;
  }

  public static FLocat createFileLocation(String filePath) {
    FLocat fileLocation = new FLocat();
    fileLocation.setType(IPConstants.METS_TYPE_SIMPLE);
    fileLocation.setLOCTYPE(LocType.URL.toString());
    fileLocation.setHref(encodeHref(filePath));
    return fileLocation;
  }

  public static MdRef createMdRef(String id, String metadataPath, MetadataType.MetadataTypeEnum mdType, String mimeType, XMLGregorianCalendar created, Long size, String otherType, String version) {
    return createMdRef(id, metadataPath, mdType.getType(), mimeType, created, size, otherType, version);
  }
  public static MdRef createMdRef(String id, String metadataPath, String mdType, String mimeType, XMLGregorianCalendar created, Long size, String otherType, String version){
    MdRef mdRef = createMdRef(id, metadataPath);
    mdRef.setMDTYPE(mdType);
    if (isNotBlank(otherType)) {
      mdRef.setOTHERMDTYPE(otherType);
    }

    mdRef.setMDTYPEVERSION(version);

    // set mimetype, date creation, etc.
    METSUtils.setFileBasicInformation( mimeType, created, size, mdRef);

    return mdRef;
  }

  /**
   * @param id
   * @param metadataPath the path to the file starting from metadata; sub-path under the base path;
   *                     sample from $basePath: "/metadata/descriptive/dc.xml"
   * @return */
  public static MdRef createMdRef(String id, String metadataPath) {
    MdRef mdRef = new MdRef();
    mdRef.setID(id);
    mdRef.setLOCTYPE(LocType.URL.toString());
    mdRef.setHref(METSUtils.encodeHref(metadataPath));
    return mdRef;
  }

  public static MdRef setFileBasicInformation(Path file, MdRef mdRef) throws IPException {
    final String mimeType;
    // mimetype info.
    try {
      mimeType = getFileMimetype(file);
    } catch (IOException e) {
      throw new IPException("Error probing file content (" + file + ")", e);
    }

    final XMLGregorianCalendar created;
    // date creation info.
    try {
      created = Utils.getCurrentCalendar();
    } catch (DatatypeConfigurationException e) {
      throw new IPException("Error getting current calendar", e);
    }

    final Long size;
    // size info.
    try {
      size = Files.size(file);
    } catch (IOException e) {
      throw new IPException("Error getting file size (" + file + ")", e);
    }
    setFileBasicInformation(mimeType, created, size, mdRef);
    return mdRef;
  }

  private static void setFileBasicInformation(String mimeType, XMLGregorianCalendar created, Long size, MdRef mdRef) {
    // mimetype info.
    mdRef.setMIMETYPE(mimeType);

    // date creation info.
    mdRef.setCREATED(created);

    // size info.
    mdRef.setSIZE(size);

  }

  public static void setFileBasicInformation(Logger logger, Path file, FileType fileType)
    throws IPException, InterruptedException {
    // mimetype info.
    try {
      logger.debug("Setting mimetype {}", file);
      fileType.setMIMETYPE(getFileMimetype(file));
      logger.debug("Done setting mimetype");
    } catch (IOException e) {
      throw new IPException("Error probing content-type (" + file.toString() + ")", e);
    }

    // date creation info.
    try {
      fileType.setCREATED(Utils.getCurrentCalendar());
    } catch (DatatypeConfigurationException e) {
      throw new IPException("Error getting curent calendar (" + file.toString() + ")", e);
    }

    // size info.
    try {
      logger.debug("Setting file size {}", file);
      fileType.setSIZE(Files.size(file));
      logger.debug("Done setting file size");
    } catch (IOException e) {
      throw new IPException("Error getting file size (" + file.toString() + ")", e);
    }
  }

  private static String getFileMimetype(Path file) throws IOException {
    String probedContentType = Files.probeContentType(file);
    if (probedContentType == null) {
      probedContentType = "application/octet-stream";
    }
    return probedContentType;
  }

  /**
   * Decodes a value from a METS HREF attribute.
   * 
   * <p>
   * 20170511 hsilva: a global variable called
   * {@link IPConstants#METS_ENCODE_AND_DECODE_HREF} is used to enable/disable
   * the effective decode (done this way to avoid lots of changes in the methods
   * that use this method)
   * </p>
   */
  public static String decodeHref(String value) {
    if (IPConstants.METS_ENCODE_AND_DECODE_HREF) {
      try {
        value = URLDecoder.decode(value, "UTF-8");
      } catch (NullPointerException | UnsupportedEncodingException e) {
        // do nothing
      }
    }
    return value;
  }

  /**
   * Encodes a value to put in METS HREF attribute.
   *
   * <p>
   * 20170511 hsilva: a global variable called
   * {@link IPConstants#METS_ENCODE_AND_DECODE_HREF} is used to enable/disable
   * the effective encode (done this way to avoid lots of changes in the methods
   * that use this method). This method is not multi-thread safe when using
   * different SIP formats.
   * </p>
   */
  public static String encodeHref(String value) {
    if (IPConstants.METS_ENCODE_AND_DECODE_HREF) {
      value = escapeSpecialCharacters(value);
    }
    return value;
  }

  public static String escapeSpecialCharacters(String input) {
    StringBuilder resultStr = new StringBuilder();
    for (char ch : input.toCharArray()) {
      if (isSafeChar(ch)) {
        resultStr.append(ch);
      } else {
        resultStr.append(encodeUnsafeChar(ch));
      }
    }
    return resultStr.toString();
  }

  private static boolean isSafeChar(char ch) {
    return (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9');
  }

  private static String encodeUnsafeChar(char ch) {
    String ret = String.valueOf(ch);
    try {
      ret = URLEncoder.encode(ret, "UTF-8");
    } catch (NullPointerException | UnsupportedEncodingException e) {
      // do nothing & return original value
    }
    return ret;
  }
}
