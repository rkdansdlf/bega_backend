package com.example.common.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class OpenApiMarkdownRenderer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Map<String, Integer> METHOD_ORDER = Map.of(
            "get", 0, "post", 1, "put", 2, "patch", 3,
            "delete", 4, "options", 5, "head", 6, "trace", 7);

    record RenderedDocuments(
            String endpoints,
            String schemas,
            int operationCount,
            int schemaCount) {
    }

    private record Operation(
            String tag,
            String path,
            String method,
            JsonNode pathItem,
            JsonNode node) {
    }

    private OpenApiMarkdownRenderer() {
    }

    static RenderedDocuments render(
            JsonNode openApi,
            String sourcePath,
            String updateCommand) {
        JsonNode paths = requireObject(openApi, "paths");
        JsonNode schemas = requireObject(openApi.path("components"), "schemas");
        Map<String, String> schemaAnchors = schemaAnchors(schemas);
        List<Operation> operations = collectOperations(paths);
        String endpointMarkdown = renderEndpoints(
                openApi, sourcePath, updateCommand, paths, operations, schemaAnchors);
        String schemaMarkdown = renderSchemas(
                openApi, sourcePath, updateCommand, schemas, schemaAnchors);
        return new RenderedDocuments(
                endpointMarkdown,
                schemaMarkdown,
                operations.size(),
                schemas.size());
    }

    private static JsonNode requireObject(JsonNode parent, String field) {
        JsonNode value = parent.path(field);
        if (!value.isObject()) {
            throw new IllegalArgumentException(
                    "OpenAPI field must be an object: " + field);
        }
        return value;
    }

    private static List<Operation> collectOperations(JsonNode paths) {
        List<Operation> operations = new ArrayList<>();
        paths.fields().forEachRemaining(pathEntry -> {
            JsonNode pathItem = pathEntry.getValue();
            if (!pathItem.isObject()) {
                throw new IllegalArgumentException(
                        "OpenAPI path item must be an object: " + pathEntry.getKey());
            }
            pathItem.fields().forEachRemaining(operationEntry -> {
                String method = operationEntry.getKey().toLowerCase(Locale.ROOT);
                if (!METHOD_ORDER.containsKey(method)) {
                    return;
                }
                JsonNode operation = operationEntry.getValue();
                if (!operation.isObject()) {
                    throw new IllegalArgumentException(
                            "OpenAPI operation must be an object: " + method + " " + pathEntry.getKey());
                }
                operations.add(new Operation(
                        primaryTag(operation),
                        pathEntry.getKey(),
                        method,
                        pathItem,
                        operation));
            });
        });
        operations.sort(Comparator
                .comparing((Operation operation) -> operation.tag().toLowerCase(Locale.ROOT))
                .thenComparing(Operation::path)
                .thenComparingInt(operation -> METHOD_ORDER.get(operation.method())));
        return operations;
    }

    private static String primaryTag(JsonNode operation) {
        JsonNode tags = operation.path("tags");
        if (!tags.isArray() || tags.size() == 0 || !tags.get(0).isTextual()) {
            return "untagged";
        }
        return tags.get(0).asText();
    }

    private static String renderEndpoints(
            JsonNode openApi,
            String sourcePath,
            String updateCommand,
            JsonNode paths,
            List<Operation> operations,
            Map<String, String> schemaAnchors) {
        StringBuilder markdown = new StringBuilder();
        appendLine(markdown, "# " + infoText(openApi, "title") + " Endpoints");
        appendLine(markdown, "");
        appendLine(markdown, "> This file is generated. Do not edit directly.");
        appendLine(markdown, "> Source: `" + sourcePath + "`");
        appendLine(markdown, "> Regenerate with: `" + updateCommand + "`");
        appendLine(markdown, "");
        appendLine(markdown, "Version: `" + infoText(openApi, "version") + "`");
        appendLine(markdown, "Paths: **" + paths.size() + "**");
        appendLine(markdown, "Operations: **" + operations.size() + "**");

        String previousTag = null;
        for (Operation operation : operations) {
            if (!operation.tag().equals(previousTag)) {
                appendLine(markdown, "");
                appendLine(markdown, "## " + operation.tag());
                previousTag = operation.tag();
            }
            appendOperation(markdown, operation, schemaAnchors);
        }
        return finish(markdown);
    }

    private static void appendOperation(
            StringBuilder markdown,
            Operation operation,
            Map<String, String> schemaAnchors) {
        appendLine(markdown, "");
        appendLine(markdown, "### " + operation.method().toUpperCase(Locale.ROOT) + " `" + operation.path() + "`");
        appendOptionalLine(markdown, operation.node().path("summary"));
        appendOptionalLine(markdown, operation.node().path("description"));
        appendLine(markdown, "- Operation ID: `" + textOrNone(operation.node().path("operationId")) + "`");
        appendLine(markdown, "- Tags: " + renderTags(operation.node().path("tags")));
        appendLine(markdown, "- Security: " + renderSecurity(operation.node().path("security")));
        appendLine(markdown, "- Deprecated: " + (operation.node().path("deprecated").asBoolean(false) ? "yes" : "no"));
        appendParameters(markdown, mergedParameters(operation.pathItem(), operation.node()), schemaAnchors);
        appendRequestBody(markdown, operation.node().path("requestBody"), schemaAnchors);
        appendResponses(markdown, operation.node().path("responses"), schemaAnchors);
        appendFallback(markdown, "Path-item extensions and metadata", operation.pathItem(), pathItemFields());
        appendFallback(markdown, "Operation extensions and metadata", operation.node(), operationFields());
    }

    private static List<JsonNode> mergedParameters(JsonNode pathItem, JsonNode operation) {
        Map<String, JsonNode> parameters = new HashMap<>();
        addParameters(parameters, pathItem.path("parameters"));
        addParameters(parameters, operation.path("parameters"));
        List<JsonNode> merged = new ArrayList<>(parameters.values());
        merged.sort(Comparator
                .<JsonNode>comparingInt(parameter -> parameterLocationOrder(parameter.path("in").asText()))
                .thenComparing(parameter -> parameter.path("name").asText()));
        return merged;
    }

    private static void addParameters(Map<String, JsonNode> destination, JsonNode parameters) {
        if (!parameters.isArray()) {
            return;
        }
        for (JsonNode parameter : parameters) {
            if (parameter.isObject()) {
                destination.put(parameter.path("in").asText() + "\u0000" + parameter.path("name").asText(), parameter);
            }
        }
    }

    private static int parameterLocationOrder(String location) {
        return switch (location) {
            case "path" -> 0;
            case "query" -> 1;
            case "header" -> 2;
            case "cookie" -> 3;
            default -> 4;
        };
    }

    private static void appendParameters(
            StringBuilder out,
            List<JsonNode> parameters,
            Map<String, String> schemaAnchors) {
        if (parameters.isEmpty()) {
            return;
        }
        appendLine(out, "");
        appendLine(out, "#### Parameters");
        appendLine(out, "| Name | In | Required | Schema | Description | Example |");
        appendLine(out, "| --- | --- | --- | --- | --- | --- |");
        for (JsonNode parameter : parameters) {
            JsonNode example = parameter.get("example");
            appendLine(out, "| `" + markdownCell(parameter.path("name").asText()) + "` | "
                    + markdownCell(parameter.path("in").asText()) + " | "
                    + (parameter.path("required").asBoolean(false) ? "yes" : "no") + " | "
                    + schemaCell(parameter.path("schema"), schemaAnchors) + " | "
                    + markdownCellOrDash(parameter.get("description")) + " | "
                    + (example == null ? "—" : "`" + markdownCell(stableJson(example)) + "`") + " |");
        }
        for (JsonNode parameter : parameters) {
            appendFallback(out, "Parameter metadata: `" + parameter.path("name").asText() + "`", parameter,
                    Set.of("name", "in", "required", "schema", "description", "example"));
        }
    }

    private static void appendRequestBody(
            StringBuilder out,
            JsonNode requestBody,
            Map<String, String> schemaAnchors) {
        if (!requestBody.isObject()) {
            return;
        }
        appendLine(out, "");
        appendLine(out, "#### Request body");
        appendLine(out, "Required: **" + (requestBody.path("required").asBoolean(false) ? "yes" : "no") + "**");
        appendOptionalLine(out, requestBody.path("description"));
        appendContent(out, requestBody.path("content"), schemaAnchors);
        appendFallback(out, "Request body metadata", requestBody,
                Set.of("required", "description", "content"));
    }

    private static void appendResponses(
            StringBuilder out,
            JsonNode responses,
            Map<String, String> schemaAnchors) {
        if (!responses.isObject()) {
            return;
        }
        List<String> codes = new ArrayList<>();
        responses.fieldNames().forEachRemaining(codes::add);
        codes.sort(OpenApiMarkdownRenderer::compareResponseCodes);
        for (String code : codes) {
            JsonNode response = responses.path(code);
            appendLine(out, "");
            appendLine(out, "### Response `" + code + "`");
            appendOptionalLine(out, response.path("description"));
            appendHeaders(out, response.path("headers"), schemaAnchors);
            appendContent(out, response.path("content"), schemaAnchors);
            appendFallback(out, "Response metadata: `" + code + "`", response,
                    Set.of("description", "headers", "content"));
        }
    }

    private static int compareResponseCodes(String left, String right) {
        boolean leftNumeric = left.matches("\\d+");
        boolean rightNumeric = right.matches("\\d+");
        if (leftNumeric && rightNumeric) {
            return Integer.compare(Integer.parseInt(left), Integer.parseInt(right));
        }
        if (leftNumeric) {
            return -1;
        }
        if (rightNumeric) {
            return 1;
        }
        if (left.equals("default")) {
            return right.equals("default") ? 0 : 1;
        }
        if (right.equals("default")) {
            return -1;
        }
        return left.compareTo(right);
    }

    private static void appendHeaders(
            StringBuilder out,
            JsonNode headers,
            Map<String, String> schemaAnchors) {
        if (!headers.isObject() || headers.isEmpty()) {
            return;
        }
        List<String> names = new ArrayList<>();
        headers.fieldNames().forEachRemaining(names::add);
        names.sort(String::compareTo);
        appendLine(out, "");
        appendLine(out, "#### Headers");
        appendLine(out, "| Name | Schema | Description |");
        appendLine(out, "| --- | --- | --- |");
        for (String name : names) {
            JsonNode header = headers.path(name);
            appendLine(out, "| `" + markdownCell(name) + "` | " + schemaCell(header.path("schema"), schemaAnchors)
                    + " | " + markdownCellOrDash(header.get("description")) + " |");
        }
        for (String name : names) {
            JsonNode header = headers.path(name);
            appendFallback(out, "Header metadata: `" + name + "`", header,
                    Set.of("description", "schema"));
        }
    }

    private static void appendContent(
            StringBuilder out,
            JsonNode content,
            Map<String, String> schemaAnchors) {
        if (!content.isObject()) {
            return;
        }
        List<String> mediaTypes = new ArrayList<>();
        content.fieldNames().forEachRemaining(mediaTypes::add);
        mediaTypes.sort(String::compareTo);
        for (String mediaType : mediaTypes) {
            JsonNode media = content.path(mediaType);
            appendLine(out, "");
            appendLine(out, "Media type: `" + mediaType + "`");
            if (media.has("schema")) {
                appendLine(out, "Schema: " + schemaCell(media.path("schema"), schemaAnchors));
            }
            appendExamples(out, media);
            appendFallback(out, "Media type metadata: `" + mediaType + "`", media,
                    Set.of("schema", "example", "examples"));
        }
    }

    private static void appendExamples(StringBuilder out, JsonNode owner) {
        if (owner.has("example")) {
            appendExample(out, "#### Example", owner.path("example"));
        }
        JsonNode examples = owner.path("examples");
        if (!examples.isObject()) {
            return;
        }
        List<String> names = new ArrayList<>();
        examples.fieldNames().forEachRemaining(names::add);
        names.sort(String::compareTo);
        for (String name : names) {
            JsonNode example = examples.path(name);
            appendExample(out, "#### Example: " + name,
                    example.has("value") ? example.path("value") : example);
        }
    }

    private static void appendExample(StringBuilder out, String heading, JsonNode value) {
        appendLine(out, "");
        appendLine(out, heading);
        appendLine(out, "```json");
        appendLine(out, stableJson(value));
        appendLine(out, "```");
    }

    private static String schemaLabel(JsonNode schema, Map<String, String> schemaAnchors) {
        if (!schema.isObject()) {
            return "—";
        }
        if (hasComplexSchemaFields(schema)) {
            return stableJson(schema);
        }
        JsonNode ref = schema.get("$ref");
        if (ref != null && ref.isTextual() && ref.asText().startsWith("#/components/schemas/")) {
            String name = ref.asText().substring("#/components/schemas/".length());
            return "[" + name + "](openapi-schemas.md#" + schemaAnchors.getOrDefault(name, anchor(name)) + ")";
        }
        if (schema.path("type").asText().equals("array")) {
            return "array<" + schemaLabel(schema.path("items"), schemaAnchors) + ">";
        }
        if (schema.path("type").isTextual()) {
            String type = schema.path("type").asText();
            JsonNode format = schema.path("format");
            return format.isTextual() && !format.asText().isBlank()
                    ? type + " (" + format.asText() + ")"
                    : type;
        }
        return stableJson(schema);
    }

    private static boolean hasComplexSchemaFields(JsonNode schema) {
        java.util.Iterator<Map.Entry<String, JsonNode>> fields = schema.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (Set.of("oneOf", "anyOf", "allOf").contains(field.getKey())) {
                return true;
            }
            if (!field.getKey().equals("items")
                    && (field.getValue().isObject() || field.getValue().isArray())) {
                return true;
            }
        }
        return false;
    }

    private static String anchor(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 -]", "")
                .trim()
                .replaceAll("\\s+", "-");
    }

    private static Map<String, String> schemaAnchors(JsonNode schemas) {
        List<String> names = new ArrayList<>();
        schemas.fieldNames().forEachRemaining(names::add);
        names.sort(String::compareTo);
        Map<String, String> anchors = new HashMap<>();
        Set<String> used = new HashSet<>();
        for (String name : names) {
            String base = anchor(name);
            if (base.isBlank()) {
                base = "schema";
            }
            String candidate = base;
            int suffix = 2;
            while (!used.add(candidate)) {
                candidate = base + "-" + suffix++;
            }
            anchors.put(name, candidate);
        }
        return anchors;
    }

    private static String schemaCell(JsonNode schema, Map<String, String> schemaAnchors) {
        String label = schemaLabel(schema, schemaAnchors);
        return label.contains("](openapi-schemas.md#") ? label : "`" + markdownCell(label) + "`";
    }

    private static String markdownCellOrDash(JsonNode value) {
        return value != null && value.isTextual() && !value.asText().isBlank()
                ? markdownCell(value.asText())
                : "—";
    }

    private static String markdownCell(String value) {
        return value.replace("|", "\\|").replace("\n", "<br>").replace("\r", "");
    }

    private static void appendOptionalLine(StringBuilder markdown, JsonNode value) {
        if (value.isTextual() && !value.asText().isBlank()) {
            appendLine(markdown, value.asText());
        }
    }

    private static String renderTags(JsonNode tags) {
        if (!tags.isArray() || tags.size() == 0) {
            return "`untagged`";
        }
        List<String> rendered = new ArrayList<>();
        for (JsonNode tag : tags) {
            if (tag.isTextual()) {
                rendered.add("`" + tag.asText() + "`");
            }
        }
        rendered.sort(String::compareTo);
        return rendered.isEmpty() ? "`untagged`" : String.join(", ", rendered);
    }

    private static Set<String> pathItemFields() {
        Set<String> fields = new HashSet<>(METHOD_ORDER.keySet());
        fields.add("parameters");
        return fields;
    }

    private static Set<String> operationFields() {
        return Set.of(
                "tags",
                "summary",
                "description",
                "operationId",
                "security",
                "deprecated",
                "parameters",
                "requestBody",
                "responses");
    }

    private static void appendFallback(
            StringBuilder markdown,
            String heading,
            JsonNode source,
            Set<String> knownFields) {
        ObjectNode fallback = OBJECT_MAPPER.createObjectNode();
        source.fields().forEachRemaining(entry -> {
            if (!knownFields.contains(entry.getKey())) {
                fallback.set(entry.getKey(), entry.getValue());
            }
        });
        if (fallback.size() == 0) {
            return;
        }
        appendLine(markdown, "");
        appendLine(markdown, "#### " + heading);
        appendLine(markdown, "```json");
        appendLine(markdown, stableJson(fallback));
        appendLine(markdown, "```");
    }

    private static String stableJson(JsonNode value) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(sortedCopy(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to render OpenAPI JSON", exception);
        }
    }

    private static JsonNode sortedCopy(JsonNode value) {
        if (value.isObject()) {
            ObjectNode copy = OBJECT_MAPPER.createObjectNode();
            List<String> names = new ArrayList<>();
            value.fieldNames().forEachRemaining(names::add);
            names.sort(String::compareTo);
            for (String name : names) {
                copy.set(name, sortedCopy(value.get(name)));
            }
            return copy;
        }
        if (value.isArray()) {
            ArrayNode copy = OBJECT_MAPPER.createArrayNode();
            for (JsonNode item : value) {
                copy.add(sortedCopy(item));
            }
            return copy;
        }
        return value;
    }

    private static String renderSecurity(JsonNode security) {
        if (!security.isArray() || security.size() == 0) {
            return "Not specified in OpenAPI";
        }
        List<String> requirements = new ArrayList<>();
        for (JsonNode requirement : security) {
            if (!requirement.isObject()) {
                throw new IllegalArgumentException("OpenAPI security requirement must be an object");
            }
            List<String> schemes = new ArrayList<>();
            requirement.fields().forEachRemaining(entry -> {
                List<String> scopes = new ArrayList<>();
                if (entry.getValue().isArray()) {
                    entry.getValue().forEach(scope -> scopes.add(scope.asText()));
                }
                scopes.sort(String::compareTo);
                schemes.add("`" + entry.getKey() + "`"
                        + (scopes.isEmpty() ? "" : " (" + String.join(", ", scopes) + ")"));
            });
            schemes.sort(String::compareTo);
            requirements.add(String.join(", ", schemes));
        }
        return String.join(" OR ", requirements);
    }

    private static String renderSchemas(
            JsonNode openApi,
            String sourcePath,
            String updateCommand,
            JsonNode schemas,
            Map<String, String> schemaAnchors) {
        StringBuilder markdown = new StringBuilder();
        appendLine(markdown, "# " + infoText(openApi, "title") + " Schemas");
        appendLine(markdown, "");
        appendLine(markdown, "> This file is generated. Do not edit directly.");
        appendLine(markdown, "> Source: `" + sourcePath + "`");
        appendLine(markdown, "> Regenerate with: `" + updateCommand + "`");
        appendLine(markdown, "");
        appendLine(markdown, "Version: `" + infoText(openApi, "version") + "`");
        appendLine(markdown, "Schemas: **" + schemas.size() + "**");
        List<String> names = new ArrayList<>();
        schemas.fieldNames().forEachRemaining(names::add);
        names.sort(String::compareTo);
        for (String name : names) {
            appendSchema(markdown, name, schemas.path(name), schemaAnchors);
        }
        return finish(markdown);
    }

    private static void appendSchema(
            StringBuilder out,
            String name,
            JsonNode schema,
            Map<String, String> schemaAnchors) {
        appendLine(out, "");
        appendLine(out, "<a id=\"" + schemaAnchors.get(name) + "\"></a>");
        appendLine(out, "## " + name);
        if (!schema.isObject()) {
            appendJsonBlock(out, "### Schema fallback", schema);
            return;
        }
        appendOptionalLine(out, schema.path("description"));
        appendLine(out, "Schema: " + schemaCell(schema, schemaAnchors));
        String constraints = constraintsLabel(schema);
        if (!constraints.equals("—")) {
            appendLine(out, "Schema constraints: " + constraints);
        }

        JsonNode required = schema.path("required");
        if (required.isArray() && !required.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (JsonNode property : required) {
                if (property.isTextual()) {
                    names.add(property.asText());
                }
            }
            names.sort(String::compareTo);
            if (!names.isEmpty()) {
                appendLine(out, "Required properties: `" + String.join("`, `", names) + "`");
            }
        }

        appendPropertyTable(out, schema.path("properties"), required, schemaAnchors);
        appendExplicitMetadata(out, "#### Explicit schema metadata", schema);
        appendComposition(out, "### Composition", schema);
        appendFallback(out, "Schema metadata", schema, schemaFields());
    }

    private static void appendPropertyTable(
            StringBuilder out,
            JsonNode properties,
            JsonNode required,
            Map<String, String> schemaAnchors) {
        if (!properties.isObject()) {
            if (!properties.isMissingNode()) {
                appendJsonBlock(out, "### Properties fallback", properties);
            }
            return;
        }
        if (properties.isEmpty()) {
            return;
        }
        Set<String> requiredNames = new HashSet<>();
        if (required.isArray()) {
            for (JsonNode property : required) {
                if (property.isTextual()) {
                    requiredNames.add(property.asText());
                }
            }
        }
        List<String> names = new ArrayList<>();
        properties.fieldNames().forEachRemaining(names::add);
        names.sort(String::compareTo);
        appendLine(out, "");
        appendLine(out, "### Properties");
        appendLine(out, "| Property | Required | Schema | Description | Constraints |");
        appendLine(out, "| --- | --- | --- | --- | --- |");
        for (String name : names) {
            JsonNode property = properties.path(name);
            appendLine(out, "| `" + markdownCell(name) + "` | "
                    + (requiredNames.contains(name) ? "yes" : "no") + " | "
                    + propertySchemaCell(property, schemaAnchors) + " | "
                    + markdownCellOrDash(property.get("description")) + " | "
                    + constraintsLabel(property) + " |");
        }
        for (String name : names) {
            JsonNode property = properties.path(name);
            if (!property.isObject()) {
                appendJsonBlock(out, "#### Property schema fallback: `" + name + "`", property);
                continue;
            }
            appendExplicitMetadata(out, "#### Property metadata: `" + name + "`", property);
            appendComposition(out, "#### Property composition: `" + name + "`", property);
            appendFallback(out, "Property metadata: `" + name + "`", property, propertyFields());
        }
    }

    private static String constraintsLabel(JsonNode schema) {
        List<String> labels = new ArrayList<>();
        for (String field : List.of(
                "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "multipleOf",
                "minLength", "maxLength", "minItems", "maxItems", "minProperties", "maxProperties")) {
            if (schema.has(field)) {
                labels.add(field + "=" + stableJson(schema.path(field)));
            }
        }
        if (schema.has("uniqueItems")) {
            labels.add("uniqueItems=" + schema.path("uniqueItems").asBoolean());
        }
        if (schema.has("pattern")) {
            labels.add("pattern=`" + markdownCell(schema.path("pattern").asText()) + "`");
        }
        for (String field : List.of("nullable", "readOnly", "writeOnly", "deprecated")) {
            if (schema.path(field).asBoolean(false)) {
                labels.add(field + "=true");
            }
        }
        return labels.isEmpty() ? "—" : String.join(", ", labels);
    }

    private static String propertySchemaCell(JsonNode schema, Map<String, String> schemaAnchors) {
        if (schema.has("oneOf") || schema.has("anyOf") || schema.has("allOf")
                || schema.has("additionalProperties")) {
            return "`composition`";
        }
        JsonNode ref = schema.get("$ref");
        if (ref != null && ref.isTextual() && ref.asText().startsWith("#/components/schemas/")) {
            String name = ref.asText().substring("#/components/schemas/".length());
            return "[" + name + "](openapi-schemas.md#" + schemaAnchors.getOrDefault(name, anchor(name)) + ")";
        }
        if (schema.path("type").asText().equals("array")) {
            return "`array<" + markdownCell(schemaLabel(schema.path("items"), schemaAnchors)) + ">`";
        }
        if (schema.path("type").isTextual()) {
            String type = schema.path("type").asText();
            JsonNode format = schema.path("format");
            String label = format.isTextual() && !format.asText().isBlank()
                    ? type + " (" + format.asText() + ")"
                    : type;
            return "`" + markdownCell(label) + "`";
        }
        return schemaCell(schema, schemaAnchors);
    }

    private static void appendExplicitMetadata(StringBuilder out, String heading, JsonNode property) {
        List<String> lines = new ArrayList<>();
        if (property.has("title")) {
            lines.add("- Title: `" + titleLabel(property.path("title")) + "`");
        }
        if (property.has("default")) {
            lines.add("- Default: `" + stableJson(property.path("default")) + "`");
        }
        if (property.has("example")) {
            lines.add("- Example: `" + stableJson(property.path("example")) + "`");
        }
        JsonNode values = property.path("enum");
        if (values.isArray()) {
            List<String> rendered = new ArrayList<>();
            for (JsonNode value : values) {
                rendered.add("`" + (value.isTextual() ? value.asText() : stableJson(value)) + "`");
            }
            lines.add("- Enum: " + String.join(", ", rendered));
        }
        if (property.has("const")) {
            lines.add("- Const: `" + stableJson(property.path("const")) + "`");
        }
        for (String field : List.of("nullable", "readOnly", "writeOnly", "deprecated")) {
            if (property.has(field)) {
                lines.add("- " + field + ": `" + property.path(field).asBoolean() + "`");
            }
        }
        JsonNode examples = property.get("examples");
        if (lines.isEmpty() && examples == null) {
            return;
        }
        appendLine(out, "");
        appendLine(out, heading);
        for (String line : lines) {
            appendLine(out, line);
        }
        if (examples != null) {
            appendLine(out, "- Examples:");
            appendLine(out, "```json");
            appendLine(out, stableJson(examples));
            appendLine(out, "```");
        }
    }

    private static void appendComposition(StringBuilder out, String heading, JsonNode schema) {
        boolean hasComposition = schema.has("oneOf")
                || schema.has("anyOf")
                || schema.has("allOf")
                || schema.has("discriminator")
                || schema.has("additionalProperties");
        if (!hasComposition) {
            return;
        }
        appendLine(out, "");
        appendLine(out, heading);
        List<String> kinds = new ArrayList<>();
        for (String field : List.of("oneOf", "anyOf", "allOf", "discriminator", "additionalProperties")) {
            if (schema.has(field)) {
                kinds.add("`" + field + "`");
            }
        }
        appendLine(out, "Includes: " + String.join(", ", kinds));
        appendLine(out, "```json");
        appendLine(out, stableJson(schema));
        appendLine(out, "```");
    }

    private static String titleLabel(JsonNode title) {
        return title.isTextual() ? markdownCell(title.asText()) : stableJson(title);
    }

    private static void appendJsonBlock(StringBuilder out, String heading, JsonNode value) {
        appendLine(out, "");
        appendLine(out, heading);
        appendLine(out, "```json");
        appendLine(out, stableJson(value));
        appendLine(out, "```");
    }

    private static Set<String> schemaFields() {
        return Set.of(
                "$ref", "type", "format", "description", "title", "required", "properties", "items",
                "oneOf", "anyOf", "allOf", "discriminator", "additionalProperties",
                "default", "example", "examples", "enum", "const", "nullable", "readOnly", "writeOnly",
                "deprecated", "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "multipleOf",
                "minLength", "maxLength", "pattern", "minItems", "maxItems", "uniqueItems",
                "minProperties", "maxProperties");
    }

    private static Set<String> propertyFields() {
        return schemaFields();
    }

    private static String infoText(JsonNode openApi, String field) {
        JsonNode value = openApi.path("info").path(field);
        return value.isTextual() ? value.asText() : "";
    }

    private static String textOrNone(JsonNode value) {
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : "None";
    }

    private static void appendLine(StringBuilder markdown, String line) {
        markdown.append(line).append('\n');
    }

    private static String finish(StringBuilder markdown) {
        return markdown.toString().stripTrailing() + "\n";
    }
}
