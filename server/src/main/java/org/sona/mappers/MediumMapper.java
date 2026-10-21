package org.sona.mappers;

import org.mapstruct.Mapper;
import org.sona.model.tables.pojos.Medium;
import org.sona.model.tables.records.MediumRecord;

@Mapper(componentModel = "spring")
public interface MediumMapper {

    MediumRecord toRecord(Medium medium);

    Medium fromRecord(MediumRecord medium);
}
