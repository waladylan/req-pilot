package com.reqpilot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqpilot.dto.ProjectCreateRequest;
import com.reqpilot.dto.RequirementCreateRequest;
import com.reqpilot.dto.RequirementUpdateRequest;
import com.reqpilot.model.Project;
import com.reqpilot.model.Requirement;
import com.reqpilot.model.RequirementStatus;
import com.reqpilot.repository.ProjectRepository;
import com.reqpilot.repository.RequirementRepository;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProjectRequirementServiceTest {

  private Map<UUID, Project> projects;
  private Map<UUID, Requirement> requirements;
  private ProjectService projectService;
  private RequirementService requirementService;

  @BeforeEach
  void setUp() {
    projects = new LinkedHashMap<>();
    requirements = new LinkedHashMap<>();
    projectService = new ProjectService(projectRepository(projects));
    requirementService =
        new RequirementService(projectService, requirementRepository(requirements), new ObjectMapper());
  }

  @Test
  void projectServiceCreatesAndListsProjects() {
    Project created =
        projectService.createProject(new ProjectCreateRequest("  Commerce  ", "  Checkout flows  "));

    assertThat(created.getName()).isEqualTo("Commerce");
    assertThat(created.getDescription()).isEqualTo("Checkout flows");
    assertThat(projectService.listProjects()).containsExactly(created);
  }

  @Test
  void requirementServiceCreatesRequirementUnderProjectWithDefaultWfmVersion() {
    Project project = saveProject("Commerce");
    saveRequirement(new Requirement(project, "Existing", "Existing requirement", 0));
    saveRequirement(new Requirement(project, "Existing 2", "Existing requirement 2", 1));

    Requirement created =
        requirementService.createRequirement(
            project.getId(),
            new RequirementCreateRequest(
                "  Checkout Approval  ",
                "  User can create a purchase request. Manager approves.  "));

    assertThat(created.getProject()).isSameAs(project);
    assertThat(created.getTitle()).isEqualTo("Checkout Approval");
    assertThat(created.getRequirementText())
        .isEqualTo("User can create a purchase request. Manager approves.");
    assertThat(created.getOrderIndex()).isEqualTo(2);
    assertThat(created.getStatus()).isEqualTo(RequirementStatus.DRAFT);
    assertThat(created.getWfmVersion()).isEqualTo("2.0");
  }

  @Test
  void requirementServiceListsRequirementsByProject() {
    Project project = saveProject("Commerce");
    Requirement first = saveRequirement(new Requirement(project, "First", "First requirement", 0));
    Requirement second = saveRequirement(new Requirement(project, "Second", "Second requirement", 1));

    assertThat(requirementService.listRequirements(project.getId())).containsExactly(first, second);
  }

  @Test
  void requirementServiceMarksGeneratedRequirementEditedWhenTextChanges() {
    Project project = saveProject("Commerce");
    Requirement requirement = saveRequirement(new Requirement(project, "Login", "User logs in", 0));
    requirement.setStatus(RequirementStatus.GENERATED);

    Requirement updated =
        requirementService.updateRequirement(
            requirement.getId(),
            new RequirementUpdateRequest(
                "Updated Login",
                "User enters username and password",
                null,
                3));

    assertThat(updated.getTitle()).isEqualTo("Updated Login");
    assertThat(updated.getRequirementText()).isEqualTo("User enters username and password");
    assertThat(updated.getOrderIndex()).isEqualTo(3);
    assertThat(updated.getStatus()).isEqualTo(RequirementStatus.EDITED);
  }

  @Test
  void requirementServiceRejectsMissingProject() {
    UUID missingProjectId = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                requirementService.createRequirement(
                    missingProjectId,
                    new RequirementCreateRequest("Login", "User logs in")))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Project not found");
  }

  private Project saveProject(String name) {
    Project project = new Project(name, null);
    if (project.getId() == null) {
      ReflectionTestUtils.setField(project, "id", UUID.randomUUID());
    }
    projects.put(project.getId(), project);
    return project;
  }

  private Requirement saveRequirement(Requirement requirement) {
    if (requirement.getId() == null) {
      ReflectionTestUtils.setField(requirement, "id", UUID.randomUUID());
    }
    requirements.put(requirement.getId(), requirement);
    return requirement;
  }

  private ProjectRepository projectRepository(Map<UUID, Project> backingStore) {
    return proxy(
        ProjectRepository.class,
        (proxy, method, args) -> {
          return switch (method.getName()) {
            case "findAll" -> List.copyOf(backingStore.values());
            case "findById" -> Optional.ofNullable(backingStore.get((UUID) args[0]));
            case "save" -> {
              Project project = (Project) args[0];
              if (project.getId() == null) {
                ReflectionTestUtils.setField(project, "id", UUID.randomUUID());
              }
              backingStore.put(project.getId(), project);
              yield project;
            }
            case "delete" -> backingStore.remove(((Project) args[0]).getId());
            default -> defaultValue(method.getReturnType());
          };
        });
  }

  private RequirementRepository requirementRepository(Map<UUID, Requirement> backingStore) {
    return proxy(
        RequirementRepository.class,
        (proxy, method, args) -> {
          return switch (method.getName()) {
            case "countByProject_Id" -> (int)
                backingStore.values().stream()
                    .filter((requirement) -> requirement.getProjectId().equals(args[0]))
                    .count();
            case "findById" -> Optional.ofNullable(backingStore.get((UUID) args[0]));
            case "findByProject_IdOrderByOrderIndexAscCreatedAtAsc" ->
                backingStore.values().stream()
                    .filter((requirement) -> requirement.getProjectId().equals(args[0]))
                    .sorted(Comparator.comparingInt(Requirement::getOrderIndex))
                    .toList();
            case "save" -> {
              Requirement requirement = (Requirement) args[0];
              if (requirement.getId() == null) {
                ReflectionTestUtils.setField(requirement, "id", UUID.randomUUID());
              }
              backingStore.put(requirement.getId(), requirement);
              yield requirement;
            }
            case "delete" -> backingStore.remove(((Requirement) args[0]).getId());
            default -> defaultValue(method.getReturnType());
          };
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
    if (returnType.equals(void.class)) {
      return null;
    }
    return null;
  }
}
