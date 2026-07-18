package com.example.common.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

/**
 * Jackson 2 Redis serializer bridge used while the application keeps its Jackson 2 API surface.
 *
 * Spring Data Redis 4 deprecates its Jackson 2 serializer implementations in favor of Jackson 3.
 * This bridge preserves the existing polymorphic JSON contract without depending on a removable
 * Spring Data Redis serializer type.
 */
final class Jackson2RedisSerializer implements RedisSerializer<Object> {

    private final ObjectMapper objectMapper;

    Jackson2RedisSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy();
        this.objectMapper.activateDefaultTyping(
                this.objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY);
    }

    @Override
    public byte[] serialize(Object source) throws SerializationException {
        if (source == null) {
            return new byte[0];
        }

        try {
            return objectMapper.writeValueAsBytes(source);
        } catch (JsonProcessingException ex) {
            throw new SerializationException("Could not write JSON", ex);
        }
    }

    @Override
    public Object deserialize(byte[] source) throws SerializationException {
        if (source == null || source.length == 0) {
            return null;
        }

        try {
            return objectMapper.readValue(source, Object.class);
        } catch (IOException ex) {
            throw new SerializationException("Could not read JSON", ex);
        }
    }
}
