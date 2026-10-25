package org.sona.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sona.db.PlaylistEntry;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.Track;

@Mapper(componentModel = "spring")
public interface PlaylistEntryMapper {

    @Mapping(target = "track", source = "track")
    @Mapping(target = "artist", source = "artist")
    @Mapping(target = "release", source = "release")
    PlaylistEntry fromPojos(Track track, Artist artist, Release release);
}
