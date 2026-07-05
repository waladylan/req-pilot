package com.reqpilot.repository;

import com.reqpilot.model.Requirement;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementRepository extends JpaRepository<Requirement, UUID> {

  List<Requirement> findByProject_IdOrderByOrderIndexAscCreatedAtAsc(UUID projectId);

  int countByProject_Id(UUID projectId);
}
