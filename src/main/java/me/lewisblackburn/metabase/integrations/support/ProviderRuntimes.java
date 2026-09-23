package me.lewisblackburn.metabase.integrations.support;

public final class ProviderRuntimes {

    private ProviderRuntimes() {
    }

    /**
     * Treats missing or zero runtime as unknown and preserves positive minutes.
     *
     * @throws IllegalArgumentException if runtime is negative
     */
    public static Integer normalizeRuntime(Integer runtime) {
        if (runtime == null || runtime == 0) {
            return null;
        }
        if (runtime < 0) {
            throw new IllegalArgumentException("Runtime must not be negative");
        }
        return runtime;
    }
}
