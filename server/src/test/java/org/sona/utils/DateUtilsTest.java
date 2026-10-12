package org.sona.utils;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DateUtilsTest {

    @Test
    void parsePartialDateFillsInTheMissingPartsAsTheFirst() {
        assertThat(DateUtils.parsePartialDate("2002")).isEqualTo(LocalDate.of(2002, 1, 1));
        assertThat(DateUtils.parsePartialDate("2002-08")).isEqualTo(LocalDate.of(2002, 8, 1));
        assertThat(DateUtils.parsePartialDate("2002-08-26")).isEqualTo(LocalDate.of(2002, 8, 26));
    }

    @Test
    void parsePartialDateReturnsNullWhenThereIsNoDate() {
        assertThat(DateUtils.parsePartialDate(null)).isNull();
        assertThat(DateUtils.parsePartialDate("")).isNull();
    }
}
