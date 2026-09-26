package me.lewisblackburn.metabase.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DateUtilsTest {

    @ParameterizedTest
    @ValueSource(strings = {"1999-03-30", "2024-02-29"})
    void parsesValidDates(String value) {
        // Given the input contains a valid ISO date, including a leap day.
        LocalDate expected =
                value.equals("1999-03-30") ? LocalDate.of(1999, 3, 30) : LocalDate.of(2024, 2, 29);

        // When the date is parsed.
        LocalDate result = DateUtils.parseOptionalLocalDate(value);

        // Then the date retains its year, month and day.
        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void returnsNullForMissingDates(String value) {
        // Given the input contains no date or only whitespace.

        // When the optional date is parsed.
        LocalDate result = DateUtils.parseOptionalLocalDate(value);

        // Then the result represents an unknown date.
        assertThat(result).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-date", "30/03/1999", "2023-02-29", "2024-04-31",
            "1999-03-30T00:00:00", " 1999-03-30 "})
    void rejectsMalformedDates(String value) {
        // Given the input contains a nonblank value that is not a valid ISO local date.

        // When parsing is attempted, then the invalid value is rejected rather than treated as
        // missing.
        assertThatThrownBy(() -> DateUtils.parseOptionalLocalDate(value))
                .isInstanceOf(DateTimeParseException.class);
    }
}
