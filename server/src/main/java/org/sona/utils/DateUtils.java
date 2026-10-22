package org.sona.utils;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class DateUtils {

    private static final DateTimeFormatter PARTIAL_DATE = new DateTimeFormatterBuilder()
            .appendPattern("uuuu[-MM[-dd]]")
            .parseDefaulting(MONTH_OF_YEAR, 1)
            .parseDefaulting(DAY_OF_MONTH, 1)
            .toFormatter();

    /**
     * Parses a date that may only give the year or month, like MusicBrainz's "2002", "2002-08" or "2002-08-26".
     *
     * @return the date, with the parts not given as the first, or null for a null or empty date
     */
    public static LocalDate parsePartialDate(final String date) {
        if (date == null || date.isEmpty()) {
            return null;
        }
        return LocalDate.parse(date, PARTIAL_DATE);
    }
}
