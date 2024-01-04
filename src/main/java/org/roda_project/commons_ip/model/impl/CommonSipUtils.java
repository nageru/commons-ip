package org.roda_project.commons_ip.model.impl;

import org.roda_project.commons_ip.mets_v1_11.beans.DivType;
import org.roda_project.commons_ip.mets_v1_11.beans.FileType;
import org.roda_project.commons_ip.mets_v1_11.beans.MdSecType;
import org.roda_project.commons_ip.model.*;
import org.roda_project.commons_ip.utils.IPException;
import org.roda_project.commons_ip.utils.Utils;
import org.roda_project.commons_ip.utils.ValidationConstants;
import org.roda_project.commons_ip.utils.ValidationUtils;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.roda_project.commons_ip.utils.Utils.validateFile;

public class CommonSipUtils {

    /** gets the main mets file from the root of the ip path
     * validates if mets file found
     * @param logger
     * @param validation
     * @param ipPath
     * @return the main mets file path if found; null otherwise */
    public static Path getMainMETSFile(Logger logger, ValidationReport validation, Path ipPath) {

        final Path mainMETSFile;
        final File ipFile;
        if (ipPath != null && (ipFile = ipPath.toFile()).isDirectory()) {
            mainMETSFile = Stream.of(ipFile.listFiles())
                    .filter(file -> !file.isDirectory() && IPConstants.METS_FILE.equalsIgnoreCase(file.getName())).map(file -> file.toPath())
                    .findAny().orElse(null);
        } else {
            mainMETSFile = null;
        }

        if (mainMETSFile != null && Files.exists(mainMETSFile)) {
            ValidationUtils.addInfo(validation, ValidationConstants.MAIN_METS_FILE_FOUND, ipPath, mainMETSFile);
            return mainMETSFile;
        } else {
            ValidationUtils.addIssue(validation, ValidationConstants.MAIN_METS_FILE_NOT_FOUND,
                    ValidationEntry.LEVEL.ERROR, ipPath, mainMETSFile);
            logger.error("Validation {} error: {}", ipPath, ValidationConstants.MAIN_METS_FILE_NOT_FOUND);
            return null;
        }
    }

    /** Does the processing of the MD type of {@link IPConstants#METADATA} metadata file {@link #processMetadataFile(IPInterface, Logger, IPRepresentation, String, MdSecType.MdRef, Path, List)}
     * @param ip
     * @param logger
     * @param representation if in a representation
     * @param metadataFile
     * @param metadataType the MD type of the metadata file
     * @param basePath
     * @throws IPException*/
    public static void processMetadata(IPInterface ip, Logger logger,
                                          IPRepresentation representation, MdSecType.MdRef metadataFile, String metadataType, Path basePath) throws IPException {

        final String href = Utils.extractedRelativePathFromHref(metadataFile);
        final Path filePath = basePath.resolve(href);
        if (Files.exists(filePath)) {
            final List<String> fileRelativeFolders = Utils.getFileRelativeFolders(basePath.resolve(IPConstants.METADATA).resolve(metadataType), filePath);
            processMetadataFile(ip, logger, representation, metadataType, metadataFile, filePath, fileRelativeFolders);
        } else {
            ValidationUtils.addIssue(ip.getValidationReport(),
                    ValidationConstants.getMetadataFileNotFoundString(metadataType), ValidationEntry.LEVEL.ERROR,
                    ip.getBasePath(), filePath);
        }
    }

    /** Does the processing of the metadata file {@link IPDescriptiveMetadata} for only the ip1 eArk and iArxiu Sip supported MD types: {@link IPConstants#DESCRIPTIVE}, {@link IPConstants#PRESERVATION}, {@link IPConstants#OTHER}
     * @param ip
     * @param logger
     * @param representation if in the representation {@link IPRepresentation#addDescriptiveMetadata(IPDescriptiveMetadata)} or if 'null' in the main METS {@link IPInterface#addDescriptiveMetadata(IPDescriptiveMetadata)}
     * @param metadataType
     * @param mdRef
     * @param filePath
     * @param fileRelativeFolders
     * @throws IPException  */
    private static void processMetadataFile(IPInterface ip, Logger logger, IPRepresentation representation,
                                              String metadataType, MdSecType.MdRef mdRef, Path filePath, List<String> fileRelativeFolders) throws IPException {
        final Optional<IPFile> metadataFile = validateMetadataFile(ip, filePath, mdRef, fileRelativeFolders);
        if (metadataFile.isPresent()) {
            ValidationUtils.addInfo(ip.getValidationReport(),
                    ValidationConstants.getMetadataFileFoundWithMatchingChecksumString(metadataType), ip.getBasePath(), filePath);

            if (IPConstants.DESCRIPTIVE.equalsIgnoreCase(metadataType)) {
                final MetadataType dmdType = new MetadataType(mdRef.getMDTYPE().toUpperCase());
                String dmdVersion = null;
                try {
                    dmdVersion = mdRef.getMDTYPEVERSION();
                    if (isNotBlank(mdRef.getOTHERMDTYPE())) {
                        dmdType.setOtherType(mdRef.getOTHERMDTYPE());
                    }
                    logger.debug("Metadata type valid: {}", dmdType);
                } catch (NullPointerException | IllegalArgumentException e) {
                    // do nothing and use already defined values for metadataType &
                    // metadataVersion
                    logger.debug("Setting metadata type to {}", dmdType);
                    ValidationUtils.addEntry(ip.getValidationReport(), ValidationConstants.UNKNOWN_DESCRIPTIVE_METADATA_TYPE,
                            ValidationEntry.LEVEL.WARN, "Setting metadata type to " + dmdType, ip.getBasePath(), filePath);
                }

                IPDescriptiveMetadata descriptiveMetadata = new IPDescriptiveMetadata(mdRef.getID(), metadataFile.get(),
                        dmdType, dmdVersion);
                descriptiveMetadata.setCreateDate(mdRef.getCREATED());
                if (representation == null) {
                    ip.addDescriptiveMetadata(descriptiveMetadata);
                } else {
                    representation.addDescriptiveMetadata(descriptiveMetadata);
                }
            } else if (IPConstants.PRESERVATION.equalsIgnoreCase(metadataType)) {
                IPMetadata preservationMetadata = new IPMetadata(metadataFile.get());
                preservationMetadata.setCreateDate(mdRef.getCREATED());
                if (representation == null) {
                    ip.addPreservationMetadata(preservationMetadata);
                } else {
                    representation.addPreservationMetadata(preservationMetadata);
                }
            } else if (IPConstants.OTHER.equalsIgnoreCase(metadataType)) {
                IPMetadata otherMetadata = new IPMetadata(metadataFile.get());
                otherMetadata.setCreateDate(mdRef.getCREATED());
                if (representation == null) {
                    ip.addOtherMetadata(otherMetadata);
                } else {
                    representation.addOtherMetadata(otherMetadata);
                }
            }
        }
    }

    private static Optional<IPFile> validateMetadataFile(IPInterface ip, Path filePath, MdSecType.MdRef mdRef,
                                                           List<String> fileRelativeFolders) {
        return Utils.validateFile(ip, filePath, fileRelativeFolders, mdRef.getCHECKSUM(), mdRef.getCHECKSUMTYPE(),
                mdRef.getID());
    }

    public static Optional<IPFile> validateFileType(IPInterface ip, Path filePath, FileType fileType,
        List<String> fileRelativeFolders) {
        return validateFile(ip, filePath, fileRelativeFolders, fileType.getCHECKSUM(), fileType.getCHECKSUMTYPE(),
        fileType.getID());
    }
}
