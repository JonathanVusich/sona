package org.sona.mappers;

import org.mapstruct.Mapper;
import org.sona.model.tables.pojos.LocalUser;
import org.sona.model.tables.records.LocalUserRecord;

@Mapper(componentModel = "spring")
public interface LocalUserMapper {

    LocalUserRecord toRecord(LocalUser user);
    LocalUser fromRecord(LocalUserRecord user);
}
