package dev.specbuilder.api.dto;

import java.util.List;

/**
 * The current output compared with a snapshot. {@code before}/{@code after} are given for added,
 * removed and changed files, and left out for unchanged ones.
 */
public record GenerationDiff(SnapshotView baseline, List<FileChange> files) {

    public enum Status { ADDED, REMOVED, CHANGED, UNCHANGED }

    public record FileChange(String path, Status status, String before, String after) {
    }
}
