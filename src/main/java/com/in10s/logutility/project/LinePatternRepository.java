package com.in10s.logutility.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LinePatternRepository extends JpaRepository<LinePattern, UUID> {

    Optional<LinePattern> findByProjectId(UUID projectId);
}
