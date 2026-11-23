package dev.specbuilder.generation;

import dev.specbuilder.api.dto.GenerationDiff;
import dev.specbuilder.api.dto.SnapshotView;
import dev.specbuilder.service.ProjectService;
import dev.specbuilder.validation.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Keeps snapshots of generated files and compares the current output with them. */
@Service
@Transactional
public class GenerationService {

    /** Snapshots kept per project and kind; older ones are removed. */
    static final int KEEP = 10;
    private static final int MAX_STORED = 1_000_000;
    private static final Logger log = LoggerFactory.getLogger(GenerationService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<TreeMap<String, String>> FILES = new TypeReference<>() { };

    private final GenerationSnapshotRepository snapshots;
    private final ProjectService projects;

    public GenerationService(GenerationSnapshotRepository snapshots, ProjectService projects) {
        this.snapshots = snapshots;
        this.projects = projects;
    }

    /**
     * Stores the files unless they are the same as the latest snapshot of this kind, in which case
     * that one is returned. Returns null if the files are too big to keep.
     */
    public SnapshotView record(UUID projectId, GenerationSnapshot.Kind kind, GenerationSnapshot.Reason reason, Map<String, String> files) {
        String json = JSON.writeValueAsString(new TreeMap<>(files));
        String hash = sha256(json);
        List<GenerationSnapshot> existing = snapshots.findByProjectIdAndKindOrderByCreatedAtDesc(projectId, kind);
        if (!existing.isEmpty() && existing.getFirst().getContentHash().equals(hash)) {
            return view(existing.getFirst());
        }
        String content = Base64.getEncoder().encodeToString(gzip(json));
        if (content.length() > MAX_STORED) {
            log.warn("Generated {} of project {} is too large to snapshot ({} chars compressed)", kind, projectId, content.length());
            return null;
        }
        GenerationSnapshot snapshot = snapshots.save(new GenerationSnapshot(projects.require(projectId), kind, reason, files.size(), hash, content));
        existing.stream().skip(KEEP - 1).forEach(snapshots::delete);
        return view(snapshot);
    }

    @Transactional(readOnly = true)
    public List<SnapshotView> list(UUID projectId, GenerationSnapshot.Kind kind) {
        projects.require(projectId);
        return snapshots.findByProjectIdAndKindOrderByCreatedAtDesc(projectId, kind).stream().map(GenerationService::view).toList();
    }

    /** Compares the current files with a snapshot; unchanged files are listed without their content. */
    @Transactional(readOnly = true)
    public GenerationDiff diff(UUID projectId, UUID snapshotId, java.util.function.Function<GenerationSnapshot.Kind, Map<String, String>> currentFiles) {
        GenerationSnapshot snapshot = snapshots.findByIdAndProjectId(snapshotId, projectId)
                .orElseThrow(() -> new NotFoundException("Snapshot", snapshotId));
        Map<String, String> current = currentFiles.apply(snapshot.getKind());
        Map<String, String> before = files(snapshot);
        SortedSet<String> paths = new TreeSet<>(before.keySet());
        paths.addAll(current.keySet());
        List<GenerationDiff.FileChange> changes = new ArrayList<>();
        for (String path : paths) {
            String a = before.get(path);
            String b = current.get(path);
            if (a == null) changes.add(new GenerationDiff.FileChange(path, GenerationDiff.Status.ADDED, null, b));
            else if (b == null) changes.add(new GenerationDiff.FileChange(path, GenerationDiff.Status.REMOVED, a, null));
            else if (!a.equals(b)) changes.add(new GenerationDiff.FileChange(path, GenerationDiff.Status.CHANGED, a, b));
            else changes.add(new GenerationDiff.FileChange(path, GenerationDiff.Status.UNCHANGED, null, null));
        }
        return new GenerationDiff(view(snapshot), changes);
    }

    static Map<String, String> files(GenerationSnapshot snapshot) {
        byte[] gz = Base64.getDecoder().decode(snapshot.getContent());
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
            return JSON.readValue(new String(in.readAllBytes(), StandardCharsets.UTF_8), FILES);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static SnapshotView view(GenerationSnapshot s) {
        return new SnapshotView(s.getId(), s.getKind(), s.getReason(), s.getCreatedAt(), s.getFileCount());
    }

    private static byte[] gzip(String text) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
