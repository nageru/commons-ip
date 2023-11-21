/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree
 */
package org.roda_project.commons_ip.model.impl.iarxiu;

import gov.loc.repository.bagit.domain.Bag;
import gov.loc.repository.bagit.domain.Manifest;
import gov.loc.repository.bagit.exceptions.*;
import gov.loc.repository.bagit.reader.BagReader;
import gov.loc.repository.bagit.verify.BagVerifier;
import org.apache.commons.io.IOUtils;
import org.roda_project.commons_ip.model.*;
import org.roda_project.commons_ip.utils.IPException;
import org.roda_project.commons_ip.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
    IPConstants.METS_ENCODE_AND_DECODE_HREF = true;
    SIP sip = new IArxiuSIP();

    Path sipPath = IArxiuUtils.extractIArxiuIPIfInZipFormat(source, destinationDirectory);
    sip.setBasePath(sipPath);

    try (BagVerifier verifier = new BagVerifier()) {
      BagReader reader = new BagReader();
      Bag bag = reader.read(sipPath);
      verifier.isValid(bag, false);

      Map<String, String> metadataMap = new HashMap<>();

      for (AbstractMap.SimpleImmutableEntry<String, String> nameValue : bag.getMetadata().getAll()) {
        String key = nameValue.getKey();
        String value = nameValue.getValue();

        if (IPConstants.PARENT_KEY.equals(key)) {
          sip.setAncestors(Arrays.asList(value));
        } else {
          if (IPConstants.ID_KEY.equals(key)) {
            sip.setId(value);
          }
          metadataMap.put(key, value);
        }
      }

      String vendor = metadataMap.get(IPConstants.VENDOR_KEY);
      Path metadataPath = destinationDirectory.resolve(Utils.generateRandomAndPrefixedUUID());
      sip.addDescriptiveMetadata(IArxiuUtils.createIArxiuMetadata(metadataMap, metadataPath));
      final Map<String, IPRepresentation> representations = new HashMap<>();
      for (Manifest payLoadManifest : bag.getPayLoadManifests()) {
        Map<Path, String> fileToChecksumMap = payLoadManifest.getFileToChecksumMap();
        for (Path payload : fileToChecksumMap.keySet()) {
          List<String> split = Arrays.asList(sipPath.relativize(payload).toString().split("/"));
          if (split.size() > 1 && IPConstants.DATA_FOLDER_KEY.equals(split.get(0))) {
            String representationId = "rep1";
            int beginIndex = 1;
            if (IPConstants.VENDOR_COMMONS_IP_KEY.equals(vendor)) {
              representationId = split.get(1);
              beginIndex = 2;
            }

            if (!representations.containsKey(representationId)) {
              representations.put(representationId, new IPRepresentation(representationId));
            }

            IPRepresentation representation = representations.get(representationId);
            List<String> directoryPath = split.subList(beginIndex, split.size() - 1);
            Path destPath = destinationDirectory.resolve(split.get(split.size() - 1));
            try (InputStream bagStream = Files.newInputStream(payload);
              OutputStream destStream = Files.newOutputStream(destPath)) {
              IOUtils.copyLarge(bagStream, destStream);
            }

            IPFile file = new IPFile(destPath, directoryPath);
            representation.addFile(file);
          }
        }
      }

      for (IPRepresentation rep : representations.values()) {
        sip.addRepresentation(rep);
      }

      return sip;
    } catch (final IPException | IOException | UnparsableVersionException e) {
      throw new ParseException("Error parsing iArxiu SIP", e);
    } catch (MissingPayloadManifestException | MissingBagitFileException | InterruptedException
      | FileNotInPayloadDirectoryException | InvalidBagitFileFormatException | VerificationException
      | UnsupportedAlgorithmException | CorruptChecksumException | MaliciousPathException
      | MissingPayloadDirectoryException e) {
      throw new ParseException("Error validating iArxiu SIP", e);
    }
  }

  @Override
  public Set<String> getExtraChecksumAlgorithms() {
    return Collections.emptySet();
  }
}