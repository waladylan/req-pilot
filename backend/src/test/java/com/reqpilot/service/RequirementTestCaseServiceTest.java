package com.reqpilot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqpilot.dto.RequirementTestCasesResponse;
import com.reqpilot.model.Project;
import com.reqpilot.model.Requirement;
import com.reqpilot.repository.ProjectRepository;
import com.reqpilot.repository.RequirementRepository;
import com.reqpilot.wfmclient.WfmServiceTestCaseGenerateRequest;
import com.reqpilot.wfmclient.WfmServiceTestCaseGenerateResponse;
import com.reqpilot.wfmclient.WfmTestCaseGenerationClient;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RequirementTestCaseServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private Map<UUID, Project> projects;
  private Map<UUID, Requirement> requirements;
  private CapturingTestCaseClient testCaseClient;
  private RequirementTestCaseService service;

  @BeforeEach
  void setUp() {
    projects = new LinkedHashMap<>();
    requirements = new LinkedHashMap<>();
    ProjectService projectService = new ProjectService(projectRepository(projects));
    RequirementRepository requirementRepository = requirementRepository(requirements);
    RequirementService requirementService =
        new RequirementService(projectService, requirementRepository, objectMapper);
    testCaseClient = new CapturingTestCaseClient();
    service =
        new RequirementTestCaseService(
            requirementRepository, requirementService, testCaseClient, objectMapper);
  }

  @Test
  void generateTestCasesSendsSavedWfmV2ToPythonAndPersistsOnlySelectedRequirement() throws Exception {
    Project project = saveProject("Commerce");
    Requirement selected = saveRequirement(new Requirement(project, "Purchase", "Purchase flow", 0));
    Requirement sibling = saveRequirement(new Requirement(project, "Login", "Login flow", 1));
    Project otherProject = saveProject("Support");
    Requirement other = saveRequirement(new Requirement(otherProject, "Ticket", "Ticket flow", 0));
    selected.setWfmJson(wfmJson("purchase_request_approval"));
    selected.setFlowchartJson("{\"nodes\":[],\"edges\":[],\"mermaid\":\"flowchart LR\\n\"}");
    JsonNode testCaseSet = objectMapper.readTree(testCaseSetJson());
    JsonNode metadata = objectMapper.readTree("{\"generator\":\"python-wfm-v2-test-case-generator\",\"strategy\":\"PATH_COVERAGE\",\"generationStatus\":\"PASSED\",\"warnings\":[]}");
    testCaseClient.response = new WfmServiceTestCaseGenerateResponse(testCaseSet, metadata);

    RequirementTestCasesResponse response = service.generateTestCases(selected.getId());

    assertThat(testCaseClient.calls).isEqualTo(1);
    assertThat(testCaseClient.request.wfm().path("wfmVersion").asText()).isEqualTo("2.0");
    assertThat(testCaseClient.request.context().projectId()).isEqualTo(project.getId().toString());
    assertThat(testCaseClient.request.context().requirementId()).isEqualTo(selected.getId().toString());
    assertThat(testCaseClient.request.options().strategy()).isEqualTo("PATH_COVERAGE");
    assertThat(selected.getTestCasesJson()).contains("TC-001");
    assertThat(selected.getTestCaseMetadataJson()).contains("python-wfm-v2-test-case-generator");
    assertThat(selected.getTestCasesGeneratedAt()).isNotNull();
    assertThat(selected.getTestCasesUpdatedAt()).isNotNull();
    assertThat(sibling.getTestCasesJson()).isNull();
    assertThat(other.getTestCasesJson()).isNull();
    assertThat(selected.getWfmJson()).contains("purchase_request_approval");
    assertThat(selected.getFlowchartJson()).contains("flowchart LR");
    assertThat(response.testCaseSet().path("testCases").get(0).path("id").asText()).isEqualTo("TC-001");
    assertThat(response.requirement().testCaseSet().path("testCases").get(0).path("id").asText()).isEqualTo("TC-001");
  }

  @Test
  void getTestCasesReturnsSavedArtifact() throws Exception {
    Project project = saveProject("Commerce");
    Requirement requirement = saveRequirement(new Requirement(project, "Purchase", "Purchase flow", 0));
    requirement.setTestCasesJson(testCaseSetJson());
    requirement.setTestCaseMetadataJson("{\"generationStatus\":\"PASSED\"}");

    RequirementTestCasesResponse response = service.getTestCases(requirement.getId());

    assertThat(response.testCaseSet().path("testCases").get(0).path("id").asText()).isEqualTo("TC-001");
    assertThat(response.metadata().path("generationStatus").asText()).isEqualTo("PASSED");
  }

  @Test
  void generateTestCasesRejectsRequirementWithoutWfm() {
    Project project = saveProject("Commerce");
    Requirement requirement = saveRequirement(new Requirement(project, "Purchase", "Purchase flow", 0));

    assertThatThrownBy(() -> service.generateTestCases(requirement.getId()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Generate flow first before generating test cases.");

    assertThat(testCaseClient.calls).isZero();
  }

  @Test
  void generateTestCasesRejectsNonWfmV2Requirement() {
    Project project = saveProject("Commerce");
    Requirement requirement = saveRequirement(new Requirement(project, "Purchase", "Purchase flow", 0));
    requirement.setWfmVersion("1.0");
    requirement.setWfmJson("{\"schemaVersion\":\"1.0\"}");

    assertThatThrownBy(() -> service.generateTestCases(requirement.getId()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Generate WFM v2 flow before generating test cases.");

    assertThat(testCaseClient.calls).isZero();
  }

  private String wfmJson(String workflowId) {
    return """
        {
          "wfmVersion": "2.0",
          "workflowId": "%s",
          "workflowName": "Purchase Request Approval",
          "nodes": [{"id": "start", "kind": "START", "name": "Start"}],
          "transitions": []
        }
        """
        .formatted(workflowId);
  }

  private String testCaseSetJson() {
    return """
        {
          "testCaseVersion": "1.0",
          "sourceWfmVersion": "2.0",
          "workflowId": "purchase_request_approval",
          "workflowName": "Purchase Request Approval",
          "testCases": [{"id": "TC-001", "title": "Manager rejects", "priority": "HIGH"}],
          "coverage": {"nodeCount": 1, "transitionCount": 0},
          "metadata": {"generator": "python-wfm-v2-test-case-generator"}
        }
        """;
  }

  private Project saveProject(String name) {
    Project project = new Project(name, null);
    ReflectionTestUtils.setField(project, "id", UUID.randomUUID());
    projects.put(project.getId(), project);
    return project;
  }

  private Requirement saveRequirement(Requirement requirement) {
    ReflectionTestUtils.setField(requirement, "id", UUID.randomUUID());
    requirements.put(requirement.getId(), requirement);
    return requirement;
  }

  private ProjectRepository projectRepository(Map<UUID, Project> backingStore) {
    return proxy(
        ProjectRepository.class,
        (proxy, method, args) -> switch (method.getName()) {
          case "findById" -> Optional.ofNullable(backingStore.get((UUID) args[0]));
          case "findAll" -> List.copyOf(backingStore.values());
          default -> defaultValue(method.getReturnType());
        });
  }

  private RequirementRepository requirementRepository(Map<UUID, Requirement> backingStore) {
    return proxy(
        RequirementRepository.class,
        (proxy, method, args) -> switch (method.getName()) {
          case "findById" -> Optional.ofNullable(backingStore.get((UUID) args[0]));
          default -> defaultValue(method.getReturnType());
        });
  }

  @SuppressWarnings("unchecked")
  private <T> T proxy(Class<T> type, InvocationHandler handler) {
    return (T)
        Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
  }

  private Object defaultValue(Class<?> returnType) {
    if (returnType.equals(boolean.class)) {
      return false;
    }
    if (returnType.equals(int.class) || returnType.equals(long.class)) {
      return 0;
    }
    return null;
  }

  private static final class CapturingTestCaseClient implements WfmTestCaseGenerationClient {

    private int calls;
    private WfmServiceTestCaseGenerateRequest request;
    private WfmServiceTestCaseGenerateResponse response;

    @Override
    public WfmServiceTestCaseGenerateResponse generateTestCases(WfmServiceTestCaseGenerateRequest request) {
      this.calls += 1;
      this.request = request;
      return response;
    }
  }
}
