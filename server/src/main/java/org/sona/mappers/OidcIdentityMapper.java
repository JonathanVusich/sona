package org.sona.mappers;

import org.mapstruct.Mapper;
import org.sona.model.tables.pojos.OidcIdentity;
import org.sona.model.tables.records.OidcIdentityRecord;

@Mapper(componentModel = "spring")
public interface OidcIdentityMapper {

    OidcIdentityRecord toRecord(OidcIdentity identity);

    OidcIdentity fromRecord(OidcIdentityRecord identity);
}
