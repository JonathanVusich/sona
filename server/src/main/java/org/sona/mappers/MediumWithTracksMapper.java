package org.sona.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sona.db.MediumWithTracks;
import org.sona.db.TrackWithArtist;
import org.sona.model.tables.records.MediumRecord;

import java.util.List;

@Mapper(componentModel = "spring", uses = MediumMapper.class)
public interface MediumWithTracksMapper {

    @Mapping(target = "medium", source = "medium")
    @Mapping(target = "tracks", source = "tracks")
    MediumWithTracks fromRecords(MediumRecord medium, List<TrackWithArtist> tracks);
}
