package me.lewisblackburn.metabase.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;

public final class ClasspathFixtures {

    private ClasspathFixtures() {}

    public static String read(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
