package com.reqpilot.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.reqpilot.model.Flowchart;
import com.reqpilot.model.GeneratedTestCase;
import com.reqpilot.model.TestCasePriority;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleBasedTestCaseGeneratorTest {

  private final RuleBasedRequirementAnalyzer analyzer = new RuleBasedRequirementAnalyzer();
  private final RuleBasedTestCaseGenerator generator = new RuleBasedTestCaseGenerator();

  @Test
  void generatesTestCasesForEachFlowPath() {
    String requirement =
        """
        Feature delete product:

        * If user confirms, delete product
        * If user cancels, do nothing
        """;
    Flowchart flowchart = analyzer.analyze(requirement);

    List<GeneratedTestCase> testCases = generator.generate(requirement, flowchart);

    assertThat(testCases).hasSize(2);
    assertThat(testCases.get(0).id()).isEqualTo("TC001");
    assertThat(testCases.get(0).title()).isEqualTo("Delete product successfully");
    assertThat(testCases.get(0).steps()).containsExactly("Click Delete", "Click confirm", "Complete the success path");
    assertThat(testCases.get(0).expectedResult())
        .isEqualTo("Product is deleted and success message is shown");
    assertThat(testCases.get(0).priority()).isEqualTo(TestCasePriority.HIGH);

    assertThat(testCases.get(1).id()).isEqualTo("TC002");
    assertThat(testCases.get(1).title()).isEqualTo("Cancel delete product");
    assertThat(testCases.get(1).steps()).containsExactly("Click Delete", "Click cancel");
    assertThat(testCases.get(1).expectedResult()).isEqualTo("Product is not deleted");
    assertThat(testCases.get(1).priority()).isEqualTo(TestCasePriority.MEDIUM);
  }

  @Test
  void generatesVietnameseTestCases() {
    String requirement =
        """
        Tính năng xóa sản phẩm:

        * Nếu người dùng xác nhận, xóa sản phẩm
        * Nếu người dùng hủy, không làm gì
        """;
    Flowchart flowchart = analyzer.analyze(requirement);

    List<GeneratedTestCase> testCases = generator.generate(requirement, flowchart);

    assertThat(testCases).hasSize(2);
    assertThat(testCases.get(0).title()).isEqualTo("Xóa sản phẩm thành công");
    assertThat(testCases.get(0).steps()).containsExactly("Nhấn Xóa", "Chọn xác nhận", "Hoàn tất nhánh thành công");
    assertThat(testCases.get(1).title()).isEqualTo("Hủy xóa sản phẩm");
    assertThat(testCases.get(1).steps()).containsExactly("Nhấn Xóa", "Chọn hủy");
  }

  @Test
  void generatesLoginTestCasesForHappyInvalidRetryCancelAndErrorPaths() {
    String requirement =
        """
        Feature: User Login

        User enters username and password.
        System validates the input.

        If credentials are valid:
        - Continue to credential validation.

        If authentication succeeds:
        - Authenticate user.
        - Generate access token.
        - Redirect to Dashboard.

        If credentials are invalid:
        - Show "Invalid username or password".
        - Allow user to retry.

        If retry:
        - Return to Login screen.

        If user cancels login:
        - Return to Home page.

        If system cannot connect to authentication server:
        - Show system error.
        """;

    List<GeneratedTestCase> testCases = generator.generate(requirement, analyzer.analyze(requirement));

    assertCompleteTestCases(testCases, 6);
    assertThat(testCases).extracting(GeneratedTestCase::title)
        .contains("Login successfully", "Handle invalid login", "Retry login", "Cancel login", "Handle login error");
    assertThat(testCases).anySatisfy((testCase) -> {
      assertThat(testCase.title()).isEqualTo("Retry login");
      assertThat(testCase.steps()).anyMatch((step) -> step.toLowerCase().contains("retry"));
      assertThat(testCase.expectedResult()).contains("retry");
    });
  }

  @Test
  void generatesRegistrationTestCasesForValidInvalidAndFailurePaths() {
    String requirement =
        """
        Feature: User Registration

        User opens registration form.
        User enters profile information.
        System validates required fields.

        If account data is valid:
        - Create user account.
        - Send welcome email.

        If required information is missing:
        - Show validation error.

        If account creation fails:
        - Show registration failure message.
        """;

    List<GeneratedTestCase> testCases = generator.generate(requirement, analyzer.analyze(requirement));

    assertCompleteTestCases(testCases, 3);
    assertThat(testCases).extracting(GeneratedTestCase::title)
        .contains("Register user successfully", "Handle invalid registration", "Handle registration error");
  }

  @Test
  void generatesCheckoutTestCasesForHappyCancelAndPaymentFailurePaths() {
    String requirement =
        """
        Feature: Checkout Order

        User opens cart.
        User reviews order summary.
        System calculates shipping and tax.

        If cart is valid:
        - Proceed to payment.

        If user cancels checkout:
        - Return to cart.

        If payment succeeds:
        - Create order and show receipt.

        If payment fails:
        - Show payment error.
        - Keep order pending.
        """;

    List<GeneratedTestCase> testCases = generator.generate(requirement, analyzer.analyze(requirement));

    assertCompleteTestCases(testCases, 4);
    assertThat(testCases).extracting(GeneratedTestCase::title)
        .contains("Checkout order successfully", "Cancel checkout", "Handle checkout error");
    assertThat(testCases).anySatisfy((testCase) -> {
      assertThat(testCase.title()).isEqualTo("Handle checkout error");
      assertThat(testCase.expectedResult()).contains("Payment error");
      assertThat(testCase.priority()).isEqualTo(TestCasePriority.HIGH);
    });
  }

  @Test
  void generatesApprovalTestCasesForApproveRejectAndPolicyFailurePaths() {
    String requirement =
        """
        Feature: Expense Approval Workflow

        Employee submits expense request.
        Manager reviews request details.
        System checks policy rules.

        If manager approves request:
        - Mark expense as approved.
        - Send approval notification.

        If manager rejects request:
        - Return request to employee.

        If policy validation succeeds:
        - Send approval notification.

        If policy validation fails:
        - Show policy violation error.
        """;

    List<GeneratedTestCase> testCases = generator.generate(requirement, analyzer.analyze(requirement));

    assertCompleteTestCases(testCases, 4);
    assertThat(testCases).extracting(GeneratedTestCase::title)
        .contains("Approve request", "Reject approval request", "Handle approval error");
  }

  private void assertCompleteTestCases(List<GeneratedTestCase> testCases, int expectedSize) {
    assertThat(testCases).hasSize(expectedSize);
    for (int index = 0; index < testCases.size(); index++) {
      GeneratedTestCase testCase = testCases.get(index);
      assertThat(testCase.id()).isEqualTo("TC%03d".formatted(index + 1));
      assertThat(testCase.title()).isNotBlank();
      assertThat(testCase.preconditions()).isNotBlank();
      assertThat(testCase.steps()).isNotEmpty();
      assertThat(testCase.expectedResult()).isNotBlank();
      assertThat(testCase.priority()).isNotNull();
    }
  }
}
