package org.sona.client.model.query;

public record RecordingQuery(
        String trackName,
        String artist,
        int trackNumber,
        int totalTracks
) {

    public String build() {
        final var query = new StringBuilder();

        query.append("\"");
        query.append(trackName);
        query.append("\"");

        if (artist != null) {
            query.append(" AND ");
            query.append("artist:");
            query.append(artist);
        }

        if (trackNumber != -1) {
            query.append(" AND ");
            query.append("tnum:");
            query.append(trackNumber);
        }

        if (totalTracks != -1) {
            query.append(" AND ");
            query.append("tracks:");
            query.append(totalTracks);
        }

        return query.toString();
    }

}