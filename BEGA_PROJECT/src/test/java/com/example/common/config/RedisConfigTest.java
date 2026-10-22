package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cheerboard.dto.PostSummaryRes;
import com.example.homepage.FeaturedMateCardDto;
import com.example.homepage.HomePageTeamRankingDto;
import com.example.homepage.HomeRankingSnapshotDto;
import com.example.homepage.HomeWidgetsResponseDto;
import com.example.kbo.dto.TeamSummaryDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

class RedisConfigTest {

    private final RedisConfig redisConfig = new RedisConfig();

    @Test
    @DisplayName("Redis command and connection timeouts are both two seconds")
    void applicationRedisTimeoutsBindToTwoSeconds() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("src/main/resources/application.yml"));
        Properties applicationProperties = yaml.getObject();
        Map<String, Object> timeoutProperties = new HashMap<>();
        timeoutProperties.put("spring.data.redis.timeout", applicationProperties.getProperty("spring.data.redis.timeout"));
        String connectTimeout = applicationProperties.getProperty("spring.data.redis.connect-timeout");
        if (connectTimeout != null) {
            timeoutProperties.put("spring.data.redis.connect-timeout", connectTimeout);
        }
        DataRedisProperties properties = new Binder(new MapConfigurationPropertySource(timeoutProperties))
                .bind("spring.data.redis", Bindable.of(DataRedisProperties.class))
                .orElseThrow(() -> new IllegalStateException("Redis properties were not bound"));

        assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("Redis serializer round-trips final root values: immutable lists/maps and records")
    void redisValueSerializerRoundTripsFinalRootValues() {
        RedisSerializer<Object> serializer = redisConfig.redisValueSerializer(new ObjectMapper().findAndRegisterModules());

        // TeamLookupService.getActiveTeamSummaries() returns Stream.toList(); empty on a DB without teams.
        List<TeamSummaryDto> empty = java.util.stream.Stream.<TeamSummaryDto>empty().toList();
        List<TeamSummaryDto> single = java.util.stream.Stream.of(new TeamSummaryDto("LG", "LG 트윈스", "LG", true)).toList();

        assertThat(serializer.deserialize(serializer.serialize(empty))).isEqualTo(empty);
        assertThat(serializer.deserialize(serializer.serialize(List.of()))).isEqualTo(List.of());
        assertThat(serializer.deserialize(serializer.serialize(new java.util.ArrayList<>()))).isEqualTo(List.of());
        assertThat(serializer.deserialize(serializer.serialize(single))).isEqualTo(single);
        assertThat(serializer.deserialize(serializer.serialize(single.get(0)))).isEqualTo(single.get(0));
        assertThat(serializer.deserialize(serializer.serialize(Map.of("k", "v")))).isEqualTo(Map.of("k", "v"));
        assertThat(serializer.deserialize(serializer.serialize("plain"))).isEqualTo("plain");
    }

    @Test
    @DisplayName("Redis serializer writes a type id for final root values and keeps natural scalars bare")
    void redisValueSerializerWireFormat() {
        RedisSerializer<Object> serializer = redisConfig.redisValueSerializer(new ObjectMapper().findAndRegisterModules());

        // Final root values must carry a type id, otherwise the next cache hit cannot read them.
        String record = new String(serializer.serialize(new TeamSummaryDto("LG", "LG 트윈스", "LG", true)));
        assertThat(record).contains("\"@class\":\"com.example.kbo.dto.TeamSummaryDto\"");
        assertThat(new String(serializer.serialize(List.of("a")))).startsWith("[\"java.util.ImmutableCollections$");

        // Natural JSON scalars stay bare.
        assertThat(new String(serializer.serialize("plain"))).isEqualTo("\"plain\"");
        assertThat(new String(serializer.serialize(true))).isEqualTo("true");
        assertThat(new String(serializer.serialize(5))).isEqualTo("5");
    }

    @Test
    @DisplayName("Redis serializer round-trips root Long, BigDecimal, enum and final-element arrays")
    void redisValueSerializerRoundTripsNonNaturalRootValues() {
        RedisSerializer<Object> serializer = redisConfig.redisValueSerializer(new ObjectMapper().findAndRegisterModules());

        // Long used to be written bare and read back as Integer; it now keeps its type.
        assertThat(serializer.deserialize(serializer.serialize(5L))).isEqualTo(5L);
        assertThat(serializer.deserialize(serializer.serialize(new java.math.BigDecimal("1.50"))))
                .isEqualTo(new java.math.BigDecimal("1.50"));
        assertThat(serializer.deserialize(serializer.serialize(java.util.concurrent.TimeUnit.SECONDS)))
                .isEqualTo(java.util.concurrent.TimeUnit.SECONDS);
        assertThat((Object[]) serializer.deserialize(serializer.serialize(new String[] {"a", "b"})))
                .containsExactly("a", "b");
    }

    @Test
    @DisplayName("Redis serializer maps null to an empty payload and an empty payload back to null")
    void redisValueSerializerHandlesNullAndEmptyPayload() {
        RedisSerializer<Object> serializer = redisConfig.redisValueSerializer(new ObjectMapper().findAndRegisterModules());

        assertThat(serializer.serialize(null)).isEmpty();
        assertThat(serializer.deserialize(new byte[0])).isNull();
        assertThat(serializer.deserialize(null)).isNull();
    }

    @Test
    @DisplayName("Redis serializer round-trips home widgets responses with Instant fields")
    void redisValueSerializerRoundTripsHomeWidgetsResponse() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        RedisSerializer<Object> serializer = redisConfig.redisValueSerializer(objectMapper);
        Instant createdAt = Instant.parse("2026-03-16T04:30:00Z");
        HomeWidgetsResponseDto payload = HomeWidgetsResponseDto.builder()
                .hotCheerPosts(List.of(
                        PostSummaryRes.of(
                                7L,
                                "LG",
                                "LG 트윈스",
                                "LG",
                                "#C30452",
                                "오늘 경기 기대됩니다.",
                                "홍길동",
                                "@hong",
                                null,
                                "LG",
                                createdAt,
                                2,
                                5,
                                1,
                                false,
                                33,
                                true,
                                false,
                                false,
                                0,
                                false,
                                "NORMAL",
                                List.of())))
                .featuredMates(List.of(
                        FeaturedMateCardDto.builder()
                                .id(11L)
                                .hostId(101L)
                                .teamId("LG")
                                .gameDate("2026-03-16")
                                .gameTime("18:30")
                                .stadium("잠실야구장")
                                .section("1루 내야")
                                .description("같이 직관 가요")
                                .homeTeam("LG")
                                .awayTeam("SS")
                                .currentParticipants(1)
                                .maxParticipants(4)
                                .ticketPrice(15000)
                                .status("PENDING")
                                .build()))
                .rankingSnapshot(HomeRankingSnapshotDto.builder()
                        .rankingSeasonYear(2025)
                        .rankingSourceMessage("2025 시즌 순위 데이터")
                        .isOffSeason(true)
                        .rankings(List.of(HomePageTeamRankingDto.builder()
                                .rank(1)
                                .teamId("LG")
                                .teamName("LG 트윈스")
                                .wins(80)
                                .losses(50)
                                .draws(2)
                                .winRate("0.615")
                                .games(132)
                                .gamesBehind(0.0)
                                .build()))
                        .build())
                .build();

        byte[] serialized = serializer.serialize(payload);
        Object deserialized = serializer.deserialize(serialized);

        assertThat(serialized).isNotEmpty();
        assertThat(deserialized).isInstanceOf(HomeWidgetsResponseDto.class);

        HomeWidgetsResponseDto response = (HomeWidgetsResponseDto) deserialized;
        assertThat(response.getHotCheerPosts()).hasSize(1);
        assertThat(response.getHotCheerPosts().get(0).createdAt()).isEqualTo(createdAt);
        assertThat(response.getFeaturedMates()).hasSize(1);
        assertThat(response.getFeaturedMates().get(0).getStatus()).isEqualTo("PENDING");
        assertThat(response.getRankingSnapshot().getRankingSeasonYear()).isEqualTo(2025);
        assertThat(response.getRankingSnapshot().isOffSeason()).isTrue();
        assertThat(response.getRankingSnapshot().getRankings()).hasSize(1);
    }
}
