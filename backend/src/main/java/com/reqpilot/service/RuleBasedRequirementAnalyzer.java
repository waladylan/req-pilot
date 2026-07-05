package com.reqpilot.service;

import com.reqpilot.model.FlowEdge;
import com.reqpilot.model.FlowEdgeType;
import com.reqpilot.model.FlowNode;
import com.reqpilot.model.FlowNodeType;
import com.reqpilot.model.Flowchart;
import com.reqpilot.wfm.WfmActor;
import com.reqpilot.wfm.WfmActorType;
import com.reqpilot.wfm.WfmAst;
import com.reqpilot.wfm.WfmDocument;
import com.reqpilot.wfm.WfmExtensions;
import com.reqpilot.wfm.WfmNode;
import com.reqpilot.wfm.WfmNodeRole;
import com.reqpilot.wfm.WfmNormalizer;
import com.reqpilot.wfm.WfmToFlowchartMapper;
import com.reqpilot.wfm.WfmToMermaidGenerator;
import com.reqpilot.wfm.WfmTransition;
import com.reqpilot.wfm.WfmTransitionSemantic;
import com.reqpilot.wfm.WfmValidator;
import com.reqpilot.wfm.WfmWorkflow;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RuleBasedRequirementAnalyzer implements WfmRequirementAnalyzer {

  private static final Pattern INLINE_CONDITION_PATTERN =
      Pattern.compile(
          "^(?:if|when|nếu|khi)\\s+(.+?)(?:\\s*,\\s*|\\s+then\\s+|\\s+thì\\s+)(.+)$",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern CONDITION_HEADER_PATTERN =
      Pattern.compile("^(?:if|when|nếu|khi)\\s+(.+?)\\s*:?$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern ELSE_IF_PATTERN =
      Pattern.compile(
          "^(?:else\\s+if|otherwise\\s+if)\\s+(.+?)\\s*:?$",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern NAMED_BRANCH_PATTERN =
      Pattern.compile(
          "^(confirm|cancel|yes|no|success|failure|fail|valid|invalid|error|retry|approved|approve|rejected|reject|xác nhận|xac nhan|hủy|huy|có|co|không|khong|đúng|dung|sai|lỗi|loi|thành công|thanh cong|thất bại|that bai)\\s*:?$",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern VIETNAMESE_MARKS =
      Pattern.compile("[ăâđêôơưáàảãạấầẩẫậắằẳẵặéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ]");

  private final WfmNormalizer wfmNormalizer;
  private final WfmValidator wfmValidator;
  private final WfmToFlowchartMapper flowchartMapper;

  public RuleBasedRequirementAnalyzer() {
    this(
        new WfmNormalizer(),
        new WfmValidator(),
        new WfmToFlowchartMapper(new WfmToMermaidGenerator()));
  }

  @Autowired
  public RuleBasedRequirementAnalyzer(
      WfmNormalizer wfmNormalizer, WfmValidator wfmValidator, WfmToFlowchartMapper flowchartMapper) {
    this.wfmNormalizer = wfmNormalizer;
    this.wfmValidator = wfmValidator;
    this.flowchartMapper = flowchartMapper;
  }

  @Override
  public Flowchart analyze(String requirement) {
    return flowchartMapper.toFlowchart(analyzeToWfm(requirement));
  }

  @Override
  public WfmDocument analyzeToWfm(String requirement) {
    List<String> lines = extractLines(requirement);
    if (lines.isEmpty()) {
      throw new IllegalArgumentException("Requirement must contain at least one meaningful line");
    }

    boolean vietnamese = isVietnamese(requirement);
    String featureLine = findFeatureLine(lines).orElse(lines.getFirst());
    String featureName = normalizeFeatureName(featureLine);
    ParsedRequirement parsedRequirement = parseRequirement(lines, featureLine, vietnamese);

    List<WfmNode> nodes = new ArrayList<>();
    List<WfmTransition> transitions = new ArrayList<>();
    NodeIdSequence nodeIds = new NodeIdSequence();

    WfmNode start = createNode(nodeIds.next(), WfmNodeRole.START, vietnamese ? "Bắt đầu" : "Start");
    WfmNode entry =
        createNode(
            nodeIds.next(),
            detectNodeRole(deriveEntryAction(featureName, requirement, vietnamese)),
            deriveEntryAction(featureName, requirement, vietnamese));
    nodes.add(start);
    nodes.add(entry);
    addTransition(transitions, start.id(), entry.id(), null, WfmTransitionSemantic.DEFAULT);

    String currentNodeId = entry.id();
    for (String action : parsedRequirement.sequenceActions()) {
      WfmNode actionNode = createNode(nodeIds.next(), detectNodeRole(action), action);
      nodes.add(actionNode);
      addTransition(transitions, currentNodeId, actionNode.id(), null, WfmTransitionSemantic.DEFAULT);
      currentNodeId = actionNode.id();
    }

    WfmNode end = createNode(nodeIds.next(), WfmNodeRole.END, vietnamese ? "Kết thúc" : "End");

    if (parsedRequirement.branches().isEmpty()) {
      nodes.add(end);
      addTransition(transitions, currentNodeId, end.id(), null, WfmTransitionSemantic.DEFAULT);
      return normalizeAndValidate(requirement, featureName, vietnamese, nodes, transitions);
    }

    WfmNode decision =
        createNode(
            nodeIds.next(),
            WfmNodeRole.DECISION,
            deriveDecisionLabel(parsedRequirement.branches(), requirement, vietnamese),
            "SYSTEM");
    nodes.add(decision);
    addTransition(transitions, currentNodeId, decision.id(), null, WfmTransitionSemantic.DEFAULT);

    int branchIndex = 1;
    for (ConditionBranch branch : parsedRequirement.branches()) {
      addWfmBranchPath(nodes, transitions, nodeIds, decision.id(), end.id(), branch, branchIndex, vietnamese);
      branchIndex++;
    }

    nodes.add(end);
    return normalizeAndValidate(requirement, featureName, vietnamese, nodes, transitions);
  }

  private ParsedRequirement parseRequirement(List<String> lines, String featureLine, boolean vietnamese) {
    List<String> sequenceActions = new ArrayList<>();
    List<ConditionBranch> branches = new ArrayList<>();
    BranchBuilder currentBranch = null;

    for (String line : lines) {
      if (line.equals(featureLine) || isFlowStartMarker(line)) {
        continue;
      }

      Optional<ConditionBranch> inlineBranch = parseInlineCondition(line, vietnamese);
      if (inlineBranch.isPresent()) {
        currentBranch = finishBranch(currentBranch, branches, vietnamese);
        branches.add(inlineBranch.get());
        continue;
      }

      Optional<String> branchHeader = parseBranchHeader(line);
      if (branchHeader.isPresent()) {
        currentBranch = finishBranch(currentBranch, branches, vietnamese);
        currentBranch = new BranchBuilder(branchHeader.get());
        continue;
      }

      if (isFlowEndMarker(line)) {
        if (currentBranch != null) {
          currentBranch.markTerminates();
        }
        continue;
      }

      String action = normalizeActionLabel(line, vietnamese);
      if (action.isBlank()) {
        continue;
      }

      if (currentBranch != null) {
        currentBranch.addOutcome(action);
      } else {
        sequenceActions.add(action);
      }
    }

    finishBranch(currentBranch, branches, vietnamese);
    return new ParsedRequirement(List.copyOf(sequenceActions), List.copyOf(branches));
  }

  private Optional<ConditionBranch> parseInlineCondition(String line, boolean vietnamese) {
    Matcher matcher = INLINE_CONDITION_PATTERN.matcher(line);
    if (!matcher.matches()) {
      return Optional.empty();
    }

    List<String> outcomes = normalizeOutcomeActions(matcher.group(2), vietnamese);
    return Optional.of(new ConditionBranch(matcher.group(1).trim(), outcomes));
  }

  private Optional<String> parseBranchHeader(String line) {
    Matcher elseIfMatcher = ELSE_IF_PATTERN.matcher(line);
    if (elseIfMatcher.matches()) {
      return Optional.of(elseIfMatcher.group(1).trim());
    }

    String normalized = normalizeForMatch(stripTrailingPunctuation(line));
    if (normalized.equals("else") || normalized.equals("otherwise") || normalized.equals("nguoc lai")) {
      return Optional.of("otherwise");
    }

    Matcher conditionMatcher = CONDITION_HEADER_PATTERN.matcher(line);
    if (conditionMatcher.matches()) {
      return Optional.of(conditionMatcher.group(1).trim());
    }

    Matcher namedBranchMatcher = NAMED_BRANCH_PATTERN.matcher(line);
    if (namedBranchMatcher.matches()) {
      return Optional.of(namedBranchMatcher.group(1).trim());
    }

    return Optional.empty();
  }

  private BranchBuilder finishBranch(
      BranchBuilder branchBuilder, List<ConditionBranch> branches, boolean vietnamese) {
    if (branchBuilder == null) {
      return null;
    }

    branches.add(branchBuilder.toBranch(vietnamese));
    return null;
  }

  private void addBranchPath(
      List<FlowNode> nodes,
      List<FlowEdge> edges,
      String decisionNodeId,
      String endNodeId,
      ConditionBranch branch,
      int branchIndex,
      boolean vietnamese) {
    String branchLabel = summarizeCondition(branch.condition(), vietnamese);
    FlowEdgeType edgeType = detectEdgeType(branch.condition() + " " + String.join(" ", branch.outcomes()));
    List<String> outcomes = branch.outcomes();

    if (outcomes.isEmpty()) {
      addEdge(edges, decisionNodeId, endNodeId, branchLabel, edgeType);
      return;
    }

    String previousNodeId = decisionNodeId;
    for (int outcomeIndex = 0; outcomeIndex < outcomes.size(); outcomeIndex++) {
      String nodeId =
          outcomeIndex == 0
              ? "branch_action_" + branchIndex
              : "branch_action_" + branchIndex + "_" + (outcomeIndex + 1);
      FlowNode actionNode = new FlowNode(nodeId, outcomes.get(outcomeIndex), FlowNodeType.ACTION);
      nodes.add(actionNode);
      addEdge(
          edges,
          previousNodeId,
          actionNode.id(),
          outcomeIndex == 0 ? branchLabel : null,
          outcomeIndex == 0 ? edgeType : FlowEdgeType.DEFAULT);
      previousNodeId = actionNode.id();
    }

    String lastAction = outcomes.getLast();
    if (shouldAppendSuccess(lastAction) && outcomes.stream().noneMatch(this::isSuccessResultAction)) {
      FlowNode successNode =
          new FlowNode(
              "branch_success_" + branchIndex,
              vietnamese ? "Hiển thị thành công" : "Show Success",
              FlowNodeType.ACTION);
      nodes.add(successNode);
      addEdge(edges, previousNodeId, successNode.id(), null);
      previousNodeId = successNode.id();
    }

    addEdge(edges, previousNodeId, endNodeId, null);
  }

  private void addWfmBranchPath(
      List<WfmNode> nodes,
      List<WfmTransition> transitions,
      NodeIdSequence nodeIds,
      String decisionNodeId,
      String endNodeId,
      ConditionBranch branch,
      int branchIndex,
      boolean vietnamese) {
    String branchLabel = summarizeCondition(branch.condition(), vietnamese);
    WfmTransitionSemantic semantic = detectTransitionSemantic(branch.condition());
    if (semantic == WfmTransitionSemantic.DEFAULT) {
      semantic = detectTransitionSemantic(String.join(" ", branch.outcomes()));
    }
    List<String> outcomes = branch.outcomes();

    if (outcomes.isEmpty()) {
      addTransition(transitions, decisionNodeId, endNodeId, branchLabel, semantic);
      return;
    }

    String previousNodeId = decisionNodeId;
    for (int outcomeIndex = 0; outcomeIndex < outcomes.size(); outcomeIndex++) {
      String outcome = outcomes.get(outcomeIndex);
      WfmNode outcomeNode = createNode(nodeIds.next(), detectNodeRole(outcome), outcome);
      nodes.add(outcomeNode);
      addTransition(
          transitions,
          previousNodeId,
          outcomeNode.id(),
          outcomeIndex == 0 ? branchLabel : null,
          outcomeIndex == 0 ? semantic : WfmTransitionSemantic.DEFAULT);
      previousNodeId = outcomeNode.id();
    }

    String lastAction = outcomes.getLast();
    if (shouldAppendSuccess(lastAction) && outcomes.stream().noneMatch(this::isSuccessResultAction)) {
      WfmNode successNode =
          createNode(
              nodeIds.next(),
              WfmNodeRole.OUTPUT,
              vietnamese ? "Hiển thị thành công" : "Show Success",
              "SYSTEM");
      nodes.add(successNode);
      addTransition(transitions, previousNodeId, successNode.id(), null, WfmTransitionSemantic.DEFAULT);
      previousNodeId = successNode.id();
    }

    addTransition(transitions, previousNodeId, endNodeId, null, terminalTransitionSemantic(outcomes, semantic));
  }

  private WfmTransitionSemantic terminalTransitionSemantic(
      List<String> outcomes, WfmTransitionSemantic branchSemantic) {
    String combined = normalizeForMatch(String.join(" ", outcomes));

    if (branchSemantic == WfmTransitionSemantic.CANCEL
        || branchSemantic == WfmTransitionSemantic.RETRY
        || branchSemantic == WfmTransitionSemantic.TIMEOUT) {
      return branchSemantic;
    }

    if (containsAny(
        combined,
        "error",
        "fail",
        "failure",
        "invalid",
        "missing",
        "not found",
        "not exist",
        "rejected",
        "reject",
        "loi",
        "that bai")) {
      return WfmTransitionSemantic.FAILURE;
    }

    if (branchSemantic == WfmTransitionSemantic.NO || branchSemantic == WfmTransitionSemantic.FAILURE) {
      return WfmTransitionSemantic.FAILURE;
    }

    if (containsAny(
            combined,
            "success",
            "succeed",
            "redirect",
            "notify",
            "send",
            "create",
            "delete",
            "approved",
            "approve",
            "authenticate",
            "generate",
            "thanh cong",
            "gui",
            "tao",
            "xoa")
        || branchSemantic == WfmTransitionSemantic.YES
        || branchSemantic == WfmTransitionSemantic.SUCCESS) {
      return WfmTransitionSemantic.SUCCESS;
    }

    return WfmTransitionSemantic.DEFAULT;
  }

  private WfmDocument normalizeAndValidate(
      String requirement,
      String featureName,
      boolean vietnamese,
      List<WfmNode> nodes,
      List<WfmTransition> transitions) {
    WfmDocument rawDocument =
        new WfmDocument(
            "1.0",
            "WORKFLOW_AST",
            new WfmWorkflow(
                toWorkflowId(featureName),
                toWorkflowTitle(featureName, vietnamese),
                null,
                vietnamese ? "vi" : "en",
                inferDomain(featureName + " " + requirement),
                requirement),
            new WfmExtensions(List.of(), List.of()),
            new WfmAst(defaultActors(), List.of(), nodes, transitions, List.of()));

    WfmDocument normalized = wfmNormalizer.normalize(rawDocument);
    return wfmValidator.validateOrThrow(normalized);
  }

  private List<WfmActor> defaultActors() {
    return List.of(
        new WfmActor("USER", "User", WfmActorType.USER),
        new WfmActor("SYSTEM", "System", WfmActorType.SYSTEM));
  }

  private WfmNode createNode(String id, WfmNodeRole role, String title) {
    return createNode(id, role, title, detectActorId(title, role));
  }

  private WfmNode createNode(String id, WfmNodeRole role, String title, String actorId) {
    return new WfmNode(id, role, deriveNodeKind(role, title), title, null, actorId, List.of(), null);
  }

  private void addTransition(
      List<WfmTransition> transitions,
      String from,
      String to,
      String condition,
      WfmTransitionSemantic semantic) {
    transitions.add(
        new WfmTransition(
            "T" + (transitions.size() + 1),
            from,
            to,
            semantic == null ? WfmTransitionSemantic.DEFAULT : semantic,
            null,
            condition,
            null,
            null));
  }

  private WfmNodeRole detectNodeRole(String title) {
    String normalized = normalizeForMatch(title);

    if (containsAny(
        normalized,
        "error",
        "fail",
        "failure",
        "invalid",
        "cannot",
        "unable",
        "unavailable",
        "loi",
        "that bai")) {
      return WfmNodeRole.ERROR;
    }

    if (containsAny(normalized, "input", "upload", "enter", "nhap", "form")) {
      return WfmNodeRole.INPUT;
    }

    if (containsAny(
        normalized,
        "show",
        "display",
        "redirect",
        "return",
        "send",
        "notify",
        "message",
        "receipt",
        "hien thi",
        "gui",
        "quay lai")) {
      return WfmNodeRole.OUTPUT;
    }

    return WfmNodeRole.ACTION;
  }

  private String detectActorId(String title, WfmNodeRole role) {
    if (role == WfmNodeRole.START || role == WfmNodeRole.END) {
      return null;
    }

    String normalized = normalizeForMatch(title);
    if (containsAny(
        normalized,
        "user",
        "customer",
        "employee",
        "manager",
        "approver",
        "nguoi dung",
        "khach hang",
        "nhan vien",
        "quan ly")) {
      return "USER";
    }
    return "SYSTEM";
  }

  private String deriveNodeKind(WfmNodeRole role, String title) {
    if (role == WfmNodeRole.START || role == WfmNodeRole.END) {
      return role.name();
    }

    String normalized = normalizeForMatch(title)
        .replaceAll("[^a-z0-9]+", "_")
        .replaceAll("^_+|_+$", "")
        .toUpperCase(Locale.ROOT);
    return normalized.isBlank() ? role.name() : normalized;
  }

  private String toWorkflowId(String featureName) {
    String normalized =
        normalizeForMatch(featureName)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
    return normalized.isBlank() ? "workflow" : normalized;
  }

  private String toWorkflowTitle(String featureName, boolean vietnamese) {
    if (featureName.isBlank()) {
      return vietnamese ? "Quy trình" : "Workflow";
    }
    return vietnamese ? capitalizeFirst(featureName) : toTitleCase(featureName);
  }

  private String inferDomain(String value) {
    String normalized = normalizeForMatch(value);
    if (containsAny(normalized, "login", "sign in", "registration", "register", "password", "dang nhap", "dang ky")) {
      return "authentication";
    }
    if (containsAny(normalized, "checkout", "payment", "cart", "order", "thanh toan", "gio hang")) {
      return "commerce";
    }
    if (containsAny(normalized, "approval", "approve", "expense", "phe duyet")) {
      return "approval";
    }
    if (containsAny(normalized, "product", "san pham")) {
      return "catalog";
    }
    return "general";
  }

  private List<String> normalizeOutcomeActions(String outcome, boolean vietnamese) {
    List<String> actions = new ArrayList<>();
    for (String part : outcome.split("\\s*(?:;|\\.\\s+)\\s*")) {
      String cleaned = cleanLine(part);
      if (cleaned.isBlank() || isFlowEndMarker(cleaned)) {
        continue;
      }

      String action = normalizeActionLabel(cleaned, vietnamese);
      if (!action.isBlank()) {
        actions.add(action);
      }
    }
    return List.copyOf(actions);
  }

  private List<String> extractLines(String requirement) {
    if (requirement == null) {
      return List.of();
    }

    return requirement
        .lines()
        .map(this::cleanLine)
        .filter((line) -> !line.isBlank())
        .toList();
  }

  private String cleanLine(String line) {
    return line.trim().replaceFirst("^[-*•]\\s*", "").replaceFirst("^\\d+[.)]\\s*", "").trim();
  }

  private Optional<String> findFeatureLine(List<String> lines) {
    Optional<String> explicitFeature =
        lines.stream().filter((line) -> normalizeForMatch(line).matches("^(feature|tinh nang)\\b.*")).findFirst();
    if (explicitFeature.isPresent()) {
      return explicitFeature;
    }

    return lines.stream()
        .filter((line) -> parseInlineCondition(line, false).isEmpty())
        .filter((line) -> parseBranchHeader(line).isEmpty())
        .filter((line) -> !isFlowStartMarker(line))
        .filter((line) -> !isFlowEndMarker(line))
        .findFirst();
  }

  private String normalizeFeatureName(String line) {
    return stripTrailingPunctuation(line.replaceFirst("(?iu)^(feature|tính năng|tinh nang)\\s*:?\\s+", "")).trim();
  }

  private String deriveEntryAction(String featureName, String requirement, boolean vietnamese) {
    String normalized = normalizeForMatch(featureName + " " + requirement);
    if (containsAny(normalized, "delete product", "xoa san pham")) {
      return vietnamese ? "Nhấn Xóa" : "Click Delete";
    }

    if (containsAny(normalized, "checkout", "cart", "payment", "thanh toan", "gio hang")) {
      return vietnamese ? "Bắt đầu thanh toán" : "Start Checkout";
    }

    if (containsAny(normalized, "registration", "register", "dang ky")) {
      return vietnamese ? "Bắt đầu đăng ký" : "Start Registration";
    }

    if (containsAny(normalized, "login", "sign in", "dang nhap")) {
      return vietnamese ? "Mở màn hình đăng nhập" : "Open Login Screen";
    }

    if (containsAny(normalized, "approval", "approve", "phe duyet")) {
      return vietnamese ? "Mở yêu cầu phê duyệt" : "Open Approval Request";
    }

    if (containsAny(normalized, "create order", "tao don hang")) {
      return vietnamese ? "Bắt đầu tạo đơn hàng" : "Start Create Order";
    }

    if (featureName.isBlank()) {
      return vietnamese ? "Bắt đầu yêu cầu" : "Start Requirement";
    }

    return vietnamese ? "Bắt đầu " + capitalizeFirst(featureName) : "Start " + toTitleCase(featureName);
  }

  private String deriveDecisionLabel(
      List<ConditionBranch> branches, String requirement, boolean vietnamese) {
    String combined =
        normalizeForMatch(
            requirement
                + " "
                + branches.stream()
                    .map(ConditionBranch::condition)
                    .reduce("", (left, right) -> left + " " + right));

    if (containsAny(combined, "confirm", "cancel", "xac nhan", "huy")) {
      return vietnamese ? "Hộp thoại xác nhận" : "Confirm Dialog";
    }

    if (containsAny(combined, "credential", "login", "password", "dang nhap", "mat khau")) {
      return vietnamese ? "Kết quả đăng nhập" : "Login Result";
    }

    if (containsAny(combined, "payment", "checkout", "cart", "thanh toan")) {
      return vietnamese ? "Kết quả thanh toán" : "Payment Result";
    }

    if (containsAny(combined, "approval", "approve", "reject", "phe duyet")) {
      return vietnamese ? "Kết quả phê duyệt" : "Approval Decision";
    }

    return vietnamese ? "Điều kiện" : "Decision";
  }

  private String summarizeCondition(String condition, boolean vietnamese) {
    String normalized = normalizeForMatch(condition);
    if (containsAny(normalized, "confirm", "xac nhan")) {
      return vietnamese ? "Xác nhận" : "Confirm";
    }

    if (containsAny(normalized, "cancel", "huy")) {
      return vietnamese ? "Hủy" : "Cancel";
    }

    if (containsAny(normalized, "retry", "try again", "thu lai")) {
      return vietnamese ? "Thử lại" : "Retry";
    }

    if (containsAny(normalized, "missing", "invalid", "not found", "khong hop le", "thieu")) {
      return vietnamese ? "Không hợp lệ" : "Invalid";
    }

    if (containsAny(normalized, "error", "cannot", "unable", "exception", "loi")) {
      return vietnamese ? "Lỗi" : "Error";
    }

    if (containsAny(normalized, "success", "succeed", "thanh cong")) {
      return vietnamese ? "Thành công" : "Success";
    }

    if (containsAny(normalized, "fail", "failure", "that bai")) {
      return vietnamese ? "Thất bại" : "Failure";
    }

    if (containsAny(normalized, "approved", "approve")) {
      return vietnamese ? "Phê duyệt" : "Approved";
    }

    if (containsAny(normalized, "rejected", "reject")) {
      return vietnamese ? "Từ chối" : "Rejected";
    }

    if (containsWholeWord(normalized, "yes")
        || containsWholeWord(normalized, "true")
        || containsWholeWord(normalized, "co")
        || containsWholeWord(normalized, "dung")) {
      return vietnamese ? "Có" : "Yes";
    }

    if (containsWholeWord(normalized, "no")
        || containsWholeWord(normalized, "false")
        || containsAny(normalized, "khong", "sai")) {
      return vietnamese ? "Không" : "No";
    }

    if (containsAny(normalized, "otherwise", "else", "nguoc lai")) {
      return vietnamese ? "Ngược lại" : "Otherwise";
    }

    return vietnamese ? capitalizeFirst(condition) : toTitleCase(condition);
  }

  private String normalizeActionLabel(String action, boolean vietnamese) {
    String normalized = normalizeForMatch(action);
    if (containsAny(normalized, "do nothing", "khong lam gi", "khong thuc hien")) {
      return vietnamese ? "Không làm gì" : "Do Nothing";
    }

    if (containsAny(normalized, "delete product", "xoa san pham")) {
      return vietnamese ? "Xóa sản phẩm" : "Delete Product";
    }

    if (containsAny(normalized, "create order", "tao don hang")) {
      return vietnamese ? "Tạo đơn hàng" : "Create Order";
    }

    if (containsAny(normalized, "show payment error", "hien thi loi thanh toan")) {
      return vietnamese ? "Hiển thị lỗi thanh toán" : "Show Payment Error";
    }

    if (containsAny(normalized, "show validation error", "hien thi loi xac thuc")) {
      return vietnamese ? "Hiển thị lỗi xác thực" : "Show Validation Error";
    }

    if (containsAny(normalized, "show system error", "hien thi loi he thong")) {
      return vietnamese ? "Hiển thị lỗi hệ thống" : "Show System Error";
    }

    if (containsAny(normalized, "show error", "hien thi loi")) {
      return vietnamese ? "Hiển thị lỗi" : "Show Error";
    }

    String cleaned =
        stripTrailingPunctuation(
            action
                .replaceFirst("(?iu)^(system|the system|hệ thống|he thong)\\s+", "")
                .replaceFirst(":$", "")
                .trim());
    return vietnamese ? capitalizeFirst(cleaned) : toTitleCase(cleaned);
  }

  private boolean shouldAppendSuccess(String actionLabel) {
    String normalized = normalizeForMatch(actionLabel);
    if (containsAny(normalized, "do nothing", "khong lam gi", "error", "loi", "return", "retry")) {
      return false;
    }

    return containsAny(
        normalized, "delete", "remove", "create", "update", "save", "approve", "xoa", "tao", "cap nhat", "luu");
  }

  private boolean isSuccessResultAction(String actionLabel) {
    return containsAny(normalizeForMatch(actionLabel), "success", "thanh cong");
  }

  private boolean isFlowStartMarker(String value) {
    String normalized = normalizeForMatch(stripTrailingPunctuation(value));
    return containsAny(normalized, "start", "begin", "bat dau") && normalized.length() <= 12;
  }

  private boolean isFlowEndMarker(String value) {
    String normalized = normalizeForMatch(stripTrailingPunctuation(value));
    return containsAny(normalized, "end", "finish", "complete", "ket thuc") && normalized.length() <= 16;
  }

  private Flowchart buildFlowchart(List<FlowNode> nodes, List<FlowEdge> edges) {
    return new Flowchart(nodes, edges, buildMermaid(nodes, edges));
  }

  private String buildMermaid(List<FlowNode> nodes, List<FlowEdge> edges) {
    StringBuilder mermaid = new StringBuilder("flowchart TD\n");
    for (FlowNode node : nodes) {
      mermaid.append("  ").append(node.id()).append(formatNode(node)).append('\n');
    }

    for (FlowEdge edge : edges) {
      mermaid.append("  ").append(edge.source()).append(" --> ");
      if (edge.label() != null && !edge.label().isBlank()) {
        mermaid.append("|").append(escapeMermaid(edge.label())).append("| ");
      }
      mermaid.append(edge.target()).append('\n');
    }

    return mermaid.toString();
  }

  private String formatNode(FlowNode node) {
    String label = escapeMermaid(node.label());
    return switch (node.type()) {
      case START, END -> "([\"" + label + "\"])";
      case DECISION -> "{\"" + label + "\"}";
      case ACTION -> "[\"" + label + "\"]";
    };
  }

  private String escapeMermaid(String label) {
    return label.replace("\\", "\\\\").replace("\"", "\\\"").replace("|", "\\|");
  }

  private void addEdge(List<FlowEdge> edges, String source, String target, String label) {
    addEdge(edges, source, target, label, FlowEdgeType.DEFAULT);
  }

  private void addEdge(
      List<FlowEdge> edges, String source, String target, String label, FlowEdgeType type) {
    edges.add(new FlowEdge("edge_" + (edges.size() + 1), source, target, label, type));
  }

  private FlowEdgeType detectEdgeType(String value) {
    return toFlowEdgeType(detectTransitionSemantic(value));
  }

  private FlowEdgeType toFlowEdgeType(WfmTransitionSemantic semantic) {
    return switch (semantic) {
      case YES -> FlowEdgeType.YES;
      case NO -> FlowEdgeType.NO;
      case SUCCESS -> FlowEdgeType.SUCCESS;
      case FAILURE -> FlowEdgeType.FAILURE;
      case CANCEL -> FlowEdgeType.CANCEL;
      case RETRY -> FlowEdgeType.RETRY;
      case TIMEOUT -> FlowEdgeType.TIMEOUT;
      case DEFAULT -> FlowEdgeType.DEFAULT;
    };
  }

  private WfmTransitionSemantic detectTransitionSemantic(String value) {
    String normalized = normalizeForMatch(value);

    if (containsAny(normalized, "retry", "try again", "return to", "thu lai")) {
      return WfmTransitionSemantic.RETRY;
    }

    if (containsAny(normalized, "timeout", "timed out", "qua han", "het thoi gian")) {
      return WfmTransitionSemantic.TIMEOUT;
    }

    if (containsAny(
        normalized,
        "fail",
        "failure",
        "failed",
        "error",
        "cannot",
        "unable",
        "unavailable",
        "exception",
        "system error",
        "loi",
        "that bai")) {
      return WfmTransitionSemantic.FAILURE;
    }

    if (containsAny(
        normalized, "success", "succeed", "succeeds", "succeeded", "successfully", "thanh cong")) {
      return WfmTransitionSemantic.SUCCESS;
    }

    if (containsAny(normalized, "cancel", "cancels", "cancelled", "canceled", "huy")) {
      return WfmTransitionSemantic.CANCEL;
    }

    if (containsAny(
        normalized,
        "reject",
        "rejects",
        "rejected",
        "if not",
        "not found",
        "not exist",
        "does not exist",
        "missing",
        "invalid",
        "khong hop le",
        "thieu",
        "otherwise",
        "else",
        "khong")) {
      return WfmTransitionSemantic.NO;
    }

    if (containsWholeWord(normalized, "no")) {
      return WfmTransitionSemantic.NO;
    }

    if (containsAny(
        normalized,
        "confirm",
        "valid",
        "provided",
        "complete",
        "exists",
        "found",
        "approved",
        "approve",
        "if pass",
        "pass",
        "xac nhan")) {
      return WfmTransitionSemantic.YES;
    }

    if (containsWholeWord(normalized, "yes")
        || containsWholeWord(normalized, "true")
        || containsWholeWord(normalized, "co")
        || containsWholeWord(normalized, "dung")) {
      return WfmTransitionSemantic.YES;
    }

    return WfmTransitionSemantic.DEFAULT;
  }

  private boolean isVietnamese(String value) {
    String lower = value.toLowerCase(Locale.ROOT);
    return VIETNAMESE_MARKS.matcher(lower).find()
        || containsAny(normalizeForMatch(value), "neu", "nguoi dung", "tinh nang", "san pham", "khong lam gi");
  }

  private String normalizeForMatch(String value) {
    String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
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

  private boolean containsWholeWord(String value, String candidate) {
    return Pattern.compile("(^|[^a-z0-9])" + Pattern.quote(candidate) + "([^a-z0-9]|$)")
        .matcher(value)
        .find();
  }

  private String toTitleCase(String value) {
    String[] words = value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim().split(" ");
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

  private String stripTrailingPunctuation(String value) {
    return value.trim().replaceFirst("[\\s.;:]+$", "").trim();
  }

  private record ParsedRequirement(List<String> sequenceActions, List<ConditionBranch> branches) {}

  private record ConditionBranch(String condition, List<String> outcomes) {}

  private static final class NodeIdSequence {
    private int nextId = 1;

    private String next() {
      return "N" + nextId++;
    }
  }

  private static final class BranchBuilder {
    private final String condition;
    private final List<String> outcomes = new ArrayList<>();
    private boolean terminates;

    private BranchBuilder(String condition) {
      this.condition = condition;
    }

    private void addOutcome(String outcome) {
      outcomes.add(outcome);
    }

    private void markTerminates() {
      terminates = true;
    }

    private ConditionBranch toBranch(boolean vietnamese) {
      if (outcomes.isEmpty() && !terminates) {
        String fallback = vietnamese ? "Hoàn tất nhánh" : "Complete Path";
        return new ConditionBranch(condition, List.of(fallback));
      }

      return new ConditionBranch(condition, List.copyOf(outcomes));
    }
  }
}
