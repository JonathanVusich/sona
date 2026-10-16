package org.sona.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sona.db.Edition;
import org.sona.model.tables.pojos.Label;
import org.sona.model.tables.pojos.Medium;
import org.sona.model.tables.pojos.Release;

import java.util.List;

@Mapper(componentModel = "spring")
public interface EditionMapper {

    @Mapping(target = "release", source = "release")
    @Mapping(target = "labels", source = "labels")
    @Mapping(target = "media", source = "media")
    Edition fromPojos(Release release, List<Label> labels, List<Medium> media);
}
