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
    LOGGER.info("Read iArxiu SIP: {}", iArxiuSIPPath);

    final SIP iArxiuSIP = parseIArxiuSIP(iArxiuSIPPath);
    verifyValidationReport(iArxiuSIP.getValidationReport());
    LOGGER.info("Parsed iArxiu '{}' SIP: {}", iArxiuSIPPath, iArxiuSIP);

    final List<IPDescriptiveMetadata> descriptiveMetadataList = validateDescriptiveMetadata(iArxiuSIP);
    LOGGER.info("Validated iArxiu SIP descriptive metadata: {}", descriptiveMetadataList);
    Assert.assertEquals("Not the expected number of descriptive metadata entries", 1, descriptiveMetadataList.size());


    final List<IPRepresentation> representations = validateRepresentations(iArxiuSIP);
    LOGGER.info("Validated iArxiu SIP representations: {}", representations);
    Assert.assertEquals("Not the expected number of representations", 2, representations.size());

    LOGGER.info("SIP with id '{}' parsed with success (valid? {})!", iArxiuSIP.getId(), iArxiuSIP.getValidationReport().isValid());
  }

  @Test
  public void readAndParseCesca1IArxiuSIP() throws ParseException {
    final Path iArxiuSIPPath = readIArxiuSIP("cesca_earxiu1.zip");
    Assert.assertNotNull(iArxiuSIPPath);
    LOGGER.info("Read iArxiu SIP: {}", iArxiuSIPPath);

    final SIP iArxiuSIP = parseIArxiuSIP(iArxiuSIPPath);
    verifyValidationReport(iArxiuSIP.getValidationReport());
    LOGGER.info("Parsed iArxiu '{}' SIP: {}", iArxiuSIPPath, iArxiuSIP);

    final List<IPDescriptiveMetadata> descriptiveMetadataList = validateDescriptiveMetadata(iArxiuSIP);
    LOGGER.info("Validated iArxiu SIP descriptive metadata: {}", descriptiveMetadataList);
    Assert.assertEquals("Not the expected number of descriptive metadata entries", 2, descriptiveMetadataList.size());


    final List<IPRepresentation> representations = validateRepresentations(iArxiuSIP);
    LOGGER.info("Validated iArxiu SIP representations: {}", representations);
    Assert.assertEquals("Not the expected number of representations", 1, representations.size());

    LOGGER.info("SIP with id '{}' parsed with success (valid? {})!", iArxiuSIP.getId(), iArxiuSIP.getValidationReport().isValid());
  }

  @Test
  public void readAndParseCescaAppPreIArxiuSIP() throws ParseException {
    final Path iArxiuSIPPath = readIArxiuSIP("cesca_earxiu-app-pre.zip"); // from Cesca e-arxiu app pre 14 nov 2016 (12392)
    Assert.assertNotNull(iArxiuSIPPath);
    LOGGER.info("Read iArxiu SIP: {}", iArxiuSIPPath);

    final SIP iArxiuSIP = parseIArxiuSIP(iArxiuSIPPath);
    verifyValidationReport(iArxiuSIP.getValidationReport());
    LOGGER.info("Parsed iArxiu '{}' SIP: {}", iArxiuSIPPath, iArxiuSIP);

    final List<IPDescriptiveMetadata> descriptiveMetadataList = validateDescriptiveMetadata(iArxiuSIP);
    LOGGER.info("Validated iArxiu SIP descriptive metadata: {}", descriptiveMetadataList);
    Assert.assertEquals("Not the expected number of descriptive metadata entries", 2, descriptiveMetadataList.size());

    final List<IPRepresentation> representations = validateRepresentations(iArxiuSIP);
    LOGGER.info("Validated iArxiu SIP representations: {}", representations);
    Assert.assertEquals("Not the expected number of representations", 4, representations.size());

    LOGGER.info("SIP with id '{}' parsed with success (valid? {})!", iArxiuSIP.getId(), iArxiuSIP.getValidationReport().isValid());
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

  private static List<IPRepresentation> validateRepresentations(SIP iArxiuSIP) {

    final List<IPRepresentation> representations = iArxiuSIP.getRepresentations();
    Assert.assertNotNull(representations);
    Assert.assertNotEquals(0, representations.size());

    for (IPRepresentation representation : representations) {
      Assert.assertNotNull(representation);
      final String representationId = representation.getRepresentationID(); // index.xml
      Assert.assertNotNull(representationId);

      final List<IPFile> representationDataFiles = representation.getData();
      Assert.assertNotEquals(0, representationDataFiles.size());
      Assert.assertTrue("representation data files to be found",
              representationDataFiles.stream().allMatch(ipFile -> verifyFileExists(ipFile)));

      verifyExistingDescriptiveMetadataFiles(representation.getDescriptiveMetadata());
    }

    LOGGER.info("SIP with id '{}' parsed with success (valid? {})!", iArxiuSIP.getId(), iArxiuSIP.getValidationReport().isValid());

    return representations;
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
