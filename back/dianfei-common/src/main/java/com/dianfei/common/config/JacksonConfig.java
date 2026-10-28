package com.dianfei.common.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;

import java.time.format.DateTimeFormatter;

/**
 * 统一 Jackson 配置（对齐全局约定）：
 * 1. LocalDateTime / LocalDate 序列化为 yyyy-MM-dd HH:mm:ss / yyyy-MM-dd
 * 2. Long / long 统一序列化为 String，避免前端 JS 精度丢失（雪花 ID 19 位）
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer dianfeiJacksonCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance);
            builder.modules(module);
            builder.serializers(
                    new LocalDateTimeSerializer(DATETIME),
                    new LocalDateSerializer(DATE));
            builder.deserializers(
                    new LocalDateTimeDeserializer(DATETIME),
                    new LocalDateDeserializer(DATE));
        };
    }
}
