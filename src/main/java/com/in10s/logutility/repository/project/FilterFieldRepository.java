package com.in10s.logutility.repository.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import com.in10s.logutility.entity.project.FilterField;

public interface FilterFieldRepository extends JpaRepository<FilterField, UUID> {

    List<FilterField> findByProjectId(UUID projectId);
}
