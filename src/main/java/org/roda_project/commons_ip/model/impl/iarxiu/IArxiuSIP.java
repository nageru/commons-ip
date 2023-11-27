/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree
 */
package org.roda_project.commons_ip.model.impl.iarxiu;

import org.roda_project.commons_ip.mets_v1_11.beans.Mets;
import org.roda_project.commons_ip.mets_v1_11.beans.MetsType;
import org.roda_project.commons_ip.model.*;
import org.roda_project.commons_ip.utils.*;
import org.roda_project.commons_ip.utils.ZIPUtils; // TODO commons_ip2.utils.ZIPUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class IArxiuSIP extends SIP {
  private static final Logger LOGGER = LoggerFactory.getLogger(IArxiuSIP.class);
  private static final String SIP_TEMP_DIR = "IARXIU";
  private static final String SIP_FILE_EXTENSION = ".zip";

  public IArxiuSIP() {
    super();
  }

  /**
   * @param sipId
   */
  public IArxiuSIP(String sipId) {
    super(sipId);
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

  private static SIP parseIArxiu(final Path source, final Path destinationDirectory) throws ParseException {
    IPConstants.METS_ENCODE_AND_DECODE_HREF = true; // anti-pattern: not valid for multithreading
    final SIP sip = new IArxiuSIP();

    final Path sipPath = ZIPUtils.extractIPIfInZipFormat(source, destinationDirectory);
    sip.setBasePath(sipPath);

    final ValidationReport validationReport = sip.getValidationReport();
    final Path mainMetsFile = IArxiuUtils.getMainMetsFile(validationReport, sipPath);
    if (mainMetsFile != null && validationReport.isValid()) {
      final Mets mainMets = IArxiuUtils.parseMainMets(validationReport, sipPath, mainMetsFile);
      if (mainMets != null && validationReport.isValid()) {

        sip.setIds(Arrays.asList(mainMets.getOBJID().split(" ")));

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

        METSUtils.getIpAgents(mainMets).forEach(ipAgent -> sip.addAgent(ipAgent));

        ValidationUtils.addInfo(validationReport, ValidationConstants.MAIN_METS_IS_VALID, sipPath, mainMetsFile);
      }
    }

    return sip;
  }

  @Override
  public Set<String> getExtraChecksumAlgorithms() {
    return Collections.emptySet();
  }
}