package com.in10s.logutility.repository.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import com.in10s.logutility.entity.project.LogSource;

public interface LogSourceRepository extends JpaRepository<LogSource, UUID> {

    List<LogSource> findByProjectId(UUID projectId);
}
