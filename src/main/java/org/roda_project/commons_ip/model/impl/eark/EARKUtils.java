/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/commons-ip
 */
package org.roda_project.commons_ip.model.impl.eark;

import org.apache.commons.lang3.StringUtils;
import org.roda_project.commons_ip.mets_v1_11.beans.*;
import org.roda_project.commons_ip.mets_v1_11.beans.DivType.Fptr;
import org.roda_project.commons_ip.mets_v1_11.beans.DivType.Mptr;
import org.roda_project.commons_ip.mets_v1_11.beans.FileType.FLocat;
import org.roda_project.commons_ip.mets_v1_11.beans.MdSecType.MdRef;
import org.roda_project.commons_ip.model.*;
import org.roda_project.commons_ip.model.impl.CommonSipUtils;
import org.roda_project.commons_ip.model.impl.ModelUtils;
import org.roda_project.commons_ip.utils.*;
import org.roda_project.commons_ip.utils.IPEnums.IPStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBException;
import javax.xml.transform.TransformerException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.roda_project.commons_ip.model.impl.CommonSipUtils.validateFileType;

public final class EARKUtils {
  protected static boolean VALIDATION_FAIL_IF_REPRESENTATION_METS_DOES_NOT_HAVE_TWO_PARTS = false;

  private static final Logger LOGGER = LoggerFactory.getLogger(EARKUtils.class);


  private EARKUtils() {
    // do nothing
  }

  protected static void addDescriptiveMetadataToZipAndMETS(Map<String, ZipEntryInfo> zipEntries,
    MetsWrapper metsWrapper, List<IPDescriptiveMetadata> descriptiveMetadata, String representationId)
    throws IPException, InterruptedException {
    if (descriptiveMetadata != null && !descriptiveMetadata.isEmpty()) {
      for (IPDescriptiveMetadata dm : descriptiveMetadata) {
        if (Thread.interrupted()) {
          throw new InterruptedException();
        }
        IPFile file = dm.getMetadata();

        String descriptiveFilePath = IPConstants.DESCRIPTIVE_FOLDER
          + ModelUtils.getFoldersFromList(file.getRelativeFolders()) + file.getFileName();
        MdRef mdRef = EARKMETSUtils.addDescriptiveMetadataToMETS(metsWrapper, dm, descriptiveFilePath);

        if (representationId != null) {
          descriptiveFilePath = IPConstants.REPRESENTATIONS_FOLDER + representationId + IPConstants.ZIP_PATH_SEPARATOR
            + descriptiveFilePath;
        }
        ZIPUtils.addMdRefFileToZip(zipEntries, file.getPath(), descriptiveFilePath, mdRef);
      }
    }
  }

  protected static void addPreservationMetadataToZipAndMETS(Map<String, ZipEntryInfo> zipEntries,
    MetsWrapper metsWrapper, List<IPMetadata> preservationMetadata, String representationId)
    throws IPException, InterruptedException {
    if (preservationMetadata != null && !preservationMetadata.isEmpty()) {
      for (IPMetadata pm : preservationMetadata) {
        if (Thread.interrupted()) {
          throw new InterruptedException();
        }
        IPFile file = pm.getMetadata();

        String preservationMetadataPath = IPConstants.PRESERVATION_FOLDER
          + ModelUtils.getFoldersFromList(file.getRelativeFolders()) + file.getFileName();
        MdRef mdRef = EARKMETSUtils.addPreservationMetadataToMETS(metsWrapper, pm, preservationMetadataPath);

        if (representationId != null) {
          preservationMetadataPath = IPConstants.REPRESENTATIONS_FOLDER + representationId
            + IPConstants.ZIP_PATH_SEPARATOR + preservationMetadataPath;
        }
        ZIPUtils.addMdRefFileToZip(zipEntries, file.getPath(), preservationMetadataPath, mdRef);
      }
    }
  }

  protected static void addOtherMetadataToZipAndMETS(Map<String, ZipEntryInfo> zipEntries, MetsWrapper metsWrapper,
    List<IPMetadata> otherMetadata, String representationId) throws IPException, InterruptedException {
    if (otherMetadata != null && !otherMetadata.isEmpty()) {
      for (IPMetadata om : otherMetadata) {
        if (Thread.interrupted()) {
          throw new InterruptedException();
        }
        IPFile file = om.getMetadata();

        String otherMetadataPath = IPConstants.OTHER_FOLDER + ModelUtils.getFoldersFromList(file.getRelativeFolders())
          + file.getFileName();
        MdRef mdRef = EARKMETSUtils.addOtherMetadataToMETS(metsWrapper, om, otherMetadataPath);

        if (representationId != null) {
          otherMetadataPath = IPConstants.REPRESENTATIONS_FOLDER + representationId + IPConstants.ZIP_PATH_SEPARATOR
            + otherMetadataPath;
        }
        ZIPUtils.addMdRefFileToZip(zipEntries, file.getPath(), otherMetadataPath, mdRef);
      }
    }
  }

  protected static void addRepresentationsToZipAndMETS(IPInterface ip, List<IPRepresentation> representations,
    Map<String, ZipEntryInfo> zipEntries, MetsWrapper mainMETSWrapper, Path buildDir)
    throws IPException, InterruptedException {
    // representations
    if (representations != null && !representations.isEmpty()) {
      if (ip instanceof SIP) {
        ((SIP) ip).notifySipBuildRepresentationsProcessingStarted(representations.size());
      }
      for (IPRepresentation representation : representations) {
        if (Thread.interrupted()) {
          throw new InterruptedException();
        }
        String representationId = representation.getObjectID();
        // 20160407 hsilva: not being used by Common Specification v0.13
        String representationProfile = "";
        String representationContentType = representation.getContentType().asString();

        IPHeader header = new IPHeader(IPEnums.IPStatus.NEW).setAgents(representation.getAgents());
        MetsWrapper representationMETSWrapper = EARKMETSUtils.generateMETS(representationId,
          representation.getDescription(),
          IPConstants.METS_REPRESENTATION_TYPE_PART_1 + ":" + representationContentType, representationProfile, false,
          Optional.empty(), null, header);
        representationMETSWrapper.getMainDiv().setTYPE(representation.getStatus().asString());

        // representation data
        addRepresentationDataFilesToZipAndMETS(ip, zipEntries, representationMETSWrapper, representation,
          representationId);

        // representation descriptive metadata
        addDescriptiveMetadataToZipAndMETS(zipEntries, representationMETSWrapper,
          representation.getDescriptiveMetadata(), representationId);

        // representation preservation metadata
        addPreservationMetadataToZipAndMETS(zipEntries, representationMETSWrapper,
          representation.getPreservationMetadata(), representationId);

        // representation other metadata
        addOtherMetadataToZipAndMETS(zipEntries, representationMETSWrapper, representation.getOtherMetadata(),
          representationId);

        // representation schemas
        addSchemasToZipAndMETS(zipEntries, representationMETSWrapper, representation.getSchemas(), representationId);

        // representation documentation
        addDocumentationToZipAndMETS(zipEntries, representationMETSWrapper, representation.getDocumentation(),
          representationId);

        // add representation METS to Zip file and to main METS file
        EARKMETSUtils.addRepresentationMETSToZipAndToMainMETS(zipEntries, mainMETSWrapper, representationId,
          representationMETSWrapper, IPConstants.REPRESENTATIONS_FOLDER + representationId
            + IPConstants.ZIP_PATH_SEPARATOR + IPConstants.METS_FILE,
          buildDir);
      }
      if (ip instanceof SIP) {
        ((SIP) ip).notifySipBuildRepresentationsProcessingEnded();
      }
    }
  }

  protected static void addRepresentationDataFilesToZipAndMETS(IPInterface ip, Map<String, ZipEntryInfo> zipEntries,
    MetsWrapper representationMETSWrapper, IPRepresentation representation, String representationId)
    throws IPException, InterruptedException {
    if (representation.getData() != null && !representation.getData().isEmpty()) {
      if (ip instanceof SIP) {
        ((SIP) ip).notifySipBuildRepresentationProcessingStarted(representation.getData().size());
      }
      int i = 0;
      for (IPFile file : representation.getData()) {
        if (Thread.interrupted()) {
          throw new InterruptedException();
        }

        String dataFilePath = IPConstants.DATA_FOLDER + ModelUtils.getFoldersFromList(file.getRelativeFolders())
          + file.getFileName();
        FileType fileType = EARKMETSUtils.addDataFileToMETS(representationMETSWrapper, dataFilePath, file.getPath());

        dataFilePath = IPConstants.REPRESENTATIONS_FOLDER + representationId + IPConstants.ZIP_PATH_SEPARATOR
          + dataFilePath;
        ZIPUtils.addFileTypeFileToZip(zipEntries, file.getPath(), dataFilePath, fileType);

        i++;
        if (ip instanceof SIP) {
          ((SIP) ip).notifySipBuildRepresentationProcessingCurrentStatus(i);
        }
      }
      if (ip instanceof SIP) {
        ((SIP) ip).notifySipBuildRepresentationProcessingEnded();
      }
    }
  }

  protected static void addSchemasToZipAndMETS(Map<String, ZipEntryInfo> zipEntries, MetsWrapper metsWrapper,
    List<IPFile> schemas, String representationId) throws IPException, InterruptedException {
    if (schemas != null && !schemas.isEmpty()) {
      for (IPFile schema : schemas) {
        if (Thread.interrupted()) {
          throw new InterruptedException();
        }

        String schemaFilePath = IPConstants.SCHEMAS_FOLDER + ModelUtils.getFoldersFromList(schema.getRelativeFolders())
          + schema.getFileName();
        FileType fileType = EARKMETSUtils.addSchemaFileToMETS(metsWrapper, schemaFilePath, schema.getPath());

        if (representationId != null) {
          schemaFilePath = IPConstants.REPRESENTATIONS_FOLDER + representationId + IPConstants.ZIP_PATH_SEPARATOR
            + schemaFilePath;
        }
        ZIPUtils.addFileTypeFileToZip(zipEntries, schema.getPath(), schemaFilePath, fileType);
      }
    }
  }

  protected static void addDocumentationToZipAndMETS(Map<String, ZipEntryInfo> zipEntries, MetsWrapper metsWrapper,
    List<IPFile> documentation, String representationId) throws IPException, InterruptedException {
    if (documentation != null && !documentation.isEmpty()) {
      for (IPFile doc : documentation) {
        if (Thread.interrupted()) {
          throw new InterruptedException();
        }

        String documentationFilePath = IPConstants.DOCUMENTATION_FOLDER
          + ModelUtils.getFoldersFromList(doc.getRelativeFolders()) + doc.getFileName();
        FileType fileType = EARKMETSUtils.addDocumentationFileToMETS(metsWrapper, documentationFilePath, doc.getPath());

        if (representationId != null) {
          documentationFilePath = IPConstants.REPRESENTATIONS_FOLDER + representationId + IPConstants.ZIP_PATH_SEPARATOR
            + documentationFilePath;
        }
        ZIPUtils.addFileTypeFileToZip(zipEntries, doc.getPath(), documentationFilePath, fileType);
      }
    }
  }

  protected static void addDefaultSchemas(Logger logger, List<IPFile> schemas, Path buildDir)
    throws InterruptedException {
    try {
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
      Path metsSchema = Utils.copyResourceFromClasspathToDir(EARKSIP.class, buildDir, "mets.xsd",
        "/schemas/mets1_11.xsd");
      schemas.add(new IPFile(metsSchema, "mets.xsd"));
      Path xlinkSchema = Utils.copyResourceFromClasspathToDir(EARKSIP.class, buildDir, "xlink.xsd",
        "/schemas/xlink.xsd");
      schemas.add(new IPFile(xlinkSchema, "xlink.xsd"));
    } catch (IOException e) {
      logger.error("Error while trying to add default schemas", e);
    }
  }

  protected static void addSubmissionsToZipAndMETS(final Map<String, ZipEntryInfo> zipEntries,
    final MetsWrapper metsWrapper, final List<IPFile> submissions) throws IPException, InterruptedException {
    if (submissions != null && !submissions.isEmpty()) {
      for (IPFile submission : submissions) {
        if (Thread.interrupted()) {
          throw new InterruptedException();
        }
        final String submissionFilePath = IPConstants.SUBMISSION_FOLDER
          + ModelUtils.getFoldersFromList(submission.getRelativeFolders()) + submission.getFileName();
        final FileType fileType = EARKMETSUtils.addSubmissionFileToMETS(metsWrapper, submissionFilePath,
          submission.getPath());
        ZIPUtils.addFileTypeFileToZip(zipEntries, submission.getPath(), submissionFilePath, fileType);
      }
    }
  }

  protected static MetsWrapper processMainMets(IPInterface ip, Path ipPath) {

    final Path mainMETSFile = CommonSipUtils.getMainMETSFile(LOGGER, ip.getValidationReport(), ipPath);
    Mets mainMets = null;
    if (mainMETSFile != null) {
      ValidationUtils.addInfo(ip.getValidationReport(), ValidationConstants.MAIN_METS_FILE_FOUND, ipPath, mainMETSFile);
      try {
        mainMets = METSUtils.instantiateMETS1_11FromFile(LOGGER, mainMETSFile);
        ip.setIds(Arrays.asList(mainMets.getOBJID().split(" ")));
        ip.setCreateDate(mainMets.getMetsHdr().getCREATEDATE());
        ip.setModificationDate(mainMets.getMetsHdr().getLASTMODDATE());
        ip.setStatus(IPStatus.parse(mainMets.getMetsHdr().getRECORDSTATUS()));
        setIPContentType(mainMets, ip);
        addAgentsToMETS(mainMets, ip, null);

        ValidationUtils.addInfo(ip.getValidationReport(), ValidationConstants.MAIN_METS_IS_VALID, ipPath, mainMETSFile);
      } catch (JAXBException | ParseException | SAXException e) {
        mainMets = null;
        ValidationUtils.addIssue(ip.getValidationReport(), ValidationConstants.MAIN_METS_NOT_VALID,
          ValidationEntry.LEVEL.ERROR, e, ip.getBasePath(), mainMETSFile);
      }
    }
    return new MetsWrapper(mainMets, mainMETSFile);
  }

  protected static void setIPContentType(Mets mets, IPInterface ip) throws ParseException {
    ip.setContentType(METSUtils.getIPContentType(mets, ip));
  }

  public static Mets addAgentsToMETS(Mets mets, IPInterface ip, IPRepresentation representation) {
    final List<IPAgent> ipAgentList = METSUtils.getHeaderIpAgents(mets);
    for (IPAgent ipAgent : ipAgentList) {
      if (representation == null) {
        ip.addAgent(ipAgent);
      } else {
        representation.addAgent(ipAgent);
      }
    }

    return mets;
  }

  protected static MetsWrapper processRepresentationMets(IPInterface ip, Path representationMetsFile,
    IPRepresentation representation) {
    Mets representationMets = null;
    if (Files.exists(representationMetsFile)) {
      ValidationUtils.addInfo(ip.getValidationReport(), ValidationConstants.REPRESENTATION_METS_FILE_FOUND,
        ip.getBasePath(), representationMetsFile);
      try {
        representationMets = METSUtils.instantiateMETS1_11FromFile(LOGGER, representationMetsFile);
        setRepresentationContentType(representationMets, representation);
        ValidationUtils.addInfo(ip.getValidationReport(), ValidationConstants.REPRESENTATION_METS_IS_VALID,
          ip.getBasePath(), representationMetsFile);
      } catch (JAXBException | ParseException | SAXException e) {
        representationMets = null;
        ValidationUtils.addIssue(ip.getValidationReport(), ValidationConstants.REPRESENTATION_METS_NOT_VALID,
          ValidationEntry.LEVEL.ERROR, e, ip.getBasePath(), representationMetsFile);
      }
    } else {
      ValidationUtils.addIssue(ip.getValidationReport(), ValidationConstants.REPRESENTATION_METS_FILE_NOT_FOUND,
        ValidationEntry.LEVEL.ERROR, ip.getBasePath(), representationMetsFile);
    }
    return new MetsWrapper(representationMets, representationMetsFile);
  }

  protected static void setRepresentationContentType(Mets mets, IPRepresentation representation) throws ParseException {
    String metsType = mets.getTYPE();

    if (StringUtils.isBlank(metsType)) {
      throw new ParseException("METS 'TYPE' attribute does not contain any value");
    }

    if ("representation".equals(metsType)) {
      if (VALIDATION_FAIL_IF_REPRESENTATION_METS_DOES_NOT_HAVE_TWO_PARTS) {
        throw new ParseException(
          "METS 'TYPE' attribute is not valid as it should be 'representation:REPRESENTATION_TYPE'");
      } else {
        return;
      }
    }

    String[] contentTypeParts = metsType.split(":");
    if (contentTypeParts.length != 2 || StringUtils.isBlank(contentTypeParts[0])
      || !"representation".equals(contentTypeParts[0]) || StringUtils.isBlank(contentTypeParts[1])) {
      throw new ParseException("METS 'TYPE' attribute does not contain a valid value");
    }

    representation.setContentType(new RepresentationContentType(contentTypeParts[1]));
  }

  public static IPInterface processRepresentations(MetsWrapper metsWrapper, IPInterface ip, Logger logger)
    throws IPException {

    if (metsWrapper.getRepresentationsDiv() != null && metsWrapper.getRepresentationsDiv().getDiv() != null) {
      for (DivType representationDiv : metsWrapper.getRepresentationsDiv().getDiv()) {
        if (representationDiv.getMptr() != null && !representationDiv.getMptr().isEmpty()) {
          // we can assume one and only one mets for each representation div
          Mptr mptr = representationDiv.getMptr().get(0);
          String href = Utils.extractedRelativePathFromHref(mptr.getHref());
          Path metsFilePath = ip.getBasePath().resolve(href);
          IPRepresentation representation = new IPRepresentation(representationDiv.getLABEL());
          MetsWrapper representationMetsWrapper = processRepresentationMets(ip, metsFilePath, representation);

          if (representationMetsWrapper.getMets() != null) {
            Path representationBasePath = metsFilePath.getParent();

            StructMapType representationStructMap = getEARKStructMap(representationMetsWrapper, ip, false);
            if (representationStructMap != null) {

              preProcessStructMap(representationMetsWrapper, representationStructMap);
              representation.setStatus(new RepresentationStatus(representationMetsWrapper.getMainDiv().getTYPE()));
              ip.addRepresentation(representation);

              // process representation agents
              processRepresentationAgents(representationMetsWrapper, representation);

              // process files
              processRepresentationFiles(ip, representationMetsWrapper, representation, representationBasePath);

              // process descriptive metadata
              processDescriptiveMetadata(representationMetsWrapper, ip, logger, representation, representationBasePath);

              // process preservation metadata
              processPreservationMetadata(representationMetsWrapper, ip, logger, representation,
                representationBasePath);

              // process other metadata
              processOtherMetadata(representationMetsWrapper, ip, logger, representation, representationBasePath);

              // process schemas
              processSchemasMetadata(representationMetsWrapper, ip, representationBasePath);

              // process documentation
              processDocumentationMetadata(representationMetsWrapper, ip, representationBasePath);
            }
          }
        }
      }

      // post-process validations
      if (ip.getRepresentations().isEmpty()) {
        ValidationUtils.addIssue(ip.getValidationReport(), ValidationConstants.MAIN_METS_NO_REPRESENTATIONS_FOUND,
          ValidationEntry.LEVEL.WARN, metsWrapper.getRepresentationsDiv(), ip.getBasePath(), metsWrapper.getMetsPath());
      }
    }

    return ip;

  }

  protected static StructMapType getEARKStructMap(MetsWrapper metsWrapper, IPInterface ip, boolean mainMets) {
    Mets mets = metsWrapper.getMets();
    StructMapType res = null;
    for (StructMapType structMap : mets.getStructMap()) {
      if (StringUtils.equals(structMap.getLABEL(), IPConstants.COMMON_SPEC_STRUCTURAL_MAP)
        || StringUtils.equals(structMap.getLABEL(), IPConstants.E_ARK_STRUCTURAL_MAP)) {
        res = structMap;
        break;
      }
    }
    if (res == null) {
      ValidationUtils.addIssue(ip.getValidationReport(),
        mainMets ? ValidationConstants.MAIN_METS_HAS_NO_E_ARK_STRUCT_MAP
          : ValidationConstants.REPRESENTATION_METS_HAS_NO_E_ARK_STRUCT_MAP,
        ValidationEntry.LEVEL.ERROR, res, ip.getBasePath(), metsWrapper.getMetsPath());
    } else {
      ValidationUtils.addInfo(ip.getValidationReport(),
        mainMets ? ValidationConstants.MAIN_METS_HAS_E_ARK_STRUCT_MAP
          : ValidationConstants.REPRESENTATION_METS_HAS_E_ARK_STRUCT_MAP,
        res, ip.getBasePath(), metsWrapper.getMetsPath());
    }
    return res;
  }

  protected static void preProcessStructMap(MetsWrapper metsWrapper, StructMapType structMap) {

    DivType aipDiv = structMap.getDiv();
    if (aipDiv.getDiv() != null) {
      metsWrapper.setMainDiv(aipDiv);
      for (DivType firstLevel : aipDiv.getDiv()) {
        if (IPConstants.METADATA.equalsIgnoreCase(firstLevel.getLABEL()) && firstLevel.getDiv() != null) {
          for (DivType secondLevel : firstLevel.getDiv()) {
            if (IPConstants.DESCRIPTIVE.equalsIgnoreCase(secondLevel.getLABEL())) {
              metsWrapper.setDescriptiveMetadataDiv(secondLevel);
            } else if (IPConstants.PRESERVATION.equalsIgnoreCase(secondLevel.getLABEL())) {
              metsWrapper.setPreservationMetadataDiv(secondLevel);
            } else if (IPConstants.OTHER.equalsIgnoreCase(secondLevel.getLABEL())) {
              metsWrapper.setOtherMetadataDiv(secondLevel);
            }
          }
        } else if (IPConstants.REPRESENTATIONS.equalsIgnoreCase(firstLevel.getLABEL())) {
          metsWrapper.setRepresentationsDiv(firstLevel);
        } else if (IPConstants.DATA.equalsIgnoreCase(firstLevel.getLABEL())) {
          metsWrapper.setDataDiv(firstLevel);
        } else if (IPConstants.SCHEMAS.equalsIgnoreCase(firstLevel.getLABEL())) {
          metsWrapper.setSchemasDiv(firstLevel);
        } else if (IPConstants.DOCUMENTATION.equalsIgnoreCase(firstLevel.getLABEL())) {
          metsWrapper.setDocumentationDiv(firstLevel);
        } else if (IPConstants.SUBMISSION.equalsIgnoreCase(firstLevel.getLABEL())) {
          metsWrapper.setSubmissionsDiv(firstLevel);
        }
      }
    }
  }

  public static IPInterface processDescriptiveMetadata(MetsWrapper metsWrapper, IPInterface ip, Logger logger,
    IPRepresentation representation, Path basePath) throws IPException {

    return processMetadata(ip, logger, metsWrapper, representation, metsWrapper.getDescriptiveMetadataDiv(),
      IPConstants.DESCRIPTIVE, basePath);
  }

  protected static IPInterface processOtherMetadata(MetsWrapper metsWrapper, IPInterface ip, Logger logger,
    IPRepresentation representation, Path basePath) throws IPException {

    return processMetadata(ip, logger, metsWrapper, representation, metsWrapper.getOtherMetadataDiv(),
      IPConstants.OTHER, basePath);
  }

  protected static IPInterface processPreservationMetadata(MetsWrapper metsWrapper, IPInterface ip, Logger logger,
    IPRepresentation representation, Path basePath) throws IPException {

    return processMetadata(ip, logger, metsWrapper, representation, metsWrapper.getPreservationMetadataDiv(),
      IPConstants.PRESERVATION, basePath);
  }

  protected static IPInterface processMetadata(IPInterface ip, Logger logger, MetsWrapper representationMetsWrapper,
                                               IPRepresentation representation, DivType div, String metadataType, Path basePath) throws IPException {
    if (div != null && div.getFptr() != null) {
      for (Fptr fptr : div.getFptr()) {
        final MdRef metadataFile = (MdRef) fptr.getFILEID();
        processMetadata(ip, logger, representation, metadataFile, metadataType, basePath);
      }
    } else {
      ValidationUtils.addIssue(ip.getValidationReport(),
        ValidationConstants.getMetadataFileFptrNotFoundString(metadataType), ValidationEntry.LEVEL.ERROR,
        ip.getBasePath(), representationMetsWrapper.getMetsPath());
    }

    return ip;
  }

  public static void processIArxiuDocuments(IPInterface ip, Logger logger, MetsWrapper mainMetsWrapper,
                                            List<MdSecType> metadataSecList, Map<String, MdSecType.MdWrap> expedientXmlData, Path basePath) throws IPException {
    processIArxiuMetadataDocuments(ip, logger, mainMetsWrapper, null, metadataSecList, basePath);
    processIArxiuMetadataExpedients(ip, logger, null, expedientXmlData, basePath);
    processIArxiuOtherDocuments(ip, logger, null, expedientXmlData, basePath);
  }

  public static void processIArxiuRepresentationDocuments(IPInterface ip, Logger logger, MetsWrapper representationMetsWrapper,
                                                          IPRepresentation representation, List<MdSecType> metadataSecList, Map<String, MdSecType.MdWrap> expedientXmlData, Path basePath) throws IPException {
    processIArxiuMetadataDocuments(ip, logger, representationMetsWrapper, representation, metadataSecList, basePath);
    processIArxiuMetadataExpedients(ip, logger, representation, expedientXmlData, basePath);
    processIArxiuOtherDocuments(ip, logger, representation, expedientXmlData, basePath);
  }

  private static void processIArxiuMetadataDocuments(IPInterface ip, Logger logger, MetsWrapper metsWrapper, IPRepresentation representation,
                                                        List<MdSecType> metadataSecList, Path basePath) throws IPException {

    final String metadataType = IPConstants.DESCRIPTIVE;
    if (metadataSecList != null) {
      for (MdSecType metadataSec : metadataSecList) {

        final String id = metadataSec.getID();
        final MdSecType.MdWrap mdXmlData = metadataSec.getMdWrap();
        // sample: ...temp.../metadata/descriptive/DOC_1_DC.xml
        final String descriptiveMetadataPath = Paths.get(IPConstants.METADATA, metadataType).toString();
        final MdSecType.MdRef mdRef = xmlToFileHref(id, basePath, mdXmlData, descriptiveMetadataPath);

        // sample, DOC_1_DC is Voc_document_exp: DOC_1
        processMetadata(ip, logger, representation, mdRef, metadataType, basePath);
      }
    } else {
      ValidationUtils.addIssue(ip.getValidationReport(),
              ValidationConstants.getMetadataFileNotFoundString(metadataType), ValidationEntry.LEVEL.ERROR,
              ip.getBasePath(), metsWrapper.getMetsPath());
    }
  }

  private static void processIArxiuMetadataExpedients(IPInterface ip, Logger logger, IPRepresentation representation,
                                                      Map<String, MdSecType.MdWrap> expedientXmlData, Path basePath) throws IPException {

    final Set<String> expIdSet = expedientXmlData.keySet();
    for (String expId : expIdSet) {
      final MdSecType.MdWrap expXmlData = expedientXmlData.get(expId);
      if (expXmlData == null) {
        LOGGER.warn("Missing iArxiu SIP '{}' {}expedient XML data for Exp metadata file '{}': {}",
                ip.getId(), representation != null ? "representation '" + representation.getRepresentationID() + "' " : "",
                expId, expedientXmlData);
        expedientXmlData.remove(expId); // not attempt to process anymore
      } else {
        final MetadataType.MetadataTypeEnum type = MetadataType.match(expXmlData.getMDTYPE());
        final MetadataType.MetadataTypeEnum otherType = MetadataType.match(expXmlData.getOTHERMDTYPE());
        if (type != null && type != MetadataType.MetadataTypeEnum.OTHER || otherType != null){
          // sample: ...temp.../metadata/OTHER/DOC_1.xml
          processIArxiuMetadataDocument(ip, logger, representation, expXmlData, expId, IPConstants.DESCRIPTIVE, basePath);
          expedientXmlData.remove(expId); // processed once only
        }
      }
    }
  }

  private static void processIArxiuOtherDocuments(IPInterface ip, Logger logger, IPRepresentation representation,
                                                  Map<String, MdSecType.MdWrap> documentsXmlData, Path basePath) throws IPException {

    final Set<String> docIdSet = documentsXmlData.keySet();
    for (String docId : docIdSet) {
      final MdSecType.MdWrap expXmlData = documentsXmlData.get(docId);
      final String expMdType = expXmlData.getMDTYPE(); // OTHER
      // .../metadata/OTHER/....xml
      processIArxiuMetadataDocument(ip, logger, representation, expXmlData, docId, expMdType, basePath);
      documentsXmlData.remove(docId); // processed once only
    }
  }

  private static void processIArxiuMetadataDocument(IPInterface ip, Logger logger, IPRepresentation representation, MdSecType.MdWrap mdXmlData,
                                                    String id, String metadataType, Path basePath) throws IPException {

    // sample: ...temp.../metadata/descriptive/DOC_1_DC.xml
    final String descriptiveMetadataPath = Paths.get(IPConstants.METADATA, metadataType).toString();
    final MdSecType.MdRef mdRef = xmlToFileHref(id, basePath, mdXmlData, descriptiveMetadataPath);

    // sample, DOC_1_DC is Voc_document_exp: DOC_1
    processMetadata(ip, logger, representation, mdRef, metadataType, basePath);
  }

  private static MdRef xmlToFileHref(String id, Path basePath, MdSecType.MdWrap mdWrap, String metadataPath) {

    final String mimetype = mdWrap.getMIMETYPE();

    String mimeTypeFileExtension = ".xml";
    if (StringUtils.isNotBlank(mimetype)) {
      final String[] mimeParts = mimetype.split("/");
      final String mimeTypeSuffix = mimeParts[mimeParts.length - 1];
      if (StringUtils.isNotBlank(mimeTypeSuffix) && mimeTypeSuffix.trim().length() > 2) {
        mimeTypeFileExtension = "." + mimeTypeSuffix.trim().toLowerCase();
      }
    }
    String fileName = id;
    if (!fileName.trim().toLowerCase().endsWith(mimeTypeFileExtension)) {
      fileName += mimeTypeFileExtension;
    }

    Long size = null;
    final Path metadataFilePath = basePath.resolve(metadataPath).resolve(fileName);

    final Element xmlData = mdWrap.getXmlData().getAny().stream().filter(o -> o instanceof Element).map(e -> (Element) e).findFirst().orElse(null);
    if (xmlData == null) {
      LOGGER.warn("No document found under xml data id '{}' ({}) for href file '{}'",
              mdWrap.getID(), mdWrap, metadataPath);
    } else {
      try {
        final File metadataFile = METSUtils.marshallXmlToFile(xmlData, metadataFilePath);
        size = metadataFile.length();
      } catch (IOException | TransformerException e) {
        LOGGER.error("Failed to convert '{}' xml data id '{}' ({}) to metadata href file '{}': {}",
                metadataPath, mdWrap.getID(), mdWrap, metadataFilePath, e);
      } // returns the mdRef anyway for later validation error
    }

    final MdRef mdRef = METSUtils.createMdRef(id,
            metadataFilePath.toString(), mdWrap.getMDTYPE(), mimetype, mdWrap.getCREATED(),
            size, mdWrap.getOTHERMDTYPE(), mdWrap.getMDTYPEVERSION());
    return mdRef;
  }

  protected static void processMetadata(IPInterface ip, Logger logger,
                                        IPRepresentation representation, MdRef metadataFile, String metadataType, Path basePath) throws IPException {

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

  protected static void processMetadataFile(IPInterface ip, Logger logger, IPRepresentation representation,
    String metadataType, MdRef mdRef, Path filePath, List<String> fileRelativeFolders) throws IPException {
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


  protected static Optional<IPFile> validateMetadataFile(IPInterface ip, Path filePath, MdRef mdRef,
    List<String> fileRelativeFolders) {
    return Utils.validateFile(ip, filePath, fileRelativeFolders, mdRef.getCHECKSUM(), mdRef.getCHECKSUMTYPE(),
      mdRef.getID());
  }

  protected static IPInterface processFile(IPInterface ip, DivType div, String folder, Path basePath) {
    if (div != null && div.getFptr() != null) {
      for (Fptr fptr : div.getFptr()) {
        FileType fileType = (FileType) fptr.getFILEID();
          CommonSipUtils.processFileType(ip, div, fileType, folder, basePath);
        }
      }
    return ip;
  }

  protected static void processRepresentationAgents(MetsWrapper representationMetsWrapper,
    IPRepresentation representation) {
    addAgentsToMETS(representationMetsWrapper.getMets(), null, representation);
  }

  protected static void processRepresentationFiles(IPInterface ip, MetsWrapper representationMetsWrapper,
    IPRepresentation representation, Path representationBasePath) throws IPException {

    if (representationMetsWrapper.getDataDiv() != null && representationMetsWrapper.getDataDiv().getFptr() != null) {
      for (Fptr fptr : representationMetsWrapper.getDataDiv().getFptr()) {
        FileType fileType = (FileType) fptr.getFILEID();

        if (fileType != null && fileType.getFLocat() != null) {
          FLocat fLocat = fileType.getFLocat().get(0);
          String href = Utils.extractedRelativePathFromHref(fLocat.getHref());
          Path filePath = representationBasePath.resolve(href);
          if (Files.exists(filePath)) {
            List<String> fileRelativeFolders = Utils
              .getFileRelativeFolders(representationBasePath.resolve(IPConstants.DATA), filePath);
            Optional<IPFile> file = validateFileType(ip, filePath, fileType, fileRelativeFolders);

            if (file.isPresent()) {
              representation.addFile(file.get());
              ValidationUtils.addInfo(ip.getValidationReport(),
                ValidationConstants.REPRESENTATION_FILE_FOUND_WITH_MATCHING_CHECKSUMS, ip.getBasePath(), filePath);
            }
          } else {
            ValidationUtils.addIssue(ip.getValidationReport(), ValidationConstants.REPRESENTATION_FILE_NOT_FOUND,
              ValidationEntry.LEVEL.ERROR, ip.getBasePath(), filePath);
          }
        } else {
          ValidationUtils.addIssue(ip.getValidationReport(), ValidationConstants.REPRESENTATION_FILE_HAS_NO_FLOCAT,
            ValidationEntry.LEVEL.ERROR, fileType, ip.getBasePath(), representationMetsWrapper.getMetsPath());
        }
      }

      // post-process validations
      if (representation.getData().isEmpty()) {
        ValidationUtils.addIssue(ip.getValidationReport(), ValidationConstants.REPRESENTATION_HAS_NO_FILES,
          ValidationEntry.LEVEL.WARN, representationMetsWrapper.getDataDiv(), ip.getBasePath(),
          representationMetsWrapper.getMetsPath());
      }
    }

  }

  protected static IPInterface processSchemasMetadata(MetsWrapper metsWrapper, IPInterface ip, Path basePath)
    throws IPException {
    return processFile(ip, metsWrapper.getSchemasDiv(), IPConstants.SCHEMAS, basePath);
  }

  protected static IPInterface processDocumentationMetadata(MetsWrapper metsWrapper, IPInterface ip, Path basePath)
    throws IPException {
    return processFile(ip, metsWrapper.getDocumentationDiv(), IPConstants.DOCUMENTATION, basePath);
  }

  protected static IPInterface processAncestors(MetsWrapper metsWrapper, IPInterface ip) {
    Mets mets = metsWrapper.getMets();

    if (mets.getStructMap() != null && !mets.getStructMap().isEmpty()) {
      ip.setAncestors(EARKMETSUtils.extractAncestorsFromStructMap(mets));
    }

    return ip;
  }

  protected static IPInterface processSubmissionMetadata(final MetsWrapper metsWrapper, final IPInterface ip,
    final Path basePath) throws IPException {
    return processFile(ip, metsWrapper.getSubmissionsDiv(), IPConstants.SUBMISSION, basePath);
  }

}
