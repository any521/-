package com.dianfei.common.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 操作日志脱敏工具（C10 安全加固）
 * <p>序列化方法参数时，将敏感字段（密码/验证码/身份证/令牌/密钥等）
 * 替换为掩码，防止密码、身份证等明文落入操作日志表。
 * 字段名按大小写不敏感正则匹配，兼容 DTO 中英文驼峰/下划线命名。
 */
public final class SensitiveLogMask {

    /** 敏感字段名匹配：password / pwd / verifyCode / idCard / token / secret / apiKey / key / mobile(部分) */
    private static final Pattern SENSITIVE_NAME = Pattern.compile(
            "(?i)^(.*(password|passwd|pwd|verifycode|verify_code|idcard|id_card|secret|token|apikey|api_key|accesstoken|refreshkey|aeskey|privatekey|credential).*|mobile)$");

    private static final String MASK = "***";

    private SensitiveLogMask() {
    }

    /** 构建带脱敏能力的 ObjectMapper（独立实例，不影响全局注入的 mapper） */
    public static ObjectMapper maskedMapper() {
        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.setSerializerModifier(new BeanSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                                                             BeanDescription beanDesc,
                                                             List<BeanPropertyWriter> beanProperties) {
                for (int i = 0; i < beanProperties.size(); i++) {
                    BeanPropertyWriter writer = beanProperties.get(i);
                    if (SENSITIVE_NAME.matcher(writer.getName()).matches()) {
                        writer.assignSerializer(new MaskSerializer());
                        beanProperties.set(i, writer);
                    }
                }
                return beanProperties;
            }
        });
        mapper.registerModule(module);
        return mapper;
    }

    /** 值级掩码：任何类型统一输出 *** */
    private static class MaskSerializer extends StdSerializer<Object> {
        MaskSerializer() {
            super(Object.class);
        }

        @Override
        public void serialize(Object value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(MASK);
        }
    }
}
