package org.sona.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sona.db.DefaultRelease;
import org.sona.db.MediumWithTracks;
import org.sona.model.tables.pojos.Release;

import java.util.List;

@Mapper(componentModel = "spring")
public interface DefaultReleaseMapper {

    @Mapping(target = "release", source = "release")
    @Mapping(target = "media", source = "media")
    DefaultRelease fromPojos(Release release, List<MediumWithTracks> media);
}
