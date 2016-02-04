/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/commons-ip
 */
package org.roda_project.commons_ip.utils;

public final class ValidationErrors {

  public static final String NO_DATA_FOLDER = "No 'data' folder in SIP";
  public static final String NO_METADATA_FOLDER = "No 'metadata' folder in SIP";
  public static final String UNKNOWN_METADATA_FOLDER = "Unknown metadata folder in SIP";
  public static final String NO_MAIN_METS_FILE = "No main METS.xml file in SIP";
  public static final String MAIN_METS_NOT_VALID = "Main METS.xml file is not valid";
  public static final String FILE_NOT_VALID = "File is not valid";
  public static final String FILE_IN_METS_DOES_NOT_EXIST = "File in METS.xml doesn't exist";
  public static final String BAD_CHECKSUM = "Checksum in METS.xml doesn't match real checksum";
  public static final String ERROR_COMPUTING_CHECKSUM = "Error computing checksum";
  public static final String ERROR_COMPUTING_CHECKSUM_NO_SUCH_ALGORYTHM = "Error computing checksum: the algorythm provided is not recognized";
  public static final String UNKNOWN_METADATA_TYPE = "The metadata type is not known";
  public static final String XML_NOT_VALID = "The XML file is not valid according to schema";
  public static final String ERROR_VALIDATING_SIP = "An error occurred while validating SIP";
  public static final String NO_STRUCT_MAP = "No struct map in METS.xml";
  public static final String NO_LOCAT_FOR_FILE = "File specified in filegroup have no FLocat element";
  public static final String REPRESENTATION_METS_NOT_VALID = "The METS of the representation is not valid";
  public static final String UNABLE_TO_UNZIP_SIP = "The path provided is a file, but this tool is unable to extract it";
  public static final String NO_HREF_IN_MDREF = "There is no href attribute associated to the mdref";
  public static final String DMDSEC_WITHOUT_MDREF = "There is a dmdsec without a mdref element";
  public static final String NO_VALID_LOCAT = "No valid locat found";
  public static final String BAD_HREF = "The href associated with the file is not in the format file://.XXXX";
  public static final String NO_MDREF_IN_DMDSEC = "There is no mdref in dmdsec";

  /** Private empty constructor */
  private ValidationErrors() {

  }

}
