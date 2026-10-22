package org.sona.mappers;

import org.mapstruct.Mapper;
import org.sona.model.tables.pojos.OidcSession;
import org.sona.model.tables.records.OidcSessionRecord;

@Mapper(componentModel = "spring")
public interface OidcSessionMapper {

    OidcSessionRecord toRecord(OidcSession session);

    OidcSession fromRecord(OidcSessionRecord session);
}
