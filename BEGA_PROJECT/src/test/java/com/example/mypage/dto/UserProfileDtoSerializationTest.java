package com.example.mypage.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class UserProfileDtoSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);

    @Test
    void readOnlyBooleanFlagsAreNullableAtTheRequestBoundary() throws Exception {
        assertThat(fieldType("hasPassword")).isEqualTo(Boolean.class);
        assertThat(fieldType("policyConsentRequired")).isEqualTo(Boolean.class);
        assertThat(fieldType("policyConsentNoticeRequired")).isEqualTo(Boolean.class);
    }

    @Test
    void partialProfileUpdatePayloadDoesNotRequireReadOnlyFlags() throws Exception {
        UserProfileDto dto = objectMapper.readValue("""
                {
                  "name": "Media E2E",
                  "email": "media-e2e@example.com",
                  "favoriteTeam": null,
                  "profileImageUrl": "media/profile/1/5.webp"
                }
                """, UserProfileDto.class);

        assertThat(dto.getName()).isEqualTo("Media E2E");
        assertThat(dto.getEmail()).isEqualTo("media-e2e@example.com");
        assertThat(dto.getFavoriteTeam()).isNull();
        assertThat(dto.getProfileImageUrl()).isEqualTo("media/profile/1/5.webp");
        var serialized = objectMapper.readTree(objectMapper.writeValueAsString(dto));
        assertThat(serialized.path("hasPassword").asBoolean()).isFalse();
        assertThat(serialized.path("policyConsentRequired").asBoolean()).isFalse();
        assertThat(serialized.path("policyConsentNoticeRequired").asBoolean()).isFalse();
    }

    private Class<?> fieldType(String name) throws NoSuchFieldException {
        Field field = UserProfileDto.class.getDeclaredField(name);
        return field.getType();
    }
}
