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
  public void readAndParseIArxiuSIP() throws ParseException {
    LOGGER.info("Creating iArxiu SIP");
    Path iArxiuSIP = readIArxiuSIP("cesca_earxiu1.zip");
    Assert.assertNotNull(iArxiuSIP);
    LOGGER.info("Done reading iArxiu SIP");

    LOGGER.info("Parsing (and validating) iArxiu SIP");
    parseAndValidateIArxiuSIP(iArxiuSIP);
    LOGGER.info("Done parsing (and validating) iArxiu SIP");
  }

  private static Path readIArxiuSIP(String iArxiuZipName) {
    Path zipSIP = Paths.get("src/test/resources/iarxiu").resolve(iArxiuZipName);
    return zipSIP;
  }

  private void parseAndValidateIArxiuSIP(Path zipSIP) throws ParseException {

    // 1) invoke static method parse and that's it
    final SIP iArxiuSIP = IArxiuSIP.parse(zipSIP, tempFolder);

    // general assessment
    iArxiuSIP.getValidationReport().getValidationEntries().stream()
      .filter(e -> e.getLevel() == ValidationEntry.LEVEL.ERROR)
      .forEach(e -> LOGGER.error("Validation report entry: {}", e));
    Assert.assertTrue(iArxiuSIP.getValidationReport().isValid());

    // - List<IPMetadata> getPreservationMetadata()
    final List<IPMetadata> preservationMetadata = iArxiuSIP.getPreservationMetadata();
    Assert.assertNotNull(preservationMetadata);

    /* TODO root descriptive metadata ID
        id = "uuid-608E04EC-A93A-484C-BADA-44AD3F7851E1"
          -> mets:div ADMID="AMD_PAC" DMDID="EXP_1 EXP_1_DC" LABEL="UDL_1435231985409" ?
		label = "descriptive"
     */

    if (false) { // TODO preservationMetadata.. discard? is present? ignore?
      Assert.assertNotEquals(0, preservationMetadata.size());
      Assert.assertTrue("preservation metadata to be found", preservationMetadata.stream().anyMatch(ipMetadata -> {
        final MetadataType preservationMetadataType = ipMetadata.getMetadataType();
        final IPFile ipFile = ipMetadata.getMetadata();
        final MetadataType.MetadataTypeEnum type = preservationMetadataType.getType();
        return preservationMetadataType != null && type != null
                && ipFile != null && isNotBlank(ipFile.getFileName());
      }));
    }

    final List<IPRepresentation> representations = iArxiuSIP.getRepresentations();
    Assert.assertNotNull(representations);
    Assert.assertNotEquals(0, representations.size());

    for(IPRepresentation representation : representations) {
      Assert.assertNotNull(representation);
      final String representationId = representation.getRepresentationID(); // index.xml
      Assert.assertNotNull(representationId);

      final List<IPFile> representationDataFiles = representation.getData();
      Assert.assertNotEquals(0, representationDataFiles.size());
      Assert.assertTrue("representation data files to be found",
              representationDataFiles.stream().allMatch(ipFile -> verifyFileExists(ipFile)));

      final List<IPDescriptiveMetadata> representationDescriptiveMetadata = representation.getDescriptiveMetadata();
      Assert.assertNotEquals(0, representationDataFiles.size());
      Assert.assertTrue("representation Descriptive Metadata to be found",
              representationDescriptiveMetadata.stream().allMatch(ipFile ->
                      ipFile != null && ipFile.getCreateDate() != null && ipFile.getMetadataType() != null && verifyFileExists(ipFile.getMetadata())));
    }

    LOGGER.info("SIP with id '{}' parsed with success (valid? {})!", iArxiuSIP.getId(),
      iArxiuSIP.getValidationReport().isValid());
  }

  private static boolean verifyFileExists(IPFile ipFile){
    return ipFile != null && isNotBlank(ipFile.getFileName()) && ipFile.getPath().toFile().exists();
  }
}
