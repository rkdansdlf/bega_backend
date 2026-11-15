package com.example.mate.entity;

import jakarta.persistence.Column;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class PartyApplicationTicketImageUrlStorageTest {

    @Test
    void ticketImageUrlUsesTheExtendedLongVarcharColumnAndKeepsLegacyColumnForCompatibility() throws Exception {
        Field currentField = PartyApplication.class.getDeclaredField("ticketImageUrl");
        Field legacyField = PartyApplication.class.getDeclaredField("legacyTicketImageUrl");

        assertThat(currentField.getAnnotation(Column.class).name()).isEqualTo("ticket_image_url_extended");
        assertThat(currentField.getAnnotation(JdbcTypeCode.class).value()).isEqualTo(SqlTypes.LONG32VARCHAR);
        assertThat(legacyField.getAnnotation(Column.class).name()).isEqualTo("ticket_image_url");
        assertThat(legacyField.getAnnotation(Column.class).length()).isEqualTo(500);
    }

    @Test
    void insertMirrorsOnlyValuesThatFitTheLegacyColumn() {
        PartyApplication shortValue = PartyApplication.builder().ticketImageUrl("x".repeat(500)).build();
        PartyApplication longValue = PartyApplication.builder().ticketImageUrl("x".repeat(2048)).build();

        shortValue.onCreate();
        longValue.onCreate();

        assertThat(shortValue.getLegacyTicketImageUrl()).hasSize(500);
        assertThat(longValue.getTicketImageUrl()).hasSize(2048);
        assertThat(longValue.getLegacyTicketImageUrl()).isNull();
    }

    @Test
    void loadedLegacyValueRemainsReadableWhenExtendedColumnIsNull() {
        PartyApplication application = PartyApplication.builder()
                .legacyTicketImageUrl("legacy-url")
                .build();

        application.restoreTicketImageUrlFromLegacyColumn();

        assertThat(application.getTicketImageUrl()).isEqualTo("legacy-url");
    }
}
