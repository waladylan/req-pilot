package com.reqpilot.wfm;

import com.reqpilot.model.GeneratedTestCase;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class TestCaseQualityValidator {

  private TestCaseQualityValidator() {}

  static List<String> validate(List<GeneratedTestCase> testCases, WfmDocument wfm) {
    List<String> errors = new ArrayList<>();
    if (testCases == null || testCases.isEmpty()) {
      errors.add("testCases must not be empty");
      return errors;
    }

    Set<String> ids = new HashSet<>();
    Set<String> titles = new HashSet<>();
    for (GeneratedTestCase testCase : testCases) {
      if (isBlank(testCase.id())) {
        errors.add("test case id is required");
      } else if (!ids.add(testCase.id())) {
        errors.add("duplicate test case id: " + testCase.id());
      }

      if (isBlank(testCase.title())) {
        errors.add("test case title is required");
      } else {
        String normalizedTitle = normalize(testCase.title());
        if (!titles.add(normalizedTitle)) {
          errors.add("duplicate test case title: " + testCase.title());
        }
        if (normalizedTitle.matches("^(test case|test case \\d+|generated test case)$")) {
          errors.add("generic test case title: " + testCase.title());
        }
      }

      if (isBlank(testCase.preconditions())) {
        errors.add(testCase.id() + " preconditions are required");
      }
      if (testCase.steps() == null || testCase.steps().isEmpty()) {
        errors.add(testCase.id() + " steps are required");
      } else if (testCase.steps().stream().anyMatch(TestCaseQualityValidator::isBlank)) {
        errors.add(testCase.id() + " contains an empty step");
      }
      if (isBlank(testCase.expectedResult())) {
        errors.add(testCase.id() + " expected result is required");
      }
      if (testCase.priority() == null) {
        errors.add(testCase.id() + " priority is required");
      }
    }

    List<WfmTransitionSemantic> semantics = wfm.ast().transitions().stream().map(WfmTransition::semantic).toList();
    String combinedCases = normalize(
        testCases.stream()
            .map((testCase) -> testCase.title() + " " + testCase.expectedResult() + " " + String.join(" ", testCase.steps()))
            .reduce("", (left, right) -> left + " " + right));

    if (containsAnySemantic(semantics, WfmTransitionSemantic.YES, WfmTransitionSemantic.SUCCESS)
        && !containsAny(combinedCases, "success", "successfully", "approved", "confirmation", "sent")) {
      errors.add("positive test case is required");
    }
    if (containsAnySemantic(semantics, WfmTransitionSemantic.NO, WfmTransitionSemantic.FAILURE)
        && !containsAny(combinedCases, "invalid", "failure", "error", "missing", "reject", "negative", "not found")) {
      errors.add("negative test case is required");
    }
    if (semantics.contains(WfmTransitionSemantic.CANCEL) && !combinedCases.contains("cancel")) {
      errors.add("cancel test case is required");
    }
    if (semantics.contains(WfmTransitionSemantic.RETRY) && !combinedCases.contains("retry")) {
      errors.add("retry test case is required");
    }
    if (semantics.contains(WfmTransitionSemantic.TIMEOUT) && !combinedCases.contains("timeout")) {
      errors.add("timeout test case is required");
    }

    return errors;
  }

  private static boolean containsAnySemantic(
      List<WfmTransitionSemantic> semantics, WfmTransitionSemantic... candidates) {
    for (WfmTransitionSemantic candidate : candidates) {
      if (semantics.contains(candidate)) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsAny(String value, String... candidates) {
    for (String candidate : candidates) {
      if (value.contains(candidate)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static String normalize(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
  }
}
