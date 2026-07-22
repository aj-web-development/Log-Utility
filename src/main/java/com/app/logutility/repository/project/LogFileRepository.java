package com.app.logutility.repository.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import com.app.logutility.entity.project.LogFile;

public interface LogFileRepository extends JpaRepository<LogFile, UUID> {

    List<LogFile> findByLogSourceId(UUID logSourceId);
}
