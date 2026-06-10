package com.example.prediction;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.common.config.SwaggerConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class PredictionOpenApiContractTest {

    @Test
    void predictionEndpointsExposeConcreteResponseTypesForOpenApi() throws Exception {
        assertConcreteResponseType(
                PredictionController.class.getMethod("getMatchDetail", String.class),
                GameDetailDto.class);
        assertConcreteResponseType(
                PredictionController.class.getMethod("getVoteStatus", String.class),
                PredictionResponseDto.class);
        assertConcreteResponseType(
                PredictionController.class.getMethod("getMyVotesBulk", PredictionMyVotesRequestDto.class, Principal.class),
                PredictionMyVotesResponseDto.class);
        assertConcreteResponseType(
                RankingPredictionController.class.getMethod("getCurrentSeason"),
                RankingPredictionCurrentSeasonDto.class);
        assertConcreteResponseType(
                RankingPredictionController.class.getMethod("savePrediction", Principal.class, RankingPredictionRequestDto.class),
                RankingPredictionResponseDto.class);
        assertConcreteResponseType(
                RankingPredictionController.class.getMethod("getSharedPrediction", String.class, int.class),
                RankingPredictionResponseDto.class);
        assertConcreteResponseType(
                PredictionController.class.getMethod("getMyStats", Principal.class),
                PredictionStatsResponseDto.class);
    }

    @Test
    void predictionBootstrapSchemaMarksRequiredAndNullableFields() {
        assertRequiredComponent(PredictionBootstrapResponseDto.class, "schedule");
        assertRequiredComponent(PredictionBootstrapResponseDto.class, "selectedGameFound");
        assertNullableComponent(PredictionBootstrapResponseDto.class, "selectedGameId");
        assertNullableComponent(PredictionBootstrapResponseDto.class, "detail");
        assertNullableComponent(PredictionBootstrapResponseDto.class, "voteStatus");
        assertRequiredComponent(PredictionBootstrapResourceDto.class, "ok");
        assertNullableComponent(PredictionBootstrapResourceDto.class, "data");
        assertNullableComponent(PredictionBootstrapResourceDto.class, "error");
        assertRequiredComponent(PredictionBootstrapErrorDto.class, "message");
        assertNullableComponent(PredictionBootstrapErrorDto.class, "status");
        assertNullableComponent(PredictionBootstrapErrorDto.class, "code");
    }

    @Test
    void predictionOpenApiCustomizerMarksRuntimeNullableProperties() {
        OpenAPI openApi = new OpenAPI().components(new Components());
        addSchema(openApi, "PredictionBootstrapResponseDto", "selectedGameId", "detail", "voteStatus");
        addSchema(openApi, "PredictionBootstrapResourceDtoGameDetailDto", "data", "error");
        addSchema(openApi, "PredictionBootstrapResourceDtoPredictionResponseDto", "data", "error");
        addSchema(openApi, "PredictionBootstrapErrorDto", "status", "code");
        addSchema(openApi, "RankingPredictionInitDto", "saved");
        addSchema(openApi, "RankingPredictionResponseDto", "shareId");
        addSchema(openApi, "TeamRankingDetail", "currentRank", "lastSeasonRank");
        addSchema(openApi, "PredictionMyVoteEntryDto", "gameId", "votedTeam");
        addSchema(openApi, "PredictionStatsResponseDto", "success", "message", "data", "code", "errors");
        addSchema(openApi, "HomeRankingSnapshotDto", "rankingSeasonYear", "rankingSourceMessage", "rankings",
                "offSeason", "isOffSeason");
        addMatchesRangePath(openApi);

        new SwaggerConfig().predictionBootstrapOpenApiCustomizer().customise(openApi);

        assertNullablePrimitive(openApi, "PredictionBootstrapResponseDto", "selectedGameId", "string", null);
        assertNullableRef(openApi, "PredictionBootstrapResponseDto", "detail",
                "PredictionBootstrapResourceDtoGameDetailDto");
        assertNullableRef(openApi, "PredictionBootstrapResponseDto", "voteStatus",
                "PredictionBootstrapResourceDtoPredictionResponseDto");
        assertNullableRef(openApi, "PredictionBootstrapResourceDtoGameDetailDto", "data", "GameDetailDto");
        assertNullableRef(openApi, "PredictionBootstrapResourceDtoGameDetailDto", "error",
                "PredictionBootstrapErrorDto");
        assertNullableRef(openApi, "PredictionBootstrapResourceDtoPredictionResponseDto", "data",
                "PredictionResponseDto");
        assertNullableRef(openApi, "PredictionBootstrapResourceDtoPredictionResponseDto", "error",
                "PredictionBootstrapErrorDto");
        assertNullablePrimitive(openApi, "PredictionBootstrapErrorDto", "status", "integer", "int32");
        assertNullablePrimitive(openApi, "PredictionBootstrapErrorDto", "code", "string", null);
        assertNullableRef(openApi, "RankingPredictionInitDto", "saved", "RankingPredictionResponseDto");
        assertNullablePrimitive(openApi, "RankingPredictionResponseDto", "shareId", "string", null);
        assertNullablePrimitive(openApi, "TeamRankingDetail", "currentRank", "integer", "int32");
        assertNullablePrimitive(openApi, "TeamRankingDetail", "lastSeasonRank", "integer", "int32");
        assertNullableEnum(openApi, "PredictionMyVoteEntryDto", "votedTeam", "home", "away");
        assertNullableRef(openApi, "PredictionStatsResponseDto", "data", "UserPredictionStatsDto");
        assertNullablePrimitive(openApi, "PredictionStatsResponseDto", "code", "string", null);
        assertNullableStringMap(openApi, "PredictionStatsResponseDto", "errors");
        assertThat(openApi.getComponents().getSchemas().get("HomeRankingSnapshotDto").getProperties())
                .doesNotContainKey("offSeason")
                .containsKey("isOffSeason");
        assertThat(openApi.getComponents().getSchemas()).containsKey("MatchRangePageResponseDto");
        assertThat(openApi.getComponents().getSchemas().get("MatchRangePageResponseDto").getRequired())
                .contains("content", "page", "size", "totalElements", "totalPages", "hasNext", "hasPrevious");
        assertMatchesRangeOneOf(openApi);
    }

    private static void assertConcreteResponseType(Method method, Class<?> expectedType) {
        assertThat(method.getGenericReturnType()).isInstanceOf(ParameterizedType.class);
        ParameterizedType responseType = (ParameterizedType) method.getGenericReturnType();
        assertThat(responseType.getRawType()).isEqualTo(ResponseEntity.class);
        Type bodyType = responseType.getActualTypeArguments()[0];
        assertThat(bodyType).isNotInstanceOf(WildcardType.class);
        assertThat(bodyType).isEqualTo(expectedType);
    }

    private static void assertRequiredComponent(Class<?> recordType, String name) {
        Schema schema = schemaFor(recordType, name);
        assertThat(schema.requiredMode()).isEqualTo(Schema.RequiredMode.REQUIRED);
    }

    private static void assertNullableComponent(Class<?> recordType, String name) {
        Schema schema = schemaFor(recordType, name);
        assertThat(schema.nullable()).isTrue();
    }

    private static Schema schemaFor(Class<?> recordType, String name) {
        for (RecordComponent component : recordType.getRecordComponents()) {
            if (component.getName().equals(name)) {
                Schema schema = findSchema(recordType, component);
                assertThat(schema).as("%s.%s @Schema", recordType.getSimpleName(), name).isNotNull();
                return schema;
            }
        }
        throw new AssertionError("Missing record component " + recordType.getSimpleName() + "." + name);
    }

    private static Schema findSchema(Class<?> recordType, RecordComponent component) {
        Schema componentSchema = component.getAnnotation(Schema.class);
        if (componentSchema != null) {
            return componentSchema;
        }
        Schema accessorSchema = component.getAccessor().getAnnotation(Schema.class);
        if (accessorSchema != null) {
            return accessorSchema;
        }
        try {
            return recordType.getDeclaredField(component.getName()).getAnnotation(Schema.class);
        } catch (NoSuchFieldException ex) {
            throw new AssertionError(ex);
        }
    }

    private static void addSchema(OpenAPI openApi, String schemaName, String... propertyNames) {
        io.swagger.v3.oas.models.media.Schema<?> schema = new io.swagger.v3.oas.models.media.Schema<>()
                .type("object");
        for (String propertyName : propertyNames) {
            schema.addProperty(propertyName, new io.swagger.v3.oas.models.media.Schema<>().type("object"));
        }
        openApi.getComponents().addSchemas(schemaName, schema);
    }

    private static void addMatchesRangePath(OpenAPI openApi) {
        openApi.path("/api/matches/range", new PathItem().get(new Operation()
                .responses(new ApiResponses()
                        .addApiResponse("200", new io.swagger.v3.oas.models.responses.ApiResponse()
                                .content(new Content()
                                        .addMediaType("*/*", new MediaType()
                                                .schema(new io.swagger.v3.oas.models.media.Schema<>()
                                                        .type("object"))))))));
    }

    private static void assertNullableRef(
            OpenAPI openApi,
            String schemaName,
            String propertyName,
            String refSchemaName) {
        io.swagger.v3.oas.models.media.Schema<?> property = modelProperty(openApi, schemaName, propertyName);
        assertThat(property.getOneOf()).hasSize(2);
        assertThat(property.getOneOf().getFirst().get$ref()).isEqualTo("#/components/schemas/" + refSchemaName);
        assertThat(property.getOneOf().get(1).getTypes()).containsExactly("null");
    }

    private static void assertNullablePrimitive(
            OpenAPI openApi,
            String schemaName,
            String propertyName,
            String type,
            String format) {
        io.swagger.v3.oas.models.media.Schema<?> property = modelProperty(openApi, schemaName, propertyName);
        assertThat(property.getTypes()).containsExactly(type, "null");
        assertThat(property.getFormat()).isEqualTo(format);
    }

    private static void assertNullableEnum(
            OpenAPI openApi,
            String schemaName,
            String propertyName,
            String... values) {
        io.swagger.v3.oas.models.media.Schema<?> property = modelProperty(openApi, schemaName, propertyName);
        assertThat(property.getOneOf()).hasSize(2);
        assertThat(property.getOneOf().getFirst().getTypes()).containsExactly("string");
        assertThat(property.getOneOf().getFirst().getEnum()).containsExactlyElementsOf(List.of(values));
        assertThat(property.getOneOf().get(1).getTypes()).containsExactly("null");
    }

    private static void assertNullableStringMap(OpenAPI openApi, String schemaName, String propertyName) {
        io.swagger.v3.oas.models.media.Schema<?> property = modelProperty(openApi, schemaName, propertyName);
        assertThat(property.getOneOf()).hasSize(2);
        io.swagger.v3.oas.models.media.Schema<?> mapSchema = property.getOneOf().getFirst();
        assertThat(mapSchema.getTypes()).containsExactly("object");
        Object additionalProperties = mapSchema.getAdditionalProperties();
        assertThat(additionalProperties).isInstanceOf(io.swagger.v3.oas.models.media.Schema.class);
        io.swagger.v3.oas.models.media.Schema<?> valueSchema =
                (io.swagger.v3.oas.models.media.Schema<?>) additionalProperties;
        assertThat(valueSchema.getTypes()).containsExactly("string");
        assertThat(property.getOneOf().get(1).getTypes()).containsExactly("null");
    }

    private static void assertMatchesRangeOneOf(OpenAPI openApi) {
        io.swagger.v3.oas.models.media.Schema<?> schema = openApi.getPaths()
                .get("/api/matches/range")
                .getGet()
                .getResponses()
                .get("200")
                .getContent()
                .get("*/*")
                .getSchema();

        assertThat(schema.getOneOf()).hasSize(2);
        assertThat(schema.getOneOf().get(0).getItems().get$ref()).isEqualTo("#/components/schemas/MatchDto");
        assertThat(schema.getOneOf().get(1).get$ref()).isEqualTo("#/components/schemas/MatchRangePageResponseDto");
    }

    private static io.swagger.v3.oas.models.media.Schema<?> modelProperty(
            OpenAPI openApi,
            String schemaName,
            String propertyName) {
        io.swagger.v3.oas.models.media.Schema<?> schema = openApi.getComponents().getSchemas().get(schemaName);
        assertThat(schema).as(schemaName).isNotNull();
        io.swagger.v3.oas.models.media.Schema<?> property =
                (io.swagger.v3.oas.models.media.Schema<?>) schema.getProperties().get(propertyName);
        assertThat(property).as("%s.%s", schemaName, propertyName).isNotNull();
        return property;
    }
}
