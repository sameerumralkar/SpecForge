package dev.specbuilder.api;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Capability imports: reading Excel, CSV and JSON, comparing with the map, editing, applying. */
class CapabilityImportApiTest extends ApiTestSupport {

    private static String b64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private Response upload(String p, String fileName, String content) {
        return post("/api/projects/" + p + "/capability-imports", map("fileName", fileName, "content", content));
    }

    private static Map<String, JsonNode> byName(JsonNode view) {
        Map<String, JsonNode> out = new LinkedHashMap<>();
        view.get("nodes").forEach(n -> out.put(n.get("name").asString(), n));
        return out;
    }

    private static List<String> findings(JsonNode node) {
        List<String> out = new ArrayList<>();
        node.get("findings").forEach(f -> out.add(f.get("type").asString() + " " + f.get("severity").asString()));
        return out;
    }

    /** An existing map: Payments (PAY) › Card payments (PAY.CARD) › Refunds, with "pay" linked to Refunds. */
    private Map<String, String> existingMap(String p) {
        String api = "/api/projects/" + p;
        String money = payload(p, "Money", field("value", "DECIMAL"));
        String pay = post(api + "/endpoints", rest("pay", "POST", "/pay", money, money)).expect(201).id();
        String payments = post(api + "/capabilities", map("name", "Payments", "code", "PAY")).id();
        String cards = post(api + "/capabilities", map("name", "Card payments", "code", "PAY.CARD", "parentId", payments)).id();
        String refunds = post(api + "/capabilities", map("name", "Refunds", "parentId", cards,
                "description", "Return money to the customer for a settled card payment")).id();
        put(api + "/capabilities/" + refunds + "/apis", map("ids", List.of(pay))).expect(200);
        return Map.of("payments", payments, "cards", cards, "refunds", refunds, "pay", pay);
    }

    @Test
    void readsLevelColumnsFillsDownAndComparesWithTheMap() {
        String p = newProject();
        Map<String, String> ids = existingMap(p);
        String csv = """
                L1,L1 code,L2,L2 Code,L3,Description,APIs,Owner
                Payments,PAY,Card payments,PAY.CARD,Card refund,Give money back for card payments,"pay, nope",Ops
                ,,,,Chargebacks,,,Ops
                ,,Wallet payment,PAY.CARD,,,,Ops
                ,,Cards payment,,Capture,,,Ops
                Lending,LND,Loans,,Refund,,,Risk
                """;
        JsonNode view = upload(p, "map.csv", b64(csv)).expect(201).json();
        assertThat(view.get("format").asString()).isEqualTo("CSV");
        assertThat(view.get("status").asString()).isEqualTo("DRAFT");
        assertThat(texts(view.get("warnings"), null)).containsExactly("Columns not used: Owner.");
        assertThat(texts(view.get("nodes"), "name")).containsExactly("Payments", "Card payments", "Card refund", "Chargebacks",
                "Wallet payment", "Cards payment", "Capture", "Lending", "Loans", "Refund");
        Map<String, JsonNode> n = byName(view);

        // Already in the map at the same place: merged into it by default.
        assertThat(n.get("Payments").get("resolution").asString()).isEqualTo("MERGE");
        assertThat(n.get("Payments").get("mergeTargetId").asString()).isEqualTo(ids.get("payments"));
        assertThat(n.get("Card payments").get("resolution").asString()).isEqualTo("MERGE");
        // Blank cells were filled from the row above.
        assertThat(texts(n.get("Chargebacks").get("path"), null)).containsExactly("Payments", "Card payments", "Chargebacks");
        assertThat(n.get("Chargebacks").get("level").asInt()).isEqualTo(3);
        // Codes per level; Description and APIs for the deepest capability of the row.
        assertThat(n.get("Card refund").get("description").asString()).isEqualTo("Give money back for card payments");
        assertThat(texts(n.get("Card refund").get("apis"), null)).containsExactly("pay", "nope");

        assertThat(findings(n.get("Card refund"))).contains("CONTAINED_NAME OVERLAP", "API_ALREADY_LINKED OVERLAP", "UNKNOWN_API WARNING");
        assertThat(findings(n.get("Wallet payment"))).contains("SAME_CODE ERROR");
        assertThat(findings(n.get("Cards payment"))).contains("SIMILAR_NAME DUPLICATE");
        assertThat(n.get("Cards payment").get("findings").get(0).get("existingId").asString()).isEqualTo(ids.get("cards"));
        assertThat(findings(n.get("Refund"))).contains("SIMILAR_NAME DUPLICATE");
        assertThat(n.get("Refund").get("findings").get(0).get("message").asString()).contains("L3 Payments › Card payments › Refunds");
        JsonNode summary = view.get("summary");
        assertThat(summary.get("total").asInt()).isEqualTo(10);
        assertThat(summary.get("merge").asInt()).isEqualTo(2);
        assertThat(summary.get("errors").asInt()).isEqualTo(1);

        // Applying is refused while an error stands.
        String id = view.get("id").asString();
        Response refused = post("/api/projects/" + p + "/capability-imports/" + id + "/apply", null).expect(400);
        assertThat(refused.errorsFor("nodes[4]")).anyMatch(m -> m.contains("Code PAY.CARD is already used"));
    }

    @Test
    void readsAnExcelWorkbookWithParentColumn() throws Exception {
        String p = newProject();
        JsonNode view = upload(p, "capabilities.xlsx", Base64.getEncoder().encodeToString(workbook())).expect(201).json();
        assertThat(view.get("format").asString()).isEqualTo("EXCEL");
        assertThat(view.get("sheet").asString()).isEqualTo("Capabilities");
        assertThat(texts(view.get("warnings"), null)).anyMatch(w -> w.contains("other sheets (Notes)"));
        assertThat(texts(view.get("nodes"), "name")).containsExactly("Customer", "Onboarding", "KYC check", "Customer care");
        Map<String, JsonNode> n = byName(view);
        assertThat(n.get("KYC check").get("level").asInt()).isEqualTo(3);
        assertThat(n.get("KYC check").get("code").asString()).isEqualTo("1.1.1");
        assertThat(n.get("KYC check").get("sourceRow").asInt()).isEqualTo(5);
        assertThat(n.get("Customer care").get("parentKey").asString()).isEqualTo(n.get("Customer").get("key").asString());

        assertThat(upload(p, "old.xls", b64("whatever")).expect(400).errorsFor("file")).anyMatch(m -> m.contains("save it as .xlsx"));
        assertThat(upload(p, "x.csv", b64("Owner,Team\nA,B")).expect(400).errorsFor("file")).anyMatch(m -> m.contains("no capability columns"));
        assertThat(upload(p, "x.json", b64("{\"nope\": 1}")).expect(400).errorsFor("file")).anyMatch(m -> m.contains("capabilities"));
    }

    @Test
    void readsJsonTreesAndOutlinesAndFlagsWhatIsTooDeep() {
        String p = newProject();
        String tree = """
                {"capabilities": [{"name": "Sales", "code": "S", "children": [
                   {"name": "Quotes", "apis": ["quote"], "children": [{"title": "Quote approval"}]},
                   {"name": "quotes"}]}]}""";
        JsonNode view = upload(p, "map.json", b64(tree)).expect(201).json();
        assertThat(texts(view.get("nodes"), "level")).containsExactly("1", "2", "3", "2");
        assertThat(findings(byName(view).get("quotes"))).contains("DUPLICATE_IN_FILE DUPLICATE");

        String outline = """
                [{"Level": 1, "Name": "A"}, {"Level": "L2", "Name": "B"}, {"Level": 3, "Name": "C"}, {"Level": 4, "Name": "D"},
                 {"Level": 5, "Name": "E"}, {"Level": 6, "Name": "F"}, {"Level": 7, "Name": "G"}, {"Level": 8, "Name": "H"},
                 {"Level": 9, "Name": "I"}, {"Level": 4, "Name": "J"}, {"Level": "x", "Name": "K"}]""";
        view = upload(p, "outline.json", b64(outline)).expect(201).json();
        Map<String, JsonNode> n = byName(view);
        assertThat(n.get("I").get("level").asInt()).isEqualTo(9);
        assertThat(findings(n.get("I"))).containsExactly("TOO_DEEP ERROR");
        assertThat(n.get("J").get("parentKey").asString()).isEqualTo(n.get("C").get("key").asString());
        assertThat(texts(view.get("warnings"), null)).anyMatch(w -> w.contains("level 'x'"));
    }

    @Test
    void editsTheDraftThenAppliesIt() {
        String p = newProject();
        Map<String, String> ids = existingMap(p);
        String csv = """
                L1,L2,L3,APIs
                Payments,Card payments,Card disputes,pay
                Payments,Card payments,Card refunds,
                Payments,Wallets,Top-up,
                Payments,Wallets,Withdraw,
                """;
        JsonNode view = upload(p, "map.csv", b64(csv)).expect(201).json();
        String base = "/api/projects/" + p + "/capability-imports/" + view.get("id").asString();
        Map<String, JsonNode> n = byName(view);

        // Merge "Card refunds" into the existing Refunds, skip Withdraw, rename Top-up, move Wallets under Card payments.
        ArrayNode nodes = (ArrayNode) view.get("nodes").deepCopy();
        for (JsonNode node : nodes) {
            ObjectNode o = (ObjectNode) node;
            switch (o.get("name").asString()) {
                case "Card refunds" -> { o.put("resolution", "MERGE"); o.put("mergeTargetId", ids.get("refunds")); o.put("description", "From the file"); }
                case "Withdraw" -> o.put("resolution", "SKIP");
                case "Top-up" -> o.put("name", "Top up wallet");
                case "Wallets" -> o.put("parentKey", n.get("Card payments").get("key").asString());
                default -> { }
            }
        }
        JsonNode edited = put(base, map("targetParentId", null, "nodes", nodes)).expect(200).json();
        Map<String, JsonNode> e = byName(edited);
        assertThat(e.get("Top up wallet").get("level").asInt()).isEqualTo(4);
        assertThat(e.get("Card refunds").get("mergeTargetPath").asString()).isEqualTo("L3 Payments › Card payments › Refunds");
        assertThat(edited.get("summary").get("skip").asInt()).isEqualTo(1);

        // A circle, and a merge without a target, are refused.
        ArrayNode bad = (ArrayNode) edited.get("nodes").deepCopy();
        ((ObjectNode) bad.get(0)).put("parentKey", bad.get(1).get("key").asString());
        ((ObjectNode) bad.get(2)).put("resolution", "MERGE");
        ((ObjectNode) bad.get(2)).putNull("mergeTargetId");
        assertThat(put(base, map("nodes", bad)).expect(400).errorFields()).contains("nodes[2].mergeTargetId");

        JsonNode applied = post(base + "/apply", null).expect(200).json();
        assertThat(applied.get("status").asString()).isEqualTo("APPLIED");
        JsonNode result = applied.get("result");
        assertThat(result.get("created").asInt()).isEqualTo(3); // Card disputes, Wallets, Top up wallet
        assertThat(result.get("merged").asInt()).isEqualTo(3);  // Payments, Card payments, Card refunds
        assertThat(result.get("skipped").asInt()).isEqualTo(1);
        assertThat(result.get("apisLinked").asInt()).isEqualTo(1);

        JsonNode map = get("/api/projects/" + p + "/capabilities").json();
        assertThat(texts(map, "name")).containsExactly("Payments", "Card payments", "Refunds", "Card disputes", "Wallets", "Top up wallet");
        JsonNode refunds = get("/api/projects/" + p + "/capabilities/" + ids.get("refunds")).json();
        assertThat(refunds.get("description").asString()).isEqualTo("Return money to the customer for a settled card payment"); // kept
        assertThat(map.get(3).get("endpointIds").get(0).asString()).isEqualTo(ids.get("pay"));

        assertThat(post(base + "/apply", null).expect(400).errorsFor("status")).anyMatch(m -> m.contains("already been applied"));
        assertThat(texts(get("/api/projects/" + p + "/capability-imports").json(), "status")).containsExactly("APPLIED");
        delete(base).expect(204);
        assertThat(get("/api/projects/" + p + "/capability-imports").json().size()).isZero();
    }

    @Test
    void importsUnderAnExistingCapability() {
        String p = newProject();
        Map<String, String> ids = existingMap(p);
        JsonNode view = upload(p, "sub.csv", b64("Name,Level\nDisputes,1\nEvidence,2\n")).expect(201).json();
        String base = "/api/projects/" + p + "/capability-imports/" + view.get("id").asString();
        JsonNode moved = put(base, map("targetParentId", ids.get("cards"), "nodes", view.get("nodes"))).expect(200).json();
        assertThat(moved.get("targetParentPath").asString()).isEqualTo("L2 Payments › Card payments");
        assertThat(texts(moved.get("nodes"), "level")).containsExactly("3", "4");
        post(base + "/apply", null).expect(200);
        assertThat(texts(get("/api/projects/" + p + "/capabilities").json(), "name"))
                .containsExactly("Payments", "Card payments", "Refunds", "Disputes", "Evidence");
    }

    // ------------------------------------------------------------------ a small .xlsx, as Excel writes it

    private static byte[] workbook() throws Exception {
        String ns = "xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"";
        String rel = "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"";
        Map<String, String> parts = new LinkedHashMap<>();
        parts.put("[Content_Types].xml", "<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>");
        parts.put("xl/workbook.xml", "<?xml version=\"1.0\"?><workbook " + ns + " " + rel + "><sheets>"
                + "<sheet name=\"Notes\" sheetId=\"1\" r:id=\"rId1\"/><sheet name=\"Capabilities\" sheetId=\"2\" r:id=\"rId2\"/></sheets></workbook>");
        parts.put("xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Target=\"worksheets/sheet1.xml\" Type=\"x\"/><Relationship Id=\"rId2\" Target=\"worksheets/sheet2.xml\" Type=\"x\"/></Relationships>");
        parts.put("xl/sharedStrings.xml", "<?xml version=\"1.0\"?><sst " + ns + ">"
                + "<si><t>Capability</t></si><si><t>Parent</t></si><si><t>Code</t></si><si><t>Customer</t></si>"
                + "<si><r><t>On</t></r><r><t>boarding</t></r></si><si><t>KYC check</t></si><si><t>Customer care</t></si><si><t>Only notes here</t></si></sst>");
        parts.put("xl/worksheets/sheet1.xml", "<?xml version=\"1.0\"?><worksheet " + ns + "><sheetData>"
                + "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>7</v></c></row></sheetData></worksheet>");
        // Row 1 is a title, row 2 the headers; row 3 has an empty cell skipped by Excel; row 5's code is a number.
        parts.put("xl/worksheets/sheet2.xml", "<?xml version=\"1.0\"?><worksheet " + ns + "><sheetData>"
                + "<row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>Capability map v2</t></is></c></row>"
                + "<row r=\"2\"><c r=\"A2\" t=\"s\"><v>0</v></c><c r=\"B2\" t=\"s\"><v>1</v></c><c r=\"C2\" t=\"s\"><v>2</v></c></row>"
                + "<row r=\"3\"><c r=\"A3\" t=\"s\"><v>3</v></c><c r=\"C3\"><v>1</v></c></row>"
                + "<row r=\"4\"><c r=\"A4\" t=\"s\"><v>4</v></c><c r=\"B4\"><v>1</v></c><c r=\"C4\" t=\"str\"><v>1.1</v></c></row>"
                + "<row r=\"5\"><c r=\"A5\" t=\"s\"><v>5</v></c><c r=\"B5\" t=\"str\"><v>1.1</v></c><c r=\"C5\" t=\"str\"><v>1.1.1</v></c></row>"
                + "<row r=\"7\"><c r=\"A7\" t=\"s\"><v>6</v></c><c r=\"B7\" t=\"s\"><v>3</v></c></row>"
                + "</sheetData></worksheet>");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> e : parts.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
