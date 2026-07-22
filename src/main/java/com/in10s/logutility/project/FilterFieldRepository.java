package com.in10s.logutility.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FilterFieldRepository extends JpaRepository<FilterField, UUID> {

    List<FilterField> findByProjectId(UUID projectId);
}
