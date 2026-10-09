package org.sona.mappers;

import org.mapstruct.Mapper;
import org.sona.model.tables.pojos.Password;
import org.sona.model.tables.records.PasswordRecord;

@Mapper(componentModel = "spring")
public interface PasswordMapper {

    PasswordRecord toRecord(Password password);
    Password fromRecord(PasswordRecord password);
}
