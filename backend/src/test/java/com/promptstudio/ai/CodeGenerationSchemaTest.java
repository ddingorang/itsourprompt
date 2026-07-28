package com.promptstudio.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CodeGenerationSchemaTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 파서가_읽는_필드를_그대로_기술한다() throws Exception {
        Map<String, Object> schema = readSchema();

        Map<String, Object> properties = propertiesOf(schema);
        assertThat(properties).containsKeys("files", "aiResponse");

        Map<String, Object> files = asObject(properties.get("files"));
        assertThat(files.get("type")).isEqualTo("array");
        assertThat(propertiesOf(asObject(files.get("items")))).containsKeys("path", "content");
    }

    @Test
    void 모든_객체가_추가_속성을_금지한다() throws Exception {
        for (Map<String, Object> objectNode : objectNodesOf(readSchema())) {
            assertThat(objectNode.get("additionalProperties"))
                    .as("additionalProperties of %s", objectNode.get("properties"))
                    .isEqualTo(false);
        }
    }

    @Test
    void 모든_객체의_속성이_전부_required에_있다() throws Exception {
        for (Map<String, Object> objectNode : objectNodesOf(readSchema())) {
            List<String> required = asStrings(objectNode.get("required"));

            assertThat(required)
                    .as("required of %s", objectNode.get("properties"))
                    .containsExactlyInAnyOrderElementsOf(propertiesOf(objectNode).keySet());
        }
    }

    private Map<String, Object> readSchema() throws Exception {
        return asObject(objectMapper.readValue(CodeGenerationSchema.JSON, Map.class));
    }

    /**
     * strict 모드 규칙은 중첩된 object 전부에 적용되므로 스키마 전체를 훑어 모아 온다.
     */
    private List<Map<String, Object>> objectNodesOf(Map<String, Object> schema) {
        List<Map<String, Object>> objectNodes = new ArrayList<>();

        if ("object".equals(schema.get("type"))) {
            objectNodes.add(schema);
        }

        for (Object value : schema.values()) {
            if (value instanceof Map<?, ?> nested) {
                objectNodes.addAll(objectNodesOf(asObject(nested)));
            }
        }

        return objectNodes;
    }

    private Map<String, Object> propertiesOf(Map<String, Object> objectNode) {
        return asObject(objectNode.get("properties"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asObject(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<String> asStrings(Object value) {
        assertThat(value).isInstanceOf(List.class);
        return (List<String>) value;
    }
}
