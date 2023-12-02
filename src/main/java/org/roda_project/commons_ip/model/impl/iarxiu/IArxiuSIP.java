/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree
 */
package org.roda_project.commons_ip.model.impl.iarxiu;

import org.roda_project.commons_ip.mets_v1_11.beans.Mets;
import org.roda_project.commons_ip.mets_v1_11.beans.MetsType;
import org.roda_project.commons_ip.mets_v1_11.beans.StructMapType;
import org.roda_project.commons_ip.model.*;
import org.roda_project.commons_ip.utils.*;
import org.roda_project.commons_ip.utils.ZIPUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    if (!validationReport.isValid()) {
      return sip;
    }

    final Mets mainMets = IArxiuUtils.parseMainMets(validationReport, sipPath, mainMetsFile);
    if (!validationReport.isValid()){
      return sip;
    }

    final MetsWrapper mainMetsWrapper = new MetsWrapper(mainMets, mainMetsFile);

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

    METSUtils.getHeaderIpAgents(mainMets).forEach(ipAgent -> sip.addAgent(ipAgent));

    final StructMapType structMap = IArxiuUtils.getMainMetsStructMap(mainMetsWrapper, sip);
    if (!sip.isValid()) {
      return sip;
    }

    IArxiuUtils.preProcessStructMap(mainMetsWrapper, structMap);
    /*  iArxiu
         mets:structMap
          mets:div DMDID="EXP_1 EXP_1_DC"
            mets:div DMDID="DOC_1 DOC_1_DC" LABEL="index.xml"
              mets:div LABEL="index.xml"
                mets:fptr FILEID="BIN_1_GRP"
        EARK:
         <structMap ID="uuid-C3C0F7C8-D8FA-43E8-A06F-1165C6CC2383" TYPE="physical" LABEL="Common Specification structural map">
          structMap ID
            div ID="...
              div ID="
                div ID="
                  <fptr FILEID="dc.xml"/>

      TODO ignore
          <structMap ID="uuid-0D8F99F6-2D5C-4F7B-9320-937B4F43683D" LABEL="RODA structural map">
            <div ..
              <mptr xlink:type="simple" xlink:href="representations%2Frep1%2FMETS.xml" LOCTYPE="URL"/>
     */

    try { // processing the binary files as documentation TODO ¿as DATA?: metsWrapper.setDataDiv(firstLevel);
      IArxiuUtils.processFilesMetadataAsDocumentation(mainMetsWrapper, sip, sip.getBasePath());
      // not yet DC metadata pre-processed: EARKUtils.processPreservationMetadata(mainMetsWrapper, sip, LOGGER, null, sip.getBasePath());
    } catch (IPException e) {
      throw new ParseException("Error processing iArxiu SIP parsed Preservation Metadata", e);
    }

    ValidationUtils.addInfo(validationReport, ValidationConstants.MAIN_METS_IS_VALID, sipPath, mainMetsFile);

    /*
      EARKUtils.processOtherMetadata(metsWrapper, sip, LOGGER, null, sip.getBasePath());
      EARKUtils.processPreservationMetadata(metsWrapper, sip, LOGGER, null, sip.getBasePath());
      EARKUtils.processRepresentations(metsWrapper, sip, LOGGER);
      EARKUtils.processSchemasMetadata(metsWrapper, sip, sip.getBasePath());
      EARKUtils.processDocumentationMetadata(metsWrapper, sip, sip.getBasePath());
      EARKUtils.processAncestors(metsWrapper, sip);
     */

   /* TODO read descriptiveMetadata -> dmdSec : Voc_document_exp:
         <-  ¿EARK processDescriptiveMetadata ?
    */
    /* TODO read descriptiveMetadata -> dmdSec : www.openarchives.org/OAI/2.0/oai_dc/ : <- EARK metadata/descriptive/dc.xml
        <- EARKUtils.processDescriptiveMetadata(metsWrapper, sip, LOGGER, null, sip.getBasePath());
     */

    // TODO mets:fileSec <- mets:fileGrp ¿processDocumentationMetadata?

    return sip;
  }

  @Override
  public Set<String> getExtraChecksumAlgorithms() {
    return Collections.emptySet();
  }
}