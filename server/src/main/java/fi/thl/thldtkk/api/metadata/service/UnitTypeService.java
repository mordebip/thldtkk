package fi.thl.thldtkk.api.metadata.service;

import fi.thl.thldtkk.api.metadata.domain.UnitType;

import java.util.Optional;
import java.util.UUID;

public interface UnitTypeService extends Service<UUID, UnitType> {

    Optional<UnitType> findByPrefLabel(String prefLabel);
}
