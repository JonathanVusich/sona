package org.sona.mappers;

import org.mapstruct.Mapper;
import org.sona.model.tables.pojos.RefreshToken;
import org.sona.model.tables.records.RefreshTokenRecord;

@Mapper(componentModel = "spring")
public interface RefreshTokenMapper {

    RefreshTokenRecord toRecord(RefreshToken token);
    RefreshToken fromRecord(RefreshTokenRecord token);
}
