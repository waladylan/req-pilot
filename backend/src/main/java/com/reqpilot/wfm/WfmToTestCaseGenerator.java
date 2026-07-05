package com.reqpilot.wfm;

import com.reqpilot.model.GeneratedTestCase;
import com.reqpilot.model.TestCasePriority;
import com.reqpilot.service.RuleBasedTestCaseGenerator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class WfmToTestCaseGenerator {

  private final WfmPathExtractor pathExtractor;

  public WfmToTestCaseGenerator() {
    this(new WfmPathExtractor());
  }

  public WfmToTestCaseGenerator(WfmPathExtractor pathExtractor) {
    this.pathExtractor = pathExtractor;
  }

  public WfmToTestCaseGenerator(
      @SuppressWarnings("unused") WfmToFlowchartMapper flowchartMapper,
      @SuppressWarnings("unused") RuleBasedTestCaseGenerator delegate) {
    this(new WfmPathExtractor());
  }

  public List<GeneratedTestCase> generate(String requirement, WfmDocument wfm) {
    if (wfm == null || wfm.ast().nodes().isEmpty()) {
      throw new IllegalArgumentException("WFM must contain at least one node");
    }

    List<WfmPath> paths = pathExtractor.extract(wfm);
    Map<String, Integer> titleCounts = new HashMap<>();
    List<GeneratedTestCase> testCases = new ArrayList<>();
    for (WfmPath path : paths) {
      int number = testCases.size() + 1;
      GeneratedTestCase testCase = toTestCase(number, requirement, wfm, path);
      String uniqueTitle = uniqueTitle(testCase.title(), path, titleCounts);
      testCases.add(
          new GeneratedTestCase(
              testCase.id(),
              uniqueTitle,
              testCase.preconditions(),
              testCase.steps(),
              testCase.expectedResult(),
              testCase.priority()));
    }
    return List.copyOf(testCases);
  }

  public int countPaths(WfmDocument wfm) {
    return pathExtractor.extract(wfm).size();
  }

  private GeneratedTestCase toTestCase(
      int number, String requirement, WfmDocument wfm, WfmPath path) {
    Map<String, WfmActor> actorsById = actorsById(wfm);
    String context = normalizeForMatch(safe(requirement) + " " + wfm.workflow().title() + " " + pathText(path));

    return new GeneratedTestCase(
        "TC%03d".formatted(number),
        buildTitle(context, wfm, path),
        buildPreconditions(context, wfm, path),
        buildSteps(path, actorsById),
        buildExpectedResult(context, path),
        buildPriority(context, path));
  }

  private String buildTitle(String context, WfmDocument wfm, WfmPath path) {
    String feature = featureNoun(context, wfm.workflow().title());
    String pathText = normalizeForMatch(pathText(path));
    String terminalTitle = terminalBusinessTitle(path);

    if (path.retryPath()) {
      if (containsAny(context, "login", "credential")) {
        return "Retry login after invalid credentials";
      }
      if (containsAny(context, "payment", "checkout")) {
        return "Retry payment after failure";
      }
      return "Retry " + feature;
    }

    if (path.cancelPath()) {
      if (containsAny(context, "delete", "product")) {
        return "Cancel delete product";
      }
      return "Cancel " + feature;
    }

    if (path.timeoutPath()) {
      return "Handle " + feature + " timeout";
    }

    if (path.errorPath() || path.negativePath()) {
      if (containsAny(pathText, "documents are missing", "missing documents")) {
        return "Show missing documents message";
      }
      if (containsAny(pathText, "validation error", "required information", "missing")) {
        return "Show validation error when required information is missing";
      }
      if (containsAny(pathText, "invalid username", "invalid credential", "credentials are invalid")) {
        return "Show invalid credentials message";
      }
      if (containsAny(pathText, "payment failure", "payment fails", "payment error")) {
        return "Show payment failure message";
      }
      if (containsAny(pathText, "email not found", "email does not exist", "not found")) {
        return "Show email not found message";
      }
      if (containsAny(pathText, "reject", "rejected")) {
        return "Reject approval request";
      }
      if (containsAny(pathText, "system error", "service fails", "cannot connect")) {
        return "Show system error";
      }
      if (!terminalTitle.isBlank()) {
        return toTitleCase(terminalTitle);
      }
      return "Handle invalid " + feature;
    }

    if (containsAny(context, "password reset", "reset link")) {
      return "Send password reset link successfully";
    }
    if (containsAny(context, "delete", "product")) {
      return "Delete product successfully";
    }
    if (containsAny(context, "login", "sign in")) {
      return "Login successfully";
    }
    if (containsAny(context, "registration", "register")) {
      return "Register user successfully";
    }
    if (containsAny(context, "checkout", "cart", "payment", "order")) {
      return "Checkout order successfully";
    }
    if (containsAny(context, "approval", "approve", "expense")) {
      return "Approve request";
    }

    return capitalizeFirst(feature) + " successfully";
  }

  private String buildPreconditions(String context, WfmDocument wfm, WfmPath path) {
    List<String> preconditions = new ArrayList<>();

    if (containsAny(context, "password reset")) {
      preconditions.add("User can access the password reset page.");
    } else if (containsAny(context, "login", "sign in")) {
      preconditions.add("User is on the login screen.");
    } else if (containsAny(context, "registration", "register")) {
      preconditions.add("User can access the registration page.");
    } else if (containsAny(context, "checkout", "payment", "cart", "order")) {
      preconditions.add("User is logged in and the cart has items.");
    } else if (containsAny(context, "delete", "product")) {
      preconditions.add("User is logged in and product exists.");
    } else if (containsAny(context, "approval", "approve", "expense")) {
      preconditions.add("Request is submitted and approver can review it.");
    } else {
      preconditions.add("The user can access the feature.");
    }

    if (path.nodes().stream().anyMatch((node) -> node.role() == WfmNodeRole.INPUT)) {
      preconditions.add("Required test data is available.");
    }

    actorNames(wfm).stream()
        .filter((actorName) -> !actorName.equalsIgnoreCase("system"))
        .findFirst()
        .ifPresent((actorName) -> preconditions.add(actorName + " is available."));

    return String.join(" ", distinct(preconditions));
  }

  private List<String> buildSteps(WfmPath path, Map<String, WfmActor> actorsById) {
    List<String> steps = new ArrayList<>();

    for (int index = 0; index < path.nodes().size(); index++) {
      WfmNode node = path.nodes().get(index);
      WfmTransition incoming = index > 0 && index - 1 < path.transitions().size()
          ? path.transitions().get(index - 1)
          : null;

      String branchStep = branchStep(incoming);
      if (!branchStep.isBlank()) {
        steps.add(branchStep);
      }

      String nodeStep = nodeStep(node, actorsById);
      if (!nodeStep.isBlank()) {
        steps.add(nodeStep);
      }
    }

    if (steps.isEmpty()) {
      steps.add("Execute the workflow path.");
    }

    return distinct(steps);
  }

  private String nodeStep(WfmNode node, Map<String, WfmActor> actorsById) {
    return switch (node.role()) {
      case START, END -> "";
      case INPUT -> actorPrefix(node, actorsById) + normalizeStepTitle(node.title());
      case ACTION, SUBPROCESS -> actorPrefix(node, actorsById) + actionStep(node.title());
      case DECISION -> "Verify " + lowerFirst(node.title()) + ".";
      case OUTPUT -> "Observe " + lowerFirst(node.title()) + ".";
      case ERROR -> "Verify error state: " + node.title() + ".";
    };
  }

  private String actionStep(String title) {
    String normalized = normalizeForMatch(title);
    if (containsAny(
        normalized,
        "click",
        "open",
        "enter",
        "submit",
        "select",
        "choose",
        "confirm",
        "cancel",
        "review",
        "return")) {
      return normalizeStepTitle(title);
    }
    return "Perform " + lowerFirst(stripSentence(title)) + ".";
  }

  private String branchStep(WfmTransition transition) {
    if (transition == null || transition.semantic() == WfmTransitionSemantic.DEFAULT) {
      return "";
    }

    String condition = transition.condition() == null ? "" : transition.condition().trim();
    return switch (transition.semantic()) {
      case YES -> condition.isBlank() ? "Choose yes." : "Choose yes when " + lowerFirst(stripSentence(condition)) + ".";
      case NO -> condition.isBlank()
          ? "Use the negative condition."
          : "Use the negative condition: " + stripSentence(condition) + ".";
      case SUCCESS -> condition.isBlank()
          ? "Complete the success path."
          : "Complete the success path for " + lowerFirst(stripSentence(condition)) + ".";
      case FAILURE -> condition.isBlank()
          ? "Simulate the failure condition."
          : "Simulate failure for " + lowerFirst(stripSentence(condition)) + ".";
      case CANCEL -> condition.isBlank() ? "Cancel the flow." : "Cancel the flow: " + stripSentence(condition) + ".";
      case RETRY -> condition.isBlank() ? "Choose retry." : "Choose retry for " + lowerFirst(stripSentence(condition)) + ".";
      case TIMEOUT -> condition.isBlank()
          ? "Simulate timeout."
          : "Simulate timeout for " + lowerFirst(stripSentence(condition)) + ".";
      case DEFAULT -> "";
    };
  }

  private String buildExpectedResult(String context, WfmPath path) {
    String terminalTitle = terminalBusinessTitle(path);
    String pathText = normalizeForMatch(pathText(path));

    if (path.retryPath()) {
      return "User is returned to the appropriate input step and can retry.";
    }
    if (path.cancelPath()) {
      if (containsAny(context, "delete", "product")) {
        return "Product is not deleted and no destructive change is made.";
      }
      if (containsAny(context, "checkout", "cart", "payment")) {
        return "Checkout is cancelled and cart state is preserved.";
      }
      return "The flow is cancelled and no unintended state change occurs.";
    }
    if (path.timeoutPath()) {
      return terminalTitle.isBlank()
          ? "Timeout is handled and the user sees a clear result."
          : terminalTitle + ".";
    }
    if (path.errorPath()) {
      return terminalTitle.isBlank()
          ? "An error message is shown and the operation is not completed."
          : terminalTitle + ".";
    }
    if (path.negativePath()) {
      if (containsAny(pathText, "reject", "rejected")) {
        return "Request is rejected and the employee is notified.";
      }
      return terminalTitle.isBlank()
          ? "The negative condition is handled without completing the happy path."
          : terminalTitle + ".";
    }
    if (!terminalTitle.isBlank()) {
      return terminalTitle + ".";
    }
    if (containsAny(context, "delete", "product")) {
      return "Product is deleted and success message is shown.";
    }
    if (containsAny(context, "checkout", "order")) {
      return "Order is created and confirmation is shown.";
    }
    return "The workflow path completes successfully.";
  }

  private TestCasePriority buildPriority(String context, WfmPath path) {
    if (path.errorPath() || path.timeoutPath()) {
      return TestCasePriority.HIGH;
    }
    if (path.cancelPath() || path.retryPath() || path.negativePath()) {
      return TestCasePriority.MEDIUM;
    }
    if (containsAny(context, "delete", "payment", "checkout", "login", "registration", "approval")) {
      return TestCasePriority.HIGH;
    }
    return TestCasePriority.MEDIUM;
  }

  private String uniqueTitle(String title, WfmPath path, Map<String, Integer> titleCounts) {
    int count = titleCounts.merge(title, 1, Integer::sum);
    if (count == 1) {
      return title;
    }

    String suffix = path.transitions().stream()
        .map(WfmTransition::condition)
        .filter(Objects::nonNull)
        .filter((condition) -> !condition.isBlank())
        .findFirst()
        .orElse(path.semanticSummary());
    return title + " - " + toTitleCase(suffix);
  }

  private String terminalBusinessTitle(WfmPath path) {
    for (int index = path.nodes().size() - 1; index >= 0; index--) {
      WfmNode node = path.nodes().get(index);
      if (node.role() == WfmNodeRole.OUTPUT || node.role() == WfmNodeRole.ERROR) {
        return stripSentence(node.title());
      }
    }
    for (int index = path.nodes().size() - 1; index >= 0; index--) {
      WfmNode node = path.nodes().get(index);
      if (node.role() != WfmNodeRole.START && node.role() != WfmNodeRole.END && node.role() != WfmNodeRole.DECISION) {
        return stripSentence(node.title());
      }
    }
    return "";
  }

  private Map<String, WfmActor> actorsById(WfmDocument wfm) {
    Map<String, WfmActor> actorsById = new HashMap<>();
    for (WfmActor actor : wfm.ast().actors()) {
      actorsById.put(actor.id(), actor);
    }
    return actorsById;
  }

  private List<String> actorNames(WfmDocument wfm) {
    return wfm.ast().actors().stream().map(WfmActor::name).filter((name) -> !name.isBlank()).toList();
  }

  private String actorPrefix(WfmNode node, Map<String, WfmActor> actorsById) {
    if (node.actorId() == null || node.actorId().isBlank()) {
      return "";
    }

    WfmActor actor = actorsById.get(node.actorId());
    if (actor == null || actor.name().isBlank()) {
      return "";
    }

    String normalizedTitle = normalizeForMatch(node.title());
    String normalizedActor = normalizeForMatch(actor.name());
    if (normalizedTitle.startsWith(normalizedActor)) {
      return "";
    }
    return actor.name() + ": ";
  }

  private String pathText(WfmPath path) {
    List<String> parts = new ArrayList<>();
    path.nodes().forEach((node) -> parts.add(node.title()));
    path.transitions().forEach((transition) -> {
      parts.add(transition.semantic().name());
      if (transition.condition() != null) {
        parts.add(transition.condition());
      }
    });
    return String.join(" ", parts);
  }

  private String featureNoun(String context, String workflowTitle) {
    if (containsAny(context, "password reset", "reset link")) {
      return "password reset";
    }
    if (containsAny(context, "login", "sign in")) {
      return "login";
    }
    if (containsAny(context, "registration", "register")) {
      return "registration";
    }
    if (containsAny(context, "checkout", "payment", "cart", "order")) {
      return "checkout";
    }
    if (containsAny(context, "approval", "approve", "expense")) {
      return "approval";
    }
    if (containsAny(context, "delete", "product")) {
      return "product deletion";
    }
    String title = workflowTitle == null || workflowTitle.isBlank() ? "workflow" : workflowTitle;
    return lowerFirst(title);
  }

  private List<String> distinct(List<String> values) {
    Set<String> unique = new LinkedHashSet<>();
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        unique.add(value.trim());
      }
    }
    return List.copyOf(unique);
  }

  private String normalizeStepTitle(String title) {
    return stripSentence(title) + ".";
  }

  private String stripSentence(String value) {
    return safe(value).trim().replaceFirst("[\\s.]+$", "");
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }

  private String normalizeForMatch(String value) {
    String normalized = Normalizer.normalize(safe(value), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    return normalized.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
  }

  private boolean containsAny(String value, String... candidates) {
    for (String candidate : candidates) {
      if (value.contains(candidate)) {
        return true;
      }
    }
    return false;
  }

  private String toTitleCase(String value) {
    String[] words = stripSentence(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim().split(" ");
    List<String> titleWords = new ArrayList<>();
    for (String word : words) {
      if (!word.isBlank()) {
        titleWords.add(capitalizeFirst(word));
      }
    }
    return String.join(" ", titleWords);
  }

  private String capitalizeFirst(String value) {
    String trimmed = value.trim();
    if (trimmed.isBlank()) {
      return trimmed;
    }
    return trimmed.substring(0, 1).toUpperCase(Locale.ROOT) + trimmed.substring(1);
  }

  private String lowerFirst(String value) {
    String trimmed = stripSentence(value);
    if (trimmed.isBlank()) {
      return trimmed;
    }
    return trimmed.substring(0, 1).toLowerCase(Locale.ROOT) + trimmed.substring(1);
  }
}
