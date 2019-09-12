package fi.thl.thldtkk.api.metadata.service.termed;

import fi.thl.thldtkk.api.metadata.domain.Organization;
import fi.thl.thldtkk.api.metadata.domain.OrganizationPersonInRole;
import fi.thl.thldtkk.api.metadata.domain.RecipientNotificationState;
import fi.thl.thldtkk.api.metadata.domain.termed.Changeset;
import fi.thl.thldtkk.api.metadata.domain.termed.Node;
import fi.thl.thldtkk.api.metadata.domain.termed.NodeId;
import fi.thl.thldtkk.api.metadata.security.annotation.AdminOnly;
import fi.thl.thldtkk.api.metadata.service.OrganizationService;
import fi.thl.thldtkk.api.metadata.service.Repository;
import fi.thl.thldtkk.api.metadata.util.spring.exception.NotFoundException;

import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.MoreObjects.firstNonNull;
import static fi.thl.thldtkk.api.metadata.domain.query.KeyValueCriteria.keyValue;
import static fi.thl.thldtkk.api.metadata.domain.query.Select.select;
import static java.util.stream.Collectors.toList;

public class OrganizationServiceImpl implements OrganizationService {

  private Repository<NodeId, Node> commonRepository;
  private Repository<NodeId, Node> editorRepository;

  public OrganizationServiceImpl(Repository<NodeId, Node> commonRepository, Repository<NodeId, Node> editorRepository) {
    this.commonRepository = commonRepository;
    this.editorRepository = editorRepository;
  }

  @Override
  public List<Organization> findAll() {
    return commonRepository.query(select("id",
      "type",
      "properties.*",
      "references.*",
      "referrers.*",
      "references.personInRoles:2",
      "OrganizationPersonInRole.referrers.personInRole:2",
      "OrganizationPersonInRole.referrers.personsInRole:2",
      "references.person:3",
      "references.role:3",
      "lastModifiedDate"), keyValue("type.id", "Organization"))
      .map(Organization::new)
      .collect(toList());
  }

  @Override
  public List<Organization> find(String query, int max) {
    return findAll();
  }

  @Override
  public Optional<Organization> get(UUID id) {
    return commonRepository.get(select("id",
      "type",
      "properties.*",
      "references.*",
      "referrers.*",
      "references.person:2",
      "references.role:2",
      "OrganizationPersonInRole.referrers.personInRole:2",
      "lastModifiedDate"),new NodeId(id, "Organization")).map(Organization::new);
  }

  @Override
  public Optional<Organization> getByVirtuId(String virtuId) {
    return findAll()
      .stream()
      .filter(org -> org.getVirtuIds().contains(virtuId))
      .findFirst();
  }

  @AdminOnly
  @Override
  public Organization save(Organization organization) {
    Organization old = null;
    if (organization.getId() != null) {
      old = get(organization.getId()).orElse(null);
    } else {
      organization.setId(UUID.randomUUID());
    }

    Changeset<NodeId, Node> changeset = saveForPersonInRoles(organization, old);
    changeset = changeset.merge(
      new Changeset<>(Collections.emptyList(), Collections.singletonList(organization.toNode())));

    commonRepository.post(changeset);
    return get(organization.getId()).get();
  }

  private Changeset<NodeId, Node> saveForPersonInRoles(Organization organization, Organization old) {
    organization.getPersonInRoles().stream()
      .filter(personInRole -> personInRole.getId() == null)
      .forEach(personInRole -> personInRole.setId(UUID.randomUUID()));

    Changeset<NodeId, Node> changes = Changeset.buildChangeset(
      organization.getPersonInRoles(),
      old != null ? old.getPersonInRoles() : Collections.emptyList()
    );

    if (old != null) {
      List<OrganizationPersonInRole> deletedPersons =
        Changeset.getDeletedNodes(organization.getPersonInRoles(), old.getPersonInRoles());
      
      List<NodeId> orphanedNotifications = deletedPersons.stream()
        .map(OrganizationPersonInRole::getNotifications)
        .flatMap(List::stream)
        .map(RecipientNotificationState::toNode)
        .map(NodeId::new)
        .collect(Collectors.toList());

      editorRepository.delete(orphanedNotifications);
    }

    return changes;
  }
}