/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree
 */
package org.roda_project.commons_ip.model.impl.iarxiu;

import org.roda_project.commons_ip.mets_v1_11.beans.Mets;
import org.roda_project.commons_ip.model.*;
import org.roda_project.commons_ip.utils.*;
import org.roda_project.commons_ip.utils.ZIPUtils; // TODO commons_ip2.utils.ZIPUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    if (true) { /* reading same as from EARK SIP */
      final Path mainMetsFile = IArxiuUtils.getMainMetsFile(validationReport, sipPath);
      if (mainMetsFile != null && validationReport.isValid()) {
        final Mets mainMets = IArxiuUtils.parseMainMets(validationReport, sipPath, mainMetsFile);
        if (mainMets != null && validationReport.isValid()) {

          sip.setIds(Arrays.asList(mainMets.getOBJID().split(" ")));
          sip.setCreateDate(mainMets.getMetsHdr().getCREATEDATE());
          sip.setModificationDate(mainMets.getMetsHdr().getLASTMODDATE());
          sip.setStatus(IPEnums.IPStatus.parse(mainMets.getMetsHdr().getRECORDSTATUS()));

          final IPContentType ipContentType = METSUtils.getIPContentType(mainMets, sip);
          sip.setContentType(ipContentType);

          METSUtils.getIpAgents(mainMets).forEach(ipAgent -> sip.addAgent(ipAgent));

          ValidationUtils.addInfo(validationReport, ValidationConstants.MAIN_METS_IS_VALID, sipPath, mainMetsFile);
        }
      }
    }

    /* Sample from BagIt <- TODO doing as from BagIt? */
    if (false) {
      Map<String, String> metadataMap = new HashMap<>();
      // loads from reader
      sip.setAncestors(Arrays.asList("")); // IPConstants.PARENT_KEY
      sip.setId(""); // value <- IPConstants.ID_KEY
      metadataMap.put("", ""); // <- key, value

      // loads from read map
      final String vendor = metadataMap.get(IPConstants.VENDOR_KEY);

      final Path metadataPath = destinationDirectory.resolve(Utils.generateRandomAndPrefixedUUID());
      try {
        sip.addDescriptiveMetadata(IArxiuUtils.createIArxiuMetadata(metadataMap, metadataPath));

        final Map<String, IPRepresentation> representations = new HashMap<>();
        String representationId = "rep1";

        final Path destPath = null;
        final List<String> directoryPath = new ArrayList<>();
        IPFile file = new IPFile(destPath, directoryPath);
        IPRepresentation representation = new IPRepresentation();
        representation.addFile(file);

        sip.addRepresentation(representation); // <- representations.values()
      } catch (IPException e) {
        throw new ParseException("Error parsing iArxiu SIP", e);
      }

      /* try ... IPRepresentation destPath:
             try (InputStream bagStream = Files.newInputStream(payload);
                OutputStream destStream = Files.newOutputStream(destPath)) {
                IOUtils.copyLarge(bagStream, destStream);
              }
      ... catch (MissingPayloadManifestException | MissingBagitFileException | InterruptedException
             | FileNotInPayloadDirectoryException | InvalidBagitFileFormatException | VerificationException
             | UnsupportedAlgorithmException | CorruptChecksumException | MaliciousPathException
             | MissingPayloadDirectoryException e) {
        throw new ParseException("Error validating iArxiu SIP", e);
      }*/
    }

    return sip;
  }

  @Override
  public Set<String> getExtraChecksumAlgorithms() {
    return Collections.emptySet();
  }
}