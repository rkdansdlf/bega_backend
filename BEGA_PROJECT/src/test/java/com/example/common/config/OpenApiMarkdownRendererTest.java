package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OpenApiMarkdownRendererTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rendersOperationsByTagPathAndMethodWithMetadata() throws Exception {
        JsonNode schema = objectMapper.readTree("""
                {
                  "openapi": "3.0.1",
                  "info": {"title": "Fixture API", "version": "1.2.3"},
                  "paths": {
                    "/zeta": {
                      "post": {
                        "tags": ["z-tag"],
                        "summary": "Create zeta",
                        "description": "Creates a zeta resource.",
                        "operationId": "createZeta",
                        "deprecated": true,
                        "security": [{"bearerAuth": []}],
                        "responses": {"204": {"description": "Created"}}
                      },
                      "get": {
                        "tags": ["a-tag"],
                        "summary": "Read zeta",
                        "operationId": "readZeta",
                        "responses": {"200": {"description": "OK"}}
                      }
                    }
                  },
                  "components": {"schemas": {}}
                }
                """);

        OpenApiMarkdownRenderer.RenderedDocuments rendered =
                OpenApiMarkdownRenderer.render(
                        schema,
                        "contracts/openapi.json",
                        "./gradlew updateOpenApiContract");

        assertThat(rendered.operationCount()).isEqualTo(2);
        assertThat(rendered.schemaCount()).isZero();
        assertThat(rendered.endpoints())
                .startsWith("# Fixture API Endpoints\n")
                .contains("Operations: **2**")
                .contains("## a-tag\n")
                .contains("### GET `/zeta`")
                .contains("- Operation ID: `readZeta`")
                .contains("- Security: Not specified in OpenAPI")
                .contains("## z-tag\n")
                .contains("### POST `/zeta`")
                .contains("- Security: `bearerAuth`")
                .contains("- Deprecated: yes")
                .contains("Creates a zeta resource.");
        assertThat(rendered.endpoints().indexOf("## a-tag"))
                .isLessThan(rendered.endpoints().indexOf("## z-tag"));
        assertThat(rendered.endpoints()).endsWith("\n").doesNotEndWith("\n\n");
    }

    @Test
    void rejectsMalformedOpenApiInsteadOfRenderingPartialDocs() throws Exception {
        JsonNode malformed = objectMapper.readTree("""
                {"openapi":"3.0.1","info":{"title":"Broken","version":"1"}}
                """);

        assertThatThrownBy(() -> OpenApiMarkdownRenderer.render(
                malformed,
                "contracts/openapi.json",
                "./gradlew updateOpenApiContract"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("OpenAPI field must be an object: paths");
    }

    @Test
    void rendersOperationTagsInSortedOrder() throws Exception {
        JsonNode schema = objectMapper.readTree("""
                {
                  "openapi": "3.0.1",
                  "info": {"title": "Fixture API", "version": "1"},
                  "paths": {
                    "/widgets": {
                      "get": {
                        "tags": ["z-tag", "a-tag"],
                        "responses": {"200": {"description": "OK"}}
                      }
                    }
                  },
                  "components": {"schemas": {}}
                }
                """);

        OpenApiMarkdownRenderer.RenderedDocuments rendered =
                OpenApiMarkdownRenderer.render(
                        schema,
                        "contracts/openapi.json",
                        "./gradlew updateOpenApiContract");

        assertThat(rendered.endpoints()).contains("- Tags: `a-tag`, `z-tag`");
    }

    @Test
    void preservesUnknownPathItemAndOperationFieldsAsStableJson() throws Exception {
        JsonNode schema = objectMapper.readTree("""
                {
                  "openapi": "3.0.1",
                  "info": {"title": "Fixture API", "version": "1"},
                  "paths": {
                    "/widgets": {
                      "x-path-metadata": {"z": 1, "a": {"d": 4, "c": 3}},
                      "parameters": [],
                      "get": {
                        "responses": {"200": {"description": "OK"}},
                        "x-operation-metadata": {"z": 2, "a": {"d": 6, "c": 5}},
                        "externalDocs": {"url": "https://example.test/docs", "description": "Widget docs"},
                        "parameters": []
                      }
                    }
                  },
                  "components": {"schemas": {}}
                }
                """);

        OpenApiMarkdownRenderer.RenderedDocuments rendered =
                OpenApiMarkdownRenderer.render(
                        schema,
                        "contracts/openapi.json",
                        "./gradlew updateOpenApiContract");

        assertThat(rendered.endpoints())
                .contains("#### Path-item extensions and metadata")
                .contains("\"x-path-metadata\" : {\n    \"a\" : {\n      \"c\" : 3,\n      \"d\" : 4\n    },\n    \"z\" : 1\n  }")
                .contains("#### Operation extensions and metadata")
                .contains("\"externalDocs\" : {\n    \"description\" : \"Widget docs\",\n    \"url\" : \"https://example.test/docs\"\n  }")
                .contains("\"x-operation-metadata\" : {\n    \"a\" : {\n      \"c\" : 5,\n      \"d\" : 6\n    },\n    \"z\" : 2\n  }")
                .doesNotContain("\"parameters\"")
                .doesNotContain("\"responses\"");
    }
}
