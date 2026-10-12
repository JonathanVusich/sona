package org.sona.mappers;

import org.mapstruct.Mapper;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.records.ArtistRecord;

@Mapper(componentModel = "spring")
public interface ArtistMapper {

    ArtistRecord toRecord(Artist artist);
    Artist fromRecord(ArtistRecord artist);
}
