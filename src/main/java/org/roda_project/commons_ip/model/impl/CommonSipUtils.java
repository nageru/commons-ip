package org.roda_project.commons_ip.model.impl;

import org.roda_project.commons_ip.mets_v1_11.beans.DivType;
import org.roda_project.commons_ip.mets_v1_11.beans.FileType;
import org.roda_project.commons_ip.model.*;
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

    public static void processFileType(IPInterface ip, DivType container, FileType fileType, String folder, Path basePath) {
        if (fileType == null || fileType.getFLocat() != null) {
            FileType.FLocat fLocat = fileType.getFLocat().get(0);
            String href = Utils.extractedRelativePathFromHref(fLocat.getHref());
            Path filePath = basePath.resolve(href);

            if (Files.exists(filePath)) {
                List<String> fileRelativeFolders = Utils.getFileRelativeFolders(basePath.resolve(folder), filePath);
                Optional<IPFile> file = validateFileType(ip, filePath, fileType, fileRelativeFolders);

                if (file.isPresent()) {
                    if (IPConstants.SCHEMAS.equalsIgnoreCase(folder)) {
                        ValidationUtils.addInfo(ip.getValidationReport(),
                                ValidationConstants.SCHEMA_FILE_FOUND_WITH_MATCHING_CHECKSUMS, ip.getBasePath(), filePath);
                        ip.addSchema(file.get());
                    } else if (IPConstants.DOCUMENTATION.equalsIgnoreCase(folder)) {
                        ValidationUtils.addInfo(ip.getValidationReport(),
                                ValidationConstants.DOCUMENTATION_FILE_FOUND_WITH_MATCHING_CHECKSUMS, ip.getBasePath(), filePath);
                        ip.addDocumentation(file.get());
                    } else if (IPConstants.SUBMISSION.equalsIgnoreCase(folder) && ip instanceof AIP) {
                        ValidationUtils.addInfo(ip.getValidationReport(),
                                ValidationConstants.SUBMISSION_FILE_FOUND_WITH_MATCHING_CHECKSUMS, ip.getBasePath(), filePath);
                        ((AIP) ip).addSubmission(file.get());
                    }
                }
            } else {
                if (IPConstants.SCHEMAS.equalsIgnoreCase(folder)) {
                    ValidationUtils.addIssue(ip.getValidationReport(), ValidationConstants.SCHEMA_FILE_NOT_FOUND,
                            ValidationEntry.LEVEL.ERROR, container, ip.getBasePath(), filePath);
                } else if (IPConstants.DOCUMENTATION.equalsIgnoreCase(folder)) {
                    ValidationUtils.addIssue(ip.getValidationReport(), ValidationConstants.DOCUMENTATION_FILE_NOT_FOUND,
                            ValidationEntry.LEVEL.ERROR, container, ip.getBasePath(), filePath);
                } else if (IPConstants.SUBMISSION.equalsIgnoreCase(folder)) {
                    ValidationUtils.addIssue(ip.getValidationReport(), ValidationConstants.SUBMISSION_FILE_NOT_FOUND,
                            ValidationEntry.LEVEL.ERROR, container, ip.getBasePath(), filePath);
                }
            }
        }
    }

    public static Optional<IPFile> validateFileType(IPInterface ip, Path filePath, FileType fileType,
        List<String> fileRelativeFolders) {
        return validateFile(ip, filePath, fileRelativeFolders, fileType.getCHECKSUM(), fileType.getCHECKSUMTYPE(),
        fileType.getID());
    }
}
