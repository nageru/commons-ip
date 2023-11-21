/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree
 */
package org.roda_project.commons_ip.model.impl.iarxiu;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jdom2.Element;
import org.jdom2.IllegalDataException;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.roda_project.commons_ip.model.*;
import org.roda_project.commons_ip.model.impl.ModelUtils;
import org.roda_project.commons_ip.utils.FileZipEntryInfo;
import org.roda_project.commons_ip.utils.ZIPUtils;
import org.roda_project.commons_ip.utils.ZipEntryInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.Map.Entry;

public final class IArxiuUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(IArxiuUtils.class);
  private static final String METADATA_TYPE = "key-value";
  protected static final String I_ARXIU_FILE_NAME = "iarxiu";
  private static final String TXT_FILE_EXTENSION = ".txt";

  private IArxiuUtils() {
    // do nothing
  }

  public static IPDescriptiveMetadata createIArxiuMetadata(Map<String, String> metadata, Path metadataPath) {
    return createIArxiuMetadata(metadata, new ArrayList<>(), metadataPath);
  }

  public static IPDescriptiveMetadata createIArxiuMetadata(Map<String, String> metadata, List<String> ancestors,
    Path metadataPath) {
    try {
      FileBasedConfigurationBuilder<PropertiesConfiguration> builder = new Configurations()
        .propertiesBuilder(metadataPath.toFile());
      Files.createFile(metadataPath);
      PropertiesConfiguration config = builder.getConfiguration();

      for (Entry<String, String> entry : metadata.entrySet()) {
        config.setProperty(entry.getKey(), entry.getValue());
      }

      for (String ancestor : ancestors) {
        config.addProperty(IPConstants.PARENT_KEY, ancestor);
      }

      builder.save();
    } catch (IOException | ConfigurationException e) {
      LOGGER.error("Could not save IArxiu metadata content on file", e);
    }

    return new IPDescriptiveMetadata(metadataPath.getFileName().toString(), new IPFile(metadataPath),
      new MetadataType(METADATA_TYPE), "");
  }

  public static Map<String, String> getIArxiuInfo(Path metadataPath) {
    final Map<String, String> metadataList = new HashMap<>();
    try {
      PropertiesConfiguration config = new Configurations().properties(metadataPath.toFile());
      Iterator<String> keys = config.getKeys();

      while (keys.hasNext()) {
        String key = keys.next();
        metadataList.put(key, config.getString(key));
      }
    } catch (ConfigurationException e) {
      LOGGER.error("Could not load properties with iArxiu metadata", e);
    }

    return metadataList;
  }

  public static String generateMetadataFile(Path metadataPath) throws IllegalDataException {
    final Map<String, String> iArxiuInfo = getIArxiuInfo(metadataPath);
    Element root = new Element(IPConstants.METADATA_KEY);
    org.jdom2.Document doc = new org.jdom2.Document();

    for (Entry<String, String> entry : iArxiuInfo.entrySet()) {
      if (!IPConstants.PARENT_KEY.equalsIgnoreCase(entry.getKey())) {
        Element child = new Element(IPConstants.FIELD_KEY);
        child.setAttribute(IPConstants.NAME_KEY, StringEscapeUtils.escapeXml11(entry.getKey()));
        child.addContent(entry.getValue());
        root.addContent(child);
      }
    }

    doc.setRootElement(root);
    XMLOutputter outter = new XMLOutputter();
    outter.setFormat(Format.getPrettyFormat());
    return outter.outputString(doc);
  }
  protected static Path extractIArxiuIPIfInZipFormat(final Path source, Path destinationDirectory)
          throws ParseException {
    Path iArxiuFolderPath = destinationDirectory;
    if (!Files.isDirectory(source)) {
      try {
        ZIPUtils.unzip(source, destinationDirectory);

        if (Files.exists(destinationDirectory)
                && !Files.exists(destinationDirectory.resolve(I_ARXIU_FILE_NAME + TXT_FILE_EXTENSION))) {
          try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(destinationDirectory)) {
            for (Path path : directoryStream) {
              if (Files.isDirectory(path) && Files.exists(path.resolve(I_ARXIU_FILE_NAME + TXT_FILE_EXTENSION))) {
                iArxiuFolderPath = path;
                break;
              }
            }
          }
        }
      } catch (IOException e) {
        throw new ParseException("Error unzipping file", e);
      }
    }

    return iArxiuFolderPath;
  }
}
