package com.example.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("KBO Platform API")
                        .version("1.0")
                        .description("KBO League 플랫폼 REST API 문서. " +
                                "JWT 인증이 필요한 엔드포인트는 우측 상단 Authorize 버튼에서 Bearer 토큰을 입력하세요.")
                        .contact(new Contact()
                                .name("BEGA Team")
                                .url("https://www.begabaseball.xyz")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .name("bearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT 액세스 토큰을 입력하세요 (Bearer 접두사 없이)")));
    }

    @Bean
    public OpenApiCustomizer predictionBootstrapOpenApiCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }

            Map<String, Schema> schemas = openApi.getComponents().getSchemas();

            // Springdoc does not reliably emit nullable ref/record fields under OpenAPI 3.1.
            replaceProperty(schemas, "PredictionBootstrapResponseDto", "selectedGameId", nullablePrimitive("string", null));
            replaceProperty(schemas, "PredictionBootstrapResponseDto", "detail",
                    nullableRef("PredictionBootstrapResourceDtoGameDetailDto"));
            replaceProperty(schemas, "PredictionBootstrapResponseDto", "voteStatus",
                    nullableRef("PredictionBootstrapResourceDtoPredictionResponseDto"));

            replaceProperty(schemas, "PredictionBootstrapResourceDtoGameDetailDto", "data", nullableRef("GameDetailDto"));
            replaceProperty(schemas, "PredictionBootstrapResourceDtoGameDetailDto", "error",
                    nullableRef("PredictionBootstrapErrorDto"));
            replaceProperty(schemas, "PredictionBootstrapResourceDtoPredictionResponseDto", "data",
                    nullableRef("PredictionResponseDto"));
            replaceProperty(schemas, "PredictionBootstrapResourceDtoPredictionResponseDto", "error",
                    nullableRef("PredictionBootstrapErrorDto"));

            replaceProperty(schemas, "PredictionBootstrapErrorDto", "status", nullablePrimitive("integer", "int32"));
            replaceProperty(schemas, "PredictionBootstrapErrorDto", "code", nullablePrimitive("string", null));

            replaceProperty(schemas, "RankingPredictionInitDto", "saved", nullableRef("RankingPredictionResponseDto"));
            replaceProperty(schemas, "RankingPredictionResponseDto", "shareId", nullablePrimitive("string", null));
            replaceProperty(schemas, "TeamRankingDetail", "currentRank", nullablePrimitive("integer", "int32"));
            replaceProperty(schemas, "TeamRankingDetail", "lastSeasonRank", nullablePrimitive("integer", "int32"));
            replaceProperty(schemas, "PredictionMyVoteEntryDto", "votedTeam", nullableEnum("home", "away"));
            replaceProperty(schemas, "PredictionStatsResponseDto", "data", nullableRef("UserPredictionStatsDto"));
            replaceProperty(schemas, "PredictionStatsResponseDto", "code", nullablePrimitive("string", null));
            replaceProperty(schemas, "PredictionStatsResponseDto", "errors", nullableStringMap());
            removeProperty(schemas, "HomeRankingSnapshotDto", "offSeason");
            ensureMatchesRangePageSchema(schemas);
            replaceMatchesRangeResponse(openApi);
        };
    }

    private static void replaceProperty(
            Map<String, Schema> schemas,
            String schemaName,
            String propertyName,
            Schema<?> propertySchema) {
        Schema<?> schema = schemas.get(schemaName);
        if (schema == null || schema.getProperties() == null || !schema.getProperties().containsKey(propertyName)) {
            return;
        }
        schema.getProperties().put(propertyName, propertySchema);
    }

    private static void removeProperty(Map<String, Schema> schemas, String schemaName, String propertyName) {
        Schema<?> schema = schemas.get(schemaName);
        if (schema == null || schema.getProperties() == null) {
            return;
        }
        schema.getProperties().remove(propertyName);
        if (schema.getRequired() != null) {
            schema.getRequired().remove(propertyName);
        }
    }

    private static void replaceMatchesRangeResponse(OpenAPI openApi) {
        if (openApi.getPaths() == null) {
            return;
        }
        PathItem pathItem = openApi.getPaths().get("/api/matches/range");
        if (pathItem == null || pathItem.getGet() == null || pathItem.getGet().getResponses() == null) {
            return;
        }
        ApiResponse response = pathItem.getGet().getResponses().get("200");
        if (response == null || response.getContent() == null) {
            return;
        }

        MediaType mediaType = response.getContent().get("*/*");
        if (mediaType == null) {
            mediaType = response.getContent().get("application/json");
        }
        if (mediaType == null) {
            return;
        }

        mediaType.schema(new Schema<>()
                .oneOf(List.of(
                        new ArraySchema().items(new Schema<>().$ref("#/components/schemas/MatchDto")),
                        new Schema<>().$ref("#/components/schemas/MatchRangePageResponseDto"))));
    }

    private static void ensureMatchesRangePageSchema(Map<String, Schema> schemas) {
        if (schemas.containsKey("MatchRangePageResponseDto")) {
            return;
        }

        Schema<?> schema = new Schema<>()
                .type("object")
                .addProperty("content",
                        new ArraySchema().items(new Schema<>().$ref("#/components/schemas/MatchDto")))
                .addProperty("page", integerSchema("int32"))
                .addProperty("size", integerSchema("int32"))
                .addProperty("totalElements", integerSchema("int64"))
                .addProperty("totalPages", integerSchema("int32"))
                .addProperty("hasNext", primitiveSchema("boolean", null))
                .addProperty("hasPrevious", primitiveSchema("boolean", null));
        schema.required(List.of(
                "content",
                "page",
                "size",
                "totalElements",
                "totalPages",
                "hasNext",
                "hasPrevious"));
        schemas.put("MatchRangePageResponseDto", schema);
    }

    private static Schema<?> integerSchema(String format) {
        return primitiveSchema("integer", format);
    }

    private static Schema<?> primitiveSchema(String type, String format) {
        Schema<?> schema = new Schema<>()
                .types(new LinkedHashSet<>(List.of(type)));
        if (format != null) {
            schema.format(format);
        }
        return schema;
    }

    private static Schema<?> nullableEnum(String... values) {
        Schema<String> enumSchema = new Schema<String>()
                .types(new LinkedHashSet<>(List.of("string")));
        for (String value : values) {
            enumSchema.addEnumItemObject(value);
        }
        return new Schema<>()
                .oneOf(List.of(
                        enumSchema,
                        nullSchema()));
    }

    private static Schema<?> nullableStringMap() {
        return new Schema<>()
                .oneOf(List.of(
                        new Schema<>()
                                .types(new LinkedHashSet<>(List.of("object")))
                                .additionalProperties(primitiveSchema("string", null)),
                        nullSchema()));
    }

    private static Schema<?> nullableRef(String schemaName) {
        return new Schema<>()
                .oneOf(List.of(
                        new Schema<>().$ref("#/components/schemas/" + schemaName),
                        nullSchema()));
    }

    private static Schema<?> nullablePrimitive(String type, String format) {
        Schema<?> schema = new Schema<>()
                .types(new LinkedHashSet<>(List.of(type, "null")));
        if (format != null) {
            schema.format(format);
        }
        return schema;
    }

    private static Schema<?> nullSchema() {
        return new Schema<>()
                .types(new LinkedHashSet<>(List.of("null")));
    }
}
