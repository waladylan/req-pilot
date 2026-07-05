package com.reqpilot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqpilot.ai.AiJsonValidator;
import com.reqpilot.ai.AiProvider;
import com.reqpilot.ai.AiProviderException;
import com.reqpilot.ai.AiRequest;
import com.reqpilot.ai.AiResponse;
import com.reqpilot.ai.WfmPrompts;
import com.reqpilot.config.AiProperties;
import com.reqpilot.dto.RequirementAnalysisDto;
import com.reqpilot.dto.RequirementAnalysisModuleDto;
import com.reqpilot.dto.WfmDefinition;
import com.reqpilot.wfm.WfmValidator;
import java.util.List;
import org.junit.jupiter.api.Test;

class WfmGenerationServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void rejectsBlankInput() {
    WfmGenerationService service = service(new StubAiProvider(validWfmJson()), new StubUsageLogService());

    assertThatThrownBy(() -> service.generateFromRequirementAnalysisJson(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Requirement analysis JSON is required");
  }

  @Test
  void callsAiProviderWithWfmGenerationTaskType() {
    StubAiProvider provider = new StubAiProvider(validWfmJson());
    StubUsageLogService usageLogService = new StubUsageLogService();
    WfmGenerationService service = service(provider, usageLogService);

    WfmDefinition definition = service.generateFromRequirementAnalysis(requirementAnalysis());

    assertThat(provider.lastRequest.taskType()).isEqualTo(WfmPrompts.TASK_TYPE);
    assertThat(provider.lastRequest.systemPrompt()).contains("Convert Requirement Analysis JSON into WFM v1 JSON");
    assertThat(provider.lastRequest.userPrompt()).contains("Purchase Request");
    assertThat(provider.lastRequest.temperature()).isEqualTo(0.1);
    assertThat(definition.workflowName()).isEqualTo("Purchase Request Approval");
    assertThat(definition.nodes()).extracting(com.reqpilot.dto.WfmNode::kind).contains("approval");
    assertThat(usageLogService.loggedEntry.taskType()).isEqualTo(WfmPrompts.TASK_TYPE);
    assertThat(usageLogService.loggedEntry.totalTokens()).isEqualTo(30);
  }

  @Test
  void validatesAiResponseBeforeReturning() {
    StubAiProvider provider =
        new StubAiProvider(
            """
            {
              "workflowName": "Invalid",
              "version": "1.0",
              "actors": [],
              "nodes": [],
              "edges": [],
              "businessRules": [],
              "validations": [],
              "assumptions": [],
              "edgeCases": [],
              "openQuestions": [],
              "riskLevel": "LOW"
            }
            """);
    WfmGenerationService service = service(provider, new StubUsageLogService());

    assertThatThrownBy(() -> service.generateFromRequirementAnalysis(requirementAnalysis()))
        .isInstanceOf(AiProviderException.class)
        .hasMessageContaining("AI response WFM is invalid");
  }

  @Test
  void normalizesRepairableAiWfmBeforeReturning() {
    WfmGenerationService service = service(new StubAiProvider(badPurchaseApprovalWfmJson()), new StubUsageLogService());

    WfmDefinition definition = service.generateFromRequirementAnalysis(requirementAnalysis());

    assertThat(definition.nodes()).extracting(com.reqpilot.dto.WfmNode::id).contains("check_amount");
    assertThat(definition.edges())
        .noneMatch(
            (edge) ->
                edge.from().equals("manager_approval")
                    && List.of("amount > 5000", "amount <= 5000").contains(edge.condition()));
    assertThat(definition.edges())
        .anySatisfy(
            (edge) -> {
              assertThat(edge.from()).isEqualTo("manager_approval");
              assertThat(edge.to()).isEqualTo("check_amount");
              assertThat(edge.label()).isEqualTo("Approve");
              assertThat(edge.condition()).isEqualTo("Manager approves");
            });
    assertThat(definition.edges())
        .anySatisfy(
            (edge) -> {
              assertThat(edge.from()).isEqualTo("check_amount");
              assertThat(edge.to()).isEqualTo("finance_approval");
              assertThat(edge.condition()).isEqualTo("amount > 5000");
            });
  }

  private WfmGenerationService service(AiProvider provider, AiUsageLogService usageLogService) {
    return new WfmGenerationService(
        provider,
        aiProperties(),
        new AiJsonValidator(objectMapper),
        usageLogService,
        objectMapper,
        new WfmValidator());
  }

  private RequirementAnalysisDto requirementAnalysis() {
    return new RequirementAnalysisDto(
        "User can create a purchase request which requires manager approval.",
        List.of("User", "Manager"),
        List.of(
            new RequirementAnalysisModuleDto(
                "Purchase Request",
                "Module for creating and approving purchase requests.",
                List.of("Purchase Request Creation Screen", "Approval Screen"),
                List.of("Manager approval is required for all purchase requests."),
                List.of("Amount must be a positive number."),
                List.of("User creates a purchase request.", "Manager reviews and approves the request."),
                List.of("What happens if the manager rejects the request?"))),
        List.of("The system supports role-based access control."),
        List.of("Is there a time limit for approvals?"),
        "MEDIUM");
  }

  private String validWfmJson() {
    return """
        {
          "workflowName": "Purchase Request Approval",
          "version": "1.0",
          "summary": "User creates a purchase request and manager approves or rejects it.",
          "actors": ["User", "Manager"],
          "nodes": [
            {"id": "start", "kind": "start", "label": "Start", "actor": null, "description": null, "metadata": {}},
            {"id": "create_purchase_request", "kind": "action", "label": "Create purchase request", "actor": "User", "description": null, "metadata": {}},
            {"id": "manager_approval", "kind": "approval", "label": "Manager approval", "actor": "Manager", "description": null, "metadata": {}},
            {"id": "approved", "kind": "end", "label": "Request approved", "actor": null, "description": null, "metadata": {}},
            {"id": "rejected", "kind": "end", "label": "Request rejected", "actor": null, "description": null, "metadata": {}}
          ],
          "edges": [
            {"id": "edge_start_create", "from": "start", "to": "create_purchase_request", "label": null, "condition": null, "metadata": {}},
            {"id": "edge_create_approval", "from": "create_purchase_request", "to": "manager_approval", "label": null, "condition": null, "metadata": {}},
            {"id": "edge_approval_approved", "from": "manager_approval", "to": "approved", "label": "Approve", "condition": "Manager approves", "metadata": {}},
            {"id": "edge_approval_rejected", "from": "manager_approval", "to": "rejected", "label": "Reject", "condition": "Manager rejects", "metadata": {}}
          ],
          "businessRules": ["Manager approval is required for all purchase requests."],
          "validations": ["Amount must be a positive number."],
          "assumptions": ["The system supports role-based access control."],
          "edgeCases": ["What happens if the manager rejects the request?"],
          "openQuestions": ["Is there a time limit for approvals?"],
          "riskLevel": "MEDIUM"
        }
        """;
  }

  private String badPurchaseApprovalWfmJson() {
    return """
        {
          "workflowName": "PurchaseRequestApproval",
          "version": "1.0",
          "summary": "Purchase request approval workflow.",
          "actors": ["User", "Manager", "Finance"],
          "nodes": [
            {"id": "start", "kind": "start", "label": "Start", "actor": null, "description": null, "metadata": {}},
            {"id": "create_purchase_request", "kind": "action", "label": "Create Purchase Request", "actor": "User", "description": "User creates a purchase request.", "metadata": {}},
            {"id": "manager_approval", "kind": "approval", "label": "Manager Approval", "actor": "Manager", "description": "Manager reviews and approves the request.", "metadata": {}},
            {"id": "finance_approval", "kind": "approval", "label": "Finance Approval", "actor": "Finance", "description": "Finance reviews and approves the request if amount > 5000.", "metadata": {}},
            {"id": "end_approved", "kind": "end", "label": "End (Approved)", "actor": null, "description": null, "metadata": {}},
            {"id": "end_rejected", "kind": "end", "label": "End (Rejected)", "actor": null, "description": null, "metadata": {}}
          ],
          "edges": [
            {"id": "start_to_create", "from": "start", "to": "create_purchase_request", "label": null, "condition": null, "metadata": {}},
            {"id": "create_to_manager", "from": "create_purchase_request", "to": "manager_approval", "label": null, "condition": null, "metadata": {}},
            {"id": "manager_to_finance", "from": "manager_approval", "to": "finance_approval", "label": "amount > 5000", "condition": "amount > 5000", "metadata": {}},
            {"id": "manager_to_end_approved", "from": "manager_approval", "to": "end_approved", "label": "amount <= 5000", "condition": "amount <= 5000", "metadata": {}},
            {"id": "manager_to_end_rejected", "from": "manager_approval", "to": "end_rejected", "label": "Rejected", "condition": null, "metadata": {}},
            {"id": "finance_to_end_approved", "from": "finance_approval", "to": "end_approved", "label": "Approved", "condition": null, "metadata": {}},
            {"id": "finance_to_end_rejected", "from": "finance_approval", "to": "end_rejected", "label": "Rejected", "condition": null, "metadata": {}}
          ],
          "businessRules": [
            "Manager approval is required for all purchase requests.",
            "Finance approval is required if amount exceeds 5000."
          ],
          "validations": [],
          "assumptions": [],
          "edgeCases": [],
          "openQuestions": [],
          "riskLevel": "MEDIUM"
        }
        """;
  }

  private AiProperties aiProperties() {
    return new AiProperties(
        "openrouter",
        "deepseek/deepseek-chat",
        "",
        60000,
        4096,
        0.2,
        true,
        new AiProperties.Cache(true, 30),
        "requirement-to-wfm-v1",
        new AiProperties.OpenRouter(
            "https://openrouter.ai/api/v1",
            "test-key",
            "deepseek/deepseek-chat",
            List.of("qwen/qwen3-32b:nitro", "deepseek/deepseek-chat-v3-0324"),
            0.2,
            4096,
            60));
  }

  private static final class StubAiProvider implements AiProvider {

    private final String responseText;
    private AiRequest lastRequest;

    private StubAiProvider(String responseText) {
      this.responseText = responseText;
    }

    @Override
    public AiResponse generate(AiRequest request) {
      lastRequest = request;
      return new AiResponse(responseText, "OPENROUTER", "deepseek/deepseek-chat", 10, 20, 30, "response-id");
    }
  }

  private static final class StubUsageLogService implements AiUsageLogService {

    private AiUsageLogEntry loggedEntry;

    @Override
    public void log(AiUsageLogEntry entry) {
      loggedEntry = entry;
    }
  }
}
