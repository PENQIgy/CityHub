package com.cityhub.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 时间格式统一配置。
 * <p>
 * 背景：Jackson 的 {@code LocalDateTime} 默认只认 ISO-8601（{@code 2026-10-13T21:56:00}），
 * 前端 {@code <input type="datetime-local">} 提交的是 {@code 2026-10-13 21:56:00}（第 10 位是空格），
 * 直接反序列化会抛 {@code InvalidFormatException: Text ... could not be parsed at index 10}。
 * <p>
 * 这里在全局注册 {@code yyyy-MM-dd HH:mm:ss} 的读写器，让所有 {@code LocalDateTime} 字段
 * 既接受带空格的格式，也按同样格式输出（前端 {@code util.formatDateTime} 两种都能解析）。
 * 单个字段上仍可用 {@code @JsonFormat} 覆盖。
 *
 * @author CityHub
 */
@Configuration
public class JacksonConfig {

    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer localDateTimeCustomizer() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(formatter));
            module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(formatter));
            builder.modulesToInstall(module);
        };
    }
}
