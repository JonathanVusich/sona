package org.sona.mappers;

import org.mapstruct.Mapper;
import org.sona.model.tables.pojos.Play;
import org.sona.model.tables.records.PlayRecord;

@Mapper(componentModel = "spring")
public interface PlayMapper {

    Play fromRecord(PlayRecord play);
}
