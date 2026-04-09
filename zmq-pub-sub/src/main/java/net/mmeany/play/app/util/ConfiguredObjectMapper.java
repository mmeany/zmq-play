package net.mmeany.play.app.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ConfiguredObjectMapper {

    public static final ObjectMapper JSON_MAPPER = new JsonMapper.Builder(new JsonMapper())
            .addModules(new JavaTimeModule(), new BlackbirdModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
            //.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .defaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_EMPTY, JsonInclude.Include.NON_EMPTY))
            .build();

    public static final ObjectMapper YAML_MAPPER = YAMLMapper.builder()
                                                             .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                                                             .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                                                             .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                                             .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                                                             .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                                                             .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
                                                             //.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
                                                             .defaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_EMPTY, JsonInclude.Include.NON_EMPTY))
                                                             .build()
                                                             .registerModules(new JavaTimeModule(), new BlackbirdModule());

}
