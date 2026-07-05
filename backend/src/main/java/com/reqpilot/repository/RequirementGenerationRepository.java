package com.reqpilot.repository;

import com.reqpilot.model.RequirementGeneration;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementGenerationRepository
    extends JpaRepository<RequirementGeneration, UUID> {}
