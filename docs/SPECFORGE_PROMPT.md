# SpecForge build prompt

Give the prompt below to an AI coding agent (Claude Code, for example) to build SpecForge from an empty folder.
It describes what the app does and how it must behave, not how the current code is written, so the agent can
make its own design choices within these limits. Build it phase by phase: each phase ends in a working,
tested app.

---

## Prompt

You are building **SpecForge**, a web app for designing APIs. A user describes data (fields and objects),
endpoints (REST and asynchronous messaging), and the processing steps each endpoint runs. SpecForge then
generates:

- validated OpenAPI 3.1 and AsyncAPI 3.0 specs;
- a runnable Spring Boot and Apache Camel project;
- architecture diagrams in Mermaid and PlantUML.

The user also maps the APIs to a functional capability model, from L1 to L8.

Work in phases (listed at the end). After each phase, the app must start, its tests must pass, and the README
must say what works. Ask me only about decisions this prompt leaves open that change what you build. For
everything else, pick a sensible default and note it in the README.

### Stack

- **Backend:**
  - Java 21, Spring Boot 4.x (Jackson 3, Hibernate 7) and Flyway migrations.
  - An H2 file database by default, and a `postgres` profile for PostgreSQL 17 with a `docker-compose.yml`.
  - Errors are RFC 9457 problem documents. A `400` lists every problem as `errors: [{field, message}]`.
- **Frontend:**
  - React 19, TypeScript, Vite, MUI, React Router, TanStack Query, dnd-kit for drag and drop, and Monaco for
    JSON, code and diffs.
  - The dev server proxies `/api` to the backend on :8080. The UI runs on :5173.
- **Tests:**
  - Backend: JUnit, with a PostgreSQL Testcontainers test that is skipped without Docker.
  - Frontend: Vitest and Testing Library.
- **Repository layout:** `backend/`, `frontend/`, `docs/` (user guide and diagrams) and `examples/`.

### Domain model

All references use ids, so renaming something updates every place that shows it.

| Entity | Holds |
|---|---|
| Project | name (unique, ignoring case), base package, version, description, default implementation style (implementation class or processing route), "allow circular references" |
| Payload | name (UpperCamelCase), description, JSON example, ordered fields, role: `OBJECT` (a reusable building block in the object inventory, e.g. Money, Address) or `STRUCTURE` (a request, response or message body) |
| Payload field | name, type (`STRING INTEGER LONG NUMBER DECIMAL BOOLEAN DATE DATE_TIME UUID OBJECT ARRAY`), item type for arrays, referenced payload for objects, required, format, min/max length, min/max value, pattern, allowed values, error codes |
| Field definition | the **field inventory**: a reusable plain field (no objects) with its constraints and error codes. Adding it to a payload copies it, and the copy then changes on its own |
| Endpoint | `REST`: method, path, path/query/header parameters, request payload, responses per status, security scheme. `ASYNC`: channel, `SEND`/`RECEIVE`, protocol (`KAFKA JMS AMQP`), message payload, headers. Both: name (lowerCamelCase operation id), summary, description, tags, implementation style |
| Processing rule | an ordered list per endpoint: type, name, optional condition, a type-specific config stored as JSON, error codes |
| Error code | code (e.g. `PAY-001`), message, category (`VALIDATION BUSINESS NOT_FOUND CONFLICT SECURITY DOWNSTREAM TIMEOUT PERSISTENCE MESSAGING TECHNICAL`), HTTP status (or the category's default), description |
| Capability | a tree of up to 8 levels: name, optional code, description, position, and linked endpoints. Level names are set per project (defaults: Business domain, Capability area, Capability, Sub-capability, Function, Process, Activity, Task) |
| Capability import | a saved draft of an imported capability file, its nodes, and the user's resolution for each node |
| Generation snapshot | the generated files at a point in time, per kind (`CODE`, `SPECS`) |

### Rule types

| Type | Config |
|---|---|
| `VALIDATE` | `checks[]` of `{field, kind (REQUIRED NOT_BLANK PATTERN MIN MAX MIN_LENGTH MAX_LENGTH CUSTOM), value, message, errorCode?}`, `failureStatus` (400) |
| `TRANSFORM` | `targetPayloadId`, `mappings[]` of `{target, source}` or `{target, expression}`; unmapped fields map by name. Later steps work on the target |
| `ENRICH` | `serviceName`, `httpMethod`, `url`, `responsePayloadId`, `targetField`, `timeoutMs`, `mode` (`ENRICH` / `POLL_ENRICH`) |
| `CONDITIONAL_ROUTE` | `branches[]` of `{name, condition, description}`, `otherwise` |
| `PERSIST` | `entityName`, `operation` (`SAVE UPDATE DELETE FIND_BY_ID`), `idField`, `targetField` (FIND_BY_ID: load the record into this field and keep the current payload) |
| `PUBLISH_EVENT` | `protocol`, `destination`, `eventPayloadId`, `keyField` |
| `SPLIT_AGGREGATE` | `collectionField`, `parallel`, `aggregation` (`COLLECT_LIST SUM COUNT NONE`), `sumField` |
| `ERROR_HANDLING` | `on[]` (`ANY VALIDATION DOWNSTREAM_UNAVAILABLE TIMEOUT PERSISTENCE MESSAGING`), `targetRule` (empty means the whole chain), `retry {maxAttempts, backoffMs, multiplier}`, `fallback {type, httpStatus, message}`, `deadLetterDestination` |

Field paths use dots for nested objects and `[]` for list items (`items[].quantity`). A single name can also
refer to an endpoint parameter.

### Expression language

Rule conditions, branch conditions, mapping expressions and CUSTOM checks all use one expression syntax.
Write a parser and a type checker for it:

- **Fields and parameters:** `status`, `customer.email`. List paths such as `items[].qty` are allowed only
  inside aggregate functions.
- **Literals:** `'text'`, numbers, `true`, `false`, `null`.
- **Operators:** `== != < <= > >= + - * / % and or not ( )`.
- **Functions:** `now today randomUUID sum count min max any all size isEmpty lower upper trim concat coalesce oneOf plusSeconds`.
- **Syntax errors** (an unknown function, `=` instead of `==`, a list path outside an aggregate) block the
  save and give the character position.
- **Unknown fields and type mismatches** show as rule-chain warnings instead, and the rule still saves.

### Validation

- **Names that become Java code** must be valid identifiers. Java keywords and the class names the generated
  code relies on are refused.
- **Uniqueness**, ignoring case: project names; payload, endpoint and field-definition names within a project;
  field names within a payload; rule names within an endpoint.
- **REST routes:**
  - method + path is unique, and `/orders/{id}` and `/orders/{orderId}` count as the same route;
  - path placeholders and PATH parameters must match;
  - GET and HEAD have no body;
  - at least one response is required, with valid, unique status codes.
- **Fields:**
  - objects need a referenced payload, and arrays need an item type (no arrays of arrays);
  - each constraint must suit the field type;
  - patterns must be valid regular expressions, and examples must be valid JSON.
- **Circular payload references** (self-references included) are refused unless the project allows them. The
  error names the cycle (`Alpha → Beta → Alpha`).
- **Rule configs:** unknown properties are refused, and referenced payloads must be in the project.
- **Rule-chain warnings** (shown, but the rule saves): a field path that doesn't exist, a check that doesn't
  suit the field type, splitting something that isn't a list, an error handler aimed at a missing rule.
- **Deleting a payload** that is still used is refused with `409` and the list of uses.

### The editor (UI)

- **Home:** the project list; New project (blank, or imported from files); add the sample.
- **Project layout:**
  - A left menu: Overview & settings, Capabilities, Field inventory, Object inventory, Error codes, API spec,
    Generated code, Diagrams.
  - Below the menu: endpoints grouped by their first tag (REST) or by channel (async), and payloads split into
    inventory objects and structures. Each item has a menu with Duplicate and Delete.
  - A **Preview** button opens the spec, code and diagrams in a panel beside the editor that can be resized.
- **Endpoint editor** with five tabs:
  1. **Definition:** includes a capability picker. Path parameters follow the `{name}`s in the path.
  2. **Request / Response.**
  3. **Processing rules:** a list you can drag to reorder, a form for each rule type, a numbered
     plain-English summary of the chain, and its warnings.
  4. **Implementation:** the style, and how each rule will be generated.
  5. **Diagrams.**
- **Payload editor:**
  - an object/structure switch, a fields table with a constraints panel, a Monaco JSON example, and "used by"
    links;
  - two palettes to drag from: the field inventory (adds a copy of the field) and the object inventory (adds a
    field of that type);
  - a bookmark button on each field that saves it to the field inventory.
- **Request / Response tab:** drag an inventory object onto the request body, a response or a message to use it
  as that body. While dragging, each drop zone is outlined and says what dropping does.
- **Field inventory, Object inventory and Error codes pages:**
  - Each has search, create, edit, duplicate and delete, and shows where every item is used.
  - Error codes link to payload fields and rules from either side.
  - Every code picker has **New error code…** to create one on the spot.
- **Form behavior:**
  - Server validation errors show beside the field they are about, and are also listed at the top.
  - Unsaved changes are flagged, and leaving the page asks first.

### Spec generation

- **OpenAPI 3.1** from the REST endpoints and **AsyncAPI 3.0** from the async endpoints, as YAML or JSON.
- Payloads become JSON Schema, using only keywords both specs share. Nested payloads become `$ref`s.
- Error codes appear as `x-error-codes` in three places: the catalog at the top of each document, the codes
  each operation can answer with, and the codes linked to each schema property.
- **Checks on each document:**
  1. against the official JSON Schemas of both specs, bundled with the app;
  2. with swagger-parser (OpenAPI only);
  3. by resolving every internal `$ref`.

  The preview lists any problems.
- **Deterministic output:** the same project always gives the same bytes, with ordered maps, sorted
  collections, `\n` line endings and fixed zip timestamps. Add a test for it.

### Code generation

Generate a Maven project for Spring Boot 4 and Camel 4 (Java 21, MapStruct, Resilience4j) that **compiles
with no edits**. Add a backend test that generates the sample projects and runs `mvn compile` on them.

- **Packages:**
  - `dto`: one class per payload, with Bean Validation;
  - `web`: a controller per tag;
  - `service`: an interface and implementation per tag, with each rule as an ordered private method;
  - `route`: a Camel `RouteBuilder` per endpoint in processing-route style;
  - `messaging`: listeners and a publishing gateway;
  - `persistence`: JPA entities, repositories and stores, one set per PERSIST entity;
  - `client`: a `RestClient` per ENRICH service;
  - `mapper`: MapStruct mappers;
  - `support`: the expression runtime;
  - `error`: exceptions, a problem-response handler, and an `ErrorCatalog` enum.
- **Rules:** each rule type maps to both styles. In implementation-class style, VALIDATE becomes Bean
  Validation plus checks, TRANSFORM a MapStruct mapper, ENRICH a `RestClient` call, and so on. In
  processing-route style, the rules become Camel EIPs: `.enrich`, `.choice`, `.split`, `onException` and
  `deadLetterChannel`.
- **Conditions:** translate them to null-safe Java. In routes, use Camel Simple when Simple can express the
  condition, and a Java predicate when it can't.
- **Error codes:** a failed check throws an exception with the status, code and message. The problem response
  carries the code.
- **Templates:**
  - Templates live under `resources/codegen/<target>/`, with a `manifest.yml` that maps each template to its
    output file.
  - A template is rendered once, or once per item of a model collection, optionally only `when` a condition
    holds.
  - A new target (Quarkus, for example) is a new folder.
- **Generation is blocked** while any rule chain has warnings. The code tab lists what to fix.

### Diagrams

- **Diagrams to draw:**
  - an overview;
  - class diagrams: everything, the data model, one per tag, and one per endpoint;
  - one sequence diagram per endpoint, showing `opt` for conditions, `alt` for branches, `loop` for splits and
    retries, and `break` for every error exit.
- Build each diagram once as a model that doesn't depend on the notation, then write it as **Mermaid**
  (rendered in the browser) and **PlantUML** (rendered on the server to SVG and PNG with Smetana, so no Graphviz
  is needed). Take the names from the code model so the diagrams match the code.
- **Viewer:** drawing or source, zoom and fit, and export as `.mmd`, `.puml`, `.svg` and `.png`.
- **Download all as a zip.** Mermaid images are drawn in the browser and added to the zip. Diagrams that fail
  to draw are listed in `MERMAID-ERRORS.txt`.

### Import, export and snapshots

- **Project export and import:**
  - Export the project as readable JSON (`<name>.specbuilder.json`). It refers to things by name, with no
    database ids, and includes the error codes, the field inventory and the capability tree.
  - Import that file back as a new project. A taken name gets the next number.
- **OpenAPI and AsyncAPI import:**
  - Import OpenAPI 3.0/3.1 and AsyncAPI 2.x/3.0 files (YAML or JSON). Several files become one project.
  - Anything the model can't hold exactly is simplified and listed as a warning: cookies, `4XX` ranges, maps,
    `oneOf`, list bodies and names that aren't valid Java.
  - Recursive schemas turn on "allow circular references", and `x-error-codes` comes back as error codes.
- **Imports are all or nothing.** An import runs in one transaction, and each error names the part of the file
  it's about (`payloads[2].fields[0].refPayload`).
- **Round-trip tests:** an export imported back gives the same export, and the generated specs imported back
  give the same payloads and endpoints.
- **Snapshots:**
  - Every code or spec download saves a snapshot. Identical consecutive snapshots are skipped, and the last 10
    per kind are kept.
  - **Compare with…** shows counts of what changed, a file tree with A/M/D marks, and a Monaco side-by-side
    diff.

### Capabilities (L1–L8)

- **The map:**
  - A tree with level chips, API counts, collapse and search.
  - Create, edit, move (with the sub-tree), reorder and delete capabilities.
  - Level names can be renamed per project.
- **Linking APIs:** link APIs to any capability, from the capability or from the endpoint.
- **API inventory:** each capability's inventory is its own APIs plus everything linked below it, and each
  entry says where it is linked. The overview lists the APIs that aren't mapped yet.
- **Import:**
  - **Formats:** `.xlsx` (read safely: no XXE, zip-bomb limits), CSV/TSV, and JSON.
  - **Layouts:**
    1. L1…L8 columns, where a blank cell means "same as the row above";
    2. Name + Parent;
    3. Name + Level;
    4. a nested JSON tree.

    Optional columns: code, description and APIs.
  - **Draft:** the import is saved as a draft and shown next to the existing map. Each node has findings:
    exact match, same code, similar name (normalized words plus edit distance, about 0.7 and at least one
    shared word), one name contained in the other, similar description, API already linked, duplicate within
    the file, deeper than L8, unknown API.
  - **Comparison:** don't compare a node with capabilities on its own line (above it or below it).
  - **Editing:** the user edits names, codes and descriptions, and picks Create, Merge into an existing
    capability, or Skip (with everything below it).
  - **Apply:** blocking problems disable Apply. Apply is all or nothing and reports what it created, merged,
    skipped and linked.

### Help

- **Screen help:** a **?** beside every screen's title. Hovering shows a summary. Clicking opens a panel with
  what the screen is, what you can do there, and tips, linked to the user guide.
- **Field tips:** hovering over or tabbing into any field shows what it is for. They can be turned off from the
  top bar.
- **Where the texts live:** keep all help texts in one registry. Add a test that every field on every screen
  has a tip.
- **The user guide:**
  - **Contents:** `docs/USER_GUIDE.md`, a task-by-task guide with numbered sections, and `docs/DIAGRAMS.md`, the
    app's own workflow, sequence and ER diagrams in Mermaid.
  - **In the app:** both are bundled into the app at `/guide/:id`, with a contents list you can search,
    `?section=N` deep links, and Mermaid drawn in place.

### Sample data

On first start, add an **Order Service** sample project with error codes `ORD-…`.

Also put a larger example in `examples/`: a payment service with QR codes and instant transfers. It should use
inventory objects (Money, Payer…), about 35 linked error codes, enrichment calls with retries, fraud and limit
checks, events, and a settlement route with a dead-letter queue. Add an `.xlsx` capability model to try the
import with.

### Phases

1. **Model and editor:** the domain, validation, the REST API, and the editor screens with the sample.
2. **Rules:** processing rules, the expression language and rule-chain warnings.
3. **Specs:** spec generation, validation and preview.
4. **Code:** code generation, with the compile test.
5. **Diagrams:** both notations, the viewer and the export.
6. **Import and export:** import/export, snapshots and diffs, the error-code and field inventories, and drag
   and drop.
7. **Capabilities:** the capability model, its import, and context help with the in-app guide.

**Done means:** `mvn test` and `npm test` pass, the generated sample projects compile, every spec validates,
every diagram renders in both notations, and the README and user guide describe what was built.
