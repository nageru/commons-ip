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
import org.roda_project.commons_ip.model.impl.eark.EARKUtils;
import org.roda_project.commons_ip.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        final String representationLabelId = getLabel(firstLevel); // iArxiu LABEL="index.xml"
        final DivType representationMetadataFilesDiv = findFilesMetadataDiv(firstLevel);
        /* firstLevel LABEL="index.xml" -> fptr FILEID="BIN_1_GRP"

              fptr = {ArrayList@3416}  size = 1
               0 = {DivType$Fptr@3419}

             -> fileSec fileGrp ID="BIN_1_GRP"
                  fileid = {MetsType$FileSec$FileGrp@3420}
                    file = {ArrayList@3422}  size = 1
                     0 = {FileType@3426}
                       fLocat = {ArrayList@3427}  size = 1
                            0 = {FileType$FLocat@3438}
                            href = "BIN_1/index.xml"
          */
        if (representationMetadataFilesDiv != null) { // process the iArxiu found files metadata as documentation TODO as data? metsWrapper.setDataDiv(filesMetadataDiv);
          LOGGER.info("Setting iArxiu IP first level div label '{}' of files metadata as representations: {}", representationLabelId, representationMetadataFilesDiv);
          metsWrapper.setRepresentationsDiv(representationMetadataFilesDiv);
        } else {
          LOGGER.warn("IP first level div label '{}' discarded; contains no files metadata: {}", representationLabelId, firstLevel);
        }

        final List<Object> dmdIdDocMetadataList =
        /* namespaceURI = "http://schemas.user.iarxiu.hp.com/2.0/Voc_document_exp"
            dmdid = {ArrayList@3243}  size = 2
                any = {ArrayList@3257}  size = 1
                  name = "voc:document"
               xmlData = {MdSecType$MdWrap$XmlData@3253}
                 0 = {ElementNSImpl@3259} "[voc:document: null]"
              id = "DOC_1"
                   nodes = {ArrayList@3269}  size = 2
                    0 = {AttrNSImpl@3272} "xmlns:mets="http://www.loc.gov/METS/""
                    1 = {AttrNSImpl@3273} "xmlns:voc="http://schemas.user.iarxiu.hp.com/2.0/Voc_document_exp""
               othermdtype = "urn:iarxiu:2.0:vocabularies:cesca:Voc_document_exp"
               mdtype = "OTHER"
               mimetype = "text/xml"
             1 = {MdSecType@3246}
              id = "DOC_1_DC"
               mdtype = "DC"
               mimetype = "text/xml"
                any = {ArrayList@3280}  size = 1
                  namespaceURI = "http://www.openarchives.org/OAI/2.0/oai_dc/"
                  name = "oai:dc"
               xmlData = {MdSecType$MdWrap$XmlData@3277}
         */
                firstLevel.getDMDID();
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

    if (fdiv.getDiv() != null) { // find only when matching the parent label TODO relax to allow file group nested div with different label?
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

  public static void processDescriptiveMetadata(MetsWrapper metsWrapper, IPInterface ip, Path basePath) throws IPException {
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

      EARKUtils.processIArxiuDocuments(ip, LOGGER, metsWrapper, documentsMetadata, documentXmlData, basePath);

    } // already validation error on pre-processing: ValidationReport MAIN_METS_HAS_NO_E_ARK_STRUCT_MAP
  }

  public static void processRepresentations(MetsWrapper metsWrapper, IPInterface ip, Path basePath)
          throws IPException {

    final DivType representationsDiv = metsWrapper.getRepresentationsDiv();

    if (representationsDiv != null) {

      final IPRepresentation representation = new IPRepresentation(representationsDiv.getLABEL());
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
      loadDescriptiveMetadataFiles(representationsDiv, documentsMetadata, documentXmlData);

      EARKUtils.processIArxiuRepresentationDocuments(ip, LOGGER, metsWrapper, representation, documentsMetadata,
              documentXmlData, basePath);

      // as IPRepresentation.List<IPFile> data
      final List<DivType.Fptr> filePointers = getFilePointersList(representationsDiv);
      processRepresentationDataFiles(metsWrapper, ip, filePointers, representation, basePath);
    }

    if (ip.getRepresentations().isEmpty()) {       // post-process validations
      ValidationUtils.addIssue(ip.getValidationReport(), ValidationConstants.MAIN_METS_NO_REPRESENTATIONS_FOUND,
              ValidationEntry.LEVEL.WARN, representationsDiv, ip.getBasePath(), metsWrapper.getMetsPath());
    }
  }

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
            List<String> fileRelativeFolders = Utils
                    .getFileRelativeFolders(representationBasePath // not as eARK; not using 'data' folder .resolve(IPConstants.DATA)
                            , filePath);
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
