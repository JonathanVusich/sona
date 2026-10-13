package org.sona.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sona.db.TrackWithArtist;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.Track;

@Mapper(componentModel = "spring")
public interface TrackWithArtistMapper {

    @Mapping(target = "track", source = "track")
    @Mapping(target = "artist", source = "artist")
    TrackWithArtist fromPojos(Track track, Artist artist);
}
