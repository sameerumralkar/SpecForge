package dev.specbuilder.capabilities;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads spreadsheets as rows of text: Excel workbooks (.xlsx, read straight from their XML, so no extra
 * library is needed) and delimited text (CSV, semicolon-separated or TSV, quotes handled).
 */
final class TableReader {

    /** A sheet: its name (null for text files) and its rows, each a list of cell texts. */
    record Sheet(String name, List<List<String>> rows) {
    }

    static final class UnreadableFileException extends RuntimeException {
        UnreadableFileException(String message) {
            super(message);
        }
    }

    private static final long MAX_ENTRY_BYTES = 30L * 1024 * 1024;
    private static final String MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String RELS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    private TableReader() {
    }

    // ------------------------------------------------------------------ xlsx

    /** Every sheet of an .xlsx workbook, in workbook order. */
    static List<Sheet> xlsx(byte[] bytes) {
        Map<String, byte[]> parts = unzip(bytes);
        byte[] workbook = parts.get("xl/workbook.xml");
        if (workbook == null) throw new UnreadableFileException("not an Excel workbook (.xlsx): xl/workbook.xml is missing");
        List<String> shared = sharedStrings(parts.get("xl/sharedStrings.xml"));
        Map<String, String> targets = new HashMap<>();
        byte[] rels = parts.get("xl/_rels/workbook.xml.rels");
        if (rels != null) {
            NodeList list = xml(rels).getElementsByTagNameNS("*", "Relationship");
            for (int i = 0; i < list.getLength(); i++) {
                Element r = (Element) list.item(i);
                String target = r.getAttribute("Target");
                target = target.startsWith("/") ? target.substring(1) : "xl/" + target;
                targets.put(r.getAttribute("Id"), target);
            }
        }
        List<Sheet> sheets = new ArrayList<>();
        NodeList list = xml(workbook).getElementsByTagNameNS(MAIN, "sheet");
        for (int i = 0; i < list.getLength(); i++) {
            Element s = (Element) list.item(i);
            String target = targets.getOrDefault(s.getAttributeNS(RELS, "id"), "xl/worksheets/sheet" + (i + 1) + ".xml");
            byte[] sheet = parts.get(target);
            if (sheet != null) sheets.add(new Sheet(s.getAttribute("name"), rows(xml(sheet), shared)));
        }
        if (sheets.isEmpty()) throw new UnreadableFileException("the workbook has no sheets");
        return sheets;
    }

    private static List<List<String>> rows(Document sheet, List<String> shared) {
        List<List<String>> rows = new ArrayList<>();
        NodeList rowNodes = sheet.getElementsByTagNameNS(MAIN, "row");
        int expectedRow = 1;
        for (int i = 0; i < rowNodes.getLength(); i++) {
            Element row = (Element) rowNodes.item(i);
            int number = row.hasAttribute("r") ? Integer.parseInt(row.getAttribute("r")) : expectedRow;
            while (rows.size() < number - 1) rows.add(new ArrayList<>()); // empty rows are left out of the XML
            expectedRow = number + 1;
            List<String> cells = new ArrayList<>();
            NodeList cellNodes = row.getElementsByTagNameNS(MAIN, "c");
            for (int j = 0; j < cellNodes.getLength(); j++) {
                Element c = (Element) cellNodes.item(j);
                int column = c.hasAttribute("r") ? column(c.getAttribute("r")) : cells.size();
                while (cells.size() < column) cells.add("");
                cells.add(cellText(c, shared));
            }
            rows.add(cells);
        }
        return rows;
    }

    private static String cellText(Element c, List<String> shared) {
        String type = c.getAttribute("t");
        if ("inlineStr".equals(type)) return allText(c, "t");
        String v = firstText(c, "v");
        if (v == null) return "";
        if ("s".equals(type)) {
            int index = Integer.parseInt(v.trim());
            return index >= 0 && index < shared.size() ? shared.get(index) : "";
        }
        if ("b".equals(type)) return "1".equals(v) ? "TRUE" : "FALSE";
        if (type.isEmpty() || "n".equals(type)) {
            // 1.0 → "1"; keep codes like 1.10 as Excel stored them.
            return v.endsWith(".0") ? v.substring(0, v.length() - 2) : v;
        }
        return v;
    }

    private static List<String> sharedStrings(byte[] part) {
        List<String> out = new ArrayList<>();
        if (part == null) return out;
        NodeList items = xml(part).getElementsByTagNameNS(MAIN, "si");
        for (int i = 0; i < items.getLength(); i++) out.add(allText((Element) items.item(i), "t"));
        return out;
    }

    /** "C12" → 2. */
    static int column(String ref) {
        int n = 0;
        for (char ch : ref.toCharArray()) {
            if (!Character.isLetter(ch)) break;
            n = n * 26 + (Character.toUpperCase(ch) - 'A' + 1);
        }
        return n - 1;
    }

    private static String allText(Element e, String tag) {
        StringBuilder b = new StringBuilder();
        NodeList ts = e.getElementsByTagNameNS(MAIN, tag);
        for (int i = 0; i < ts.getLength(); i++) {
            // Skip phonetic runs (rPh), which repeat the text in another script.
            if (!"rPh".equals(ts.item(i).getParentNode().getLocalName())) b.append(ts.item(i).getTextContent());
        }
        return b.toString();
    }

    private static String firstText(Element e, String tag) {
        NodeList ts = e.getElementsByTagNameNS(MAIN, tag);
        return ts.getLength() == 0 ? null : ts.item(0).getTextContent();
    }

    private static Map<String, byte[]> unzip(byte[] bytes) {
        Map<String, byte[]> parts = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (!name.endsWith(".xml") && !name.endsWith(".rels")) continue;
                parts.put(name.startsWith("/") ? name.substring(1) : name, readLimited(zip, name));
            }
        } catch (IOException e) {
            throw new UnreadableFileException("not a readable Excel workbook (.xlsx): " + e.getMessage());
        }
        if (parts.isEmpty()) throw new UnreadableFileException("not an Excel workbook (.xlsx); older .xls files must be saved as .xlsx or CSV first");
        return parts;
    }

    private static byte[] readLimited(InputStream in, String name) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        for (int n; (n = in.read(buffer)) > 0; ) {
            total += n;
            if (total > MAX_ENTRY_BYTES) throw new UnreadableFileException(name + " in the workbook is too large");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private static Document xml(byte[] bytes) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            DocumentBuilder builder = f.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            throw new UnreadableFileException("the workbook's XML could not be read: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ delimited text

    /** CSV, semicolon-separated or TSV, whichever the first line uses most. */
    static Sheet delimited(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) text = text.substring(1);
        String firstLine = text.lines().findFirst().orElse("");
        char delimiter = ',';
        long best = -1;
        for (char d : new char[]{',', ';', '\t'}) {
            long n = firstLine.chars().filter(ch -> ch == d).count();
            if (n > best) {
                best = n;
                delimiter = d;
            }
        }
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"' && cell.isEmpty()) {
                quoted = true;
            } else if (ch == delimiter) {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(ch);
            }
        }
        if (!cell.isEmpty() || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return new Sheet(null, rows);
    }
}
