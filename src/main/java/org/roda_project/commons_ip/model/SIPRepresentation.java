/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/commons-ip
 */
package org.roda_project.commons_ip.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.roda_project.commons_ip.utils.Pair;

public class SIPRepresentation {
  private String representationID;
  private String objectID;
  private List<SIPAgent> agents;
  private List<SIPDescriptiveMetadata> descriptiveMetadata;
  private List<SIPMetadata> administrativeMetadata;
  private List<SIPMetadata> otherMetadata;
  // FIXME hsilva 20160203 using pair is ugly and very limiting
  private List<Pair<Path, List<String>>> data;
  private List<Path> schemas;
  private List<Path> documentation;

  public SIPRepresentation(String representationID) {
    this.representationID = representationID;
    this.objectID = representationID;
    this.agents = new ArrayList<SIPAgent>();
    this.descriptiveMetadata = new ArrayList<SIPDescriptiveMetadata>();
    this.administrativeMetadata = new ArrayList<SIPMetadata>();
    this.otherMetadata = new ArrayList<SIPMetadata>();
    this.data = new ArrayList<Pair<Path, List<String>>>();
    this.schemas = new ArrayList<Path>();
    this.documentation = new ArrayList<Path>();
  }

  public String getRepresentationID() {
    return representationID;
  }

  public String getObjectID() {
    return objectID;
  }

  public void setObjectID(String objectID) {
    this.objectID = objectID;
  }

  public List<SIPAgent> getAgents() {
    return agents;
  }

  public List<Pair<Path, List<String>>> getData() {
    return data;
  }

  public void addAgent(SIPAgent agent) {
    agents.add(agent);
  }

  public void addFile(Path filePath, List<String> folders) {
    data.add(new Pair<Path, List<String>>(filePath, folders));
  }

  public void addDescriptiveMetadata(SIPDescriptiveMetadata metadata) {
    descriptiveMetadata.add(metadata);
  }

  public void addAdministrativeMetadata(SIPMetadata metadata) {
    administrativeMetadata.add(metadata);
  }

  public void addOtherMetadata(SIPMetadata metadata) {
    otherMetadata.add(metadata);
  }

  public List<SIPDescriptiveMetadata> getDescriptiveMetadata() {
    return descriptiveMetadata;
  }

  public List<SIPMetadata> getAdministrativeMetadata() {
    return administrativeMetadata;
  }

  public List<SIPMetadata> getOtherMetadata() {
    return otherMetadata;
  }

  public void addDocumentation(Path documentationPath) {
    documentation.add(documentationPath);
  }

  public void addSchema(Path schemaPath) {
    schemas.add(schemaPath);
  }

  @Override
  public String toString() {
    return "SIPRepresentation [representationID=" + representationID + ", objectID=" + objectID + ", agents=" + agents
      + ", descriptiveMetadata=" + descriptiveMetadata + ", administrativeMetadata=" + administrativeMetadata
      + ", otherMetadata=" + otherMetadata + ", data=" + data + ", schemas=" + schemas + ", documentation="
      + documentation + "]";
  }

}
