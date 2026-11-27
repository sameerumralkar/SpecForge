package dev.specbuilder.capabilities;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a capability file into a draft tree. Spreadsheets (Excel .xlsx, CSV, TSV) and JSON lists of rows can
 * have one of these layouts, recognised from the column headers:
 * <ul>
 *   <li><b>Level columns</b>: L1, L2 … L8 (or "Level 1" …), one row per capability path. Blank cells take the
 *   value above, as with merged cells. Optional "L2 code" / "L2 description" columns, and Code, Description
 *   and APIs columns for the deepest capability of the row.</li>
 *   <li><b>Parent column</b>: Name and Parent (the parent's code or name), with optional Code, Description, APIs.</li>
 *   <li><b>Level column</b>: Name and Level (1–8 or L1–L8), in outline order.</li>
 *   <li><b>Name only</b>: a flat list of top-level capabilities.</li>
 * </ul>
 * JSON can also be a nested tree ({@code name, code, description, apis, children}), such as the
 * {@code capabilities} of a SpecForge project export.
 */
final class CapabilityFileParser {

    record Parsed(String format, String sheet, List<ImportNode> nodes, List<String> warnings) {
    }

    static final int MAX_NODES = 5000;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private enum Role { NAME, CODE, DESCRIPTION, PARENT, LEVEL, APIS, LEVEL_NAME, LEVEL_CODE, LEVEL_DESCRIPTION }

    private record Column(int index, Role role, int level) {
    }

    private static final Pattern LEVEL_HEADER = Pattern.compile(
            "^(?:l|lvl|level|tier)\\s*-?\\s*([1-8])(?:\\s+(name|capability|capability name|title|code|id|ref|reference|number|description|desc|definition))?$");

    private CapabilityFileParser() {
    }

    static Parsed parse(String fileName, byte[] bytes) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".xls")) {
            throw new TableReader.UnreadableFileException("older .xls workbooks can't be read: save it as .xlsx (Excel Workbook) or CSV first");
        }
        boolean zip = bytes.length > 3 && bytes[0] == 'P' && bytes[1] == 'K';
        if (lower.endsWith(".xlsx") || zip) {
            List<String> warnings = new ArrayList<>();
            List<TableReader.Sheet> sheets = TableReader.xlsx(bytes);
            // The first sheet with recognisable headers; the others are named in a warning.
            for (TableReader.Sheet s : sheets) {
                if (headerRow(s.rows()) >= 0) {
                    if (sheets.size() > 1) {
                        warnings.add("Read sheet '" + s.name() + "'; the workbook's other sheets ("
                                + String.join(", ", sheets.stream().map(TableReader.Sheet::name).filter(n -> !n.equals(s.name())).toList())
                                + ") were not read.");
                    }
                    return table("EXCEL", s.name(), s.rows(), warnings);
                }
            }
            throw new TableReader.UnreadableFileException(noHeaders());
        }
        String text = new String(bytes, StandardCharsets.UTF_8).strip();
        if (text.startsWith("﻿")) text = text.substring(1).strip();
        if (lower.endsWith(".json") || text.startsWith("[") || text.startsWith("{")) {
            return json(text);
        }
        return table(lower.endsWith(".tsv") ? "TSV" : "CSV", null, TableReader.delimited(bytes).rows(), new ArrayList<>());
    }

    // ------------------------------------------------------------------ JSON

    private static Parsed json(String text) {
        JsonNode root;
        try {
            root = JSON.readTree(text);
        } catch (JacksonException e) {
            throw new TableReader.UnreadableFileException("not valid JSON: " + e.getOriginalMessage());
        }
        if (root.isObject()) {
            JsonNode list = firstPresent(root, "capabilities", "capabilityMap", "items", "data");
            if (list == null) throw new TableReader.UnreadableFileException("the JSON object has no \"capabilities\" list");
            root = list;
        }
        if (!root.isArray()) throw new TableReader.UnreadableFileException("expected a JSON list of capabilities");
        boolean tree = false;
        boolean rows = false;
        for (JsonNode item : root) {
            if (!item.isObject()) throw new TableReader.UnreadableFileException("each capability must be a JSON object");
            if (firstPresent(item, "children", "subCapabilities", "capabilities") != null) tree = true;
            for (String key : item.propertyNames()) {
                Column c = column(0, key);
                if (c != null && (c.role() == Role.LEVEL_NAME || c.role() == Role.PARENT || c.role() == Role.LEVEL)) rows = true;
            }
        }
        List<String> warnings = new ArrayList<>();
        if (tree || !rows) {
            List<ImportNode> nodes = new ArrayList<>();
            int[] counter = {0};
            for (JsonNode item : root) jsonTree(item, null, nodes, counter, warnings);
            return new Parsed("JSON", null, nodes, warnings);
        }
        // A list of row objects: their keys are the headers.
        List<String> headers = new ArrayList<>();
        for (JsonNode item : root) for (String key : item.propertyNames()) if (!headers.contains(key)) headers.add(key);
        List<List<String>> table = new ArrayList<>();
        table.add(headers);
        for (JsonNode item : root) {
            List<String> row = new ArrayList<>();
            for (String h : headers) row.add(text(item.path(h)));
            table.add(row);
        }
        return table("JSON", null, table, warnings);
    }

    private static void jsonTree(JsonNode item, String parentKey, List<ImportNode> nodes, int[] counter, List<String> warnings) {
        if (nodes.size() >= MAX_NODES) throw new TableReader.UnreadableFileException("more than " + MAX_NODES + " capabilities in one file");
        int itemNumber = ++counter[0];
        String name = text(firstPresent(item, "name", "title", "capability", "label"));
        if (name.isBlank()) {
            warnings.add("Item " + itemNumber + " has no name and was left out, with everything below it.");
            return;
        }
        String key = "n" + (nodes.size() + 1);
        nodes.add(new ImportNode(key, parentKey, name.strip(), blankToNull(text(firstPresent(item, "code", "id", "ref"))),
                blankToNull(text(firstPresent(item, "description", "desc", "definition"))), apis(firstPresent(item, "apis", "endpoints", "api")),
                ImportNode.Resolution.CREATE, null, itemNumber));
        JsonNode children = firstPresent(item, "children", "subCapabilities", "capabilities");
        if (children != null && children.isArray()) for (JsonNode child : children) jsonTree(child, key, nodes, counter, warnings);
    }

    // ------------------------------------------------------------------ tables

    private static Parsed table(String format, String sheet, List<List<String>> rows, List<String> warnings) {
        int header = headerRow(rows);
        if (header < 0) throw new TableReader.UnreadableFileException(noHeaders());
        List<Column> columns = new ArrayList<>();
        List<String> ignored = new ArrayList<>();
        List<String> headers = rows.get(header);
        for (int i = 0; i < headers.size(); i++) {
            Column c = column(i, headers.get(i));
            if (c != null) columns.add(c);
            else if (!headers.get(i).isBlank()) ignored.add(headers.get(i).strip());
        }
        if (!ignored.isEmpty()) warnings.add("Columns not used: " + String.join(", ", ignored) + ".");
        List<List<String>> data = rows.subList(header + 1, rows.size());
        int firstDataRow = header + 2; // 1-based row numbers, as the spreadsheet shows them
        List<ImportNode> nodes;
        if (columns.stream().anyMatch(c -> c.role() == Role.LEVEL_NAME)) {
            nodes = levelColumns(columns, data, firstDataRow, warnings);
        } else if (has(columns, Role.NAME) && has(columns, Role.PARENT)) {
            nodes = parentColumn(columns, data, firstDataRow, warnings);
        } else if (has(columns, Role.NAME) && has(columns, Role.LEVEL)) {
            nodes = levelColumn(columns, data, firstDataRow, warnings);
        } else if (has(columns, Role.NAME)) {
            nodes = new ArrayList<>();
            for (int r = 0; r < data.size(); r++) {
                String name = cell(data.get(r), columns, Role.NAME, 0);
                if (!name.isEmpty()) nodes.add(node(nodes, null, name, columns, data.get(r), firstDataRow + r));
            }
            if (!nodes.isEmpty()) warnings.add("There are no level or parent columns, so every capability is at the top level.");
        } else {
            throw new TableReader.UnreadableFileException(noHeaders());
        }
        if (nodes.isEmpty()) throw new TableReader.UnreadableFileException("the file has headers but no capabilities below them");
        return new Parsed(format, sheet, nodes, warnings);
    }

    /** One row per path, L1 … L8 columns; blank cells take the value from the row above. */
    private static List<ImportNode> levelColumns(List<Column> columns, List<List<String>> data, int firstRow, List<String> warnings) {
        List<ImportNode> nodes = new ArrayList<>();
        Map<String, Integer> byPath = new HashMap<>();
        String[] previous = new String[9];
        for (int r = 0; r < data.size(); r++) {
            List<String> row = data.get(r);
            int rowNumber = firstRow + r;
            int deepest = 0;
            for (Column c : columns) {
                if (c.role() == Role.LEVEL_NAME && !value(row, c).isEmpty()) deepest = Math.max(deepest, c.level());
            }
            if (deepest == 0) continue;
            String[] path = new String[deepest + 1];
            boolean ok = true;
            for (int level = 1; level <= deepest; level++) {
                String v = cell(row, columns, Role.LEVEL_NAME, level);
                if (v.isEmpty()) v = previous[level] == null ? "" : previous[level];
                if (v.isEmpty()) {
                    warnings.add("Row " + rowNumber + ": L" + level + " is empty and there is no row above to take it from; the row was left out.");
                    ok = false;
                    break;
                }
                path[level] = v;
            }
            if (!ok) continue;
            for (int level = 1; level <= deepest; level++) previous[level] = path[level];
            for (int level = deepest + 1; level <= 8; level++) previous[level] = null;

            String parentKey = null;
            StringBuilder pathKey = new StringBuilder();
            for (int level = 1; level <= deepest; level++) {
                pathKey.append('\u0001').append(path[level].toLowerCase(Locale.ROOT));
                Integer index = byPath.get(pathKey.toString());
                String code = blankToNull(cell(row, columns, Role.LEVEL_CODE, level));
                String description = blankToNull(cell(row, columns, Role.LEVEL_DESCRIPTION, level));
                if (level == deepest) {
                    if (code == null) code = blankToNull(cell(row, columns, Role.CODE, 0));
                    if (description == null) description = blankToNull(cell(row, columns, Role.DESCRIPTION, 0));
                }
                List<String> apis = level == deepest ? apis(cell(row, columns, Role.APIS, 0)) : List.of();
                if (index == null) {
                    if (nodes.size() >= MAX_NODES) throw new TableReader.UnreadableFileException("more than " + MAX_NODES + " capabilities in one file");
                    nodes.add(new ImportNode("n" + (nodes.size() + 1), parentKey, path[level], code, description, apis,
                            ImportNode.Resolution.CREATE, null, rowNumber));
                    index = nodes.size() - 1;
                    byPath.put(pathKey.toString(), index);
                } else {
                    ImportNode n = nodes.get(index);
                    if (code != null && n.code() != null && !code.equalsIgnoreCase(n.code())) {
                        warnings.add("Row " + rowNumber + ": " + path[level] + " already has code " + n.code() + " from row " + n.sourceRow() + "; " + code + " was ignored.");
                    }
                    List<String> allApis = new ArrayList<>(n.apis());
                    apis.stream().filter(a -> !allApis.contains(a)).forEach(allApis::add);
                    nodes.set(index, new ImportNode(n.key(), n.parentKey(), n.name(), n.code() != null ? n.code() : code,
                            n.description() != null ? n.description() : description, allApis, n.resolution(), null, n.sourceRow()));
                }
                parentKey = nodes.get(index).key();
            }
        }
        return nodes;
    }

    /** Name and Parent columns; the parent is found by code, then by name. */
    private static List<ImportNode> parentColumn(List<Column> columns, List<List<String>> data, int firstRow, List<String> warnings) {
        List<ImportNode> nodes = new ArrayList<>();
        List<String> parents = new ArrayList<>();
        for (int r = 0; r < data.size(); r++) {
            String name = cell(data.get(r), columns, Role.NAME, 0);
            if (name.isEmpty()) continue;
            nodes.add(node(nodes, null, name, columns, data.get(r), firstRow + r));
            parents.add(cell(data.get(r), columns, Role.PARENT, 0));
        }
        Map<String, String> byCode = new HashMap<>();
        Map<String, List<String>> byName = new HashMap<>();
        for (ImportNode n : nodes) {
            if (n.code() != null) byCode.putIfAbsent(n.code().toLowerCase(Locale.ROOT), n.key());
            byName.computeIfAbsent(n.name().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(n.key());
        }
        Map<String, String> parentOf = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            String p = parents.get(i).strip().toLowerCase(Locale.ROOT);
            if (p.isEmpty()) continue;
            String key = byCode.get(p);
            if (key == null) {
                List<String> named = byName.getOrDefault(p, List.of());
                if (named.size() > 1) warnings.add("Row " + nodes.get(i).sourceRow() + ": more than one capability is called '" + parents.get(i).strip() + "'; the first was used as the parent.");
                key = named.isEmpty() ? null : named.get(0);
            }
            if (key == null) {
                warnings.add("Row " + nodes.get(i).sourceRow() + ": parent '" + parents.get(i).strip() + "' is not in the file; " + nodes.get(i).name() + " was put at the top level.");
            } else if (key.equals(nodes.get(i).key())) {
                warnings.add("Row " + nodes.get(i).sourceRow() + ": " + nodes.get(i).name() + " is its own parent; it was put at the top level.");
            } else {
                parentOf.put(nodes.get(i).key(), key);
            }
        }
        // Break circles: a capability whose parents lead back to itself goes to the top level.
        for (ImportNode n : nodes) {
            Set<String> seen = new HashSet<>();
            for (String k = n.key(); k != null; k = parentOf.get(k)) {
                if (!seen.add(k)) {
                    warnings.add("Row " + n.sourceRow() + ": the parents of " + n.name() + " go round in a circle; it was put at the top level.");
                    parentOf.remove(n.key());
                    break;
                }
            }
        }
        List<ImportNode> out = new ArrayList<>();
        for (ImportNode n : nodes) out.add(new ImportNode(n.key(), parentOf.get(n.key()), n.name(), n.code(), n.description(), n.apis(), n.resolution(), null, n.sourceRow()));
        return ordered(out);
    }

    /** Name and Level columns, in outline order: each capability goes under the last one a level up. */
    private static List<ImportNode> levelColumn(List<Column> columns, List<List<String>> data, int firstRow, List<String> warnings) {
        List<ImportNode> nodes = new ArrayList<>();
        String[] last = new String[10];
        for (int r = 0; r < data.size(); r++) {
            List<String> row = data.get(r);
            String name = cell(row, columns, Role.NAME, 0);
            if (name.isEmpty()) continue;
            String levelText = cell(row, columns, Role.LEVEL, 0).replaceAll("(?i)^(l|lvl|level)\\s*", "").strip();
            int level;
            try {
                level = (int) Double.parseDouble(levelText);
            } catch (NumberFormatException e) {
                warnings.add("Row " + (firstRow + r) + ": level '" + levelText + "' is not a number from 1 to 8; " + name + " was left out.");
                continue;
            }
            if (level < 1 || level > 9) {
                warnings.add("Row " + (firstRow + r) + ": level " + level + " is not from 1 to 8; " + name + " was left out.");
                continue;
            }
            int parentLevel = level - 1;
            while (parentLevel > 0 && last[parentLevel] == null) parentLevel--;
            if (parentLevel != level - 1) {
                warnings.add("Row " + (firstRow + r) + ": " + name + " is L" + level + " but there is no L" + (level - 1) + " above it; it went under the nearest capability above.");
            }
            ImportNode n = node(nodes, parentLevel == 0 ? null : last[parentLevel], name, columns, row, firstRow + r);
            nodes.add(n);
            last[level] = n.key();
            for (int l = level + 1; l < last.length; l++) last[l] = null;
        }
        return nodes;
    }

    private static ImportNode node(List<ImportNode> nodes, String parentKey, String name, List<Column> columns, List<String> row, int rowNumber) {
        if (nodes.size() >= MAX_NODES) throw new TableReader.UnreadableFileException("more than " + MAX_NODES + " capabilities in one file");
        return new ImportNode("n" + (nodes.size() + 1), parentKey, name, blankToNull(cell(row, columns, Role.CODE, 0)),
                blankToNull(cell(row, columns, Role.DESCRIPTION, 0)), apis(cell(row, columns, Role.APIS, 0)), ImportNode.Resolution.CREATE, null, rowNumber);
    }

    /** Tree order: each node followed by its children, keeping the file's order among siblings. */
    static List<ImportNode> ordered(List<ImportNode> nodes) {
        Map<String, List<ImportNode>> children = new LinkedHashMap<>();
        Set<String> keys = new HashSet<>();
        nodes.forEach(n -> keys.add(n.key()));
        for (ImportNode n : nodes) {
            String parent = n.parentKey() != null && keys.contains(n.parentKey()) ? n.parentKey() : null;
            children.computeIfAbsent(parent, k -> new ArrayList<>()).add(n);
        }
        List<ImportNode> out = new ArrayList<>();
        Deque<ImportNode> stack = new ArrayDeque<>();
        List<ImportNode> roots = children.getOrDefault(null, List.of());
        for (int i = roots.size() - 1; i >= 0; i--) stack.push(roots.get(i));
        Set<String> seen = new HashSet<>();
        while (!stack.isEmpty()) {
            ImportNode n = stack.pop();
            if (!seen.add(n.key())) continue;
            out.add(n);
            List<ImportNode> kids = children.getOrDefault(n.key(), List.of());
            for (int i = kids.size() - 1; i >= 0; i--) stack.push(kids.get(i));
        }
        return out;
    }

    // ------------------------------------------------------------------ headers

    /** The row (within the first 20) with the most recognised headers; -1 if none. */
    private static int headerRow(List<List<String>> rows) {
        int best = -1;
        int bestCount = 0;
        for (int r = 0; r < Math.min(20, rows.size()); r++) {
            int count = 0;
            boolean structure = false;
            for (int i = 0; i < rows.get(r).size(); i++) {
                Column c = column(i, rows.get(r).get(i));
                if (c != null) {
                    count++;
                    if (c.role() == Role.NAME || c.role() == Role.LEVEL_NAME) structure = true;
                }
            }
            if (structure && count > bestCount) {
                best = r;
                bestCount = count;
            }
        }
        return best;
    }

    private static Column column(int index, String header) {
        String h = header == null ? "" : header.replace("*", "").replace('_', ' ').strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (h.isEmpty()) return null;
        Matcher m = LEVEL_HEADER.matcher(h);
        if (m.matches()) {
            int level = Integer.parseInt(m.group(1));
            String kind = m.group(2);
            if (kind == null || kind.matches("name|capability|capability name|title")) return new Column(index, Role.LEVEL_NAME, level);
            if (kind.matches("code|id|ref|reference|number")) return new Column(index, Role.LEVEL_CODE, level);
            return new Column(index, Role.LEVEL_DESCRIPTION, level);
        }
        return switch (h) {
            case "name", "capability", "capability name", "title", "label" -> new Column(index, Role.NAME, 0);
            case "code", "id", "capability id", "capability code", "ref", "reference", "number" -> new Column(index, Role.CODE, 0);
            case "description", "desc", "definition", "details", "summary" -> new Column(index, Role.DESCRIPTION, 0);
            case "parent", "parent code", "parent id", "parent name", "parent capability" -> new Column(index, Role.PARENT, 0);
            case "level", "lvl", "tier", "depth" -> new Column(index, Role.LEVEL, 0);
            case "apis", "api", "endpoints", "endpoint", "operations", "linked apis" -> new Column(index, Role.APIS, 0);
            default -> null;
        };
    }

    private static boolean has(List<Column> columns, Role role) {
        return columns.stream().anyMatch(c -> c.role() == role);
    }

    private static String cell(List<String> row, List<Column> columns, Role role, int level) {
        for (Column c : columns) {
            if (c.role() == role && c.level() == level) {
                String v = value(row, c);
                if (!v.isEmpty()) return v;
            }
        }
        return "";
    }

    private static String value(List<String> row, Column c) {
        return c.index() < row.size() && row.get(c.index()) != null ? row.get(c.index()).strip() : "";
    }

    private static List<String> apis(String text) {
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(text.split("[,;\\n|]")).map(String::strip).filter(s -> !s.isEmpty()).distinct().toList();
    }

    private static List<String> apis(JsonNode node) {
        if (node == null || node.isNull()) return List.of();
        if (node.isArray()) {
            List<String> out = new ArrayList<>();
            node.forEach(n -> { if (!n.asString("").isBlank()) out.add(n.asString().strip()); });
            return out;
        }
        return apis(node.asString(""));
    }

    private static JsonNode firstPresent(JsonNode item, String... keys) {
        for (String k : keys) {
            for (String actual : item.propertyNames()) {
                if (actual.equalsIgnoreCase(k)) {
                    JsonNode v = item.get(actual);
                    if (v != null && !v.isNull()) return v;
                }
            }
        }
        return null;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return "";
        if (node.isArray()) {
            List<String> parts = new ArrayList<>();
            node.forEach(n -> parts.add(n.asString("")));
            return String.join(", ", parts);
        }
        return node.isNumber() ? node.asString() : node.asString("");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static String noHeaders() {
        return "no capability columns found. Use a header row with L1, L2 … L8 columns (one row per capability path), "
                + "or Name with Parent, or Name with Level; Code, Description and APIs columns are optional";
    }
}
