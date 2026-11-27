package dev.specbuilder.service;

import dev.specbuilder.api.dto.*;
import dev.specbuilder.domain.Capability;
import dev.specbuilder.domain.Endpoint;
import dev.specbuilder.domain.Project;
import dev.specbuilder.repository.CapabilityRepository;
import dev.specbuilder.repository.EndpointRepository;
import dev.specbuilder.validation.NotFoundException;
import dev.specbuilder.validation.ValidationErrors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The project's functional capability model: a tree of up to eight levels, L1 (the broadest, e.g. a
 * business domain) to L8, and the APIs linked to each capability. A capability's API inventory is the
 * APIs linked to it and to everything below it.
 */
@Service
@Transactional
public class CapabilityService {

    public static final List<String> DEFAULT_LEVELS = List.of("Business domain", "Capability area", "Capability",
            "Sub-capability", "Function", "Process", "Activity", "Task");

    private final ProjectService projects;
    private final CapabilityRepository capabilities;
    private final EndpointRepository endpoints;

    public CapabilityService(ProjectService projects, CapabilityRepository capabilities, EndpointRepository endpoints) {
        this.projects = projects;
        this.capabilities = capabilities;
        this.endpoints = endpoints;
    }

    // ------------------------------------------------------------------ levels

    @Transactional(readOnly = true)
    public CapabilityLevels levels(UUID projectId) {
        return new CapabilityLevels(levelNames(projects.require(projectId)));
    }

    public CapabilityLevels setLevels(UUID projectId, CapabilityLevels request) {
        Project project = projects.require(projectId);
        List<String> names = request == null || request.levels() == null ? List.of() : request.levels();
        ValidationErrors errors = new ValidationErrors();
        if (!names.isEmpty() && names.size() != Capability.MAX_LEVEL) {
            errors.add("levels", "give a name for each of the " + Capability.MAX_LEVEL + " levels, or none for the defaults");
        }
        for (int i = 0; i < names.size(); i++) {
            String n = ValidationErrors.trimToNull(names.get(i));
            if (n == null) errors.add("levels[" + i + "]", "is required");
            else errors.maxLength("levels[" + i + "]", n, 60);
        }
        errors.throwIfAny();
        List<String> clean = names.stream().map(String::strip).toList();
        project.setCapabilityLevels(clean.equals(DEFAULT_LEVELS) ? List.of() : clean);
        project.touch();
        return new CapabilityLevels(levelNames(project));
    }

    public static List<String> levelNames(Project project) {
        return project.getCapabilityLevels().size() == Capability.MAX_LEVEL ? project.getCapabilityLevels() : DEFAULT_LEVELS;
    }

    // ------------------------------------------------------------------ the tree

    /** Every capability, in tree order: each followed by everything below it, siblings by position. */
    @Transactional(readOnly = true)
    public List<CapabilityView> list(UUID projectId) {
        Project project = projects.require(projectId);
        Tree tree = tree(projectId);
        List<CapabilityView> out = new ArrayList<>();
        Map<UUID, String> endpointNames = endpointNames(projectId);
        tree.walk(c -> out.add(view(c, tree, levelNames(project), endpointNames)));
        return out;
    }

    @Transactional(readOnly = true)
    public CapabilityView get(UUID projectId, UUID id) {
        Project project = projects.require(projectId);
        Tree tree = tree(projectId);
        return view(tree.require(id), tree, levelNames(project), endpointNames(projectId));
    }

    public CapabilityView create(UUID projectId, CapabilityRequest request) {
        Project project = projects.require(projectId);
        Tree tree = tree(projectId);
        Capability c = new Capability(project);
        apply(c, request, tree, true);
        capabilities.save(c);
        project.touch();
        Tree after = tree(projectId);
        return view(after.require(c.getId()), after, levelNames(project), endpointNames(projectId));
    }

    public CapabilityView update(UUID projectId, UUID id, CapabilityRequest request) {
        Project project = projects.require(projectId);
        Tree tree = tree(projectId);
        Capability c = tree.require(id);
        apply(c, request, tree, false);
        project.touch();
        Tree after = tree(projectId);
        return view(after.require(id), after, levelNames(project), endpointNames(projectId));
    }

    /** Deletes the capability and everything below it. The APIs themselves stay. */
    public void delete(UUID projectId, UUID id) {
        Project project = projects.require(projectId);
        Tree tree = tree(projectId);
        Capability c = tree.require(id);
        List<Capability> subtree = new ArrayList<>();
        tree.walkFrom(c, subtree::add);
        Collections.reverse(subtree); // deepest first
        capabilities.deleteAll(subtree);
        renumber(tree.children(c.getParentId()).stream().filter(s -> !s.getId().equals(id)).toList());
        project.touch();
    }

    // ------------------------------------------------------------------ API links

    /** The capability's API inventory: the APIs linked to it and to every capability below it. */
    @Transactional(readOnly = true)
    public List<CapabilityApi> apis(UUID projectId, UUID id) {
        Tree tree = tree(projectId);
        Capability root = tree.require(id);
        Map<UUID, Endpoint> byId = endpoints.findByProjectId(projectId).stream().collect(Collectors.toMap(Endpoint::getId, e -> e));
        Map<UUID, List<CapabilityApi.Via>> via = new LinkedHashMap<>();
        Set<UUID> direct = new HashSet<>(root.getEndpointIds());
        tree.walkFrom(root, c -> {
            if (c == root) return;
            c.getEndpointIds().forEach(e -> via.computeIfAbsent(e, k -> new ArrayList<>())
                    .add(new CapabilityApi.Via(c.getId(), c.getName(), c.getLevel())));
        });
        Set<UUID> all = new LinkedHashSet<>(direct);
        all.addAll(via.keySet());
        return all.stream().map(byId::get).filter(Objects::nonNull)
                .sorted(Comparator.comparing(Endpoint::getName, String.CASE_INSENSITIVE_ORDER))
                .map(e -> new CapabilityApi(e.getId(), e.getName(), e.getKind(), e.getHttpMethod(), e.getPath(), e.getChannel(),
                        e.getAsyncOperation(), e.getSummary(), direct.contains(e.getId()), via.getOrDefault(e.getId(), List.of())))
                .toList();
    }

    /** Replaces the APIs linked directly to the capability. */
    public CapabilityView setApis(UUID projectId, UUID id, IdList request) {
        Project project = projects.require(projectId);
        Tree tree = tree(projectId);
        Capability c = tree.require(id);
        List<UUID> ids = request == null || request.ids() == null ? List.of() : request.ids();
        checkEndpoints(projectId, ids, "ids");
        c.setEndpointIds(new LinkedHashSet<>(ids));
        project.touch();
        return view(c, tree, levelNames(project), endpointNames(projectId));
    }

    /** The capabilities an API is linked to directly, in tree order. */
    @Transactional(readOnly = true)
    public List<CapabilityView> ofEndpoint(UUID projectId, UUID endpointId) {
        return list(projectId).stream().filter(c -> c.endpointIds().contains(endpointId)).toList();
    }

    /** Links the API to exactly these capabilities, from the API's side. */
    public List<CapabilityView> setEndpointCapabilities(UUID projectId, UUID endpointId, IdList request) {
        Project project = projects.require(projectId);
        checkEndpoints(projectId, List.of(endpointId), "endpointId");
        Tree tree = tree(projectId);
        Set<UUID> wanted = new HashSet<>(request == null || request.ids() == null ? List.of() : request.ids());
        ValidationErrors errors = new ValidationErrors();
        int i = 0;
        for (UUID id : wanted) {
            if (id == null || tree.byId.get(id) == null) errors.add("ids[" + i + "]", "no capability with this id in the project");
            i++;
        }
        errors.throwIfAny();
        for (Capability c : tree.byId.values()) {
            if (wanted.contains(c.getId())) c.getEndpointIds().add(endpointId);
            else c.getEndpointIds().remove(endpointId);
        }
        project.touch();
        return ofEndpoint(projectId, endpointId);
    }

    /** A duplicated API is linked to the same capabilities as the original. */
    public void copyLinks(UUID projectId, UUID fromEndpoint, UUID toEndpoint) {
        capabilities.findByProjectId(projectId).stream()
                .filter(c -> c.getEndpointIds().contains(fromEndpoint))
                .forEach(c -> c.getEndpointIds().add(toEndpoint));
    }

    // ------------------------------------------------------------------ internals

    private void apply(Capability c, CapabilityRequest request, Tree tree, boolean creating) {
        ValidationErrors errors = new ValidationErrors();
        if (request == null) throw ValidationErrors.single("name", "is required");
        String name = ValidationErrors.trimToNull(request.name());
        if (name == null) errors.add("name", "is required");
        else errors.maxLength("name", name, 200);
        String code = ValidationErrors.trimToNull(request.code());
        if (code != null) {
            errors.maxLength("code", code, 40);
            tree.byId.values().stream()
                    .filter(o -> !o.getId().equals(c.getId()) && code.equalsIgnoreCase(o.getCode()))
                    .findFirst().ifPresent(o -> errors.add("code", "already used by " + o.getName()));
        }
        errors.maxLength("description", request.description(), 4000);

        UUID parentId = request.parentId();
        Capability parent = null;
        if (parentId != null) {
            parent = tree.byId.get(parentId);
            if (parent == null) {
                errors.add("parentId", "no capability with this id in the project");
            } else if (!creating && (parentId.equals(c.getId()) || tree.isBelow(parent, c))) {
                errors.add("parentId", "a capability cannot go under itself or under a capability below it");
                parent = null;
            }
        }
        int level = parent == null ? 1 : parent.getLevel() + 1;
        int depthBelow = creating ? 0 : tree.height(c) - 1;
        if (errors.isEmpty() && level + depthBelow > Capability.MAX_LEVEL) {
            errors.add("parentId", "there are only " + Capability.MAX_LEVEL + " levels: this would put "
                    + (depthBelow == 0 ? "it" : "the capabilities below it") + " at L" + (level + depthBelow));
        }
        if (name != null) {
            UUID parentKey = parentId;
            tree.children(parentKey).stream()
                    .filter(s -> !s.getId().equals(c.getId()) && s.getName().equalsIgnoreCase(name))
                    .findFirst().ifPresent(s -> errors.add("name", (parentKey == null ? "another L1 capability" : "another capability at this place")
                            + " is already called '" + s.getName() + "'"));
        }
        errors.throwIfAny();

        UUID oldParent = c.getParentId();
        boolean moved = creating || !Objects.equals(oldParent, parentId);
        c.setName(name);
        c.setCode(code);
        c.setDescription(ValidationErrors.trimToNull(request.description()));
        c.setParentId(parentId);
        int shift = level - c.getLevel();
        c.setLevel(level);
        if (!creating && shift != 0) {
            tree.walkFrom(c, d -> {
                if (d != c) d.setLevel(d.getLevel() + shift);
            });
        }

        List<Capability> siblings = new ArrayList<>(tree.children(parentId).stream().filter(s -> !s.getId().equals(c.getId())).toList());
        int position = request.position() == null
                ? (moved ? siblings.size() : Math.min(c.getPosition(), siblings.size()))
                : Math.max(0, Math.min(request.position(), siblings.size()));
        siblings.add(position, c);
        renumber(siblings);
        if (moved && !creating) {
            renumber(tree.children(oldParent).stream().filter(s -> !s.getId().equals(c.getId())).toList());
        }
    }

    private static void renumber(List<Capability> siblings) {
        for (int i = 0; i < siblings.size(); i++) siblings.get(i).setPosition(i);
    }

    private void checkEndpoints(UUID projectId, Collection<UUID> ids, String at) {
        Set<UUID> known = endpoints.findByProjectId(projectId).stream().map(Endpoint::getId).collect(Collectors.toSet());
        ValidationErrors errors = new ValidationErrors();
        int i = 0;
        for (UUID id : ids) {
            if (id == null || !known.contains(id)) errors.add(ids.size() == 1 && at.equals("endpointId") ? at : at + "[" + i + "]", "no API with this id in the project");
            i++;
        }
        errors.throwIfAny();
    }

    private Map<UUID, String> endpointNames(UUID projectId) {
        return endpoints.findByProjectId(projectId).stream().collect(Collectors.toMap(Endpoint::getId, Endpoint::getName));
    }

    private CapabilityView view(Capability c, Tree tree, List<String> levelNames, Map<UUID, String> endpointNames) {
        Set<UUID> inventory = new HashSet<>();
        tree.walkFrom(c, d -> inventory.addAll(d.getEndpointIds()));
        inventory.retainAll(endpointNames.keySet());
        List<UUID> direct = c.getEndpointIds().stream().filter(endpointNames::containsKey)
                .sorted(Comparator.comparing(endpointNames::get, String.CASE_INSENSITIVE_ORDER)).toList();
        return new CapabilityView(c.getId(), c.getProject().getId(), c.getParentId(), c.getLevel(), levelNames.get(c.getLevel() - 1),
                c.getPosition(), c.getName(), c.getCode(), c.getDescription(), tree.path(c), direct, inventory.size(),
                tree.children(c.getId()).size());
    }

    private Tree tree(UUID projectId) {
        return new Tree(capabilities.findByProjectId(projectId));
    }

    /** The capabilities of a project as a tree. */
    private static final class Tree {
        final Map<UUID, Capability> byId = new LinkedHashMap<>();
        final Map<UUID, List<Capability>> children = new HashMap<>();

        Tree(List<Capability> all) {
            all.forEach(c -> byId.put(c.getId(), c));
            for (Capability c : all) {
                UUID key = c.getParentId() != null && byId.containsKey(c.getParentId()) ? c.getParentId() : null;
                children.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
            }
            children.values().forEach(l -> l.sort(Comparator.comparingInt(Capability::getPosition).thenComparing(Capability::getName)));
        }

        Capability require(UUID id) {
            Capability c = byId.get(id);
            if (c == null) throw new NotFoundException("Capability", id);
            return c;
        }

        List<Capability> children(UUID parentId) {
            return children.getOrDefault(parentId, List.of());
        }

        void walk(java.util.function.Consumer<Capability> visit) {
            children(null).forEach(c -> walkFrom(c, visit));
        }

        void walkFrom(Capability c, java.util.function.Consumer<Capability> visit) {
            visit.accept(c);
            children(c.getId()).forEach(child -> walkFrom(child, visit));
        }

        /** Levels from c down to its deepest descendant, c included. */
        int height(Capability c) {
            return 1 + children(c.getId()).stream().mapToInt(this::height).max().orElse(0);
        }

        boolean isBelow(Capability candidate, Capability ancestor) {
            for (Capability p = candidate; p != null; p = p.getParentId() == null ? null : byId.get(p.getParentId())) {
                if (p.getId().equals(ancestor.getId())) return true;
            }
            return false;
        }

        List<String> path(Capability c) {
            LinkedList<String> names = new LinkedList<>();
            for (Capability p = c; p != null; p = p.getParentId() == null ? null : byId.get(p.getParentId())) names.addFirst(p.getName());
            return names;
        }
    }
}
