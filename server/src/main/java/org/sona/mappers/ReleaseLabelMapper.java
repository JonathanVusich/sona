package org.sona.mappers;

import org.mapstruct.Mapper;
import org.sona.model.tables.pojos.ReleaseLabel;
import org.sona.model.tables.records.ReleaseLabelRecord;

@Mapper(componentModel = "spring")
public interface ReleaseLabelMapper {

    ReleaseLabelRecord toRecord(ReleaseLabel releaseLabel);

    ReleaseLabel fromRecord(ReleaseLabelRecord releaseLabel);
}
