package org.roda_project.commons_ip.model.iarxiu;

import org.junit.*;
import org.roda_project.commons_ip.model.*;
import org.roda_project.commons_ip.model.impl.iarxiu.IArxiuSIP;
import org.roda_project.commons_ip.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.junit.Assert.assertEquals;

/**
 */
public class IArxiuTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(IArxiuTest.class);
  private static Path tempFolder;

  @BeforeClass
  public static void setup() throws IOException {
    tempFolder = Files.createTempDirectory("temp");
  }

  @AfterClass
  public static void cleanup() throws Exception {
    Utils.deletePath(tempFolder);
  }


  @Test
  public void readAndParseClientIArxiu_1707399731319_4560004_SIP() throws ParseException {
    final Path iArxiuSIPPath = readIArxiuSIP("client_iArxiu_1707399731319_4560004.zip");
    Assert.assertNotNull(iArxiuSIPPath);
    LOGGER.info("Read client iArxiu SIP: {}", iArxiuSIPPath);

    final SIP iArxiuSIP = parseIArxiuSIP(iArxiuSIPPath);
    verifyValidationReport(iArxiuSIP.getValidationReport());
    LOGGER.info("Parsed client iArxiu '{}' SIP: {}", iArxiuSIPPath, iArxiuSIP);

    final List<IPDescriptiveMetadata> descriptiveMetadataList = validateDescriptiveMetadata(iArxiuSIP);
    LOGGER.info("Validated client iArxiu SIP descriptive metadata: {}", descriptiveMetadataList);
    assertEquals("Not the expected number of descriptive metadata entries", 1, descriptiveMetadataList.size());


    final List<IPRepresentation> representations = validateIpRepresentationsMetadata(iArxiuSIP);
    LOGGER.info("Validated client iArxiu SIP representations: {}", representations);
    assertEquals("Not the expected number of representations", 2, representations.size());

    final int representationFiles = validateRepresentationsFiles(iArxiuSIP.getId(), representations);
    assertEquals("Expected number of total client iArxiu SIP representations files", 2, representationFiles);

    LOGGER.info("SIP client with id '{}' parsed with success (valid? {})!", iArxiuSIP.getId(), iArxiuSIP.getValidationReport().isValid());
  }

  @Test
  public void readAndParseCSUCIArxiu_1709195242267_4698167_SIP() throws ParseException {
    final Path iArxiuSIPPath = readIArxiuSIP("client_CSUC_iArxiu_1709195242267_4698167.zip");
    Assert.assertNotNull(iArxiuSIPPath);
    LOGGER.info("Read CSUC iArxiu SIP: {}", iArxiuSIPPath);

    final SIP iArxiuSIP = parseIArxiuSIP(iArxiuSIPPath);
    verifyValidationReport(iArxiuSIP.getValidationReport());
    LOGGER.info("Parsed CSUC iArxiu '{}' SIP: {}", iArxiuSIPPath, iArxiuSIP);

    final List<IPDescriptiveMetadata> descriptiveMetadataList = validateDescriptiveMetadata(iArxiuSIP);
    LOGGER.info("Validated CSUC iArxiu SIP descriptive metadata: {}", descriptiveMetadataList);
    assertEquals("Not the expected number of descriptive metadata entries", 1, descriptiveMetadataList.size());

    final List<IPRepresentation> representations = validateIpRepresentations(iArxiuSIP); // CSUC contains only the representations with representation file...
    assertEquals("Not the expected number of representations", 1, representations.size());
    assertEquals(0, representations.get(0).getDescriptiveMetadata().size()); // ... but no representation descriptive metadata

    LOGGER.info("Validated CSUC iArxiu SIP representations: {}", representations);

    final int representationFiles = validateRepresentationsFiles(iArxiuSIP.getId(), representations);
    assertEquals("Expected number of total CSUC iArxiu SIP representations files", 1, representationFiles);

    LOGGER.info("SIP CSUC with id '{}' parsed with success (valid? {})!", iArxiuSIP.getId(), iArxiuSIP.getValidationReport().isValid());
  }

  @Test
  public void readAndParseCesca1IArxiuSIP() throws ParseException {
    final Path iArxiuSIPPath = readIArxiuSIP("cesca_earxiu1.zip");
    Assert.assertNotNull(iArxiuSIPPath);
    LOGGER.info("Read Cesca-1 iArxiu SIP: {}", iArxiuSIPPath);

    final SIP iArxiuSIP = parseIArxiuSIP(iArxiuSIPPath);
    verifyValidationReport(iArxiuSIP.getValidationReport());
    LOGGER.info("Parsed Cesca-1 iArxiu '{}' SIP: {}", iArxiuSIPPath, iArxiuSIP);

    final List<IPDescriptiveMetadata> descriptiveMetadataList = validateDescriptiveMetadata(iArxiuSIP);
    LOGGER.info("Validated Cesca-1 iArxiu SIP descriptive metadata: {}", descriptiveMetadataList);
    assertEquals("Not the expected number of descriptive metadata entries", 2, descriptiveMetadataList.size());

    final List<IPRepresentation> representations = validateIpRepresentationsMetadata(iArxiuSIP);
    LOGGER.info("Validated Cesca-1 iArxiu SIP representations: {}", representations);
    assertEquals("Not the expected number of representations", 1, representations.size());

    final int representationFiles = validateRepresentationsFiles(iArxiuSIP.getId(), representations);
    assertEquals("Expected number of total Cesca-1 iArxiu SIP representations files", 1, representationFiles);

    LOGGER.info("SIP Cesca-1 with id '{}' parsed with success (valid? {})!", iArxiuSIP.getId(), iArxiuSIP.getValidationReport().isValid());
  }

  @Test
  public void readAndParseCescaAppPreIArxiuSIP() throws ParseException {
    final Path iArxiuSIPPath = readIArxiuSIP("cesca_earxiu-app-pre.zip"); // from Cesca e-arxiu app pre 14 nov 2016 (12392)
    Assert.assertNotNull(iArxiuSIPPath);
    LOGGER.info("Read Cesca-PRE iArxiu SIP: {}", iArxiuSIPPath);

    final SIP iArxiuSIP = parseIArxiuSIP(iArxiuSIPPath);
    verifyValidationReport(iArxiuSIP.getValidationReport());
    LOGGER.info("Parsed Cesca-PRE iArxiu '{}' SIP: {}", iArxiuSIPPath, iArxiuSIP);

    final List<IPDescriptiveMetadata> descriptiveMetadataList = validateDescriptiveMetadata(iArxiuSIP);
    LOGGER.info("Validated Cesca-PRE iArxiu SIP descriptive metadata: {}", descriptiveMetadataList);
    assertEquals("Not the expected number of descriptive metadata entries", 2, descriptiveMetadataList.size());

    final List<IPRepresentation> representations = validateIpRepresentationsMetadata(iArxiuSIP);
    LOGGER.info("Validated Cesca-PRE iArxiu SIP representations: {}", representations);
    assertEquals("Not the expected number of representations", 4, representations.size());

    int representationFiles = validateRepresentationsFiles(iArxiuSIP.getId(), representations);
    assertEquals("Expected number of total Cesca-PRE iArxiu SIP representations files", 7, representationFiles);

    LOGGER.info("SIP Cesca-PRE with id '{}' parsed with success (valid? {})!", iArxiuSIP.getId(), iArxiuSIP.getValidationReport().isValid());
  }

  private static Path readIArxiuSIP(String iArxiuZipName) {
    Path zipSIP = Paths.get("src/test/resources/iarxiu").resolve(iArxiuZipName);
    return zipSIP;
  }

  private static SIP parseIArxiuSIP(Path zipSIP) throws ParseException {
    // 1) invoke static method parse and that's it
    final SIP iArxiuSIP = IArxiuSIP.parse(zipSIP, tempFolder);
    Assert.assertNotNull(iArxiuSIP);
    Assert.assertNotNull(iArxiuSIP.getId());
    return iArxiuSIP;
  }

  private static void verifyValidationReport(ValidationReport validationReport){
    Assert.assertNotNull(validationReport);
    // general assessment
    validationReport.getValidationEntries().stream()
            .filter(e -> e.getLevel() == ValidationEntry.LEVEL.ERROR)
            .forEach(e -> LOGGER.error("Validation report entry: {}", e));
    Assert.assertTrue(validationReport.isValid());
  }

  private static List<IPDescriptiveMetadata> validateDescriptiveMetadata(SIP iArxiuSIP) {

    final List<IPDescriptiveMetadata> descriptiveMetadataList = iArxiuSIP.getDescriptiveMetadata();
    /* root descriptive metadata ID
      id = "uuid-608E04EC-A93A-484C-BADA-44AD3F7851E1"
        -> mets:div ADMID="AMD_PAC" DMDID="EXP_1 EXP_1_DC" LABEL="UDL_1435231985409"
      label = "descriptive" */
    verifyExistingDescriptiveMetadataFiles(descriptiveMetadataList);

    return descriptiveMetadataList;
  }

  private static List<IPRepresentation> validateIpRepresentationsMetadata(SIP iArxiuSIP) {

    final List<IPRepresentation> representations = validateIpRepresentations(iArxiuSIP);

    for (IPRepresentation representation : representations) {
      verifyExistingDescriptiveMetadataFiles(representation.getDescriptiveMetadata());
    }

    LOGGER.info("SIP with id '{}' parsed representations metadata with success (valid? {})!", iArxiuSIP.getId(), iArxiuSIP.getValidationReport().isValid());
    return representations;
  }

  private static List<IPRepresentation> validateIpRepresentations(SIP iArxiuSIP) {

    final List<IPRepresentation> representations = iArxiuSIP.getRepresentations();
    Assert.assertNotNull(representations);
    Assert.assertNotEquals(0, representations.size());

    for (IPRepresentation representation : representations) {
      Assert.assertNotNull(representation);
      final String representationId = representation.getRepresentationID(); // index.xml
      Assert.assertNotNull("Representation must have an id", representationId);
    }

    LOGGER.info("SIP with id '{}' parsed representations with success (valid? {})!", iArxiuSIP.getId(), iArxiuSIP.getValidationReport().isValid());

    return representations;
  }

  private static int validateRepresentationsFiles(String sipId, List<IPRepresentation> representations) {
    int representationFiles = 0;
    for (IPRepresentation representation : representations) {
      Assert.assertNotNull(representation);
      final List<IPFile> representationDataFiles = representation.getData();
      Assert.assertNotEquals(0, representationDataFiles.size());
      representationFiles += representationDataFiles.size();
      Assert.assertTrue("representation data files to be found",
              representationDataFiles.stream().allMatch(ipFile -> verifyFileExists(ipFile)));

    }

    LOGGER.info("SIP with id '{}' parsed with {} representation files!", sipId, representationFiles);
    return representationFiles;
  }

  private static void verifyExistingDescriptiveMetadataFiles(final List<IPDescriptiveMetadata> descriptiveMetadata){
    Assert.assertNotNull(descriptiveMetadata);
    Assert.assertNotEquals(0, descriptiveMetadata.size());
    Assert.assertTrue("Descriptive Metadata to be found",
            descriptiveMetadata.stream().allMatch(ipFile ->
                    ipFile != null && ipFile.getCreateDate() != null && ipFile.getMetadataType() != null && verifyFileExists(ipFile.getMetadata())));

  }

  private static boolean verifyFileExists(IPFile ipFile){
    return ipFile != null && isNotBlank(ipFile.getFileName()) && ipFile.getPath().toFile().exists();
  }
}
