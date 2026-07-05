package com.reqpilot.repository;

import com.reqpilot.model.RequirementWfmCacheEntry;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementWfmCacheRepository extends JpaRepository<RequirementWfmCacheEntry, UUID> {

  Optional<RequirementWfmCacheEntry> findByCacheKey(String cacheKey);
}
