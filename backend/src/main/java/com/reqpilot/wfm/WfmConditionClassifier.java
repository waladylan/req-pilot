package com.reqpilot.wfm;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class WfmConditionClassifier {

  private static final Pattern NUMERIC_CONDITION =
      Pattern.compile("\\b([A-Za-z][A-Za-z0-9_]*)\\s*(>=|<=|==|=|>|<)\\s*-?\\d+(?:\\.\\d+)?\\b");

  private WfmConditionClassifier() {}

  static boolean isNumericBusinessCondition(String value) {
    return numericConditionField(value).isPresent();
  }

  static Optional<String> numericConditionField(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    Matcher matcher = NUMERIC_CONDITION.matcher(value);
    if (!matcher.find()) {
      return Optional.empty();
    }
    return Optional.of(matcher.group(1));
  }

  static String toSnakeCase(String value) {
    if (value == null || value.isBlank()) {
      return "condition";
    }
    String snake =
        value
            .trim()
            .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
            .replaceAll("[^A-Za-z0-9]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_+|_+$", "")
            .toLowerCase(Locale.ROOT);
    return snake.isBlank() ? "condition" : snake;
  }

  static String toTitle(String value) {
    String snake = toSnakeCase(value);
    String[] parts = snake.split("_");
    StringBuilder title = new StringBuilder();
    for (String part : parts) {
      if (part.isBlank()) {
        continue;
      }
      if (!title.isEmpty()) {
        title.append(' ');
      }
      title.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
      if (part.length() > 1) {
        title.append(part.substring(1));
      }
    }
    return title.isEmpty() ? "Condition" : title.toString();
  }
}
