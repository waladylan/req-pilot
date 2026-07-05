package com.reqpilot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqpilot.config.AiProperties;
import com.reqpilot.config.WfmGenerationMode;
import com.reqpilot.config.WfmGenerationProperties;
import com.reqpilot.dto.RequirementMapper;
import com.reqpilot.dto.SavedRequirementGenerationResponse;
import com.reqpilot.model.FlowEdge;
import com.reqpilot.model.FlowEdgeType;
import com.reqpilot.model.FlowNode;
import com.reqpilot.model.FlowNodeType;
import com.reqpilot.model.Flowchart;
import com.reqpilot.model.Project;
import com.reqpilot.model.Requirement;
import com.reqpilot.model.RequirementStatus;
import com.reqpilot.repository.ProjectRepository;
import com.reqpilot.repository.RequirementRepository;
import com.reqpilot.wfmclient.WfmGenerationClient;
import com.reqpilot.wfmclient.WfmServiceGenerateRequest;
import com.reqpilot.wfmclient.WfmServiceGenerateResponse;
import com.reqpilot.wfmclient.WfmServiceMetadata;
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

class SavedRequirementGenerationServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private Map<UUID, Project> projects;
  private Map<UUID, Requirement> requirements;
  private CapturingWfmGenerationClient wfmGenerationClient;
  private SavedRequirementGenerationService service;

  @BeforeEach
  void setUp() {
    projects = new LinkedHashMap<>();
    requirements = new LinkedHashMap<>();
    ProjectRepository projectRepository = projectRepository(projects);
    RequirementRepository requirementRepository = requirementRepository(requirements);
    ProjectService projectService = new ProjectService(projectRepository);
    RequirementService requirementService =
        new RequirementService(projectService, requirementRepository, objectMapper);
    wfmGenerationClient = new CapturingWfmGenerationClient();
    service =
        new SavedRequirementGenerationService(
            requirementRepository,
            requirementService,
            wfmGenerationClient,
            aiProperties(),
            new WfmGenerationProperties(WfmGenerationMode.AI, false, "2.0"),
            new RequirementMapper(),
            objectMapper);
  }

  @Test
  void generateFlowCallsPythonWorkflowEngineAndPersistsArtifacts() throws Exception {
    Project project = saveProject("Commerce");
    Requirement requirement =
        saveRequirement(new Requirement(project, "Purchase Approval", "Manager approves purchase.", 0));
    JsonNode wfm = objectMapper.readTree("{\"wfmVersion\":\"2.0\",\"workflowId\":\"purchase_approval\"}");
    Flowchart flowchart =
        new Flowchart(
            List.of(
                new FlowNode("N1", "Start", FlowNodeType.START),
                new FlowNode("N2", "Manager approves", FlowNodeType.ACTION),
                new FlowNode("N3", "End", FlowNodeType.END)),
            List.of(
                new FlowEdge("T1", "N1", "N2", "Start", FlowEdgeType.DEFAULT),
                new FlowEdge("T2", "N2", "N3", "Approved", FlowEdgeType.SUCCESS)),
            "flowchart LR\n  N1 --> N2\n  N2 --> N3");
    WfmServiceMetadata metadata =
        new WfmServiceMetadata(
            "deepseek/deepseek-chat",
            "wfm-v2-python-001",
            "AI",
            "2.0",
            "python-wfm-v2-ai-generator",
            "python-wfm-v2-flowchart-mapper",
            "PASSED",
            "PASSED",
            "PASSED",
            "PASSED",
            List.of("Mapped WFM v2 to flowchart."));
    wfmGenerationClient.response = new WfmServiceGenerateResponse(wfm, flowchart, metadata);

    SavedRequirementGenerationResponse response = service.generateFlow(requirement.getId());

    assertThat(wfmGenerationClient.calls).isEqualTo(1);
    assertThat(wfmGenerationClient.request.requirement()).isEqualTo("Manager approves purchase.");
    assertThat(wfmGenerationClient.request.context().projectId()).isEqualTo(project.getId().toString());
    assertThat(wfmGenerationClient.request.context().requirementId())
        .isEqualTo(requirement.getId().toString());
    assertThat(wfmGenerationClient.request.options().wfmVersion()).isEqualTo("2.0");
    assertThat(wfmGenerationClient.request.options().generationMode()).isEqualTo("AI");

    assertThat(requirement.getWfmVersion()).isEqualTo("2.0");
    assertThat(requirement.getStatus()).isEqualTo(RequirementStatus.GENERATED);
    assertThat(requirement.getWfmJson()).contains("\"wfmVersion\":\"2.0\"");
    assertThat(requirement.getFlowchartJson()).contains("\"nodes\"");
    assertThat(requirement.getMetadataJson()).contains("python-wfm-v2-flowchart-mapper");
    assertThat(response.requirement().wfm().path("wfmVersion").asText()).isEqualTo("2.0");
    assertThat(response.wfm().path("wfmVersion").asText()).isEqualTo("2.0");
    assertThat(response.flowchart().edges().get(1).type()).isEqualTo(FlowEdgeType.SUCCESS);
    assertThat(response.metadata().generationMode()).isEqualTo("AI");
    assertThat(response.metadata().warnings()).contains("Mapped WFM v2 to flowchart.");
  }

  @Test
  void generateFlowRejectsBlankSavedRequirementWithoutCallingPython() {
    Project project = saveProject("Commerce");
    Requirement requirement = saveRequirement(new Requirement(project, "Blank", "   ", 0));

    assertThatThrownBy(() -> service.generateFlow(requirement.getId()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Requirement text must not be blank");

    assertThat(wfmGenerationClient.calls).isZero();
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
          case "save" -> {
            Project project = (Project) args[0];
            if (project.getId() == null) {
              ReflectionTestUtils.setField(project, "id", UUID.randomUUID());
            }
            backingStore.put(project.getId(), project);
            yield project;
          }
          default -> defaultValue(method.getReturnType());
        });
  }

  private RequirementRepository requirementRepository(Map<UUID, Requirement> backingStore) {
    return proxy(
        RequirementRepository.class,
        (proxy, method, args) -> switch (method.getName()) {
          case "findById" -> Optional.ofNullable(backingStore.get((UUID) args[0]));
          case "countByProjectId" -> (int)
              backingStore.values().stream()
                  .filter((requirement) -> requirement.getProjectId().equals(args[0]))
                  .count();
          case "save" -> {
            Requirement requirement = (Requirement) args[0];
            if (requirement.getId() == null) {
              ReflectionTestUtils.setField(requirement, "id", UUID.randomUUID());
            }
            backingStore.put(requirement.getId(), requirement);
            yield requirement;
          }
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

  private static final class CapturingWfmGenerationClient implements WfmGenerationClient {

    private int calls;
    private WfmServiceGenerateRequest request;
    private WfmServiceGenerateResponse response;

    @Override
    public WfmServiceGenerateResponse generate(WfmServiceGenerateRequest request) {
      this.calls += 1;
      this.request = request;
      return response;
    }
  }
}
