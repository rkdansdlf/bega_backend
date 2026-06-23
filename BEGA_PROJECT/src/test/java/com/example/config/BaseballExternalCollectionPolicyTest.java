package com.example.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class BaseballExternalCollectionPolicyTest {

    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath();
    private static final List<Path> SCANNED_ROOTS = List.of(
            PROJECT_ROOT.resolve("src/main/java"),
            PROJECT_ROOT.resolve("src/main/resources"));
    private static final List<String> FORBIDDEN_MARKERS = List.of(
            "kbo_crawler_sync",
            "org.openqa.selenium",
            "selenium",
            "beautifulsoup",
            "from bs4",
            "import bs4",
            "scrapy");

    @Test
    void backendRuntimeCodeDoesNotIntroduceExternalBaseballCollection() throws IOException {
        List<String> violations;
        try (Stream<Path> paths = SCANNED_ROOTS.stream()
                .filter(Files::exists)
                .flatMap(BaseballExternalCollectionPolicyTest::walk)) {
            violations = paths
                    .filter(Files::isRegularFile)
                    .filter(BaseballExternalCollectionPolicyTest::isTextRuntimeFile)
                    .flatMap(BaseballExternalCollectionPolicyTest::violationsFor)
                    .toList();
        }

        assertThat(violations)
                .as("Backend runtime code must not add external baseball crawling/scraping collection paths")
                .isEmpty();
    }

    private static Stream<Path> walk(Path root) {
        try {
            return Files.walk(root);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to scan " + root, ex);
        }
    }

    private static boolean isTextRuntimeFile(Path path) {
        String filename = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return filename.endsWith(".java")
                || filename.endsWith(".yml")
                || filename.endsWith(".yaml")
                || filename.endsWith(".properties")
                || filename.endsWith(".json")
                || filename.endsWith(".sql");
    }

    private static Stream<String> violationsFor(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            return FORBIDDEN_MARKERS.stream()
                    .filter(content::contains)
                    .map(marker -> PROJECT_ROOT.relativize(path) + " contains forbidden marker `" + marker + "`");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read " + path, ex);
        }
    }
}
