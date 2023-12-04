package org.roda_project.commons_ip.model.iarxiu;

import org.apache.commons.lang3.StringUtils;
import org.hamcrest.core.Is;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.roda_project.commons_ip.model.*;
import org.roda_project.commons_ip.model.impl.iarxiu.IArxiuSIP;
import org.roda_project.commons_ip.utils.IPException;
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
    if (false) { // TODO preservation / metadata (DC) not yet
      Assert.assertNotEquals(0, preservationMetadata.size());
      Assert.assertTrue("preservation metadata to be found", preservationMetadata.stream().anyMatch(ipMetadata -> {
        final MetadataType preservationMetadataType = ipMetadata.getMetadataType();
        final IPFile documentationIpFile = ipMetadata.getMetadata();
        return preservationMetadataType != null && MetadataType.MetadataTypeEnum.DC == preservationMetadataType.getType()
                && documentationIpFile != null && isNotBlank(documentationIpFile.getFileName());
      }));
    }

    // - List<IPFile> getDocumentation()
    final List<IPFile> documentationFiles = iArxiuSIP.getDocumentation();
    Assert.assertNotNull(documentationFiles);
    Assert.assertNotEquals(0, documentationFiles.size());
    Assert.assertTrue("documentation files to be found",
            documentationFiles.stream().allMatch(ipFile -> ipFile != null && isNotBlank(ipFile.getFileName())));

    // TODO - List<IPDescriptiveMetadata> getDescriptiveMetadata()

    // iArxiu #0 representations
    final List<IPRepresentation> representations = iArxiuSIP.getRepresentations();
    Assert.assertNotNull(representations);

    LOGGER.info("SIP with id '{}' parsed with success (valid? {})!", iArxiuSIP.getId(),
      iArxiuSIP.getValidationReport().isValid());
  }
}
