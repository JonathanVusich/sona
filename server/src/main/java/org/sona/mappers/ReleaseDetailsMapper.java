package org.sona.mappers;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sona.db.MediumWithTracks;
import org.sona.db.ReleaseDetails;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.Label;
import org.sona.model.tables.pojos.Release;

@Mapper(componentModel = "spring")
public interface ReleaseDetailsMapper {

    @Mapping(target = "release", source = "release")
    @Mapping(target = "artist", source = "artist")
    @Mapping(target = "labels", source = "labels")
    @Mapping(target = "media", source = "media")
    ReleaseDetails fromPojos(Release release, Artist artist, List<Label> labels, List<MediumWithTracks> media);
}
