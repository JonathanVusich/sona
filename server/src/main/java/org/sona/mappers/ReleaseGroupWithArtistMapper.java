package org.sona.mappers;

import org.jooq.Record2;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sona.db.ReleaseGroupWithArtist;
import org.sona.model.tables.records.ArtistRecord;
import org.sona.model.tables.records.ReleaseGroupRecord;

@Mapper(componentModel = "spring", uses = {ReleaseGroupMapper.class, ArtistMapper.class})
public interface ReleaseGroupWithArtistMapper {

    @Mapping(target = "releaseGroup", source = "releaseGroup")
    @Mapping(target = "artist", source = "artist", qualifiedByName = "fromJoinedRecord")
    ReleaseGroupWithArtist fromRecords(ReleaseGroupRecord releaseGroup, ArtistRecord artist);

    default ReleaseGroupWithArtist fromRecord(final Record2<ReleaseGroupRecord, ArtistRecord> record) {
        return fromRecords(record.value1(), record.value2());
    }
}
