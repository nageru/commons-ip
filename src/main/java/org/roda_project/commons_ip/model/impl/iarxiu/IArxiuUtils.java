/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree
 */
package org.roda_project.commons_ip.model.impl.iarxiu;

import org.apache.commons.lang3.StringUtils;
import org.roda_project.commons_ip.mets_v1_11.beans.*;
import org.roda_project.commons_ip.model.*;
import org.roda_project.commons_ip.model.MetadataType.MetadataTypeEnum;
import org.roda_project.commons_ip.model.impl.CommonSipUtils;
import org.roda_project.commons_ip.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.roda_project.commons_ip.model.IPConstants.COMMON_SPEC_STRUCTURAL_MAP_ID;
import static org.roda_project.commons_ip.model.impl.CommonSipUtils.validateFileType;

public final class IArxiuUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(IArxiuUtils.class);

  private IArxiuUtils() {
    // do nothing
  }

  /**
   * extracts and validates the main mets from the main mets file
   * @param logger
   * @param validation updates the done validations
   * @param ipPath
   * @return if the mets parsed null otherwise
   */
  public static Mets parseMainMets(Logger logger, ValidationReport validation, Path ipPath, Path mainMETSFile) {
      try {
        return METSUtils.instantiateIArxiuMETSFromFile(logger, mainMETSFile);
      } catch (JAXBException | SAXException e) {
        ValidationUtils.addIssue(validation, ValidationConstants.MAIN_METS_NOT_VALID,
                ValidationEntry.LEVEL.ERROR, e, ipPath, mainMETSFile);
        logger.error("Error parsing IP '{}' main METS '{}': {}", ipPath, mainMETSFile, e);
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
      ValidationUtils.addIssue(ip.getValidationReport(),
              ValidationConstants.MAIN_METS_HAS_NO_I_ARXIU_STRUCT_MAP,
              ValidationEntry.LEVEL.ERROR, structMap = null, ip.getBasePath(), metsWrapper.getMetsPath());
    } else {
      structMap = metsStructMapList.remove(0);
      if (smCount > 1){
        LOGGER.warn("Main METS.xml file has too many ({}) structural map. Will take first only! Ignored: {}", smCount, metsStructMapList);
      }
      ValidationUtils.addInfo(ip.getValidationReport(),
              ValidationConstants.MAIN_METS_HAS_I_ARXIU_STRUCT_MAP,
              structMap, ip.getBasePath(), metsWrapper.getMetsPath());
    }
    return structMap;
  }

  protected static void preProcessStructMap(MetsWrapper metsWrapper, StructMapType structMap) {

    if (structMap == null || structMap.getDiv() == null){
      LOGGER.warn("Mets has no struct map information! {}: {}", metsWrapper, structMap);
      return;
    }

    final DivType aipDiv = structMap.getDiv();
    if (aipDiv.getDiv() != null) {

      metsWrapper.setMainDiv(aipDiv); //  descriptive metadata: aipDiv -> dmdid -> List {MdSecType@3284}

      for (DivType firstLevel : aipDiv.getDiv()) {
        final String representationLabelId = getLabel(firstLevel); // iArxiu LABEL ="[FILE].xml"
        final DivType representationMetadataFilesDiv = findFilesMetadataDiv(firstLevel);

        if (representationMetadataFilesDiv != null) { // process the found files metadata each as a representation
          LOGGER.info("Setting iArxiu IP first level div label '{}' of files metadata as representations: {}", representationLabelId, representationMetadataFilesDiv);
          metsWrapper.addRepresentationDiv(representationMetadataFilesDiv);
        } else {
          LOGGER.warn("IP first level div label '{}' discarded; contains no files metadata: {}", representationLabelId, firstLevel);
        }
      }
    }
  }

  private static List<MdSecType> findMainDescriptiveMetadataFiles(DivType fdiv){
    if (fdiv == null || fdiv.getDMDID() == null){
      return new ArrayList<>();
    }
    return fdiv.getDMDID().stream().filter(o -> o instanceof MdSecType).map(md -> ((MdSecType) md)).filter(mdSecType -> mdSecType.getID() != null && mdSecType.getMdWrap() != null).collect(Collectors.toList());
  }

  private static DivType findFilesMetadataDiv(DivType fdiv){
    final String labelId = getLabel(fdiv);
    final List<String> fileIds;
    if (labelId != null && !(fileIds = getFilePointerFileIds(fdiv)).isEmpty()){
      LOGGER.info("Div label '{}' with File Metadata metadata File Pointer files: {}", labelId, fileIds);
      return fdiv;
    }

    if (fdiv.getDiv() != null) { // find only when matching the parent label (it can be relaxed to allow file group nested div with different label)
      final List<DivType> relevant2ndLevels = fdiv.getDiv().stream()
              .filter(div -> div != null && isNotBlank(div.getLABEL()) && (labelId ==  null || div.getLABEL().equalsIgnoreCase(labelId))).collect(Collectors.toList());

      final List<String> relevant2ndLevelsFilePointers = new ArrayList<>();
      relevant2ndLevels.stream().map(IArxiuUtils::getFilePointerFileIds).forEach(relevant2ndLevelsFilePointers::addAll);
      if (!relevant2ndLevelsFilePointers.isEmpty()){
        LOGGER.info("Div label '{}' with File Metadata File Pointer files in secondary levels: {}", labelId, relevant2ndLevelsFilePointers);
        return fdiv;
      }
    }

    return null;
  }

  /** Process the main METS descriptive metadata {@link #loadDescriptiveMetadataFiles(DivType, List, Map)} as
   * - documents {@link #processMetadataDocument(IPInterface, Logger, IPRepresentation, MdSecType.MdWrap, String, String, Path)}
   * - expedients  {@link #processMetadataExpedients(IPInterface, Logger, IPRepresentation, Map, Path)}
   * - and also supports the not known other expedients types: {@link #processOtherDocuments(IPInterface, Logger, IPRepresentation, Map, Path)}
   * @param metsWrapper
   * @param ip
   * @param basePath
   * @throws IPException */
  protected static void processDescriptiveMetadata(MetsWrapper metsWrapper, IPInterface ip, Path basePath) throws IPException {
    final DivType mainDiv = metsWrapper.getMainDiv();
    if (mainDiv != null) {
      /*  Expedient
       *  dmdSec ID="EXP_1_DC": mdWrap MDTYPE="DC"
       *  dmdSec ID="EXP_1": 1 = {MdSecType@3269}
       *     mdtype = "OTHER"
       *      OTHERMDTYPE="Voc_expedient" */
      final List<MdSecType> documentsMetadata = new ArrayList<>();
      /* IPRepresentation.List<IPDescriptiveMetadata>.descriptiveMetadata"
       *   mdWrap = {MdSecType$MdWrap@3277}
       *   1 = {MdSecType@3269}
       *     mdtype = "DC" */
      final Map<String, MdSecType.MdWrap> documentXmlData = new HashMap<>();
      /*  1 = {MdSecType@3269}
       *     mdtype = "OTHER"
       *      OTHERMDTYPE="Voc_document_exp" */
      loadDescriptiveMetadataFiles(mainDiv, documentsMetadata, documentXmlData);

      processMetadataDocuments(ip, LOGGER, metsWrapper, null, documentsMetadata, basePath);
      processMetadataExpedients(ip, LOGGER, null, documentXmlData, basePath);
      processOtherDocuments(ip, LOGGER, null, documentXmlData, basePath);

    } // already validation error on pre-processing: ValidationReport MAIN_METS_HAS_NO_E_ARK_STRUCT_MAP
  }

  /** The Descriptive {@link IPConstants#DESCRIPTIVE} metadata {@link IPConstants#METADATA} to process  {@link CommonSipUtils#processMetadata(IPInterface, Logger, IPRepresentation, MdSecType.MdRef, String, Path)}
   * @param ip
   * @param logger
   * @param metsWrapper
   * @param representation if a representation or 'null' a main METS
   * @param metadataSecList
   * @param basePath
   * @throws IPException */
  private static void processMetadataDocuments(IPInterface ip, Logger logger, MetsWrapper metsWrapper, IPRepresentation representation,
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
        CommonSipUtils.processMetadata(ip, logger, representation, mdRef, metadataType, basePath);
      }
    } else {
      ValidationUtils.addIssue(ip.getValidationReport(),
              ValidationConstants.getMetadataFileNotFoundString(metadataType), ValidationEntry.LEVEL.ERROR,
              ip.getBasePath(), metsWrapper.getMetsPath());
    }
  }

  /** The MD {@link MetadataTypeEnum#OTHER} type of the expedients {@link #processMetadataDocument(IPInterface, Logger, IPRepresentation, MdSecType.MdWrap, String, String, Path)}
   *  as {@link IPConstants#DESCRIPTIVE} metadata
   * @param ip
   * @param logger
   * @param representation if a representation or if 'null' a main METS
   * @param expedientXmlData
   * @param basePath
   * @throws IPException */
  private static void processMetadataExpedients(IPInterface ip, Logger logger, IPRepresentation representation,
                                                Map<String, MdSecType.MdWrap> expedientXmlData, Path basePath) throws IPException {

    final Iterator<Map.Entry<String, MdSecType.MdWrap>> expedientSet = expedientXmlData.entrySet().iterator();
    while (expedientSet.hasNext()) {
      final Map.Entry<String, MdSecType.MdWrap> expEntry = expedientSet.next();
      final String expId = expEntry.getKey();
      final MdSecType.MdWrap expXmlData = expedientXmlData.get(expId);
      if (expXmlData == null) {
        LOGGER.warn("Missing iArxiu SIP '{}' {}expedient XML data for Exp metadata file '{}': {}",
                ip.getId(), representation != null ? "representation '" + representation.getRepresentationID() + "' " : "",
                expId, expedientXmlData);
        expedientSet.remove(); // not attempt to process anymore
      } else {
        final MetadataType.MetadataTypeEnum type = MetadataType.match(expXmlData.getMDTYPE());
        final MetadataType.MetadataTypeEnum otherType = MetadataType.match(expXmlData.getOTHERMDTYPE());
        if (type != null && type != MetadataType.MetadataTypeEnum.OTHER || otherType != null){
          // sample: ...temp.../metadata/OTHER/DOC_1.xml
          processMetadataDocument(ip, logger, representation, expXmlData, expId, IPConstants.DESCRIPTIVE, basePath);
          expedientSet.remove(); // processed once only
        }
      }
    }
  }

  /** Process as the MD type metadata {@link #processMetadataDocument(IPInterface, Logger, IPRepresentation, MdSecType.MdWrap, String, String, Path)}
   * @param ip
   * @param logger
   * @param representation
   * @param documentsXmlData the Document ID and the XML with the MD type {@link MdSecType.MdWrap#getMDTYPE()}
   * @param basePath
   * @throws IPException */
  private static void processOtherDocuments(IPInterface ip, Logger logger, IPRepresentation representation,
                                            Map<String, MdSecType.MdWrap> documentsXmlData, Path basePath) throws IPException {

    final Set<String> docIdSet = documentsXmlData.keySet();
    for (String docId : docIdSet) {
      final MdSecType.MdWrap expXmlData = documentsXmlData.get(docId);
      final String expMdType = expXmlData.getMDTYPE(); // OTHER
      // .../metadata/OTHER/....xml
      processMetadataDocument(ip, logger, representation, expXmlData, docId, expMdType, basePath);
      documentsXmlData.remove(docId); // processed once only
    }
  }

  /** the given type of metadata type as metadata {@link IPConstants#METADATA} to process {@link CommonSipUtils#processMetadata(IPInterface, Logger, IPRepresentation, MdSecType.MdRef, String, Path)}
   * @param ip
   * @param logger
   * @param representation
   * @param mdXmlData the XML data to extract {@link #xmlToFileHref(String, Path, MdSecType.MdWrap, String)}
   * @param id
   * @param metadataType the type of the metadata
   * @param basePath
   * @throws IPException */
  private static void processMetadataDocument(IPInterface ip, Logger logger, IPRepresentation representation, MdSecType.MdWrap mdXmlData,
                                              String id, String metadataType, Path basePath) throws IPException {

    // sample: ...temp.../metadata/descriptive/DOC_1_DC.xml
    final String descriptiveMetadataPath = Paths.get(IPConstants.METADATA, metadataType).toString();
    final MdSecType.MdRef mdRef = xmlToFileHref(id, basePath, mdXmlData, descriptiveMetadataPath);

    // sample, DOC_1_DC is Voc_document_exp: DOC_1
    CommonSipUtils.processMetadata(ip, logger, representation, mdRef, metadataType, basePath);
  }

  /** creates a new representation with its...
   * - documents {@link #processMetadataDocument(IPInterface, Logger, IPRepresentation, MdSecType.MdWrap, String, String, Path)}
   * - expedients  {@link #processMetadataExpedients(IPInterface, Logger, IPRepresentation, Map, Path)}
   * - and also supports the not known other expedients types: {@link #processOtherDocuments(IPInterface, Logger, IPRepresentation, Map, Path)}
   * - {@link #processRepresentationDataFiles(MetsWrapper, IPInterface, List, IPRepresentation, Path)}
   * @param metsWrapper each {@link MetsWrapper#getRepresentationDivList()}
   * @param ip
   * @param basePath
   * @throws IPException */
  protected static void processRepresentations(MetsWrapper metsWrapper, IPInterface ip, Path basePath)
          throws IPException {

    final List<DivType> representationDivList = metsWrapper.getRepresentationDivList();

    for (DivType representationDiv : representationDivList) {

      final IPRepresentation representation = new IPRepresentation(representationDiv.getLABEL());
      ip.addRepresentation(representation);

      final List<MdSecType> documentsMetadata = new ArrayList<>();
      /* IPRepresentation.List<IPDescriptiveMetadata>.descriptiveMetadata"
       *   mdWrap = {MdSecType$MdWrap@3277}
       *   1 = {MdSecType@3269}
       *     mdtype = "DC" */
      final Map<String, MdSecType.MdWrap> documentXmlData = new HashMap<>();
      /*  1 = {MdSecType@3269}
       *     mdtype = "OTHER"
       *      OTHERMDTYPE="Voc_document_exp" */
      loadDescriptiveMetadataFiles(representationDiv, documentsMetadata, documentXmlData);

      processMetadataDocuments(ip, LOGGER, metsWrapper, representation, documentsMetadata, basePath);
      processMetadataExpedients(ip, LOGGER, representation, documentXmlData, basePath);
      processOtherDocuments(ip, LOGGER, representation, documentXmlData, basePath);

      // as IPRepresentation.List<IPFile> data
      final List<DivType.Fptr> filePointers = getFilePointersList(representationDiv);
      processRepresentationDataFiles(metsWrapper, ip, filePointers, representation, basePath);
    }

    if (ip.getRepresentations().isEmpty()) {       // post-process validations
      ValidationUtils.addIssue(ip.getValidationReport(), ValidationConstants.MAIN_METS_NO_REPRESENTATIONS_FOUND,
              ValidationEntry.LEVEL.WARN, (DivType) null, ip.getBasePath(), metsWrapper.getMetsPath());
    }
  }

  /** prepares the documents and expedients
   * @param div the struct map preprocessed div type (for a main METS or each Representation)
   * @param metadataList documents {@link MetadataTypeEnum#DC}
   * @param xmlDataMap expedients {@link MetadataTypeEnum#OTHER} (includes the OTHER MD TYPEs for the iArxiu expedients, like: {@link MetadataTypeEnum#OTHER_VOC_DOC_EXP}, {@link MetadataTypeEnum#OTHER_VOC_EXP} or {@link MetadataTypeEnum#OTHER_VOC_UPF}) */
  private static void loadDescriptiveMetadataFiles(DivType div, List<MdSecType> metadataList, Map<String, MdSecType.MdWrap> xmlDataMap) {

    final List<MdSecType> metadataTypes = findMainDescriptiveMetadataFiles(div);
    for (MdSecType metadata : metadataTypes) {
      final MdSecType.MdWrap mdWRef = metadata.getMdWrap();
      final String mdType = mdWRef.getMDTYPE();
      final String otherMdType = mdWRef.getOTHERMDTYPE();
      /* 1 = {MdSecType@3269} mdtype = "DC" */
      if (MetadataTypeEnum.DC.getType().equalsIgnoreCase(mdType)) {
        metadataList.add(metadata);
      } else if (MetadataTypeEnum.OTHER.getType().equalsIgnoreCase(mdType)) { /*  MdSecType mdtype = "OTHER" */
        xmlDataMap.put(metadata.getID(), metadata.getMdWrap());
      } else {
        LOGGER.warn("Unknown MD Type '{}' (other '{}') for iArxiu metadata: {}", mdType, otherMdType, mdWRef);
      }
    }
  }

  /** the binary 'data' files in a representation
   * @param metsWrapper
   * @param ip
   * @param filePointers the data files
   * @param representation
   * @param representationBasePath */
  protected static void processRepresentationDataFiles(MetsWrapper metsWrapper, IPInterface ip, List<DivType.Fptr> filePointers,
                                                       IPRepresentation representation, Path representationBasePath) {
    for (DivType.Fptr fptr : filePointers) {
      final List<FileType> fileTypes = getFileTypes(fptr);
      for (FileType fileType : fileTypes) {

        if (fileType != null && fileType.getFLocat() != null) {
          FileType.FLocat fLocat = fileType.getFLocat().get(0);
          String href = Utils.extractedRelativePathFromHref(fLocat.getHref());
          Path filePath = representationBasePath.resolve(href);
          if (Files.exists(filePath)) {
            final List<String> fileRelativeFolders = Utils
                    .getFileRelativeFolders(representationBasePath // not as eARK; not using 'data' folder .resolve(IPConstants.DATA)
                            , filePath);
            final Optional<IPFile> file = validateFileType(ip, filePath, fileType, fileRelativeFolders);

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
                  ValidationEntry.LEVEL.ERROR, fileType, ip.getBasePath(), metsWrapper.getMetsPath());
        }
      }
    }
    // post-process validations
    if (representation.getData().isEmpty()) {
      ValidationUtils.addIssue(ip.getValidationReport(), ValidationConstants.REPRESENTATION_HAS_NO_FILES,
              ValidationEntry.LEVEL.WARN, metsWrapper.getDataDiv(), ip.getBasePath(),
              metsWrapper.getMetsPath());
    }
  }

  /** does the extraction of the inner XML data to a href link file {@link MdSecType.MdWrap#getXmlData()}
   * @param id
   * @param basePath
   * @param mdWrap the XML data
   * @param metadataPath
   * @return the md type ref to a newly created file */
  private static MdSecType.MdRef xmlToFileHref(String id, Path basePath, MdSecType.MdWrap mdWrap, String metadataPath) {

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

    final MdSecType.MdRef mdRef = METSUtils.createMdRef(id,
            metadataFilePath.toString(), mdWrap.getMDTYPE(), mimetype, mdWrap.getCREATED(),
            size, mdWrap.getOTHERMDTYPE(), mdWrap.getMDTYPEVERSION());
    return mdRef;
  }

  private static String getLabel(DivType div){
    if (div == null){
      return null;
    }
    return isNotBlank(div.getLABEL()) ? div.getLABEL().trim() : null;
  }

  private static List<String> getFilePointerFileIds(DivType div){
    if (div == null || div.getFptr() == null){
      return new ArrayList<>();
    }
    return div.getFptr().stream().filter(fptrDiv -> fptrDiv != null && (fptrDiv.getFILEID() instanceof MetsType.FileSec.FileGrp)).map(fptrDiv -> (MetsType.FileSec.FileGrp)fptrDiv.getFILEID())
            .filter(fileGrp -> isNotBlank(fileGrp.getID())).map(fileGrp -> fileGrp.getID().trim()).collect(Collectors.toList());
  }

  private static List<DivType.Fptr> getFilePointersList(DivType div){
    if (div == null){
      return new ArrayList<>();
    }
    if (div.getFptr() != null && !div.getFptr().isEmpty() // has something
            || div.getDiv() == null || div.getDiv().isEmpty()) { // has no child to look
      return new ArrayList<>();
    }

    final List<DivType.Fptr> filePointersList = new ArrayList<>();

    div.getDiv().stream().filter(dv -> dv != null && dv.getFptr() != null && !dv.getFptr().isEmpty()).map(DivType::getFptr).forEach(fps -> filePointersList.addAll(fps));
    return filePointersList;
  }

  private static List<FileType> getFileTypes(DivType.Fptr filePointer){
    final Object fileId = filePointer.getFILEID();
    if (fileId == null){
      return new ArrayList<>();
    }

    if (fileId instanceof FileType){
      return new ArrayList<FileType>() {{
        add((FileType) fileId);
      }};
    } else if (fileId instanceof MetsType.FileSec.FileGrp){
      return ((MetsType.FileSec.FileGrp) fileId).getFile();
    } else {
      LOGGER.warn("Unknown file id ({}) under file pointer: {}", fileId, filePointer);
      return new ArrayList<>();
    }
  }
}
