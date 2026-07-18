package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "spring.profiles.active=local,dev",
        "spring.jwt.secret=test-jwt-secret-64-characters-long-for-hs512-signature-tests-key-1234567890",
        "spring.jwt.refresh-expiration=86400000",
        "app.oauth2.cookie-secret=test-oauth2-cookie-secret",
        "spring.datasource.url=jdbc:h2:mem:backend_openapi_contract;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.open-in-view=false",
        "jobrunr.background-job-server.enabled=false",
        "jobrunr.dashboard.enabled=false",
        "storage.type=oci",
        "oci.s3.endpoint=http://localhost:4566",
        "oci.s3.access-key=test-access-key",
        "oci.s3.secret-key=test-secret-key",
        "oci.s3.bucket=test-bucket",
        "oci.s3.region=ap-seoul-1",
        "app.home.bootstrap.warmup.enabled=false",
        "app.prediction.warmup.enabled=false",
        "app.ai.coach-auto-brief.monitoring.enabled=false"
})
class BackendOpenApiContractTest {

    private static final Path CONTRACT_PATH = Path.of("contracts", "openapi.json");
    private static final Path ENDPOINTS_PATH =
            Path.of("contracts", "openapi-endpoints.md");
    private static final Path SCHEMAS_PATH =
            Path.of("contracts", "openapi-schemas.md");
    private static final String UPDATE_PROPERTY = "updateOpenApiContract";
    private static final String UPDATE_COMMAND = "./gradlew updateOpenApiContract";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void runtimeOpenApiMatchesCommittedContract() throws Exception {
        String runtimeJson = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode runtimeContract = objectMapper.readTree(runtimeJson);
        assertOperation(runtimeContract, "/api/payments/capability", "get");
        assertOperation(runtimeContract, "/api/admin/maintenance/cheer-posts/cleanup", "post");
        JsonNode normalizedRuntime = normalize(runtimeContract);
        OpenApiMarkdownRenderer.RenderedDocuments rendered =
                OpenApiMarkdownRenderer.render(
                        normalizedRuntime,
                        CONTRACT_PATH.toString(),
                        UPDATE_COMMAND);

        assertThat(rendered.operationCount()).isEqualTo(countOperations(runtimeContract));
        assertThat(rendered.operationCount()).isPositive();
        assertThat(rendered.endpoints()).contains(
                "Operations: **" + countOperations(runtimeContract) + "**");
        assertThat(rendered.schemas()).contains(
                "Schemas: **" + runtimeContract.path("components").path("schemas").size() + "**");

        if (Boolean.getBoolean(UPDATE_PROPERTY)) {
            writeAtomic(
                    CONTRACT_PATH,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(normalizedRuntime) + "\n");
            writeAtomic(ENDPOINTS_PATH, rendered.endpoints());
            writeAtomic(SCHEMAS_PATH, rendered.schemas());
        }

        assertThat(CONTRACT_PATH)
                .withFailMessage("Backend OpenAPI contract is missing. Run ./gradlew updateOpenApiContract")
                .exists();

        JsonNode committedContract = objectMapper.readTree(Files.readString(CONTRACT_PATH));
        assertThat(normalizedRuntime)
                .withFailMessage("Backend OpenAPI contract drift detected. Run ./gradlew updateOpenApiContract")
                .isEqualTo(normalize(committedContract));

        assertThat(ENDPOINTS_PATH)
                .withFailMessage("Backend OpenAPI Markdown drift detected. Run ./gradlew updateOpenApiContract")
                .exists();
        assertThat(Files.readAllBytes(ENDPOINTS_PATH))
                .withFailMessage("Backend OpenAPI Markdown drift detected. Run ./gradlew updateOpenApiContract")
                .isEqualTo(rendered.endpoints().getBytes(StandardCharsets.UTF_8));

        assertThat(SCHEMAS_PATH)
                .withFailMessage("Backend OpenAPI Markdown drift detected. Run ./gradlew updateOpenApiContract")
                .exists();
        assertThat(Files.readAllBytes(SCHEMAS_PATH))
                .withFailMessage("Backend OpenAPI Markdown drift detected. Run ./gradlew updateOpenApiContract")
                .isEqualTo(rendered.schemas().getBytes(StandardCharsets.UTF_8));
    }

    private void assertOperation(JsonNode contract, String path, String method) {
        JsonNode operation = contract.path("paths").path(path).path(method);
        assertThat(operation.isMissingNode())
                .withFailMessage("OpenAPI operation is missing: %s %s", method.toUpperCase(), path)
                .isFalse();
    }

    private int countOperations(JsonNode contract) {
        java.util.Set<String> methods = java.util.Set.of(
                "get", "post", "put", "patch", "delete", "options", "head", "trace");
        int count = 0;
        for (JsonNode pathItem : contract.path("paths")) {
            java.util.Iterator<String> names = pathItem.fieldNames();
            while (names.hasNext()) {
                if (methods.contains(names.next())) {
                    count++;
                }
            }
        }
        return count;
    }

    private void writeAtomic(Path target, String content) throws Exception {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(
                target.getParent(), "." + target.getFileName(), ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporary,
                        target,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(
                        temporary,
                        target,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private JsonNode normalize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode normalized = objectMapper.createObjectNode();
            List<String> fieldNames = new ArrayList<>();
            node.fieldNames().forEachRemaining(fieldNames::add);
            fieldNames.stream()
                    .sorted(Comparator.naturalOrder())
                    .forEach(fieldName -> normalized.set(fieldName, normalize(node.get(fieldName))));
            return normalized;
        }

        if (node.isArray()) {
            ArrayNode normalized = objectMapper.createArrayNode();
            node.forEach(item -> normalized.add(normalize(item)));
            return normalized;
        }

        return node.deepCopy();
    }
}
