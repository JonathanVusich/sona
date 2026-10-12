package org.sona.mappers;

import org.jooq.Record2;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sona.db.TrackWithArtist;
import org.sona.model.tables.records.ArtistRecord;
import org.sona.model.tables.records.TrackRecord;

@Mapper(componentModel = "spring", uses = {TrackMapper.class, ArtistMapper.class})
public interface TrackWithArtistMapper {

    @Mapping(target = "track", source = "track")
    @Mapping(target = "artist", source = "artist")
    TrackWithArtist fromRecords(TrackRecord track, ArtistRecord artist);

    default TrackWithArtist fromRecord(final Record2<TrackRecord, ArtistRecord> record) {
        return fromRecords(record.value1(), record.value2());
    }
}
