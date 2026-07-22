package com.in10s.logutility.repository.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import com.in10s.logutility.entity.project.LinePattern;

public interface LinePatternRepository extends JpaRepository<LinePattern, UUID> {

    Optional<LinePattern> findByProjectId(UUID projectId);
}
