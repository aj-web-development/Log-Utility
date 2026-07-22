package com.in10s.logutility.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LogSourceRepository extends JpaRepository<LogSource, UUID> {

    List<LogSource> findByProjectId(UUID projectId);
}
