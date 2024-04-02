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

import static org.apache.commons.lang3.StringUtils.isBlank;
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

    final List<StructMapType> structMapList;
    final List<StructMapType> metsStructMap = mets.getStructMap();

    if (metsStructMap == null || metsStructMap.isEmpty()){
      LOGGER.error("Main METS.xml file has no structural map! {}", metsWrapper.getMetsPath());
      structMapList = null;
    } else if (metsStructMap.size() == 1){
      structMapList = new ArrayList<>(metsStructMap);
      final StructMapType structMapType = structMapList.get(0);
      if (structMapType == null || !COMMON_SPEC_STRUCTURAL_MAP_ID.equalsIgnoreCase(structMapType.getID())){ // not a formal scenario
        LOGGER.warn("Main METS.xml file using a single structural map with unknown id: '{}'", structMapType.getID());
      }
    } else {
      structMapList = mets.getStructMap().stream().filter(structMapType -> {
        final String structMapTypeId = structMapType.getID();
        final boolean foundValidId = COMMON_SPEC_STRUCTURAL_MAP_ID.equalsIgnoreCase(structMapTypeId);
        if (!foundValidId) {
          LOGGER.warn("Main METS.xml file has not recognized structural map id: '{}'", structMapTypeId);
        }
        return foundValidId;
      }).collect(Collectors.toList());
    }

    final StructMapType structMap;
    final long smCount;
    if (structMapList == null){
      LOGGER.error("Main METS.xml file has not any structural map for IArxiu METS '{}'", metsWrapper.getMetsPath());
      ValidationUtils.addIssue(ip.getValidationReport(),
              ValidationConstants.MAIN_METS_HAS_NO_I_ARXIU_STRUCT_MAP,
              ValidationEntry.LEVEL.ERROR, structMap = null, ip.getBasePath(), metsWrapper.getMetsPath());
    } else if ((smCount = structMapList.size()) == 0) {
      LOGGER.error("Main METS.xml file has no structural map for IArxiu '{}' ID", COMMON_SPEC_STRUCTURAL_MAP_ID);
      ValidationUtils.addIssue(ip.getValidationReport(),
              ValidationConstants.MAIN_METS_HAS_NO_I_ARXIU_STRUCT_MAP,
              ValidationEntry.LEVEL.ERROR, structMap = null, ip.getBasePath(), metsWrapper.getMetsPath());
    } else {
      structMap = structMapList.remove(0);
      if (smCount > 1){
        LOGGER.warn("Main METS.xml file has too many ({}) structural map. Will take first only! Ignored: {}", smCount, structMapList);
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
        final DivType representationMetadataFilesDiv = findNestedRepresentationFilesMetadata(firstLevel);

        if (representationMetadataFilesDiv != null) { // process the found files metadata each as a representation
          LOGGER.info("Setting iArxiu IP first level div label '{}' of files metadata as representations: {}", representationLabelId, representationMetadataFilesDiv);
          metsWrapper.addRepresentationDiv(representationMetadataFilesDiv);
        } else {
          LOGGER.info("IP first level div label '{}' discarded; contains no files metadata: {}", representationLabelId, firstLevel);
        }
      }

      final String aipLabelId = getLabel(aipDiv);
      final List<DivType> mainDivFiles = findMainDivFilesMetadata(aipLabelId, aipDiv); // existing files under the main div has to be added to a representation
      if (!mainDivFiles.isEmpty()) {
        final DivType mainDivFilesWrapper = new DivType();
        mainDivFilesWrapper.setLABEL(aipLabelId);
        mainDivFilesWrapper.getDiv().addAll(mainDivFiles);
        LOGGER.info("IP main div label '{}' contains directly {} files to be added as representation: {}", aipLabelId, mainDivFiles.size(), mainDivFilesWrapper);
        metsWrapper.addRepresentationDiv(mainDivFilesWrapper);
      }
    }
  }

  private static List<DivType> findMainDivFilesMetadata(String mainDivLabelId, DivType fdiv){
    if (isBlank(mainDivLabelId)){
      LOGGER.warn("Discarding the MainDiv included Files Metadata check because no main div label id has been provided! " + mainDivLabelId);
      return new ArrayList<>();
    }
    if (fdiv == null || fdiv.getDiv() == null){
      return new ArrayList<>();
    }
    return fdiv.getDiv().stream().filter(dType -> {
      final String mDivFileLabelId = dType.getLABEL();
      final List<DivType.Fptr> filePointers =  dType.getFptr();
      if (isNotBlank(mDivFileLabelId) && filePointers != null && !filePointers.isEmpty()){
        final List<String> filePtrIds = dType.getFptr().stream().map(fptr -> fptr != null ?
                isNotBlank(fptr.getID()) ? fptr.getID() : fptr.getFILEID() != null && fptr.getFILEID() instanceof  MetsType.FileSec.FileGrp ? ((MetsType.FileSec.FileGrp)fptr.getFILEID()).getID()
                        : null : null).collect(Collectors.toList());
        LOGGER.info("Found MainDiv '{}' File Metadata '{}': {}", mainDivLabelId, mDivFileLabelId, filePtrIds);
        return true;
      }
      return false;
    }).collect(Collectors.toList());
  }

  private static List<MdSecType> findMainDescriptiveMetadataFiles(DivType fdiv){
    if (fdiv == null || fdiv.getDMDID() == null){
      return new ArrayList<>();
    }
    return fdiv.getDMDID().stream().filter(o -> o instanceof MdSecType).map(md -> ((MdSecType) md)).filter(mdSecType -> mdSecType.getID() != null && mdSecType.getMdWrap() != null).collect(Collectors.toList());
  }

  private static DivType findNestedRepresentationFilesMetadata(DivType fdiv){
    final String labelId = getLabel(fdiv);

    if (fdiv.getDiv() != null) {
      final List<DivType> relevant2ndLevels = fdiv.getDiv().stream() // find only when matching the parent label (it can be relaxed to allow file group nested div with different label)
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

    String foundSipId;
    if (isBlank(foundSipId = ip.getId())) {
      LOGGER.warn("iArxiu SIP {}for Exp metadata with {} expedients xml data has not informed any SIP Id! {}",
              representation != null ? "representation '" + representation.getRepresentationID() + "' " : "",
              expedientXmlData.size(), foundSipId);
      foundSipId = "[NOT_INFORMED_SIP_ID]"; // for traces
    }
    final Iterator<Map.Entry<String, MdSecType.MdWrap>> expedientSet = expedientXmlData.entrySet().iterator();
    while (expedientSet.hasNext()) {
      final Map.Entry<String, MdSecType.MdWrap> expEntry = expedientSet.next();
      final String expId = expEntry.getKey();
      final MdSecType.MdWrap expXmlData = expedientXmlData.get(expId);
      if (expXmlData == null) {
        LOGGER.warn("Missing iArxiu SIP '{}' {}expedient XML data for Exp metadata file '{}': {}",
                foundSipId, representation != null ? "representation '" + representation.getRepresentationID() + "' " : "",
                expId, expedientXmlData);
        expedientSet.remove(); // not attempt to process anymore
      } else {
        final String mdType = expXmlData.getMDTYPE();
        final MetadataType.MetadataTypeEnum type = MetadataType.match(mdType);
        final String otherMdType = expXmlData.getOTHERMDTYPE();
        final MetadataType.MetadataTypeEnum otherType = MetadataType.match(otherMdType);
        if (type != null && type != MetadataType.MetadataTypeEnum.OTHER || otherType != null){
          // sample: ...temp.../metadata/OTHER/DOC_1.xml
          processMetadataDocument(ip, logger, representation, expXmlData, expId, IPConstants.DESCRIPTIVE, basePath);
          expedientSet.remove(); // processed once only
        } else {
          LOGGER.warn("iArxiu SIP '{}' {}expedient XML data for Exp metadata file '{}' contains unknown Expedient MD types: {} - {}",
                  foundSipId, representation != null ? "representation '" + representation.getRepresentationID() + "' " : "",
                  expId, type, otherMdType);
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
      final List<DivType.Fptr> filePointers = getRepresentationFilePointersList(representationDiv);
      processRepresentationDataFiles(metsWrapper, ip, filePointers, representation, basePath);
    }

    if (ip.getRepresentations().isEmpty()) { // post-process validations
      LOGGER.warn("{} for ip '{}' with mets {} representation descriptions", ValidationConstants.MAIN_METS_NO_REPRESENTATIONS_FOUND, ip.getId(), representationDivList.size());
      ValidationUtils.addIssue(ip.getValidationReport(), ValidationConstants.MAIN_METS_NO_REPRESENTATIONS_FOUND,
              ValidationEntry.LEVEL.WARN, (DivType) null, ip.getBasePath(), metsWrapper.getMetsPath());
    }
  }

  /** prepares the documents and expedients
   * @param div the struct map preprocessed div type (for a main METS or each Representation)
   * @param metadataList
   *          dublin core metadata {@link MetadataTypeEnum#DC} & {@link MetadataTypeEnum#I_ARXIU_DC}
   *          and documents {@link MetadataTypeEnum#I_ARXIU_VOC_DOC}, {@link MetadataTypeEnum#I_ARXIU_VOC_DOC_EXP} & {@link MetadataTypeEnum#I_ARXIU_VOC_UPF} & {@link MetadataTypeEnum#I_ARXIU_DOC}
   * @param xmlDataMap expedients {@link MetadataTypeEnum#I_ARXIU_VOC_EXP}
   *          and {@link MetadataTypeEnum#OTHER} for the OTHER MD TYPEs */
  private static void loadDescriptiveMetadataFiles(DivType div, List<MdSecType> metadataList, Map<String, MdSecType.MdWrap> xmlDataMap) throws IPException {

    final List<MdSecType> metadataTypes = findMainDescriptiveMetadataFiles(div);
    for (MdSecType metadata : metadataTypes) {
      final MdSecType.MdWrap mdWRef = metadata.getMdWrap();
      final MetadataTypeEnum mdType = MetadataType.match(mdWRef.getMDTYPE());
      final MetadataTypeEnum otherMdType = MetadataType.match(mdWRef.getOTHERMDTYPE());
      if (isDocument(mdType, otherMdType)) { // 1 = {MdSecType@3269} mdtype = "DC" or known iArxiu DC or iArxiu document
        overrideIArxiuDocumentMdType(mdWRef, mdType, otherMdType);
        metadataList.add(metadata);
      } else if (isExpedient(mdType, otherMdType)) { /*  MdSecType mdtype = "OTHER" */
        overrideIArxiuExpedientMdType(mdWRef, mdType, otherMdType);
        xmlDataMap.put(metadata.getID(), metadata.getMdWrap());
      } else {
        LOGGER.warn("Unknown MD Type '{}' (other '{}') for iArxiu metadata: {}", mdWRef.getMDTYPE(), mdWRef.getOTHERMDTYPE(), mdWRef);
      }
    }
  }

  /** "DC" or the known iArxiu DC or iArxiu documents
   * @param mdType
   * @return */
  private static boolean isDocument(MetadataTypeEnum mdType){
    return MetadataTypeEnum.DC == mdType || MetadataTypeEnum.I_ARXIU_DC == mdType ||
            MetadataTypeEnum.I_ARXIU_VOC_DOC == mdType || MetadataTypeEnum.I_ARXIU_VOC_DOC_EXP == mdType || MetadataTypeEnum.I_ARXIU_VOC_UPF == mdType || MetadataTypeEnum.I_ARXIU_DOC == mdType;
  }

  /** is a document metadata type {@link #isDocument(MetadataTypeEnum)} or is other metadata type {@link MetadataTypeEnum#OTHER} and other metadata type {@link #isDocument(MetadataTypeEnum)}
   * @param mdType
   * @param otherMdType
   * @return */
  private static boolean isDocument(MetadataTypeEnum mdType, MetadataTypeEnum otherMdType){
    return isDocument(mdType) || mdType == MetadataTypeEnum.OTHER && isDocument(otherMdType);
  }

  /** all document types are overridden with the IArxiu Document type for Roda: {@link MetadataTypeEnum#I_ARXIU_DC} or {@link MetadataTypeEnum#I_ARXIU_DOC}
   * @param mdWRef metadata ref to be overridden
   * @param mdType
   * @param otherMdType */
  private static void overrideIArxiuDocumentMdType(MdSecType.MdWrap mdWRef, MetadataTypeEnum mdType, MetadataTypeEnum otherMdType) throws IPException {
    if (mdType == MetadataTypeEnum.I_ARXIU_DC || mdType == MetadataTypeEnum.I_ARXIU_DOC){
      return; // is the correct expected IArxiu Dublic Core or IArxiu document
    }

    if (!isDocument(mdType, otherMdType)) { // not valid scenario (fix needed)
      throw new IPException("Not valid iArxiu document type: '" + mdType + "'- '" + otherMdType + "'");
    }

    if (mdType != MetadataTypeEnum.DC && mdType != MetadataTypeEnum.OTHER){ // not expected scenario...
      LOGGER.warn("Found MD Type '{}' and Other MD Type '{}' is not an expected iArxiu document!", mdType, otherMdType);
      // ... but continues for a fallback
    }

    if (mdType!= null && mdType != MetadataTypeEnum.OTHER) { // relevant metadata type was informed...
      mdWRef.setOTHERMDTYPE(mdType.getType()); // ... saved in the other md type
    }

    if (mdType == MetadataTypeEnum.DC || otherMdType == MetadataTypeEnum.DC) {
      mdWRef.setMDTYPE(MetadataTypeEnum.I_ARXIU_DC.getType());
    } else { // the fallback is set
      mdWRef.setMDTYPE(MetadataTypeEnum.I_ARXIU_DOC.getType());
    }
  }

  /** the known iArxiu expedients or any other {@link MetadataTypeEnum#OTHER} that is not a document {@link #isDocument(MetadataTypeEnum)} are treated as expedient
   * @param mdType
   * @param otherMdType
   * @return*/
  private static boolean isExpedient(MetadataTypeEnum mdType, MetadataTypeEnum otherMdType){
     if (mdType == MetadataTypeEnum.I_ARXIU_VOC_EXP || otherMdType == MetadataTypeEnum.I_ARXIU_EXP) {
       return true; // the expected iArxiu expedient type
     }
     return MetadataTypeEnum.OTHER == mdType && !isDocument(otherMdType);
  }

  /** all expedient types are overridden with the IArxiu Expedient type for Roda: {@link MetadataTypeEnum#I_ARXIU_EXP}
   * @param mdType
   */
  private static void overrideIArxiuExpedientMdType(MdSecType.MdWrap mdWRef, MetadataTypeEnum mdType, MetadataTypeEnum otherMdType) throws IPException {
    if (mdType == MetadataTypeEnum.I_ARXIU_EXP) {
      return;  // is the correct expected IArxiu Expedient
    }
    if (!isExpedient(mdType, otherMdType) || isDocument(mdType, otherMdType)) {  // not valid scenario (fix needed)
      throw new IPException("Not valid iArxiu expedient type: '" + mdType + "'- '" + otherMdType + "'");
    }

    if (mdType!= null && mdType != MetadataTypeEnum.OTHER) { // relevant metadata type was informed...
      mdWRef.setOTHERMDTYPE(mdType.getType()); // ... saved in the other md type
    }
    mdWRef.setMDTYPE(MetadataTypeEnum.I_ARXIU_EXP.getType());
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
      LOGGER.warn("{} for ip '{}' with mets {} file pointers descriptions", ValidationConstants.REPRESENTATION_HAS_NO_FILES, ip.getId(), filePointers.size());
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

  private static List<DivType.Fptr> getRepresentationFilePointersList(DivType div){
    if (div == null){
      return new ArrayList<>();
    }
    if (div.getFptr() != null && !div.getFptr().isEmpty() // has something on the first level (the SIP descriptive metadata)
            || div.getDiv() == null || div.getDiv().isEmpty()) { // but has no representation childs to look
      return new ArrayList<>();
    }

    final List<DivType.Fptr> filePointersList = new ArrayList<>();
    // looks for representation files under the DivType 2nd level: DivType getDiv list:
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
