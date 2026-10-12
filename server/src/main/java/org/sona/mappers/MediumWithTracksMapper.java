package org.sona.mappers;

import org.jooq.Record2;
import org.jooq.Result;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sona.db.MediumWithTracks;
import org.sona.model.tables.records.ArtistRecord;
import org.sona.model.tables.records.MediumRecord;
import org.sona.model.tables.records.TrackRecord;

import java.util.List;

@Mapper(componentModel = "spring", uses = {MediumMapper.class, TrackWithArtistMapper.class})
public interface MediumWithTracksMapper {

    @Mapping(target = "medium", source = "medium")
    @Mapping(target = "tracks", source = "tracks")
    MediumWithTracks fromRecords(MediumRecord medium, List<Record2<TrackRecord, ArtistRecord>> tracks);

    default MediumWithTracks fromRecord(
            final Record2<MediumRecord, Result<Record2<TrackRecord, ArtistRecord>>> record) {
        return fromRecords(record.value1(), record.value2());
    }
}
