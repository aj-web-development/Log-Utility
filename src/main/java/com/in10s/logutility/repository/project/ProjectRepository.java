package com.in10s.logutility.repository.project;

import com.in10s.logutility.response.project.ProjectSummaryDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.in10s.logutility.entity.project.FilterField;
import com.in10s.logutility.entity.project.LogSource;
import com.in10s.logutility.entity.project.Project;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    /**
     * Loads every project as a lightweight summary for the admin list. Node/field counts come
     * from count subqueries so the whole list is one query rather than one-per-project.
     */
    @Query("""
            select new com.in10s.logutility.response.project.ProjectSummaryDto(
                p.id, p.name, p.description,
                (select count(ls) from LogSource ls where ls.project = p),
                (select count(ff) from FilterField ff where ff.project = p),
                p.updatedAt)
            from Project p
            order by p.name""")
    List<ProjectSummaryDto> findAllSummaries();

    /**
     * Loads a project with its filter fields in one query to avoid an N+1 when rendering the
     * search form. Fields and sources are separate {@code List} bags, so they are fetched by
     * distinct queries rather than a single join (which would raise MultipleBagFetchException).
     */
    @Query("select p from Project p left join fetch p.filterFields where p.id = :id")
    Optional<Project> findByIdWithFilterFields(@Param("id") UUID id);

    /** Loads a project with its log sources in one query (see {@link #findByIdWithFilterFields}). */
    @Query("select p from Project p left join fetch p.logSources where p.id = :id")
    Optional<Project> findByIdWithLogSources(@Param("id") UUID id);
}
