package org.sona.mappers;

import org.mapstruct.Mapper;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.records.ReleaseRecord;

@Mapper(componentModel = "spring")
public interface ReleaseMapper {

    ReleaseRecord toRecord(Release release);
    Release fromRecord(ReleaseRecord release);
}
