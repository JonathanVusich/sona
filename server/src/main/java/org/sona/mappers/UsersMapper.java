package org.sona.mappers;

import org.mapstruct.Mapper;
import org.sona.model.tables.pojos.Users;
import org.sona.model.tables.records.UsersRecord;

@Mapper(componentModel = "spring")
public interface UsersMapper {

    UsersRecord toRecord(Users user);
    Users fromRecord(UsersRecord user);
}
