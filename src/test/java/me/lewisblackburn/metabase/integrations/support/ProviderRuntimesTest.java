package me.lewisblackburn.metabase.integrations.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProviderRuntimesTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0})
    void returnsNullForUnknownRuntime(Integer runtime) {
        // Given a provider supplies a missing or zero runtime.

        // When the runtime is normalized.
        Integer result = ProviderRuntimes.normalizeRuntime(runtime);

        // Then the result represents an unknown runtime.
        assertThat(result).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 136, Integer.MAX_VALUE})
    void preservesPositiveRuntime(Integer runtime) {
        // Given a provider supplies a positive runtime in minutes.

        // When the runtime is normalized.
        Integer result = ProviderRuntimes.normalizeRuntime(runtime);

        // Then the runtime remains unchanged.
        assertThat(result).isEqualTo(runtime);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -136, Integer.MIN_VALUE})
    void rejectsNegativeRuntime(Integer runtime) {
        // Given a provider supplies an invalid negative runtime.

        // When normalization is attempted, then the invalid runtime is rejected.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ProviderRuntimes.normalizeRuntime(runtime))
                .withMessage("Runtime must not be negative");
    }
}
