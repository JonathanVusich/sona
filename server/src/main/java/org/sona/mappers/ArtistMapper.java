package org.sona.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Named;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.records.ArtistRecord;

@Mapper(componentModel = "spring")
public interface ArtistMapper {

    ArtistRecord toRecord(Artist artist);
    Artist fromRecord(ArtistRecord artist);

    /**
     * @return the artist, or null when a left join found none, which gives a record of nulls rather than null
     */
    @Named("fromJoinedRecord")
    default Artist fromJoinedRecord(final ArtistRecord artist) {
        if (artist.getArtistId() == null) {
            return null;
        }
        return fromRecord(artist);
    }
}
