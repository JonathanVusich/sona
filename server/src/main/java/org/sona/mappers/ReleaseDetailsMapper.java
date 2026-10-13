package org.sona.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sona.db.MediumWithTracks;
import org.sona.db.ReleaseDetails;
import org.sona.model.tables.pojos.Label;
import org.sona.model.tables.records.ArtistRecord;
import org.sona.model.tables.records.ReleaseRecord;

import java.util.List;

@Mapper(componentModel = "spring", uses = {ReleaseMapper.class, ArtistMapper.class})
public interface ReleaseDetailsMapper {

    @Mapping(target = "release", source = "release")
    @Mapping(target = "artist", source = "artist")
    @Mapping(target = "labels", source = "labels")
    @Mapping(target = "media", source = "media")
    ReleaseDetails fromRecords(ReleaseRecord release, ArtistRecord artist, List<Label> labels,
                               List<MediumWithTracks> media);
}
