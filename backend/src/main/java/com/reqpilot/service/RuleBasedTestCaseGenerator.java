package com.reqpilot.service;

import com.reqpilot.model.FlowEdge;
import com.reqpilot.model.FlowEdgeType;
import com.reqpilot.model.FlowNode;
import com.reqpilot.model.FlowNodeType;
import com.reqpilot.model.Flowchart;
import com.reqpilot.model.GeneratedTestCase;
import com.reqpilot.model.TestCasePriority;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class RuleBasedTestCaseGenerator implements TestCaseGenerator {

  private static final Pattern VIETNAMESE_MARKS =
      Pattern.compile("[ăâđêôơưáàảãạấầẩẫậắằẳẵặéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ]");

  @Override
  public List<GeneratedTestCase> generate(String requirement, Flowchart flowchart) {
    if (flowchart.nodes().isEmpty()) {
      throw new IllegalArgumentException("Flowchart must contain at least one node");
    }

    boolean vietnamese = isVietnamese(requirement);
    List<FlowPath> paths = enumeratePaths(flowchart);
    List<GeneratedTestCase> testCases = new ArrayList<>();

    for (int index = 0; index < paths.size(); index++) {
      testCases.add(toTestCase(index + 1, paths.get(index), requirement, vietnamese));
    }

    return testCases;
  }

  private List<FlowPath> enumeratePaths(Flowchart flowchart) {
    Map<String, FlowNode> nodesById = new LinkedHashMap<>();
    for (FlowNode node : flowchart.nodes()) {
      nodesById.put(node.id(), node);
    }

    FlowNode startNode =
        flowchart.nodes().stream()
            .filter((node) -> node.type() == FlowNodeType.START)
            .findFirst()
            .orElse(flowchart.nodes().getFirst());

    Map<String, List<FlowEdge>> edgesBySource = new HashMap<>();
    for (FlowEdge edge : flowchart.edges()) {
      edgesBySource.computeIfAbsent(edge.source(), ignored -> new ArrayList<>()).add(edge);
    }
    edgesBySource.values().forEach((edges) -> edges.sort(Comparator.comparing(FlowEdge::id)));

    List<FlowPath> paths = new ArrayList<>();
    List<FlowNode> nodePath = new ArrayList<>();
    List<FlowEdge> edgePath = new ArrayList<>();
    Set<String> visiting = new HashSet<>();
    nodePath.add(startNode);
    visiting.add(startNode.id());
    dfs(startNode, nodesById, edgesBySource, visiting, nodePath, edgePath, paths, flowchart.nodes().size() + 5);

    if (paths.isEmpty()) {
      paths.add(new FlowPath(flowchart.nodes(), List.of()));
    }

    return paths;
  }

  private void dfs(
      FlowNode current,
      Map<String, FlowNode> nodesById,
      Map<String, List<FlowEdge>> edgesBySource,
      Set<String> visiting,
      List<FlowNode> nodePath,
      List<FlowEdge> edgePath,
      List<FlowPath> paths,
      int maxDepth) {
    if (nodePath.size() > maxDepth) {
      paths.add(new FlowPath(List.copyOf(nodePath), List.copyOf(edgePath)));
      return;
    }

    if (current.type() == FlowNodeType.END) {
      paths.add(new FlowPath(List.copyOf(nodePath), List.copyOf(edgePath)));
      return;
    }

    List<FlowEdge> outgoing = edgesBySource.getOrDefault(current.id(), List.of());
    if (outgoing.isEmpty()) {
      paths.add(new FlowPath(List.copyOf(nodePath), List.copyOf(edgePath)));
      return;
    }

    for (FlowEdge edge : outgoing) {
      FlowNode next = nodesById.get(edge.target());
      if (next == null) {
        continue;
      }

      edgePath.add(edge);
      if (visiting.contains(next.id())) {
        paths.add(new FlowPath(List.copyOf(nodePath), List.copyOf(edgePath)));
        edgePath.removeLast();
        continue;
      }

      nodePath.add(next);
      visiting.add(next.id());
      dfs(next, nodesById, edgesBySource, visiting, nodePath, edgePath, paths, maxDepth);
      visiting.remove(next.id());
      nodePath.removeLast();
      edgePath.removeLast();
    }
  }

  private GeneratedTestCase toTestCase(
      int number, FlowPath path, String requirement, boolean vietnamese) {
    List<String> actionLabels =
        path.nodes().stream()
            .filter((node) -> node.type() == FlowNodeType.ACTION)
            .map(FlowNode::label)
            .toList();
    List<String> branchLabels =
        path.edges().stream()
            .map((edge) -> edgeBranchLabel(edge, vietnamese))
            .filter((label) -> label != null && !label.isBlank())
            .toList();
    String context = normalizeForMatch(requirement + " " + String.join(" ", actionLabels));
    String branchText = normalizeForMatch(String.join(" ", branchLabels));
    String pathText =
        normalizeForMatch(String.join(" ", actionLabels) + " " + String.join(" ", branchLabels));

    return new GeneratedTestCase(
        "TC%03d".formatted(number),
        buildTitle(pathText, branchText, context, vietnamese),
        buildPreconditions(context, vietnamese),
        buildSteps(path, vietnamese),
        buildExpectedResult(pathText, branchText, context, vietnamese),
        buildPriority(pathText, branchText, context));
  }

  private String buildTitle(String pathText, String branchText, String context, boolean vietnamese) {
    if (isRetryPath(pathText, branchText)) {
      return vietnamese ? "Thử lại luồng" : "Retry " + featureNoun(context);
    }

    if (isCancelPath(pathText, branchText)) {
      if (containsAny(context, "product", "san pham")) {
        return vietnamese ? "Hủy xóa sản phẩm" : "Cancel delete product";
      }
      return vietnamese ? "Hủy thao tác" : "Cancel " + featureNoun(context);
    }

    if (isInvalidPath(pathText, branchText)) {
      return vietnamese ? "Xử lý dữ liệu không hợp lệ" : "Handle invalid " + featureNoun(context);
    }

    if (containsAny(pathText + " " + branchText, "reject", "rejected")) {
      return vietnamese ? "Từ chối yêu cầu" : "Reject approval request";
    }

    if (isErrorPath(pathText, branchText)) {
      return vietnamese ? "Xử lý lỗi" : "Handle " + featureNoun(context) + " error";
    }

    if (containsAny(pathText, "delete product", "xoa san pham")) {
      return vietnamese ? "Xóa sản phẩm thành công" : "Delete product successfully";
    }

    if (containsAny(context, "login", "sign in", "dang nhap")) {
      return vietnamese ? "Đăng nhập thành công" : "Login successfully";
    }

    if (containsAny(context, "registration", "register", "dang ky")) {
      return vietnamese ? "Đăng ký thành công" : "Register user successfully";
    }

    if (containsAny(context, "checkout", "cart", "payment", "order", "thanh toan", "gio hang")) {
      return vietnamese ? "Thanh toán thành công" : "Checkout order successfully";
    }

    if (containsAny(context, "approval", "approve", "approved", "phe duyet")) {
      return vietnamese ? "Phê duyệt yêu cầu" : "Approve request";
    }

    if (!branchText.isBlank()) {
      String readableBranch = branchText.replaceAll("\\s+", " ").trim();
      return vietnamese ? "Kiểm tra nhánh " + readableBranch : "Validate " + readableBranch + " path";
    }

    return vietnamese ? "Kiểm tra luồng chính" : "Validate main flow";
  }

  private String buildPreconditions(String combined, boolean vietnamese) {
    if (containsAny(combined, "product", "san pham")) {
      return vietnamese
          ? "Người dùng đã đăng nhập và sản phẩm tồn tại"
          : "User is logged in and product exists";
    }

    if (containsAny(combined, "login", "sign in", "dang nhap")) {
      return vietnamese ? "Người dùng đang ở màn hình đăng nhập" : "User is on the login screen";
    }

    if (containsAny(combined, "registration", "register", "dang ky")) {
      return vietnamese ? "Người dùng đang ở màn hình đăng ký" : "User is on the registration page";
    }

    if (containsAny(combined, "order", "checkout", "cart", "don hang", "gio hang")) {
      return vietnamese
          ? "Người dùng đã đăng nhập và giỏ hàng có sản phẩm"
          : "User is logged in and the cart has items";
    }

    if (containsAny(combined, "approval", "approve", "expense", "phe duyet")) {
      return vietnamese
          ? "Yêu cầu đã được gửi và người phê duyệt đã đăng nhập"
          : "Request is submitted and approver is logged in";
    }

    return vietnamese
        ? "Người dùng đã đăng nhập và dữ liệu cần thiết tồn tại"
        : "User is authenticated and required data exists";
  }

  private List<String> buildSteps(FlowPath path, boolean vietnamese) {
    List<String> steps = new ArrayList<>();

    for (int index = 0; index < path.nodes().size(); index++) {
      FlowNode node = path.nodes().get(index);
      if (index > 0 && index - 1 < path.edges().size()) {
        String branchLabel = edgeBranchLabel(path.edges().get(index - 1), vietnamese);
        if (branchLabel != null && !branchLabel.isBlank()) {
          steps.add(toDecisionStep(branchLabel, vietnamese));
        }
      }

      if (node.type() == FlowNodeType.ACTION && shouldIncludeActionAsStep(node.label(), steps.isEmpty())) {
        steps.add(toUserActionStep(node.label(), vietnamese));
      }
    }

    if (steps.isEmpty()) {
      steps.add(vietnamese ? "Thực hiện luồng chính" : "Execute the main flow");
    }

    return List.copyOf(new LinkedHashSet<>(steps));
  }

  private String buildExpectedResult(String pathText, String branchText, String context, boolean vietnamese) {
    if (isRetryPath(pathText, branchText)) {
      return vietnamese
          ? "Người dùng được đưa về bước phù hợp để thử lại"
          : "User is returned to the appropriate step and can retry";
    }

    if (isCancelPath(pathText, branchText)) {
      if (containsAny(context, "product", "san pham")) {
        return vietnamese ? "Sản phẩm không bị xóa" : "Product is not deleted";
      }
      if (containsAny(context, "checkout", "cart", "payment", "gio hang")) {
        return vietnamese ? "Đơn hàng không được tạo và người dùng quay lại giỏ hàng" : "Order is not created and user returns to cart";
      }
      return vietnamese ? "Dữ liệu không thay đổi" : "No data is changed";
    }

    if (isInvalidPath(pathText, branchText)) {
      return vietnamese
          ? "Thông báo dữ liệu không hợp lệ được hiển thị và người dùng có thể sửa lại"
          : "Invalid input is rejected and the user can correct the data";
    }

    if (containsAny(pathText, "show payment error", "hien thi loi thanh toan")) {
      return vietnamese
          ? "Thông báo lỗi thanh toán được hiển thị và đơn hàng không được tạo"
          : "Payment error is shown and the order is not created";
    }

    if (isErrorPath(pathText, branchText)) {
      return vietnamese
          ? "Thông báo lỗi được hiển thị và thao tác không hoàn tất"
          : "An error message is shown and the operation is not completed";
    }

    if (containsAny(pathText, "reject", "rejected")) {
      return vietnamese ? "Yêu cầu bị từ chối và được trả về người gửi" : "Request is rejected and returned to the requester";
    }

    if (containsAny(pathText, "delete product", "xoa san pham")) {
      return vietnamese
          ? "Sản phẩm được xóa và thông báo thành công được hiển thị"
          : "Product is deleted and success message is shown";
    }

    if (containsAny(context, "login", "sign in", "dang nhap")) {
      return vietnamese ? "Người dùng đăng nhập thành công và vào hệ thống" : "User is authenticated and redirected successfully";
    }

    if (containsAny(context, "registration", "register", "dang ky")) {
      return vietnamese ? "Tài khoản được tạo thành công" : "Account is created successfully";
    }

    if (containsAny(context, "checkout", "cart", "payment", "order", "gio hang", "thanh toan")) {
      return vietnamese ? "Đơn hàng được tạo và biên nhận được hiển thị" : "Order is created and receipt is shown";
    }

    if (containsAny(context, "approval", "approve", "approved", "phe duyet")) {
      return vietnamese ? "Yêu cầu được phê duyệt và thông báo được gửi" : "Request is approved and notification is sent";
    }

    return vietnamese
        ? "Luồng hoàn tất với kết quả mong đợi"
        : "The selected path completes with the expected result";
  }

  private TestCasePriority buildPriority(String pathText, String branchText, String context) {
    if (isCancelPath(pathText, branchText) || isRetryPath(pathText, branchText) || isInvalidPath(pathText, branchText)) {
      return TestCasePriority.MEDIUM;
    }

    if (isErrorPath(pathText, branchText)) {
      return TestCasePriority.HIGH;
    }

    if (containsAny(
        context,
        "delete",
        "remove",
        "create",
        "payment",
        "checkout",
        "login",
        "registration",
        "approval",
        "xoa",
        "tao",
        "thanh toan")) {
      return TestCasePriority.HIGH;
    }

    return TestCasePriority.MEDIUM;
  }

  private boolean shouldIncludeActionAsStep(String label, boolean firstStep) {
    String normalized = normalizeForMatch(label);
    if (firstStep) {
      return true;
    }

    if (isSystemResult(label)) {
      return false;
    }

    return containsAny(
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
        "retry",
        "return",
        "go back",
        "nhan",
        "mo",
        "nhap",
        "chon",
        "thu lai",
        "quay lai");
  }

  private boolean isSystemResult(String label) {
    String normalized = normalizeForMatch(label);
    return containsAny(
        normalized,
        "show success",
        "show error",
        "show validation",
        "show payment",
        "show system",
        "redirect",
        "generate token",
        "send notification",
        "hien thi");
  }

  private String toUserActionStep(String label, boolean vietnamese) {
    String normalized = normalizeForMatch(label);
    if (containsAny(normalized, "click", "open", "start", "enter", "submit", "select", "review", "return", "nhan", "mo", "bat dau", "nhap", "chon")) {
      return label;
    }

    return vietnamese ? "Thực hiện " + lowerFirst(label) : "Perform " + lowerFirst(label);
  }

  private String toDecisionStep(String branchLabel, boolean vietnamese) {
    String normalized = normalizeForMatch(branchLabel);
    if (containsAny(normalized, "confirm", "xac nhan")) {
      return vietnamese ? "Chọn xác nhận" : "Click confirm";
    }

    if (containsAny(normalized, "cancel", "huy", "khong") || containsWholeWord(normalized, "no")) {
      return vietnamese ? "Chọn hủy" : "Click cancel";
    }

    if (containsAny(normalized, "invalid", "khong hop le")) {
      return vietnamese ? "Nhập dữ liệu không hợp lệ" : "Use invalid data";
    }

    if (containsAny(normalized, "error", "loi")) {
      return vietnamese ? "Giả lập lỗi hệ thống" : "Simulate the error condition";
    }

    if (containsAny(normalized, "retry", "thu lai")) {
      return vietnamese ? "Chọn thử lại" : "Choose retry";
    }

    if (containsAny(normalized, "success", "thanh cong")) {
      return vietnamese ? "Hoàn tất nhánh thành công" : "Complete the success path";
    }

    if (containsAny(normalized, "failure", "fail", "that bai")) {
      return vietnamese ? "Hoàn tất nhánh thất bại" : "Complete the failure path";
    }

    if (containsAny(normalized, "approved", "approve")) {
      return vietnamese ? "Chọn phê duyệt" : "Approve the request";
    }

    if (containsAny(normalized, "rejected", "reject")) {
      return vietnamese ? "Chọn từ chối" : "Reject the request";
    }

    if (containsWholeWord(normalized, "yes")
        || containsWholeWord(normalized, "co")
        || containsWholeWord(normalized, "dung")) {
      return vietnamese ? "Chọn có" : "Choose yes";
    }

    return vietnamese ? "Chọn " + lowerFirst(branchLabel) : "Choose " + lowerFirst(branchLabel);
  }

  private boolean isVietnamese(String value) {
    String lower = value.toLowerCase(Locale.ROOT);
    return VIETNAMESE_MARKS.matcher(lower).find()
        || containsAny(normalizeForMatch(value), "neu", "nguoi dung", "tinh nang", "san pham", "khong lam gi");
  }

  private String edgeBranchLabel(FlowEdge edge, boolean vietnamese) {
    if (edge.label() != null && !edge.label().isBlank()) {
      return edge.label();
    }

    FlowEdgeType type = edge.type() == null ? FlowEdgeType.DEFAULT : edge.type();
    return switch (type) {
      case YES -> vietnamese ? "Có" : "Yes";
      case NO -> vietnamese ? "Không" : "No";
      case SUCCESS -> vietnamese ? "Thành công" : "Success";
      case FAILURE -> vietnamese ? "Thất bại" : "Failure";
      case CANCEL -> vietnamese ? "Hủy" : "Cancel";
      case RETRY -> vietnamese ? "Thử lại" : "Retry";
      case TIMEOUT -> vietnamese ? "Hết thời gian" : "Timeout";
      case DEFAULT -> "";
    };
  }

  private boolean isCancelPath(String pathText, String branchText) {
    String combined = branchText + " " + pathText;
    return containsAny(combined, "cancel", "huy", "khong", "do nothing", "khong lam gi")
        || containsWholeWord(combined, "no");
  }

  private boolean isInvalidPath(String pathText, String branchText) {
    return containsAny(branchText + " " + pathText, "invalid", "validation error", "missing", "not found", "khong hop le");
  }

  private boolean isErrorPath(String pathText, String branchText) {
    return containsAny(branchText + " " + pathText, "error", "failure", "fail", "cannot", "unable", "system error", "loi", "that bai");
  }

  private boolean isRetryPath(String pathText, String branchText) {
    return containsAny(branchText, "retry", "thu lai")
        || containsAny(pathText, "return to login", "return to form", "try again", "thu lai");
  }

  private String featureNoun(String context) {
    if (containsAny(context, "login", "sign in", "dang nhap")) {
      return "login";
    }
    if (containsAny(context, "registration", "register", "dang ky")) {
      return "registration";
    }
    if (containsAny(context, "checkout", "payment", "cart", "gio hang", "thanh toan")) {
      return "checkout";
    }
    if (containsAny(context, "approval", "approve", "expense", "phe duyet")) {
      return "approval";
    }
    if (containsAny(context, "product", "san pham")) {
      return "product flow";
    }
    return "flow";
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

  private String lowerFirst(String value) {
    String trimmed = value.trim();
    if (trimmed.isBlank()) {
      return trimmed;
    }
    return trimmed.substring(0, 1).toLowerCase(Locale.ROOT) + trimmed.substring(1);
  }

  private record FlowPath(List<FlowNode> nodes, List<FlowEdge> edges) {}
}
