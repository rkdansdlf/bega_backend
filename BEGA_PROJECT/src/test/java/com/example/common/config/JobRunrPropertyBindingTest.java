package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.jobrunr.spring.autoconfigure.JobRunrProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.yaml.snakeyaml.Yaml;

/**
 * application.yml 의 JobRunr 설정이 스타터가 실제로 읽는 프로퍼티에 바인딩되는지 검증한다.
 *
 * <p>이 테스트가 존재하는 이유: jobrunr-spring-boot-4-starter 8.x 는 7.x 의 {@code org.jobrunr.*}
 * 접두사를 {@code jobrunr.*} 로 바꿨다. 틀린 접두사는 <em>오류 없이 무시</em>되고
 * {@code enabled} 기본값 false 가 적용되므로, 기동은 정상으로 보이지만 배경 작업이
 * 조용히 멈춘다. 2026-07-18 스타터 업그레이드 때 실제로 이 일이 벌어졌고
 * 3주간 아무도 눈치채지 못했다. 다음 업그레이드에서 접두사가 또 바뀌면 여기서 잡힌다.
 */
class JobRunrPropertyBindingTest {

    @Test
    @DisplayName("application.yml 의 JobRunr 설정이 스타터의 JobRunrProperties 로 바인딩된다")
    void jobRunrConfigurationBindsToStarterProperties() {
        JobRunrProperties bound = bindDefaultDocument();

        assertThat(bound.getBackgroundJobServer().isEnabled())
                .as("배경 작업 서버가 꺼져 있으면 반복 작업이 등록만 되고 실행되지 않는다")
                .isTrue();
        assertThat(bound.getDashboard().isEnabled()).isTrue();
        assertThat(bound.getBackgroundJobServer().getWorkerCount()).isEqualTo(4);
        assertThat(bound.getBackgroundJobServer().getPollIntervalInSeconds())
                .as("Main DB 폴링 부하의 실제 조절점 — 커스텀 StorageProvider 빈이 아니다")
                .isEqualTo(15);
        assertThat(bound.getDatabase().getTablePrefix()).isEqualTo("jobrunr_");
    }

    @Test
    @DisplayName("설정이 레거시 org.jobrunr 접두사로 되돌아가지 않았다")
    void configurationDoesNotUseTheLegacyPrefix() {
        Map<String, Object> yaml = loadDefaultDocument();

        assertThat(yaml).containsKey("jobrunr");
        Object legacyRoot = yaml.get("org");
        if (legacyRoot instanceof Map<?, ?> legacy) {
            assertThat(legacy.containsKey("jobrunr"))
                    .as("org.jobrunr.* 는 8.x 스타터가 무시한다 — jobrunr.* 를 써야 한다")
                    .isFalse();
        }
    }

    private JobRunrProperties bindDefaultDocument() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources()
                .addFirst(new MapPropertySource("application-yml", flatten(loadDefaultDocument())));
        return Binder.get(environment)
                .bind("jobrunr", JobRunrProperties.class)
                .orElseThrow(() -> new AssertionError("jobrunr 접두사로 바인딩되는 설정이 없다"));
    }

    /**
     * 운영에 실제로 실리는 {@code src/main/resources/application.yml} 의 첫 문서
     * (프로필 없는 기본값)를 읽는다. 클래스패스로 읽으면 테스트 리소스의 동명 파일이
     * 잡혀 정작 검증하려는 설정을 보지 못하므로 경로로 직접 읽는다. 프로필별 문서는
     * dashboard 포트만 덮어쓰므로 배경 작업 서버 판정에는 영향이 없다.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> loadDefaultDocument() {
        Path path = Paths.get("src/main/resources/application.yml");
        assertThat(path).as("운영 application.yml 을 찾을 수 없다").exists();
        try (InputStream in = Files.newInputStream(path)) {
            return (Map<String, Object>) new Yaml().loadAll(in).iterator().next();
        } catch (Exception ex) {
            throw new AssertionError("application.yml 을 읽지 못했다", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> flatten(Map<String, Object> source) {
        Map<String, Object> flat = new java.util.LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (value instanceof Map<?, ?> nested) {
                flatten((Map<String, Object>) nested).forEach((k, v) -> flat.put(key + "." + k, v));
            } else if (value != null) {
                flat.put(key, value);
            }
        });
        return flat;
    }
}
