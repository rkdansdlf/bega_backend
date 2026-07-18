package com.example.common.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
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
        List<Operation> operations = collectOperations(paths);
        String endpointMarkdown = renderEndpoints(
                openApi, sourcePath, updateCommand, paths, operations);
        String schemaMarkdown = renderSchemas(
                openApi, sourcePath, updateCommand, schemas);
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
            List<Operation> operations) {
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
            appendOperation(markdown, operation);
        }
        return finish(markdown);
    }

    private static void appendOperation(StringBuilder markdown, Operation operation) {
        appendLine(markdown, "");
        appendLine(markdown, "### " + operation.method().toUpperCase(Locale.ROOT) + " `" + operation.path() + "`");
        appendOptionalLine(markdown, operation.node().path("summary"));
        appendOptionalLine(markdown, operation.node().path("description"));
        appendLine(markdown, "- Operation ID: `" + textOrNone(operation.node().path("operationId")) + "`");
        appendLine(markdown, "- Tags: " + renderTags(operation.node().path("tags")));
        appendLine(markdown, "- Security: " + renderSecurity(operation.node().path("security")));
        appendLine(markdown, "- Deprecated: " + (operation.node().path("deprecated").asBoolean(false) ? "yes" : "no"));
        appendFallback(markdown, "Path-item extensions and metadata", operation.pathItem(), pathItemFields());
        appendFallback(markdown, "Operation extensions and metadata", operation.node(), operationFields());
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
            JsonNode schemas) {
        StringBuilder markdown = new StringBuilder();
        appendLine(markdown, "# " + infoText(openApi, "title") + " Schemas");
        appendLine(markdown, "");
        appendLine(markdown, "> This file is generated. Do not edit directly.");
        appendLine(markdown, "> Source: `" + sourcePath + "`");
        appendLine(markdown, "> Regenerate with: `" + updateCommand + "`");
        appendLine(markdown, "");
        appendLine(markdown, "Version: `" + infoText(openApi, "version") + "`");
        appendLine(markdown, "Schemas: **" + schemas.size() + "**");
        return finish(markdown);
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
