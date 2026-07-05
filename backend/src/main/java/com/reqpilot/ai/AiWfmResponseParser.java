package com.reqpilot.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqpilot.wfm.WfmDocument;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AiWfmResponseParser {

  private static final Set<String> UI_ONLY_FIELDS =
      Set.of(
          "x",
          "y",
          "position",
          "color",
          "shape",
          "width",
          "height",
          "selected",
          "dragging",
          "sourceHandle",
          "targetHandle",
          "reactFlowType",
          "edgeLabel");

  private final ObjectMapper objectMapper;

  public AiWfmResponseParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public WfmDocument parse(String responseText) {
    if (responseText == null || responseText.isBlank()) {
      throw new AiWfmResponseParsingException("AI_EMPTY_RESPONSE", "AI response is empty");
    }

    String json = extractJson(responseText.trim());
    try {
      JsonNode root = objectMapper.readTree(json);
      rejectUiFields(root, "$");
      WfmDocument document = objectMapper.treeToValue(root, WfmDocument.class);
      assertWfmRoot(document);
      return document;
    } catch (JsonProcessingException exception) {
      throw new AiWfmResponseParsingException("AI_INVALID_JSON", "AI response is not valid WFM JSON", exception);
    }
  }

  private String extractJson(String value) {
    String cleaned = stripCodeFence(value);
    int start = cleaned.indexOf('{');
    int end = cleaned.lastIndexOf('}');
    if (start < 0 || end <= start) {
      throw new AiWfmResponseParsingException("AI_JSON_NOT_FOUND", "AI response does not contain a JSON object");
    }
    return cleaned.substring(start, end + 1);
  }

  private String stripCodeFence(String value) {
    if (!value.startsWith("```")) {
      return value;
    }

    String withoutOpeningFence = value.replaceFirst("^```(?:json)?\\s*", "");
    return withoutOpeningFence.replaceFirst("\\s*```\\s*$", "").trim();
  }

  private void assertWfmRoot(WfmDocument document) {
    if (document == null
        || document.schemaVersion() == null
        || document.modelType() == null
        || document.workflow() == null
        || document.ast() == null) {
      throw new AiWfmResponseParsingException("AI_MALFORMED_WFM", "AI response is missing WFM root fields");
    }
  }

  private void rejectUiFields(JsonNode node, String path) {
    if (node == null) {
      return;
    }
    if (node.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        String childPath = path + "." + field.getKey();
        if (UI_ONLY_FIELDS.contains(field.getKey())) {
          throw new AiWfmResponseParsingException("AI_UI_FIELD", "AI response contains UI-only field at " + childPath);
        }
        rejectUiFields(field.getValue(), childPath);
      }
      return;
    }
    if (node.isArray()) {
      for (int index = 0; index < node.size(); index++) {
        rejectUiFields(node.get(index), path + "[" + index + "]");
      }
    }
  }
}
