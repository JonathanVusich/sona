package org.sona.mappers;

import org.mapstruct.Mapper;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.sona.model.tables.records.ReleaseGroupRecord;

@Mapper(componentModel = "spring")
public interface ReleaseGroupMapper {

    ReleaseGroupRecord toRecord(ReleaseGroup releaseGroup);
    ReleaseGroup fromRecord(ReleaseGroupRecord releaseGroup);
}
