package com.app.logutility.repository.project;

import com.app.logutility.response.project.ProjectSummaryDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.app.logutility.entity.project.FilterField;
import com.app.logutility.entity.project.LogSource;
import com.app.logutility.entity.project.Project;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    /**
     * Loads every project as a lightweight summary for the admin list. Node/field counts come
     * from count subqueries so the whole list is one query rather than one-per-project.
     */
    @Query("""
            select new com.app.logutility.response.project.ProjectSummaryDto(
                p.id, p.name, p.description,
                (select count(ls) from LogSource ls where ls.project = p),
                (select count(ff) from FilterField ff where ff.project = p),
                p.updatedAt)
            from Project p
            order by p.name""")
    List<ProjectSummaryDto> findAllSummaries();

    /**
     * Loads a project with its filter fields and line pattern in one query to avoid an N+1 when
     * rendering the search form (fields for the filter inputs, line pattern for the zone the date
     * pickers must use - see {@code PublicProjectView}). {@code linePattern} is a to-one
     * association so joining it alongside the {@code filterFields} bag is safe; a second {@code
     * List} bag would raise MultipleBagFetchException, a to-one association does not.
     */
    @Query("select p from Project p left join fetch p.filterFields left join fetch p.linePattern where p.id = :id")
    Optional<Project> findByIdWithFilterFields(@Param("id") UUID id);

    /** Loads a project with its log sources in one query (see {@link #findByIdWithFilterFields}). */
    @Query("select p from Project p left join fetch p.logSources where p.id = :id")
    Optional<Project> findByIdWithLogSources(@Param("id") UUID id);
}
