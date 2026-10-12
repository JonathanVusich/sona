package org.sona.mappers;

import org.jooq.Record4;
import org.jooq.Result;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sona.db.ReleaseDetails;
import org.sona.model.tables.records.ArtistRecord;
import org.sona.model.tables.records.LabelRecord;
import org.sona.model.tables.records.MediumRecord;
import org.sona.model.tables.records.ReleaseRecord;

import java.util.List;

@Mapper(componentModel = "spring", uses = {ReleaseMapper.class, ArtistMapper.class, LabelMapper.class,
        MediumMapper.class})
public interface ReleaseDetailsMapper {

    @Mapping(target = "release", source = "release")
    @Mapping(target = "artist", source = "artist", qualifiedByName = "fromJoinedRecord")
    @Mapping(target = "labels", source = "labels")
    @Mapping(target = "media", source = "media")
    ReleaseDetails fromRecords(ReleaseRecord release, ArtistRecord artist, List<LabelRecord> labels,
                               List<MediumRecord> media);

    default ReleaseDetails fromRecord(
            final Record4<ReleaseRecord, ArtistRecord, Result<LabelRecord>, Result<MediumRecord>> record) {
        return fromRecords(record.value1(), record.value2(), record.value3(), record.value4());
    }
}
