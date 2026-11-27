package dev.specbuilder.capabilities;

import dev.specbuilder.api.dto.CapabilityRequest;
import dev.specbuilder.api.dto.CapabilityView;
import dev.specbuilder.api.dto.EndpointView;
import dev.specbuilder.api.dto.IdList;
import dev.specbuilder.domain.Capability;
import dev.specbuilder.domain.CapabilityImport;
import dev.specbuilder.domain.Project;
import dev.specbuilder.repository.CapabilityImportRepository;
import dev.specbuilder.service.CapabilityService;
import dev.specbuilder.service.EndpointService;
import dev.specbuilder.service.ProjectService;
import dev.specbuilder.validation.NotFoundException;
import dev.specbuilder.validation.ValidationErrors;
import dev.specbuilder.validation.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Capability imports: a file read into a draft (saved, so it can be reviewed later), compared with the
 * project's capabilities, edited, and applied, creating new capabilities, merging into existing ones, or
 * skipping. Nothing changes in the capability map until the draft is applied.
 */
@Service
@Transactional
public class CapabilityImportService {

    public enum Severity { ERROR, DUPLICATE, OVERLAP, WARNING, INFO }

    public enum FindingType { MATCH, SAME_CODE, SIMILAR_NAME, CONTAINED_NAME, SIMILAR_DESCRIPTION, API_ALREADY_LINKED,
        DUPLICATE_IN_FILE, TOO_DEEP, UNKNOWN_API, MERGE_TARGET_MISSING }

    public record Finding(FindingType type, Severity severity, String message, UUID existingId, String existingPath,
                          Integer existingLevel, String otherKey, Integer score) {
    }

    public record NodeView(String key, String parentKey, int level, List<String> path, String name, String code, String description,
                           List<String> apis, ImportNode.Resolution resolution, UUID mergeTargetId, String mergeTargetPath,
                           Integer sourceRow, List<Finding> findings) {
    }

    public record Summary(int total, int create, int merge, int skip, int matches, int duplicates, int overlaps, int errors) {
    }

    public record ImportView(UUID id, UUID projectId, String fileName, String format, String sheet, CapabilityImport.Status status,
                             UUID targetParentId, String targetParentPath, List<String> warnings, List<NodeView> nodes, Summary summary,
                             Result result, Instant createdAt, Instant updatedAt) {
    }

    public record ImportListItem(UUID id, String fileName, String format, CapabilityImport.Status status, int total,
                                 Instant createdAt, Instant updatedAt) {
    }

    public record Upload(String fileName, String content) {
    }

    /** The draft after editing: where it goes, and every node. */
    public record Update(UUID targetParentId, List<ImportNode> nodes) {
    }

    public record Result(int created, int merged, int skipped, int apisLinked) {
    }

    static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<ImportNode>> NODES = new TypeReference<>() { };
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() { };

    private final ProjectService projects;
    private final CapabilityImportRepository imports;
    private final CapabilityService capabilities;
    private final EndpointService endpoints;

    public CapabilityImportService(ProjectService projects, CapabilityImportRepository imports, CapabilityService capabilities,
                                   EndpointService endpoints) {
        this.projects = projects;
        this.imports = imports;
        this.capabilities = capabilities;
        this.endpoints = endpoints;
    }

    // ================================================================== commands

    public ImportView upload(UUID projectId, Upload upload) {
        Project project = projects.require(projectId);
        if (upload == null || upload.content() == null || upload.content().isBlank()) throw ValidationErrors.single("file", "choose a file");
        String fileName = ValidationErrors.trimToNull(upload.fileName()) == null ? "capabilities" : upload.fileName().strip();
        byte[] bytes;
        try {
            String content = upload.content().contains(",") && upload.content().startsWith("data:")
                    ? upload.content().substring(upload.content().indexOf(',') + 1) : upload.content();
            bytes = Base64.getMimeDecoder().decode(content);
        } catch (IllegalArgumentException e) {
            throw ValidationErrors.single("file", "the file content must be sent as base64");
        }
        if (bytes.length > MAX_BYTES) throw ValidationErrors.single("file", "files up to 10 MB can be imported");
        CapabilityFileParser.Parsed parsed;
        try {
            parsed = CapabilityFileParser.parse(fileName, bytes);
        } catch (TableReader.UnreadableFileException e) {
            throw ValidationErrors.single("file", e.getMessage());
        }

        // Capabilities that are already in the map (same path) merge into them by default.
        Map<String, CapabilityView> byPath = byPath(capabilities.list(projectId));
        Map<String, List<String>> pathOf = new HashMap<>();
        List<ImportNode> nodes = new ArrayList<>();
        for (ImportNode n : parsed.nodes()) {
            List<String> path = new ArrayList<>(n.parentKey() == null ? List.of() : pathOf.getOrDefault(n.parentKey(), List.of()));
            path.add(n.name());
            pathOf.put(n.key(), path);
            CapabilityView existing = byPath.get(pathKey(path));
            nodes.add(existing == null ? n : n.with(ImportNode.Resolution.MERGE, existing.id()));
        }

        CapabilityImport draft = new CapabilityImport(project, fileName.length() > 300 ? fileName.substring(0, 300) : fileName, parsed.format());
        draft.setSheet(parsed.sheet());
        draft.setNodes(JSON.writeValueAsString(nodes));
        draft.setWarnings(parsed.warnings().isEmpty() ? null : JSON.writeValueAsString(parsed.warnings()));
        imports.save(draft);
        return view(draft);
    }

    @Transactional(readOnly = true)
    public List<ImportListItem> list(UUID projectId) {
        projects.require(projectId);
        return imports.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(i -> new ImportListItem(i.getId(), i.getFileName(), i.getFormat(), i.getStatus(), nodes(i).size(), i.getCreatedAt(), i.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ImportView get(UUID projectId, UUID id) {
        return view(require(projectId, id));
    }

    /** Replaces the draft's nodes (after editing) and where it goes. */
    public ImportView update(UUID projectId, UUID id, Update update) {
        CapabilityImport draft = require(projectId, id);
        requireDraft(draft);
        if (update == null || update.nodes() == null) throw ValidationErrors.single("nodes", "are required");
        List<CapabilityView> existing = capabilities.list(projectId);
        Set<UUID> existingIds = existing.stream().map(CapabilityView::id).collect(Collectors.toSet());
        ValidationErrors errors = new ValidationErrors();
        if (update.targetParentId() != null && !existingIds.contains(update.targetParentId())) {
            errors.add("targetParentId", "no capability with this id in the project");
        }
        Set<String> keys = new HashSet<>();
        List<ImportNode> nodes = update.nodes();
        for (int i = 0; i < nodes.size(); i++) {
            ImportNode n = nodes.get(i);
            String at = "nodes[" + i + "]";
            if (n == null || ValidationErrors.trimToNull(n.key()) == null) {
                errors.add(at + ".key", "is required");
                continue;
            }
            if (!keys.add(n.key())) errors.add(at + ".key", "is used twice");
            if (ValidationErrors.trimToNull(n.name()) == null) errors.add(at + ".name", "is required");
            else errors.maxLength(at + ".name", n.name().strip(), 200);
            errors.maxLength(at + ".code", n.code(), 40);
            errors.maxLength(at + ".description", n.description(), 4000);
            if (n.resolution() == ImportNode.Resolution.MERGE && (n.mergeTargetId() == null || !existingIds.contains(n.mergeTargetId()))) {
                errors.add(at + ".mergeTargetId", "choose an existing capability to merge into");
            }
        }
        for (int i = 0; i < nodes.size(); i++) {
            ImportNode n = nodes.get(i);
            if (n != null && n.parentKey() != null && !keys.contains(n.parentKey())) {
                errors.add("nodes[" + i + "].parentKey", "no node " + n.parentKey() + " in the import");
            }
        }
        errors.throwIfAny();
        Map<String, String> parentOf = new HashMap<>();
        nodes.forEach(n -> parentOf.put(n.key(), n.parentKey()));
        for (int i = 0; i < nodes.size(); i++) {
            Set<String> seen = new HashSet<>();
            for (String k = nodes.get(i).key(); k != null; k = parentOf.get(k)) {
                if (!seen.add(k)) {
                    errors.add("nodes[" + i + "].parentKey", nodes.get(i).name() + " would end up below itself");
                    break;
                }
            }
        }
        errors.throwIfAny();
        List<ImportNode> clean = nodes.stream().map(n -> new ImportNode(n.key(), n.parentKey(), n.name().strip(),
                ValidationErrors.trimToNull(n.code()), ValidationErrors.trimToNull(n.description()),
                n.apis() == null ? List.of() : n.apis().stream().map(String::strip).filter(s -> !s.isEmpty()).distinct().toList(),
                n.resolution() == null ? ImportNode.Resolution.CREATE : n.resolution(),
                n.resolution() == ImportNode.Resolution.MERGE ? n.mergeTargetId() : null, n.sourceRow())).toList();
        draft.setTargetParentId(update.targetParentId());
        draft.setNodes(JSON.writeValueAsString(CapabilityFileParser.ordered(clean)));
        draft.touch();
        return view(draft);
    }

    /** Applies the draft to the capability map, all or nothing. */
    public ImportView apply(UUID projectId, UUID id) {
        CapabilityImport draft = require(projectId, id);
        requireDraft(draft);
        ImportView before = view(draft);
        ValidationErrors errors = new ValidationErrors();
        for (int i = 0; i < before.nodes().size(); i++) {
            NodeView n = before.nodes().get(i);
            if (skipped(n, before)) continue;
            for (Finding f : n.findings()) {
                if (f.severity() == Severity.ERROR) errors.add("nodes[" + i + "]", n.name() + ": " + f.message());
            }
        }
        errors.throwIfAny();

        Map<String, UUID> endpointIds = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        endpoints.list(projectId).forEach(e -> endpointIds.put(e.name(), e.id()));
        Map<String, UUID> resolved = new HashMap<>();
        Set<String> skippedKeys = new HashSet<>();
        int created = 0, merged = 0, skippedCount = 0, linked = 0;
        for (int i = 0; i < before.nodes().size(); i++) {
            NodeView n = before.nodes().get(i);
            if (n.resolution() == ImportNode.Resolution.SKIP || (n.parentKey() != null && skippedKeys.contains(n.parentKey()))) {
                skippedKeys.add(n.key());
                skippedCount++;
                continue;
            }
            UUID parentId = n.parentKey() == null ? draft.getTargetParentId() : resolved.get(n.parentKey());
            CapabilityView saved;
            try {
                if (n.resolution() == ImportNode.Resolution.MERGE) {
                    CapabilityView target = capabilities.get(projectId, n.mergeTargetId());
                    boolean fillCode = target.code() == null && n.code() != null;
                    boolean fillDescription = target.description() == null && n.description() != null;
                    saved = fillCode || fillDescription
                            ? capabilities.update(projectId, target.id(), new CapabilityRequest(target.name(), fillCode ? n.code() : target.code(),
                                    fillDescription ? n.description() : target.description(), target.parentId(), null))
                            : target;
                    merged++;
                } else {
                    saved = capabilities.create(projectId, new CapabilityRequest(n.name(), n.code(), n.description(), parentId, null));
                    created++;
                }
            } catch (ValidationException e) {
                throw ValidationErrors.single("nodes[" + i + "]", n.name() + ": " + e.getMessage());
            }
            resolved.put(n.key(), saved.id());
            List<UUID> apis = n.apis().stream().map(endpointIds::get).filter(Objects::nonNull).toList();
            if (!apis.isEmpty()) {
                Set<UUID> all = new LinkedHashSet<>(saved.endpointIds());
                int before0 = all.size();
                all.addAll(apis);
                if (all.size() > before0) {
                    capabilities.setApis(projectId, saved.id(), new IdList(List.copyOf(all)));
                    linked += all.size() - before0;
                }
            }
        }
        Result result = new Result(created, merged, skippedCount, linked);
        draft.setResult(JSON.writeValueAsString(result));
        draft.setStatus(CapabilityImport.Status.APPLIED);
        draft.touch();
        return view(draft);
    }

    public void discard(UUID projectId, UUID id) {
        imports.delete(require(projectId, id));
    }

    // ================================================================== the view, with its comparison

    private ImportView view(CapabilityImport draft) {
        UUID projectId = draft.getProject().getId();
        List<CapabilityView> existing = capabilities.list(projectId);
        Map<UUID, CapabilityView> byId = existing.stream().collect(Collectors.toMap(CapabilityView::id, c -> c));
        Map<String, CapabilityView> byPath = byPath(existing);
        CapabilityView target = draft.getTargetParentId() == null ? null : byId.get(draft.getTargetParentId());
        List<ImportNode> nodes = nodes(draft);
        Map<String, String> endpointNames = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<UUID, String> endpointById = new HashMap<>();
        for (EndpointView e : endpoints.list(projectId)) {
            endpointNames.put(e.name(), e.name());
            endpointById.put(e.id(), e.name());
        }
        Map<String, List<UUID>> capabilitiesOfApi = new HashMap<>();
        existing.forEach(c -> c.endpointIds().forEach(e -> capabilitiesOfApi.computeIfAbsent(endpointById.get(e), k -> new ArrayList<>()).add(c.id())));

        // Levels and paths, following merges: what is below a merged node goes below the capability it merges into.
        Map<String, Integer> level = new HashMap<>();
        Map<String, List<String>> path = new HashMap<>();
        Map<String, ImportNode> byKey = new HashMap<>();
        for (ImportNode n : nodes) {
            byKey.put(n.key(), n);
            int parentLevel;
            List<String> parentPath;
            if (n.parentKey() == null) {
                parentLevel = target == null ? 0 : target.level();
                parentPath = target == null ? List.of() : target.path();
            } else {
                parentLevel = level.getOrDefault(n.parentKey(), 0);
                parentPath = path.getOrDefault(n.parentKey(), List.of());
            }
            CapabilityView mergeTarget = n.resolution() == ImportNode.Resolution.MERGE ? byId.get(n.mergeTargetId()) : null;
            if (mergeTarget != null) {
                level.put(n.key(), mergeTarget.level());
                path.put(n.key(), mergeTarget.path());
            } else {
                level.put(n.key(), parentLevel + 1);
                List<String> p = new ArrayList<>(parentPath);
                p.add(n.name());
                path.put(n.key(), p);
            }
        }

        // Duplicates within the file: the same normalised name, or the same code, in two places.
        Map<String, List<ImportNode>> sameName = new HashMap<>();
        Map<String, List<ImportNode>> sameCode = new HashMap<>();
        for (ImportNode n : nodes) {
            String w = String.join(" ", new TreeSet<>(Similarity.words(n.name())));
            sameName.computeIfAbsent(w.isEmpty() ? Similarity.normalize(n.name()) : w, k -> new ArrayList<>()).add(n);
            if (n.code() != null) sameCode.computeIfAbsent(n.code().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(n);
        }
        boolean light = (long) nodes.size() * existing.size() > 2_000_000; // very large maps: words only, no spelling distance

        // Where each node lands in the map: the existing capability it is (merge target or exact match), else the
        // nearest such capability above it. Names along that line are expected to repeat ("Payments" above
        // "Payment initiation"), so they are not compared.
        Map<UUID, UUID> parentOf = new HashMap<>();
        existing.forEach(c -> { if (c.parentId() != null) parentOf.put(c.id(), c.parentId()); });
        Map<String, UUID> itself = new HashMap<>();
        for (ImportNode n : nodes) {
            CapabilityView m = n.resolution() == ImportNode.Resolution.MERGE ? byId.get(n.mergeTargetId()) : byPath.get(pathKey(path.get(n.key())));
            if (m != null) itself.put(n.key(), m.id());
        }

        List<NodeView> views = new ArrayList<>();
        for (ImportNode n : nodes) {
            List<Finding> findings = new ArrayList<>();
            List<String> p = path.get(n.key());
            CapabilityView exact = byPath.get(pathKey(p));
            CapabilityView mergeTarget = n.resolution() == ImportNode.Resolution.MERGE ? byId.get(n.mergeTargetId()) : null;
            Set<UUID> related = new HashSet<>(); // existing capabilities this node already corresponds to
            Set<UUID> lineage = new HashSet<>(); // capabilities above or below where it lands: not compared by name
            UUID anchor = itself.get(n.key());
            for (String k = n.parentKey(); anchor == null && k != null; k = byKey.get(k).parentKey()) anchor = itself.get(k);
            if (anchor == null && target != null) anchor = target.id();
            for (UUID a = anchor; a != null; a = parentOf.get(a)) lineage.add(a);
            UUID own = itself.get(n.key());
            if (own != null) {
                for (CapabilityView c : existing) {
                    for (UUID a = c.id(); a != null; a = parentOf.get(a)) {
                        if (a.equals(own)) { lineage.add(c.id()); break; }
                    }
                }
            }
            if (mergeTarget != null) related.add(mergeTarget.id());
            if (n.resolution() == ImportNode.Resolution.MERGE && mergeTarget == null) {
                findings.add(new Finding(FindingType.MERGE_TARGET_MISSING, Severity.ERROR, "The capability to merge into no longer exists; choose another, or create this one",
                        null, null, null, null, null));
            }
            if (exact != null && n.resolution() != ImportNode.Resolution.MERGE) {
                related.add(exact.id());
                findings.add(new Finding(FindingType.MATCH, Severity.INFO, "Already in the map at this place: merge into it rather than create a second one",
                        exact.id(), String.join(" › ", exact.path()), exact.level(), null, 100));
            }
            int lvl = level.get(n.key());
            if (lvl > Capability.MAX_LEVEL && n.resolution() != ImportNode.Resolution.SKIP) {
                findings.add(new Finding(FindingType.TOO_DEEP, Severity.ERROR, "Would be at L" + lvl + ", but there are only " + Capability.MAX_LEVEL
                        + " levels: move it (or what is above it) up, or skip it", null, null, null, null, null));
            }
            if (n.code() != null) {
                for (CapabilityView c : existing) {
                    if (related.contains(c.id()) || c.code() == null || !c.code().equalsIgnoreCase(n.code())) continue;
                    boolean blocks = n.resolution() == ImportNode.Resolution.CREATE;
                    findings.add(new Finding(FindingType.SAME_CODE, blocks ? Severity.ERROR : Severity.DUPLICATE,
                            "Code " + c.code() + " is already used by L" + c.level() + " " + c.name()
                                    + (blocks ? ": merge into it, change the code or clear it" : ": probably the same capability"),
                            c.id(), String.join(" › ", c.path()), c.level(), null, 100));
                    related.add(c.id());
                }
            }
            List<Finding> similar = new ArrayList<>();
            for (CapabilityView c : existing) {
                if (related.contains(c.id()) || lineage.contains(c.id())) continue;
                double score = light ? Similarity.jaccard(Similarity.words(n.name()), Similarity.words(c.name())) : Similarity.names(n.name(), c.name());
                int pct = (int) Math.round(score * 100);
                String where = "L" + c.level() + " " + String.join(" › ", c.path());
                if (score >= 0.85) {
                    similar.add(new Finding(FindingType.SIMILAR_NAME, Severity.DUPLICATE, "Possible duplicate of " + where + " (" + pct + "% alike)",
                            c.id(), String.join(" › ", c.path()), c.level(), null, pct));
                } else if (Similarity.contains(n.name(), c.name())
                        || (score >= 0.7 && Similarity.jaccard(Similarity.words(n.name()), Similarity.words(c.name())) > 0)) {
                    similar.add(new Finding(FindingType.CONTAINED_NAME, Severity.OVERLAP, "Overlaps with " + where
                            + (Similarity.contains(n.name(), c.name()) ? " (one name contains the other)" : " (" + pct + "% alike)"),
                            c.id(), String.join(" › ", c.path()), c.level(), null, pct));
                } else if (n.description() != null && c.description() != null) {
                    Set<String> a = Similarity.words(n.description());
                    Set<String> b = Similarity.words(c.description());
                    double d = a.size() >= 4 && b.size() >= 4 ? Similarity.jaccard(a, b) : 0;
                    if (d >= 0.6) {
                        similar.add(new Finding(FindingType.SIMILAR_DESCRIPTION, Severity.OVERLAP, "Description overlaps with " + where
                                + " (" + Math.round(d * 100) + "% of the words)", c.id(), String.join(" › ", c.path()), c.level(), null, (int) Math.round(d * 100)));
                    }
                }
            }
            similar.sort(Comparator.comparing((Finding f) -> f.severity()).thenComparing(f -> -f.score()));
            findings.addAll(similar.subList(0, Math.min(3, similar.size())));
            for (String api : n.apis()) {
                if (!endpointNames.containsKey(api)) {
                    findings.add(new Finding(FindingType.UNKNOWN_API, Severity.WARNING, "API " + api + " is not in the project; it will not be linked",
                            null, null, null, null, null));
                    continue;
                }
                for (UUID capId : capabilitiesOfApi.getOrDefault(endpointNames.get(api), List.of())) {
                    if (related.contains(capId)) continue;
                    CapabilityView c = byId.get(capId);
                    findings.add(new Finding(FindingType.API_ALREADY_LINKED, Severity.OVERLAP, "API " + endpointNames.get(api)
                            + " is already linked to L" + c.level() + " " + String.join(" › ", c.path()), c.id(),
                            String.join(" › ", c.path()), c.level(), null, null));
                }
            }
            String nameKey = String.join(" ", new TreeSet<>(Similarity.words(n.name())));
            for (ImportNode other : sameName.getOrDefault(nameKey.isEmpty() ? Similarity.normalize(n.name()) : nameKey, List.of())) {
                if (other.key().equals(n.key())) continue;
                findings.add(new Finding(FindingType.DUPLICATE_IN_FILE, Severity.DUPLICATE, "Also in this file at "
                        + String.join(" › ", path.get(other.key())) + (other.sourceRow() == null ? "" : " (row " + other.sourceRow() + ")"),
                        null, null, null, other.key(), null));
            }
            if (n.code() != null) {
                for (ImportNode other : sameCode.getOrDefault(n.code().toLowerCase(Locale.ROOT), List.of())) {
                    if (other.key().equals(n.key()) || sameName.getOrDefault(nameKey, List.of()).contains(other)) continue;
                    findings.add(new Finding(FindingType.DUPLICATE_IN_FILE, Severity.DUPLICATE, "Code " + n.code() + " is also used in this file by "
                            + other.name() + (other.sourceRow() == null ? "" : " (row " + other.sourceRow() + ")"), null, null, null, other.key(), null));
                }
            }
            views.add(new NodeView(n.key(), n.parentKey(), lvl, p, n.name(), n.code(), n.description(), n.apis(), n.resolution(),
                    n.mergeTargetId(), mergeTarget == null ? null : "L" + mergeTarget.level() + " " + String.join(" › ", mergeTarget.path()),
                    n.sourceRow(), findings));
        }

        int create = 0, merge = 0, skip = 0, matches = 0, duplicates = 0, overlaps = 0, errors = 0;
        Set<String> skipped = new HashSet<>();
        for (NodeView v : views) {
            boolean isSkipped = v.resolution() == ImportNode.Resolution.SKIP || (v.parentKey() != null && skipped.contains(v.parentKey()));
            if (isSkipped) {
                skipped.add(v.key());
                skip++;
                continue;
            }
            if (v.resolution() == ImportNode.Resolution.MERGE) merge++;
            else create++;
            if (v.findings().stream().anyMatch(f -> f.type() == FindingType.MATCH) || v.resolution() == ImportNode.Resolution.MERGE) matches++;
            if (v.findings().stream().anyMatch(f -> f.severity() == Severity.DUPLICATE)) duplicates++;
            if (v.findings().stream().anyMatch(f -> f.severity() == Severity.OVERLAP)) overlaps++;
            if (v.findings().stream().anyMatch(f -> f.severity() == Severity.ERROR)) errors++;
        }
        List<String> warnings = draft.getWarnings() == null ? List.of() : JSON.readValue(draft.getWarnings(), STRINGS);
        Result result = draft.getResult() == null ? null : JSON.readValue(draft.getResult(), Result.class);
        return new ImportView(draft.getId(), projectId, draft.getFileName(), draft.getFormat(), draft.getSheet(), draft.getStatus(),
                draft.getTargetParentId(), target == null ? null : "L" + target.level() + " " + String.join(" › ", target.path()), warnings, views,
                new Summary(views.size(), create, merge, skip, matches, duplicates, overlaps, errors), result, draft.getCreatedAt(), draft.getUpdatedAt());
    }

    private static boolean skipped(NodeView n, ImportView view) {
        Map<String, NodeView> byKey = new HashMap<>();
        view.nodes().forEach(v -> byKey.put(v.key(), v));
        for (NodeView v = n; v != null; v = v.parentKey() == null ? null : byKey.get(v.parentKey())) {
            if (v.resolution() == ImportNode.Resolution.SKIP) return true;
        }
        return false;
    }

    // ================================================================== helpers

    private CapabilityImport require(UUID projectId, UUID id) {
        return imports.findByIdAndProjectId(id, projectId).orElseThrow(() -> new NotFoundException("Capability import", id));
    }

    private static void requireDraft(CapabilityImport draft) {
        if (draft.getStatus() != CapabilityImport.Status.DRAFT) throw ValidationErrors.single("status", "this import has already been applied");
    }

    private static List<ImportNode> nodes(CapabilityImport draft) {
        return JSON.readValue(draft.getNodes(), NODES);
    }

    private static Map<String, CapabilityView> byPath(List<CapabilityView> existing) {
        Map<String, CapabilityView> out = new HashMap<>();
        existing.forEach(c -> out.put(pathKey(c.path()), c));
        return out;
    }

    private static String pathKey(List<String> path) {
        return path.stream().map(Similarity::normalize).collect(Collectors.joining("\u0001"));
    }
}
