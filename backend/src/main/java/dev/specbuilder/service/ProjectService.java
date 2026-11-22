package dev.specbuilder.service;

import dev.specbuilder.api.dto.ProjectRequest;
import dev.specbuilder.api.dto.ProjectView;
import dev.specbuilder.domain.ImplementationStyle;
import dev.specbuilder.domain.Payload;
import dev.specbuilder.domain.Project;
import dev.specbuilder.repository.EndpointRepository;
import dev.specbuilder.repository.PayloadRepository;
import dev.specbuilder.repository.ProjectRepository;
import dev.specbuilder.validation.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProjectService {

    private static final Pattern VERSION = Pattern.compile("[0-9A-Za-z][0-9A-Za-z.+_-]*");

    private final ProjectRepository projects;
    private final PayloadRepository payloads;
    private final EndpointRepository endpoints;
    private final EntityManager em;

    public ProjectService(ProjectRepository projects, PayloadRepository payloads, EndpointRepository endpoints, EntityManager em) {
        this.projects = projects;
        this.payloads = payloads;
        this.endpoints = endpoints;
        this.em = em;
    }

    @Transactional(readOnly = true)
    public List<ProjectView> list() {
        return projects.findAllByOrderByNameAsc().stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public ProjectView get(UUID id) {
        return view(require(id));
    }

    public ProjectView create(ProjectRequest request) {
        Project project = new Project();
        apply(project, request, true);
        projects.save(project);
        return view(project);
    }

    public ProjectView update(UUID id, ProjectRequest request) {
        Project project = require(id);
        apply(project, request, false);
        project.touch();
        return view(project);
    }

    /** Deletes the project and everything in it. */
    public void delete(UUID id) {
        require(id);
        em.flush();
        // Clear payload-to-payload references first so the cascading delete never trips over them.
        String inProject = "(select id from payload where project_id = :id)";
        em.createNativeQuery("update payload_field set ref_payload_id = null where payload_id in " + inProject)
                .setParameter("id", id).executeUpdate();
        em.createNativeQuery("update endpoint_response set payload_id = null where endpoint_id in (select id from endpoint where project_id = :id)")
                .setParameter("id", id).executeUpdate();
        em.createNativeQuery("update endpoint set request_payload_id = null, message_payload_id = null where project_id = :id")
                .setParameter("id", id).executeUpdate();
        em.createNativeQuery("delete from project where id = :id").setParameter("id", id).executeUpdate();
        em.clear();
    }

    public Project require(UUID id) {
        return projects.findById(id).orElseThrow(() -> new NotFoundException("Project", id));
    }

    private void apply(Project project, ProjectRequest request, boolean isNew) {
        ValidationErrors errors = new ValidationErrors();
        String name = ValidationErrors.trimToNull(request.name());
        if (name == null) {
            errors.add("name", "is required");
        } else {
            errors.maxLength("name", name, 100);
            boolean taken = isNew ? projects.existsByNameIgnoreCase(name) : projects.existsByNameIgnoreCaseAndIdNot(name, project.getId());
            if (taken) {
                errors.add("name", "another project is already called '" + name + "'");
            }
        }
        String basePackage = ValidationErrors.trimToNull(request.basePackage());
        String packageError = IdentifierRules.checkPackageName(basePackage);
        if (packageError != null) {
            errors.add("basePackage", packageError);
        } else {
            errors.maxLength("basePackage", basePackage, 255);
        }
        String version = ValidationErrors.trimToNull(request.version());
        if (version == null) {
            errors.add("version", "is required");
        } else if (!VERSION.matcher(version).matches()) {
            errors.add("version", "use a Maven version such as 1.0.0 or 1.0.0-SNAPSHOT");
        } else {
            errors.maxLength("version", version, 50);
        }
        errors.maxLength("description", request.description(), 4000);

        boolean allowCycles = Boolean.TRUE.equals(request.allowCircularReferences());
        if (!isNew && !allowCycles && project.isAllowCircularReferences()) {
            List<Payload> all = payloads.findByProjectId(project.getId());
            new PayloadGraph(PayloadGraphs.edges(all)).anyCycle().ifPresent(cycle -> {
                Map<UUID, String> names = all.stream().collect(Collectors.toMap(Payload::getId, Payload::getName));
                errors.add("allowCircularReferences", "these payloads still refer to each other in a circle: "
                        + PayloadGraphs.describe(cycle, names) + ". Remove the cycle first.");
            });
        }
        errors.throwIfAny();

        project.setName(name);
        project.setBasePackage(basePackage);
        project.setVersion(version);
        project.setDescription(ValidationErrors.trimToNull(request.description()));
        project.setDefaultImplementationStyle(request.defaultImplementationStyle() == null
                ? ImplementationStyle.IMPLEMENTATION_CLASS : request.defaultImplementationStyle());
        project.setAllowCircularReferences(allowCycles);
    }

    private ProjectView view(Project p) {
        return Views.project(p, payloads.countByProjectId(p.getId()), endpoints.countByProjectId(p.getId()));
    }
}
