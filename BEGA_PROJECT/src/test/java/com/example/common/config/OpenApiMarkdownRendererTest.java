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
    void rejectsMalformedPathItemParametersInsteadOfDroppingThem() throws Exception {
        assertMalformedEndpoint("""
                {"openapi":"3.0.1","info":{"title":"Broken","version":"1"},
                "paths":{"/widgets":{"parameters":{},"get":{"responses":{"200":{"description":"OK"}}}}},
                "components":{"schemas":{}}}
                """, "OpenAPI parameters must be an array: path item /widgets");
    }

    @Test
    void rejectsMalformedOperationParametersInsteadOfDroppingThem() throws Exception {
        assertMalformedEndpoint("""
                {"openapi":"3.0.1","info":{"title":"Broken","version":"1"},
                "paths":{"/widgets":{"get":{"parameters":{},"responses":{"200":{"description":"OK"}}}}},
                "components":{"schemas":{}}}
                """, "OpenAPI parameters must be an array: GET /widgets");
    }

    @Test
    void rejectsMalformedRequestContentInsteadOfDroppingIt() throws Exception {
        assertMalformedEndpoint("""
                {"openapi":"3.0.1","info":{"title":"Broken","version":"1"},
                "paths":{"/widgets":{"post":{"requestBody":{"content":[]},"responses":{"200":{"description":"OK"}}}}},
                "components":{"schemas":{}}}
                """, "OpenAPI content must be an object: request body POST /widgets");
    }

    @Test
    void rejectsMalformedResponsesInsteadOfDroppingThem() throws Exception {
        assertMalformedEndpoint("""
                {"openapi":"3.0.1","info":{"title":"Broken","version":"1"},
                "paths":{"/widgets":{"get":{"responses":[]}}},"components":{"schemas":{}}}
                """, "OpenAPI responses must be an object: GET /widgets");
    }

    @Test
    void rejectsMalformedResponseContentInsteadOfDroppingIt() throws Exception {
        assertMalformedEndpoint("""
                {"openapi":"3.0.1","info":{"title":"Broken","version":"1"},
                "paths":{"/widgets":{"get":{"responses":{"200":{"description":"OK","content":[]}}}}},
                "components":{"schemas":{}}}
                """, "OpenAPI content must be an object: response GET /widgets 200");
    }

    @Test
    void rejectsMalformedHeadersAndHeaderContentInsteadOfDroppingThem() throws Exception {
        assertMalformedEndpoint("""
                {"openapi":"3.0.1","info":{"title":"Broken","version":"1"},
                "paths":{"/widgets":{"get":{"responses":{"200":{"description":"OK","headers":[]}}}}},
                "components":{"schemas":{}}}
                """, "OpenAPI headers must be an object: response GET /widgets 200");
        assertMalformedEndpoint("""
                {"openapi":"3.0.1","info":{"title":"Broken","version":"1"},
                "paths":{"/widgets":{"get":{"responses":{"200":{"description":"OK","headers":{"X-Trace":{"content":[]}}}}}}},
                "components":{"schemas":{}}}
                """, "OpenAPI content must be an object: header X-Trace in response GET /widgets 200");
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

    @Test
    void rendersParametersBodiesResponsesAndOnlyExplicitExamples() throws Exception {
        JsonNode schema = objectMapper.readTree("""
                {
                  "openapi": "3.0.1",
                  "info": {"title": "Fixture API", "version": "1"},
                  "paths": {
                    "/widgets/{id}": {
                      "parameters": [
                        {"name": "id", "in": "path", "required": true,
                         "description": "Widget ID", "schema": {"type": "string"},
                         "example": "w-1"}
                      ],
                      "post": {
                        "tags": ["widgets"],
                        "parameters": [
                          {"name": "verbose", "in": "query", "required": false,
                           "schema": {"type": "boolean", "default": false}}
                        ],
                        "requestBody": {
                          "required": true,
                          "content": {
                            "application/json": {
                              "schema": {"$ref": "#/components/schemas/WidgetRequest"},
                              "examples": {"valid": {"value": {"name": "sample"}}}
                            }
                          }
                        },
                        "responses": {
                          "201": {
                            "description": "Created",
                            "headers": {"Location": {"schema": {"type": "string"}}},
                            "content": {
                              "application/json": {
                                "schema": {"$ref": "#/components/schemas/WidgetResponse"},
                                "example": {"id": "w-1"}
                              }
                            }
                          },
                          "default": {"description": "Unexpected error"}
                        }
                      }
                    }
                  },
                  "components": {
                    "schemas": {
                      "WidgetRequest": {"type": "object"},
                      "WidgetResponse": {"type": "object"}
                    }
                  }
                }
                """);

        OpenApiMarkdownRenderer.RenderedDocuments rendered =
                OpenApiMarkdownRenderer.render(
                        schema,
                        "contracts/openapi.json",
                        "./gradlew updateOpenApiContract");

        assertThat(rendered.endpoints())
                .contains("| `id` | path | yes | `string` | Widget ID | — |")
                .contains("#### Parameter example: `id`")
                .contains("\"w-1\"")
                .contains("| `verbose` | query | no | `boolean` | — | — |")
                .contains("Required: **yes**")
                .contains("Media type: `application/json`")
                .contains("[WidgetRequest](openapi-schemas.md#widgetrequest)")
                .contains("#### Example: valid")
                .contains("{\n  \"name\" : \"sample\"\n}")
                .contains("### Response `201`")
                .contains("[WidgetResponse](openapi-schemas.md#widgetresponse)")
                .contains("#### Example")
                .contains("\"id\" : \"w-1\"")
                .contains("### Response `default`");
        assertThat(rendered.endpoints())
                .doesNotContain("Example: false");
    }

    @Test
    void rendersOnlyActualParameterExamplesSemanticallyAndPreservesOtherEntries() throws Exception {
        JsonNode schema = objectMapper.readTree("""
                {
                  "openapi": "3.0.1",
                  "info": {"title": "Fixture API", "version": "1"},
                  "paths": {"/widgets": {"get": {
                    "parameters": [
                      {"name": "direct", "in": "query", "schema": {"type": "string", "default": "DEFAULT"}, "example": "actual"},
                      {"name": "plural", "in": "query", "schema": {"type": "string", "enum": ["A", "B"]}, "examples": {
                        "actual": {"summary": "Actual example", "value": {"sort": "asc"}},
                        "external": {"externalValue": "https://example.test/value"},
                        "ref": {"$ref": "#/components/examples/Shared"},
                        "malformed": "not-an-example-object"
                      }}
                    ],
                    "responses": {"200": {"description": "OK"}}
                  }}},
                  "components": {"schemas": {}}
                }
                """);

        String endpoints = OpenApiMarkdownRenderer.render(
                schema,
                "contracts/openapi.json",
                "./gradlew updateOpenApiContract").endpoints();

        assertThat(endpoints)
                .contains("#### Parameter example: `direct`")
                .contains("\"actual\"")
                .contains("#### Parameter example: `plural`: actual")
                .contains("\"sort\" : \"asc\"")
                .contains("#### Parameter metadata: `plural`")
                .contains("\"external\"")
                .contains("\"ref\"")
                .contains("\"malformed\"")
                .doesNotContain("#### Parameter example: `plural`: external")
                .doesNotContain("#### Parameter example: `plural`: ref")
                .doesNotContain("#### Parameter example: `plural`: malformed")
                .doesNotContain("DEFAULT");
        assertThat(occurrences(endpoints, "\"actual\"")).isEqualTo(1);
        assertThat(occurrences(endpoints, "\"sort\" : \"asc\"")).isEqualTo(1);
    }

    @Test
    void rendersOnlyActualResponseHeaderExamplesSemanticallyAndPreservesOtherEntries() throws Exception {
        JsonNode schema = objectMapper.readTree("""
                {
                  "openapi": "3.0.1",
                  "info": {"title": "Fixture API", "version": "1"},
                  "paths": {"/widgets": {"get": {
                    "responses": {"200": {"description": "OK", "headers": {
                      "X-Direct": {"schema": {"type": "string", "default": "DEFAULT"}, "example": "trace-1"},
                      "X-Plural": {"schema": {"type": "string", "enum": ["A", "B"]}, "examples": {
                        "actual": {"value": {"trace": "trace-2"}},
                        "external": {"externalValue": "https://example.test/value"},
                        "ref": {"$ref": "#/components/examples/Shared"},
                        "malformed": 42
                      }}
                    }}}
                  }}},
                  "components": {"schemas": {}}
                }
                """);

        String endpoints = OpenApiMarkdownRenderer.render(
                schema,
                "contracts/openapi.json",
                "./gradlew updateOpenApiContract").endpoints();

        assertThat(endpoints)
                .contains("#### Header example: `X-Direct`")
                .contains("\"trace-1\"")
                .contains("#### Header example: `X-Plural`: actual")
                .contains("\"trace\" : \"trace-2\"")
                .contains("#### Header metadata: `X-Plural`")
                .contains("\"external\"")
                .contains("\"ref\"")
                .contains("\"malformed\"")
                .doesNotContain("#### Header example: `X-Plural`: external")
                .doesNotContain("#### Header example: `X-Plural`: ref")
                .doesNotContain("#### Header example: `X-Plural`: malformed")
                .doesNotContain("DEFAULT");
        assertThat(occurrences(endpoints, "\"trace-1\"")).isEqualTo(1);
        assertThat(occurrences(endpoints, "\"trace\" : \"trace-2\"")).isEqualTo(1);
    }

    @Test
    void preservesNestedUnrenderedEndpointDetailsAsStableJson() throws Exception {
        JsonNode schema = objectMapper.readTree("""
                {
                  "openapi": "3.0.1",
                  "info": {"title": "Fixture API", "version": "1"},
                  "paths": {
                    "/widgets": {
                      "get": {
                        "parameters": [{
                          "name": "filter", "in": "query", "schema": {"type": "string"},
                          "content": {"application/json": {"schema": {"type": "object"}}},
                          "x-parameter": {"z": 2, "a": 1}
                        }],
                        "requestBody": {
                          "content": {"application/json": {
                            "schema": {"type": "object"},
                            "encoding": {"name": {"contentType": "text/plain"}},
                            "x-media": {"z": 2, "a": 1}
                          }},
                          "x-request": {"z": 2, "a": 1}
                        },
                        "responses": {"200": {
                          "description": "OK",
                          "headers": {"Location": {
                            "schema": {"type": "string"},
                            "x-header": {"z": 2, "a": 1}
                          }},
                          "content": {"application/json": {
                            "schema": {"type": "object"},
                            "encoding": {"name": {"contentType": "text/plain"}},
                            "x-media": {"z": 2, "a": 1}
                          }},
                          "links": {"next": {"operationId": "nextWidget"}},
                          "x-response": {"z": 2, "a": 1}
                        }}
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
                .contains("#### Parameter metadata: `filter`")
                .contains("\"x-parameter\" : {\n    \"a\" : 1,\n    \"z\" : 2\n  }")
                .contains("#### Request body metadata")
                .contains("\"x-request\" : {\n    \"a\" : 1,\n    \"z\" : 2\n  }")
                .contains("#### Response metadata: `200`")
                .contains("\"links\" : {\n    \"next\" : {\n      \"operationId\" : \"nextWidget\"\n    }\n  }")
                .contains("#### Header metadata: `Location`")
                .contains("\"x-header\" : {\n    \"a\" : 1,\n    \"z\" : 2\n  }")
                .contains("#### Media type metadata: `application/json`")
                .contains("\"encoding\" : {\n    \"name\" : {\n      \"contentType\" : \"text/plain\"\n    }\n  }")
                .contains("\"x-media\" : {\n    \"a\" : 1,\n    \"z\" : 2\n  }");
    }

    @Test
    void preservesComposedSchemasWithTypeAsStableJsonLabels() throws Exception {
        JsonNode schema = objectMapper.readTree("""
                {
                  "openapi": "3.0.1",
                  "info": {"title": "Fixture API", "version": "1"},
                  "paths": {"/widgets": {"get": {
                    "parameters": [{
                      "name": "widget", "in": "query",
                      "schema": {"type": "object", "oneOf": [
                        {"$ref": "#/components/schemas/Widget"},
                        {"type": "string"}
                      ]}
                    }],
                    "responses": {"200": {"description": "OK"}}
                  }}},
                  "components": {"schemas": {"Widget": {"type": "object"}}}
                }
                """);

        OpenApiMarkdownRenderer.RenderedDocuments rendered =
                OpenApiMarkdownRenderer.render(
                        schema,
                        "contracts/openapi.json",
                        "./gradlew updateOpenApiContract");

        assertThat(rendered.endpoints())
                .contains("`{<br>  \"oneOf\" : [")
                .contains("\"$ref\" : \"#/components/schemas/Widget\"")
                .contains("\"type\" : \"object\"")
                .doesNotContain("| `widget` | query | no | `object` |");
    }

    @Test
    void rendersAllHeaderRowsBeforeHeaderMetadataFallbacks() throws Exception {
        JsonNode schema = objectMapper.readTree("""
                {
                  "openapi": "3.0.1",
                  "info": {"title": "Fixture API", "version": "1"},
                  "paths": {"/widgets": {"get": {
                    "responses": {"200": {
                      "description": "OK",
                      "headers": {
                        "Alpha": {"schema": {"type": "string"}, "x-alpha": {"enabled": true}},
                        "Zulu": {"schema": {"type": "integer"}}
                      }
                    }}
                  }}},
                  "components": {"schemas": {}}
                }
                """);

        OpenApiMarkdownRenderer.RenderedDocuments rendered =
                OpenApiMarkdownRenderer.render(
                        schema,
                        "contracts/openapi.json",
                        "./gradlew updateOpenApiContract");

        String endpoints = rendered.endpoints();
        assertThat(endpoints).contains("| `Alpha` | `string` | — |")
                .contains("| `Zulu` | `integer` | — |")
                .contains("#### Header metadata: `Alpha`");
        assertThat(endpoints.indexOf("| `Zulu` | `integer` | — |"))
                .isLessThan(endpoints.indexOf("#### Header metadata: `Alpha`"));
    }

    @Test
    void rendersSchemaPropertiesConstraintsCompositionAndExplicitMetadata() throws Exception {
        JsonNode schema = objectMapper.readTree("""
                {
                  "openapi": "3.0.1",
                  "info": {"title": "Fixture API", "version": "1"},
                  "paths": {},
                  "components": {
                    "schemas": {
                      "SearchResult": {
                        "description": "Search result union",
                        "oneOf": [
                          {"$ref": "#/components/schemas/WidgetRequest"},
                          {"type": "string"}
                        ]
                      },
                      "WidgetRequest": {
                        "type": "object",
                        "description": "Widget creation request",
                        "required": ["name"],
                        "properties": {
                          "count": {
                            "type": "integer",
                            "format": "int32",
                            "minimum": 0,
                            "maximum": 10,
                            "readOnly": true
                          },
                          "enabled": {
                            "type": "boolean",
                            "nullable": true,
                            "default": false
                          },
                          "name": {
                            "type": "string",
                            "description": "Display name",
                            "minLength": 1,
                            "maxLength": 80,
                            "pattern": "^[A-Z]",
                            "enum": ["ACTIVE", "INACTIVE"],
                            "example": "WIDGET"
                          },
                          "secret": {
                            "type": "string",
                            "writeOnly": true
                          }
                        }
                      }
                    }
                  }
                }
                """);

        OpenApiMarkdownRenderer.RenderedDocuments rendered =
                OpenApiMarkdownRenderer.render(
                        schema,
                        "contracts/openapi.json",
                        "./gradlew updateOpenApiContract");

        String schemas = rendered.schemas();
        assertThat(schemas)
                .startsWith("# Fixture API Schemas\n")
                .contains("Schemas: **2**")
                .contains("## SearchResult")
                .contains("### Composition")
                .contains("`oneOf`")
                .contains("## WidgetRequest")
                .contains("Required properties: `name`")
                .contains("| `name` | yes | `string` |")
                .contains("minLength=1")
                .contains("maxLength=80")
                .contains("pattern=`^[A-Z]`")
                .contains("Default: `false`")
                .contains("Example: `\"WIDGET\"")
                .contains("Enum: `ACTIVE`, `INACTIVE`");
        assertThat(rendered.schemas()).endsWith("\n").doesNotEndWith("\n\n");
    }

    @Test
    void preservesSchemaMetadataPropertyExamplesAndUnknownFields() throws Exception {
        JsonNode schema = objectMapper.readTree("""
                {
                  "openapi": "3.0.1",
                  "info": {"title": "Fixture API", "version": "1"},
                  "paths": {},
                  "components": {
                    "schemas": {
                      "Decorated": {
                        "type": "string",
                        "default": "DEFAULT",
                        "example": "EXAMPLE",
                        "examples": {
                          "zeta": {"value": "Z"},
                          "alpha": {"summary": "First", "value": "A"}
                        },
                        "enum": ["ALPHA", "BETA"],
                        "const": "ALPHA",
                        "nullable": true,
                        "readOnly": false,
                        "writeOnly": true,
                        "deprecated": false,
                        "minLength": 1,
                        "maxLength": 10,
                        "pattern": "^[A-Z]+$",
                        "x-schema": {"z": 2, "a": 1},
                        "properties": {
                          "code": {
                            "type": "string",
                            "examples": {
                              "zeta": {"value": "Z"},
                              "alpha": {"value": "A"}
                            },
                            "x-property": {"z": 4, "a": 3}
                          }
                        }
                      }
                    }
                  }
                }
                """);

        OpenApiMarkdownRenderer.RenderedDocuments rendered =
                OpenApiMarkdownRenderer.render(
                        schema,
                        "contracts/openapi.json",
                        "./gradlew updateOpenApiContract");

        String schemas = rendered.schemas();
        assertThat(schemas)
                .contains("Schema constraints: minLength=1, maxLength=10, pattern=`^[A-Z]+$`")
                .contains("Default: `\"DEFAULT\"`")
                .contains("Example: `\"EXAMPLE\"`")
                .contains("Examples:")
                .contains("\"alpha\" : {")
                .contains("\"zeta\" : {")
                .contains("Enum: `ALPHA`, `BETA`")
                .contains("Const: `\"ALPHA\"`")
                .contains("nullable: `true`")
                .contains("readOnly: `false`")
                .contains("writeOnly: `true`")
                .contains("deprecated: `false`")
                .contains("#### Property metadata: `code`")
                .contains("#### Schema metadata")
                .contains("\"x-schema\" : {\n    \"a\" : 1,\n    \"z\" : 2\n  }")
                .contains("#### Property metadata: `code`")
                .contains("\"x-property\" : {\n    \"a\" : 3,\n    \"z\" : 4\n  }");
        assertThat(schemas.substring(schemas.indexOf("#### Property metadata: `code`")))
                .contains("- Examples:")
                .contains("\"alpha\" : {")
                .contains("\"zeta\" : {");
    }

    @Test
    void rendersTitlesAndFencedFallbacksForNonTableAndPropertyCompositionSchemas() throws Exception {
        JsonNode schema = objectMapper.readTree("""
                {
                  "openapi": "3.0.1",
                  "info": {"title": "Fixture API", "version": "1"},
                  "paths": {},
                  "components": {
                    "schemas": {
                      "BooleanSchema": true,
                      "BrokenProperties": {
                        "type": "object",
                        "properties": true
                      },
                      "Titled": {
                        "type": "object",
                        "title": "Titled schema",
                        "properties": {
                          "choice": {
                            "oneOf": [
                              {"type": "string"},
                              {"type": "integer"}
                            ]
                          },
                          "named": {
                            "type": "string",
                            "title": "Named property"
                          }
                        }
                      }
                    }
                  }
                }
                """);

        OpenApiMarkdownRenderer.RenderedDocuments rendered =
                OpenApiMarkdownRenderer.render(
                        schema,
                        "contracts/openapi.json",
                        "./gradlew updateOpenApiContract");

        String schemas = rendered.schemas();
        assertThat(schemas)
                .contains("## BooleanSchema\n\n### Schema fallback\n```json\ntrue\n```")
                .contains("## BrokenProperties")
                .contains("### Properties fallback\n```json\ntrue\n```")
                .contains("Title: `Titled schema`")
                .contains("Title: `Named property`")
                .contains("#### Property composition: `choice`")
                .contains("Includes: `oneOf`")
                .contains("\"oneOf\" : [ {");
    }

    @Test
    void assignsDistinctStableAnchorsToCollidingSchemaNamesAndReferences() throws Exception {
        JsonNode schema = objectMapper.readTree("""
                {
                  "openapi": "3.0.1",
                  "info": {"title": "Fixture API", "version": "1"},
                  "paths": {
                    "/users": {
                      "get": {
                        "parameters": [{
                          "name": "user", "in": "query",
                          "schema": {"$ref": "#/components/schemas/UserInfo"}
                        }],
                        "responses": {"200": {"description": "OK"}}
                      }
                    }
                  },
                  "components": {
                    "schemas": {
                      "Envelope": {
                        "type": "object",
                        "properties": {
                          "dotted": {"$ref": "#/components/schemas/User.Info"},
                          "plain": {"$ref": "#/components/schemas/UserInfo"},
                          "suffixed": {"$ref": "#/components/schemas/UserInfo-2"}
                        }
                      },
                      "User.Info": {"type": "string"},
                      "UserInfo": {"type": "string"},
                      "UserInfo-2": {"type": "string"},
                      "WidgetRequest": {"type": "string"}
                    }
                  }
                }
                """);

        OpenApiMarkdownRenderer.RenderedDocuments rendered =
                OpenApiMarkdownRenderer.render(
                        schema,
                        "contracts/openapi.json",
                        "./gradlew updateOpenApiContract");

        assertThat(rendered.schemas())
                .contains("<a id=\"userinfo\"></a>\n## User.Info")
                .contains("<a id=\"userinfo-2\"></a>\n## UserInfo")
                .contains("<a id=\"userinfo-2-2\"></a>\n## UserInfo-2")
                .contains("<a id=\"widgetrequest\"></a>\n## WidgetRequest")
                .contains("[User.Info](openapi-schemas.md#userinfo)")
                .contains("[UserInfo](openapi-schemas.md#userinfo-2)")
                .contains("[UserInfo-2](openapi-schemas.md#userinfo-2-2)");
        assertThat(rendered.endpoints())
                .contains("[UserInfo](openapi-schemas.md#userinfo-2)");
    }

    private void assertMalformedEndpoint(String document, String message) throws Exception {
        JsonNode malformed = objectMapper.readTree(document);

        assertThatThrownBy(() -> OpenApiMarkdownRenderer.render(
                malformed,
                "contracts/openapi.json",
                "./gradlew updateOpenApiContract"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);
    }

    private static int occurrences(String value, String fragment) {
        return value.split(java.util.regex.Pattern.quote(fragment), -1).length - 1;
    }
}
