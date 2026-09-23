package me.lewisblackburn.metabase.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TextUtilsTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void returnsNullForMissingOrBlankText(String value) {
        // Given the input contains missing, empty or whitespace-only text.

        // When the text is normalized.
        String result = TextUtils.blankToNull(value);

        // Then the result represents an absent value.
        assertThat(result).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"The Matrix", "en", "  Original title  ", "Line one\nLine two"})
    void preservesNonblankText(String value) {
        // Given the input contains nonblank text that may contain whitespace.

        // When the text is normalized.
        String result = TextUtils.blankToNull(value);

        // Then the content and surrounding whitespace remain unchanged.
        assertThat(result).isEqualTo(value);
    }
}
