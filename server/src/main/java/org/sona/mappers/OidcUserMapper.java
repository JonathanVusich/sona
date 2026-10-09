package org.sona.mappers;

import org.mapstruct.Mapper;
import org.sona.model.tables.pojos.OidcUser;
import org.sona.model.tables.records.OidcUserRecord;

@Mapper(componentModel = "spring")
public interface OidcUserMapper {

    OidcUserRecord toRecord(OidcUser user);
    OidcUser fromRecord(OidcUserRecord user);
}
