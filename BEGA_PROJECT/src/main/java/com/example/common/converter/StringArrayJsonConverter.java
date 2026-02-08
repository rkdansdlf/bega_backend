package com.example.common.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@Slf4j
@Converter
public class StringArrayJsonConverter implements AttributeConverter<String[], String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(String[] attribute) {
        if (attribute == null) {
            return null; // or "[]" depending on preference
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.error("Error converting String[] to JSON", e);
            throw new RuntimeException("Error converting String[] to JSON", e);
        }
    }

    @Override
    public String[] convertToEntityAttribute(String dbData) {
        if (!StringUtils.hasText(dbData)) {
            return new String[0];
        }
        try {
            return objectMapper.readValue(dbData, String[].class);
        } catch (JsonProcessingException e) {
            log.error("Error converting JSON to String[]", e);
            // Fallback: return empty array or treat as single string?
            // For now, return empty array to avoid crash
            return new String[0];
        }
    }
}
