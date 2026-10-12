package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.example.BegaDiary.Entity.SeatViewPhoto;
import com.example.BegaDiary.Entity.SeatViewPhoto.ClassificationLabel;
import com.example.BegaDiary.Entity.SeatViewPhoto.ModerationStatus;
import com.example.BegaDiary.Entity.SeatViewPhoto.SourceType;
import com.example.BegaDiary.Repository.SeatViewPhotoRepository;
import com.example.cheerboard.storage.service.ImageService;
import com.example.media.entity.MediaAsset;
import com.example.media.entity.MediaAssetLink;
import com.example.media.entity.MediaAssetStatus;
import com.example.media.entity.MediaDomain;
import com.example.media.entity.MediaLinkRole;
import com.example.media.repository.MediaAssetLinkRepository;
import com.example.media.repository.MediaAssetRepository;
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
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import reactor.core.publisher.Mono;

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
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy securityFilterChain;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SeatViewPhotoRepository seatViewPhotoRepository;

    @Autowired
    private MediaAssetRepository mediaAssetRepository;

    @Autowired
    private MediaAssetLinkRepository mediaAssetLinkRepository;

    @MockitoBean
    private ImageService imageService;

    @Test
    @Transactional
    void standaloneSeatViewCanBeStoredAndReadByPublicAndAdminQueries() {
        SeatViewPhoto saved = seatViewPhotoRepository.saveAndFlush(SeatViewPhoto.builder()
                .diary(null)
                .userId(77L)
                .storagePath("media/seat-view/77/31.webp")
                .sourceType(SourceType.SEATMAP_UPLOAD)
                .stadium("잠실")
                .section("205블록")
                .rating(5)
                .userSelected(true)
                .moderationStatus(ModerationStatus.APPROVED)
                .adminLabel(ClassificationLabel.SEAT_VIEW)
                .rewardGranted(false)
                .build());
        SeatViewPhoto firstPending = seatViewPhotoRepository.saveAndFlush(SeatViewPhoto.builder()
                .userId(78L)
                .storagePath("media/seat-view/78/32.webp")
                .sourceType(SourceType.SEATMAP_UPLOAD)
                .stadium("잠실")
                .userSelected(true)
                .moderationStatus(ModerationStatus.PENDING)
                .rewardGranted(false)
                .build());
        SeatViewPhoto newestPending = seatViewPhotoRepository.saveAndFlush(SeatViewPhoto.builder()
                .userId(79L)
                .storagePath("media/seat-view/79/33.webp")
                .sourceType(SourceType.SEATMAP_UPLOAD)
                .stadium("잠실")
                .userSelected(true)
                .moderationStatus(ModerationStatus.PENDING)
                .rewardGranted(false)
                .build());

        assertThat(seatViewPhotoRepository.findApprovedPublicSeatViews(
                "잠실", "205블록", ModerationStatus.APPROVED, ClassificationLabel.SEAT_VIEW,
                PageRequest.of(0, 10)))
                .extracting(SeatViewPhoto::getId)
                .containsExactly(saved.getId());
        var adminFirstPage = seatViewPhotoRepository.findForAdmin(
                null, "잠실", null, null, false, ModerationStatus.PENDING, PageRequest.of(0, 1));
        var adminSecondPage = seatViewPhotoRepository.findForAdmin(
                null, "잠실", null, null, false, ModerationStatus.PENDING, PageRequest.of(1, 1));
        var adminThirdPage = seatViewPhotoRepository.findForAdmin(
                null, "잠실", null, null, false, ModerationStatus.PENDING, PageRequest.of(2, 1));

        assertThat(adminFirstPage.getTotalElements()).isEqualTo(3);
        assertThat(adminFirstPage.getContent()).extracting(SeatViewPhoto::getId).containsExactly(newestPending.getId());
        assertThat(adminSecondPage.getContent()).extracting(SeatViewPhoto::getId).containsExactly(firstPending.getId());
        assertThat(adminThirdPage.getContent()).extracting(SeatViewPhoto::getId).containsExactly(saved.getId());
    }

    @Test
    void publicSeatViewGalleryCanBeReadWithoutAuthenticationThroughSecurityFilterChain() throws Exception {
        MockMvc securedMockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .addFilters(securityFilterChain)
                .build();

        securedMockMvc.perform(get("/api/diary/seat-views").param("stadium", "잠실"))
                .andExpect(status().isOk());
    }

    @Test
    @Transactional
    void standaloneSeatViewPostPersistsPendingSubmissionAndLinksOwnedFinalizedAsset() throws Exception {
        String storagePath = "media/seat-view/77/openapi-fixture.webp";
        MediaAsset asset = mediaAssetRepository.saveAndFlush(MediaAsset.builder()
                .ownerUserId(77L)
                .domain(MediaDomain.SEAT_VIEW)
                .status(MediaAssetStatus.READY)
                .originalFileName("seat.webp")
                .declaredContentType("image/webp")
                .declaredBytes(512L)
                .declaredWidth(1280)
                .declaredHeight(720)
                .stagingObjectKey("media/staging/seat-view/77/openapi-fixture.webp")
                .objectKey(storagePath)
                .storedContentType("image/webp")
                .storedBytes(512L)
                .storedWidth(1280)
                .storedHeight(720)
                .uploadExpiresAt(LocalDateTime.now().plusHours(1))
                .build());
        when(imageService.getSeatViewImageSignedUrls(
                eq(List.of(storagePath)), eq(77L), anyLong(), eq(300)))
                .thenReturn(Mono.just(List.of("https://signed.invalid/seat-view")));

        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new TestingAuthenticationToken(77L, null, "ROLE_USER"));
        SecurityContextHolder.setContext(securityContext);
        String response;
        try {
            response = mockMvc.perform(post("/api/seat-views")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "storagePath", storagePath,
                                    "stadium", "잠실",
                                    "section", "205블록",
                                    "block", "205",
                                    "seatRow", "10열",
                                    "seatNumber", "12번",
                                    "rating", 5,
                                    "comment", "전광판이 잘 보여요",
                                    "tags", List.of("전광판 잘 보임")))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.moderationStatus").value("PENDING"))
                    .andExpect(jsonPath("$.data.sourceType").value("SEATMAP_UPLOAD"))
                    .andExpect(jsonPath("$.data.photoUrl").value(org.hamcrest.Matchers.nullValue()))
                    .andReturn()
                    .getResponse()
                    .getContentAsString(StandardCharsets.UTF_8);
            long seatViewId = objectMapper.readTree(response).path("data").path("id").asLong();
            mockMvc.perform(get("/api/seat-views/{seatViewId}/preview", seatViewId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.photoUrl").value("https://signed.invalid/seat-view"));
        } finally {
            SecurityContextHolder.clearContext();
        }

        long seatViewId = objectMapper.readTree(response).path("data").path("id").asLong();
        SeatViewPhoto saved = seatViewPhotoRepository.findById(seatViewId).orElseThrow();
        assertThat(saved.getDiary()).isNull();
        assertThat(saved.getUserId()).isEqualTo(77L);
        assertThat(saved.getRating()).isEqualTo(5);
        assertThat(saved.getTagsJson()).isEqualTo("[\"전광판 잘 보임\"]");
        assertThat(seatViewPhotoRepository.findApprovedPublicSeatViews(
                "잠실", "205블록", ModerationStatus.APPROVED, ClassificationLabel.SEAT_VIEW,
                PageRequest.of(0, 10))).isEmpty();
        MediaAssetLink link = mediaAssetLinkRepository.findById(asset.getId()).orElseThrow();
        assertThat(link.getDomain()).isEqualTo(MediaDomain.SEAT_VIEW);
        assertThat(link.getEntityId()).isEqualTo(seatViewId);
        assertThat(link.getRole()).isEqualTo(MediaLinkRole.SEAT_VIEW_PHOTO);
    }

    @Test
    void standaloneSeatViewPostRejectsAnotherUsersAssetWithoutPersistingSubmissionOrLink() throws Exception {
        String storagePath = "media/seat-view/99/foreign-fixture.webp";
        MediaAsset otherUsersAsset = mediaAssetRepository.saveAndFlush(MediaAsset.builder()
                .ownerUserId(99L)
                .domain(MediaDomain.SEAT_VIEW)
                .status(MediaAssetStatus.READY)
                .originalFileName("seat.webp")
                .declaredContentType("image/webp")
                .declaredBytes(512L)
                .declaredWidth(1280)
                .declaredHeight(720)
                .stagingObjectKey("media/staging/seat-view/99/foreign-fixture.webp")
                .objectKey(storagePath)
                .storedContentType("image/webp")
                .storedBytes(512L)
                .storedWidth(1280)
                .storedHeight(720)
                .uploadExpiresAt(LocalDateTime.now().plusHours(1))
                .build());
        long seatViewCountBefore = seatViewPhotoRepository.count();

        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new TestingAuthenticationToken(77L, null, "ROLE_USER"));
        SecurityContextHolder.setContext(securityContext);
        try {
            mockMvc.perform(post("/api/seat-views")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "storagePath", storagePath,
                                    "stadium", "잠실",
                                    "rating", 5,
                                    "tags", List.of()))))
                    .andExpect(status().isBadRequest());
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThat(seatViewPhotoRepository.count()).isEqualTo(seatViewCountBefore);
        assertThat(mediaAssetLinkRepository.findById(otherUsersAsset.getId())).isEmpty();
    }

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
        assertOperation(runtimeContract, "/api/admin/mate-link-reconciliation-cases", "get");
        assertOperation(runtimeContract, "/api/admin/payment-intents/reconciliation/{intentId}/resolution", "post");
        assertThat(runtimeContract.path("paths")
                .path("/api/admin/payment-intents/reconciliation/{intentId}/resolution")
                .path("post").path("description").asText())
                .contains("does not bind a payment key", "trusted provider proof is still required");
        assertOperation(runtimeContract, "/api/seat-views", "post");
        assertThat(runtimeContract.path("paths").path("/api/seat-views").path("post")
                .path("requestBody").path("required").asBoolean()).isTrue();
        assertThat(runtimeContract.path("paths").path("/api/seat-views").path("post")
                .path("requestBody").path("content").path("application/json").path("schema")
                .path("$ref").asText()).isEqualTo("#/components/schemas/SeatViewSubmissionRequest");
        assertThat(runtimeContract.path("components").path("schemas").path("InitMediaUploadRequest")
                .path("properties").path("domain").path("enum").toString()).contains("SEAT_VIEW");
        assertThat(runtimeContract.path("paths").path("/api/notifications/mark-all-read")
                .path("post").path("requestBody").path("required").asBoolean()).isTrue();
        assertThat(runtimeContract.path("paths").path("/api/notifications/mark-all-read")
                .path("post").path("responses").path("200").path("content").path("*/*")
                .path("schema").path("$ref").asText()).isEqualTo("#/components/schemas/MarkAllAsReadResponse");
        JsonNode markAllAsReadRequest = runtimeContract.path("components").path("schemas")
                .path("MarkAllAsReadRequest");
        assertThat(markAllAsReadRequest.path("required").toString()).contains("notificationIds")
                .doesNotContain("readThrough");
        assertThat(markAllAsReadRequest.path("properties").has("readThrough")).isFalse();
        assertThat(runtimeContract.path("paths").path("/api/notifications/my")
                .path("get").path("responses").path("200").path("headers")
                .has("X-Server-Time")).isFalse();
        JsonNode schemas = runtimeContract.path("components").path("schemas");
        assertThat(schemas.path("PaymentIntentReconciliationResolutionResultDto")
                .path("properties").path("awaitingTrustedProviderProof").path("description").asText())
                .contains("trusted provider proof");
        assertThat(schemas.path("PaymentIntentReconciliationResolutionRequest")
                .path("properties").path("evidenceReference").path("description").asText())
                .contains("does not dereference or verify");
        assertThat(schemas.path("ApiResponsePageAdminSeatViewDto").path("properties").path("data")
                .path("$ref").asText()).isEqualTo("#/components/schemas/PageAdminSeatViewDto");
        assertThat(schemas.path("PageAdminSeatViewDto").path("properties").path("content")
                .path("items").path("$ref").asText()).isEqualTo("#/components/schemas/AdminSeatViewDto");
        assertThat(schemas.path("ApiResponsePageAuditLogDto").path("properties").path("data")
                .path("$ref").asText()).isEqualTo("#/components/schemas/PageAuditLogDto");
        assertThat(schemas.path("PageAuditLogDto").path("properties").path("content")
                .path("items").path("$ref").asText()).isEqualTo("#/components/schemas/AuditLogDto");
        assertThat(schemas.path("AdminSeatViewDto").path("properties").path("diaryId")
                .path("type").toString()).contains("null");
        assertThat(schemas.path("AdminSeatViewDto").path("properties").path("rating")
                .path("type").toString()).contains("null");
        assertThat(schemas.path("AdminSeatViewDto").path("properties").path("comment")
                .path("type").toString()).contains("null");
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
