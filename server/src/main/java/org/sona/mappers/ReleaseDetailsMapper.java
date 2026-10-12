package org.sona.mappers;

import org.jooq.Record2;
import org.jooq.Record5;
import org.jooq.Result;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sona.db.ReleaseDetails;
import org.sona.model.tables.records.ArtistRecord;
import org.sona.model.tables.records.LabelRecord;
import org.sona.model.tables.records.MediumRecord;
import org.sona.model.tables.records.ReleaseRecord;
import org.sona.model.tables.records.TrackRecord;

import java.util.List;

@Mapper(componentModel = "spring", uses = {ReleaseMapper.class, ArtistMapper.class, LabelMapper.class,
        MediumMapper.class, TrackWithArtistMapper.class})
public interface ReleaseDetailsMapper {

    @Mapping(target = "release", source = "release")
    @Mapping(target = "artist", source = "artist")
    @Mapping(target = "labels", source = "labels")
    @Mapping(target = "media", source = "media")
    @Mapping(target = "tracks", source = "tracks")
    ReleaseDetails fromRecords(ReleaseRecord release, ArtistRecord artist, List<LabelRecord> labels,
                               List<MediumRecord> media, List<Record2<TrackRecord, ArtistRecord>> tracks);

    default ReleaseDetails fromRecord(final Record5<ReleaseRecord, ArtistRecord, Result<LabelRecord>,
            Result<MediumRecord>, Result<Record2<TrackRecord, ArtistRecord>>> record) {
        return fromRecords(record.value1(), record.value2(), record.value3(), record.value4(), record.value5());
    }
}
