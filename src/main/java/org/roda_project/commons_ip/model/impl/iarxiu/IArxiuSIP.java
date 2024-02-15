/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree
 */
package org.roda_project.commons_ip.model.impl.iarxiu;

import org.apache.commons.lang3.StringUtils;
import org.roda_project.commons_ip.mets_v1_11.beans.Mets;
import org.roda_project.commons_ip.mets_v1_11.beans.MetsType;
import org.roda_project.commons_ip.mets_v1_11.beans.StructMapType;
import org.roda_project.commons_ip.model.*;
import org.roda_project.commons_ip.model.impl.CommonSipUtils;
import org.roda_project.commons_ip.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.datatype.XMLGregorianCalendar;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

public class IArxiuSIP extends SIP {
  private static final Logger LOGGER = LoggerFactory.getLogger(IArxiuSIP.class);
  private static final String SIP_TEMP_DIR = "IARXIU";
  private static final String SIP_FILE_EXTENSION = ".zip";

  public IArxiuSIP() {
    this("");
  }

  /**
   * @param sipId
   */
  public IArxiuSIP(String sipId) {
    super(sipId, "METS/IP.xml");
  }

  /**
   * @param sipId
   */
  public IArxiuSIP(String sipId, IPContentType contentType) {
    super(sipId, contentType);
  }


  public static SIP parse(Path source, Path destinationDirectory) throws ParseException {
    return parseIArxiu(source, destinationDirectory);
  }

  @Override
  public Path build(Path destinationDirectory) throws IPException, InterruptedException {
    LOGGER.warn("iArxiu SIP used for import only! Aborted build for " + destinationDirectory);
    return null;
  }

  @Override
  public Path build(Path destinationDirectory, boolean onlyManifest) throws IPException, InterruptedException {
    LOGGER.warn("iArxiu SIP used for import only! Aborted build for " + destinationDirectory + "; only Manifest: " + onlyManifest);
    return null;
  }

  @Override
  public Path build(Path destinationDirectory, String fileNameWithoutExtension) throws IPException, InterruptedException {
    LOGGER.warn("iArxiu SIP used for import only! Aborted build for " + destinationDirectory +"; file Name Without Extension: " + fileNameWithoutExtension);
    return null;
  }

  @Override
  public Path build(Path destinationDirectory, String fileNameWithoutExtension, boolean onlyManifest) throws IPException, InterruptedException {
    LOGGER.warn("iArxiu SIP used for import only! Aborted build for " + destinationDirectory + "; file Name Without Extension: " + fileNameWithoutExtension + "; only Manifest: " + onlyManifest);
    return null;
  }

  public static SIP parse(Path source) throws ParseException {
    try {
      return parse(source, Files.createTempDirectory("unzipped"));
    } catch (IOException e) {
      throw new ParseException("Error creating temporary directory for iArxiu SIP parse", e);
    }
  }

  /** the iArxiu main METS descriptive metadata and representations
   * - {@link IArxiuUtils#processDescriptiveMetadata(MetsWrapper, IPInterface, Path)}
   * - {@link IArxiuUtils#processRepresentations(MetsWrapper, IPInterface, Path)}
   * all of them, main METS and each representation with its documents metadata and expedients
   * the representations contains the binary data files
   * @param source
   * @param destinationDirectory
   * @return
   * @throws ParseException */
  private static SIP parseIArxiu(final Path source, final Path destinationDirectory) throws ParseException {
    IPConstants.METS_ENCODE_AND_DECODE_HREF = true; // anti-pattern: not valid for multithreading

    final SIP sip = new IArxiuSIP();
    final Path sipPath = ZIPUtils.extractIPIfInZipFormat(source, destinationDirectory);
    sip.setBasePath(sipPath);

    final ValidationReport validationReport = sip.getValidationReport();
    final Path mainMetsFile = CommonSipUtils.getMainMETSFile(LOGGER, validationReport, sipPath);
    if (!validationReport.isValid()) {
      return sip;
    }

    final Mets mainMets = IArxiuUtils.parseMainMets(LOGGER, validationReport, sipPath, mainMetsFile);
    if (!validationReport.isValid()){
      return sip;
    }

    final MetsWrapper mainMetsWrapper = new MetsWrapper(mainMets, mainMetsFile);

    final String metsObjId = mainMets.getOBJID();
    if (StringUtils.isNotBlank(metsObjId)) {
      sip.setIds(Arrays.asList(metsObjId.split(" ")));
    }

    final MetsType.MetsHdr metsHdr = mainMets.getMetsHdr();
    if (metsHdr != null) {
      sip.setCreateDate(metsHdr.getCREATEDATE());
      sip.setModificationDate(metsHdr.getLASTMODDATE());
      sip.setStatus(IPEnums.IPStatus.parse(metsHdr.getRECORDSTATUS()));
    } else {
      LOGGER.info("iArxiu sip '{}' contains no headers in the main mets file: {}", sipPath, mainMetsFile);
      final XMLGregorianCalendar currentDateTime = Utils.getCurrentTime().orElse(null);
      sip.setCreateDate(currentDateTime);
      sip.setModificationDate(currentDateTime);
      sip.setStatus(IPEnums.IPStatus.NEW);
    }

    final IPContentType ipContentType = IArxiuUtils.getSipContentType(mainMets);
    sip.setContentType(ipContentType);

    METSUtils.getHeaderIpAgents(mainMets).forEach(ipAgent -> sip.addAgent(ipAgent));

    final StructMapType structMap = IArxiuUtils.getMainMetsStructMap(mainMetsWrapper, sip);
    if (!sip.isValid()) {
      return sip;
    }

    IArxiuUtils.preProcessStructMap(mainMetsWrapper, structMap);

    try {
      // process the main Descriptive Metadata ( as 'null' representation)
      IArxiuUtils.processDescriptiveMetadata(mainMetsWrapper, sip, sip.getBasePath());
      /* process the representations Descriptive Metadata with their binary files as representation data:  IPRepresentation.List<IPFile> data <- metsWrapper.setDataDiv(eachLevel); */
      IArxiuUtils.processRepresentations(mainMetsWrapper, sip, sip.getBasePath());

    } catch (IPException e) {
      throw new ParseException("Error processing iArxiu SIP parsed Representations Metadata", e);
    }

    ValidationUtils.addInfo(validationReport, ValidationConstants.MAIN_METS_IS_VALID, sipPath, mainMetsFile);

    /* does not support: process of SchemasMetadata, PreservationMetadata, DocumentationMetadata or Ancestors */
    return sip;
  }

  @Override
  public Set<String> getExtraChecksumAlgorithms() {
    return Collections.emptySet();
  }
}