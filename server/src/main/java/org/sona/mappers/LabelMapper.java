package org.sona.mappers;

import org.mapstruct.Mapper;
import org.sona.model.tables.pojos.Label;
import org.sona.model.tables.records.LabelRecord;

@Mapper(componentModel = "spring")
public interface LabelMapper {

    LabelRecord toRecord(Label label);
    Label fromRecord(LabelRecord label);
}
