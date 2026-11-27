# SpecForge - Specifications Builder

Design APIs, attach processing rules, and generate OpenAPI/AsyncAPI specs, runnable Spring Boot / Camel
code and architecture diagrams.

**Status: all six phases are done.** The app covers:

- a functional capability model (L1 to L8) with the APIs linked to each capability, and the API inventory at
  every level;
- capability import from Excel (.xlsx), CSV/TSV and JSON into a saved draft, compared with the existing map
  (matches, possible duplicates, overlaps), editable, then applied (create, merge or skip per capability);
- context help on every screen (a ? beside each title), field tips on hover, and the user guide inside the app
  (**User guide** in the top bar; help panels link to its sections);
- the editor, with a field inventory of reusable fields you drag into objects, and an object inventory of reusable
  building blocks you drag into requests and responses;
- generation of specs (OpenAPI 3.1 / AsyncAPI 3.0, validated), Java code (implementation classes and Camel
  routes) and diagrams (Mermaid and PlantUML);
- import from OpenAPI/AsyncAPI files and project JSON, and export to project JSON;
- comparing generated output with earlier snapshots.

**New here? Read the [user guide](docs/USER_GUIDE.md):** a task-by-task walk through the app.

**Diagrams:** [docs/DIAGRAMS.md](docs/DIAGRAMS.md) has the workflow, sequence and entity-relationship diagrams
(also in the app: **User guide → Diagrams**).

**Build prompt:** [docs/SPECFORGE_PROMPT.md](docs/SPECFORGE_PROMPT.md) describes the whole app as a prompt for an AI
coding agent, to rebuild it from scratch phase by phase.

## Run it

Needs Java 21, Maven 3.9+ and Node 22+.

```bash
cd backend && mvn spring-boot:run          # API on http://localhost:8080
cd frontend && npm install && npm run dev  # UI on http://localhost:5173 (calls /api on :8080)
```

Tests:

```bash
cd backend && mvn test        # 153 tests; see the notes below
cd frontend && npm test       # 73 component and unit tests (Vitest + Testing Library)
```

- The PostgreSQL test needs Docker, and is skipped without it.
- `GeneratedProjectCompilesTest` generates three projects and runs `mvn compile test-compile` on each, so
  the first run downloads their dependencies. Skip it with `-Dcodegen.compile.skip=true`.

```
```

On first start the app adds the **Order Service** sample project. Data is kept in `backend/data/` (H2 file
database); delete that folder to start fresh. The H2 console is at http://localhost:8080/h2-console
(JDBC URL `jdbc:h2:file:./data/specbuilder`, user `sa`, no password).

To use PostgreSQL instead:

```bash
docker compose up -d
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

## Versions

Backend: Spring Boot 4.2 (Jackson 3, Hibernate 7.4), Java 21, Flyway, H2 2.5 / PostgreSQL 17.
Frontend: React 19, TypeScript 7, Vite 8, MUI 9, React Router 8, TanStack Query 5, dnd-kit, Monaco.
Generated projects will also target Spring Boot 4.x with Apache Camel 4.

## Domain model

| Entity | What it holds |
|---|---|
| **Project** | name, base package, version, description, default implementation style, "allow circular references" |
| **Payload** | a reusable schema: name, description, JSON example, ordered fields, and a role: `OBJECT` (a building block in the object inventory, e.g. Money, Address) or `STRUCTURE` (a request, response, message or working body) |
| **Payload field** | name, type (`STRING INTEGER LONG NUMBER DECIMAL BOOLEAN DATE DATE_TIME UUID OBJECT ARRAY`), item type for arrays, referenced payload for objects, required, format, min/max length, min/max value, pattern, allowed values |
| **Endpoint** | `REST`: method, path, path/query/header parameters, request payload, responses per status, security. `ASYNC`: channel, `SEND`/`RECEIVE`, protocol (`KAFKA JMS AMQP`), message payload, headers. Both: name (operation id), summary, tags, implementation style (or the project default) |
| **Processing rule** | ordered per endpoint: type, name, optional condition, type-specific config (stored as JSON), and the error codes the step can fail with |
| **Field definition** | the field inventory: a plain field (any type except objects, which belong in the object inventory) with its constraints and error codes. Adding it to a payload copies it there; the copy can then be changed on its own |
| **Capability** | the functional capability model: a tree of up to eight levels (L1 the broadest, to L8), each with a name, optional code and description, and the APIs (endpoints) that realise it. A capability's API inventory is its own APIs plus those of everything below it. The level names are per project (defaults: Business domain, Capability area, Capability, Sub-capability, Function, Process, Activity, Task) |
| **Error code** | the error-code inventory: a code (e.g. `PAY-001`), the message callers see, a category (`VALIDATION BUSINESS NOT_FOUND CONFLICT SECURITY DOWNSTREAM TIMEOUT PERSISTENCE MESSAGING TECHNICAL`), an HTTP status (or the category's default: 400, 422, 404, 409, 403, 503, 504, 500…), a description. Payload fields and rules list the codes they can fail with, by id |

References between payloads, endpoints and rules use ids, so renaming a payload updates every place that
shows it, with nothing to rewrite.

### Rule types and their config

| Type | Config |
|---|---|
| `VALIDATE` | `checks[]` of `{field, kind, value, message}`, where kind is `REQUIRED NOT_BLANK PATTERN MIN MAX MIN_LENGTH MAX_LENGTH CUSTOM`; `failureStatus` (default 400) |
| `TRANSFORM` | `targetPayloadId`; `mappings[]` of `{target, source}` or `{target, expression}`. Other fields map by name. The target becomes the payload later steps work on |
| `ENRICH` | `serviceName`, `httpMethod` (GET), `url`, `responsePayloadId`, `targetField`, `timeoutMs` (2000), `mode` (`ENRICH`/`POLL_ENRICH`) |
| `CONDITIONAL_ROUTE` | `branches[]` of `{name, condition, description}`; `otherwise` |
| `PERSIST` | `entityName`, `operation` (`SAVE UPDATE DELETE FIND_BY_ID`), `idField`, `targetField` (FIND_BY_ID only: put the loaded record into this object field and keep the current payload, e.g. load the QR being paid into `payment.qr` to check it in later steps; empty = the record replaces the payload) |
| `PUBLISH_EVENT` | `protocol` (KAFKA), `destination`, `eventPayloadId` (default: the current payload), `keyField` |
| `SPLIT_AGGREGATE` | `collectionField`, `parallel`, `aggregation` (`COLLECT_LIST SUM COUNT NONE`), `sumField` |
| `ERROR_HANDLING` | `on[]` (`ANY VALIDATION DOWNSTREAM_UNAVAILABLE TIMEOUT PERSISTENCE MESSAGING`), `targetRule` (empty = whole chain), `retry {maxAttempts, backoffMs, multiplier}`, `fallback {type, httpStatus, message}`, `deadLetterDestination` |

Field paths use dots for nested objects and `[]` for array items, e.g. `items[].quantity`. A single name
can also refer to an endpoint parameter, e.g. `orderId` on `GET /orders/{orderId}`.

## The editor

- **Sidebar:** the menu below, endpoints grouped by their first tag (REST) or by channel
  (async), and payloads split into inventory objects and structures. Each item's ⋮ menu has Duplicate and Delete.
- **Left menu:** **Overview & settings**, **Field inventory**, **Object inventory**, **Error codes**, **API spec** (OpenAPI/AsyncAPI with
  validation), **Generated code** and **Diagrams** each open a full page; below them are the endpoints and payloads. The **Preview** button
  in the top bar shows the same outputs beside the editor.
- **Endpoint editor:** five tabs.
  - **Definition:** name, summary, description, tags, kind (REST or async, switchable; the body becomes the
    message and back), method + path or channel. Path parameters follow `{name}`s in the path when you
    leave the field.
  - **Request / Response:** parameters, request body, responses, or the message payload and headers.
  - **Processing rules:** drag-and-drop list (or ↑/↓), a form for each rule type, a plain-English summary
    and warnings.
  - **Implementation:** implementation class vs processing route, and how each rule will be generated.
  - **Diagrams:** this endpoint's sequence diagram and class diagram.
- **Payload editor:** an Inventory object / Structure switch, a fields table (type, list item type,
  referenced object, required, plus a constraints panel), a JSON example in Monaco, and "used by" links. A
  blocked delete lists every use. Beside it are two palettes: the **field inventory** (drag a field onto the
  field list, or click its + to add a copy; a name already in the payload gets a number) and the **object
  inventory**. Each plain field has a bookmark button that saves it to the field inventory (filled in when
  the inventory already has that name).
- **Capabilities:** the capability map (a tree with L1–L8 chips, API counts, collapse and search), the selected
  capability's details and path, its **API inventory** (its own APIs and every API linked below it, each saying
  where it is linked), and what is below it. Create, edit, move (under another capability, with its
  sub-tree), reorder and delete capabilities; **Link APIs…**; **Level names**. The overview lists the APIs not
  mapped yet. An endpoint's Definition tab has a **Linked capabilities** picker too.
- **Context help:** a **?** beside each screen's title (hover for a summary, click for a panel: what the
  screen is, what you can do, tips) and **field tips**: hovering over or tabbing into any field shows what it
  is for. The texts live in `frontend/src/lib/help/`; **Field tips** in the top bar turns them off.
- **Field inventory:** a page listing the reusable fields with their type and constraints, error codes and
  the payload fields that share the name, with search, New field, Edit, Duplicate and Delete. Deleting an
  entry leaves the payload fields copied from it alone.
- **Object inventory:** a page listing the inventory objects with their fields and where each is used,
  with New object, Edit, Duplicate, Delete (refused, with the list of uses, while anything uses it) and
  moving payloads between the inventory and the structures.
- **Error codes:** the error-code inventory, with search and a filter per category. Each code shows its
  message, HTTP status and where it is linked. Create, edit and delete codes there (deleting unlinks the
  code everywhere). **Link** opens a dialog that links a code to payload fields (payload → field) and
  processing rules (endpoint → rule); a new code opens it straight away. You can also link from the other
  side:
  - a field's constraints panel has an **Error codes** picker, and the field row shows its codes;
  - the rule dialog has **Error codes of this step**, and each Validate check has its own optional
    **Error code**. Rule cards show their codes.

  Every picker has **New error code…** to create one on the spot.
- **Drag and drop:** the payload editor and an endpoint's Request / Response tab have an inventory panel.
  Drag an object from it onto:
  - the **fields** of a payload, to add a field of that type (named after the object, e.g. `money`);
  - the **request body**, a **response**, or an async **message**, to use it as that body.

  While dragging, each drop zone is outlined and says what dropping does. A drop is an unsaved edit, like
  any other. Payload pickers list inventory objects first, then structures.
- Server validation errors show next to the field they are about, and are also listed at the top. Unsaved
  changes are flagged, and leaving the page asks first.

## Spec generation

**Spec preview** (top bar) opens a panel on the right with the generated **OpenAPI 3.1** and **AsyncAPI 3.0**
documents, as YAML or JSON. It reflects the saved project, refreshes after every save, and scrolls to the
endpoint you're editing. Drag its left edge to resize it. **Generate → Spec** downloads everything as a zip.

| OpenAPI 3.1 (REST endpoints) | AsyncAPI 3.0 (async endpoints) |
|---|---|
| `paths` sorted by path then method; `operationId` = endpoint name | `operations` keyed by endpoint name, `action` send/receive |
| path/query/header parameters, JSON request body, responses (default descriptions from the status code) | `channels` keyed by camelCase address (`orders.created` → `ordersCreated`) |
| security schemes for the schemes used (`bearerAuth`, `apiKeyAuth`, `basicAuth`, `oauth2`) | one `server` per protocol (localhost placeholders) |
| `components.schemas`: payloads reachable from the endpoints | `components.messages` (payload + headers) and `components.schemas` |

Payloads become JSON Schema using only keywords both specs share. That covers `type`/`format`, `required`,
`minLength`/`maxLength`, `minItems`/`maxItems` (from a list's length limits), `minimum`/`maximum`, `pattern`,
`enum`, `description`, `examples` (typed per field, plus the payload's JSON example), and `$ref` for nested
payloads.

Error codes appear as `x-error-codes` extensions:

- at the top of both documents: the catalog (code, status, category, message, description);
- on each operation: the codes it can answer with, from its rules, its checks and the fields of its
  request body (objects included);
- on schema properties: the codes linked to that field.

Every document is checked three ways, and problems are listed under the preview:
1. Against the **official JSON Schema** of the spec, bundled in `backend/src/main/resources/schemas/`:
   OpenAPI 3.1 (2022-10-07) and AsyncAPI 3.0.0.
2. With **swagger-parser** (OpenAPI only), which also finds semantic problems such as repeated operation ids.
3. By resolving every internal **`$ref`**.

Output is deterministic: maps are ordered and collections sorted, JSON is written with fixed
formatting and `\n` line endings, and zip entries have a fixed timestamp. The same project always gives the
same bytes, and tests check this.

API: `GET /api/projects/{id}/specs` (both specs with problems), `GET …/specs/{openapi|asyncapi}.{yaml|json}`
(download), `GET …/generate/specs.zip`.

## Code generation

**Preview → Code** shows the generated Maven project file by file, opened at the endpoint you're editing.
**Generate → Code** downloads it as a zip. Generation is blocked while any rule chain has warnings or
the generator finds a problem; the Code tab lists what to fix.

The generated project targets Spring Boot 4.1 and Apache Camel 4.23 (the Boot version Camel is built for),
with Java 21, MapStruct and Resilience4j. It compiles with `mvn compile` with no edits, and a test
checks this on every build. It contains:

- `dto`: one class per payload, with Bean Validation from the field constraints.
- `web`: a controller per tag. Endpoints in processing-route style call their Camel route through
  `ProducerTemplate`.
- `service`: an interface and implementation per tag. Each endpoint is a method, and each rule is an
  ordered private method.
- `route`: one `RouteBuilder` per processing-route endpoint, with the rules as EIP steps.
- `messaging`: `@KafkaListener` / `@JmsListener` / `@RabbitListener` classes and a gateway that publishes JSON.
- `persistence`: a JPA entity, repository and store for each entity the PERSIST rules name. Columns come
  from the payload they save.
- `client`: a `RestClient` per service that ENRICH rules call.
- `mapper`: MapStruct mappers for the TRANSFORM rules, and for converting to events and responses.
- `support`: `Expressions`, the runtime for the shared expression syntax.
- `error`: the exceptions and an error handler that turns them into problem responses, and `ErrorCatalog`,
  an enum of the project's error codes (code, status, message), when it has any.

It also includes `application.yml`, a README with a to-do list and a test skeleton.

How each rule type is generated:

| Rule | Implementation class | Processing route |
|---|---|---|
| VALIDATE | Bean Validation + generated checks | `bean-validator:` + a check processor |
| TRANSFORM | MapStruct mapper method | `.bean(dtoMapper, …)` |
| ENRICH | `RestClient` call | `.enrich("direct:…", strategy)` + a sub-route calling the client |
| CONDITIONAL_ROUTE | if / else calling branch methods | `.choice().when(…).otherwise()` + branch sub-routes |
| PERSIST | store (Spring Data repository) call | `.bean(store, "save")` or a processor |
| PUBLISH_EVENT | `KafkaTemplate` / `JmsTemplate` / `RabbitTemplate` | `.to("kafka:…")` / `jms:` / `spring-rabbitmq:` |
| SPLIT_AGGREGATE | stream over the items | `.split(…)` with an aggregation strategy |
| ERROR_HANDLING | Resilience4j retry + fallback around the step or chain | aimed at a step: the step runs in its own sub-route with a retrying error handler, called inside `doTry/doCatch` for the fallback; for the whole route: `onException(…)` with redelivery, `deadLetterChannel` |

**Error codes** in the generated code:

- A failed Validate check throws `RuleViolationException(status, code, message)`. The code is the
  check's own, else the first code of the field it checks, else the rule's first code. The status is the
  code's, else the rule's failure status. The message is the check's, else the code's.
- A Validate rule's first code also covers payloads that break their Bean Validation constraints.
- An Error handling fallback answers with its rule's first code. Its own status and message win if set.
  In a Camel route the code travels in the `X-Error-Code` header.
- The problem response carries the code: `{"status": 422, "detail": "Insufficient funds", "code": "SGQR-3003"}`.
- Other rules list their codes in their Javadoc ("Fails with SGQR-5002.").

Conditions become null-safe Java everywhere. In routes they become Camel Simple when Simple can express
them (field paths, parameters, comparisons, an and-only or or-only chain), and a Java predicate otherwise.
Error categories map to generated exceptions (e.g. TIMEOUT → `DownstreamTimeoutException`, thrown by the
clients).

**Adding a target** (Quarkus, say) means adding a folder under `backend/src/main/resources/codegen/`. Its
`manifest.yml` lists each template with the file it produces: once, or per item of a model collection
(`dtos`, `groups`, `routes`, `entities`, `clients`), optionally only `when` a condition holds. The model
(`generator/code/CodeModel`) supplies resolved names, types and translated expressions. The templates
decide the shape of the code.

## Diagrams

**Diagrams** (left menu, or Preview → Diagrams) draws every diagram in **Mermaid** (rendered in the browser with mermaid.js) or
**PlantUML** (rendered on the server to SVG/PNG, using PlantUML's built-in Smetana layout, so no Graphviz is
needed). It opens on the sequence diagram of the endpoint you're editing, and **Sequence / Classes** switches
to that endpoint's class diagram. The choice follows you as you open other endpoints, and the diagram list
puts the open endpoint's diagrams first. Switch between the drawing and
its source, zoom or fit to width, and export as `.mmd`/`.puml`/`.svg`/`.png`. **Generate → Diagrams**
downloads all of them in both notations: `<id>.mmd`, `<id>.puml`, `<id>.plantuml.svg/.png` (rendered by
the server) and `<id>.mermaid.svg/.png`. The Mermaid images are drawn in your browser and added to the
zip, because Mermaid only runs in a browser. Any diagram Mermaid can't draw is listed in
`MERMAID-ERRORS.txt` rather than failing the export.

| Diagram | Shows |
|---|---|
| Overview | REST clients, the service, the services it calls, the database, and the topics/queues it sends to or receives from |
| Class diagram: everything | The generated classes by package (dto, web, service, route, messaging, persistence, client, mapper), with payload composition (`*--` with multiplicity), implements, and uses |
| Class diagram: data model | Just the payloads and how they contain each other |
| Class diagram: one per tag | One tag's classes: controller, service, listener, gateway, its endpoints' routes, what they use (stores with their entities, clients, mapper), and the payloads its endpoints take and return. Generated when there is more than one tag |
| Class diagram: one per endpoint | Just what one endpoint touches: its methods on the controller, service, listener or gateway (other endpoints' methods left out), its route, what its rules use (stores with their repositories and entities, the clients and calls it makes, its mapper methods, the messaging gateway), and its payloads. Drawn top to bottom |
| Sequence diagram (one per endpoint) | Client → controller → service or Camel route → each rule step → external service, database or broker → response |

The sequence diagrams show conditions as `opt`, branches as `alt`, split items and retries as `loop`, and
every error exit as a `break`: validation (400), not found (404), fallbacks (e.g. 503) and dead letters.

Each diagram is built once as a notation-neutral model, then written as Mermaid and as PlantUML, so the
two always agree. Names come from the code generator's model, so the diagrams match the generated code.
PlantUML sources are syntax-checked and problems are reported. Every sample diagram is checked to render
in both notations.

API: `GET /api/projects/{id}/diagrams` (all, both notations),
`GET …/diagrams/{diagramId}.{mmd|puml|svg|png}` (`?download=false` serves it inline),
`GET …/generate/diagrams.zip`.

## Examples

`examples/group-capability-model.xlsx` is a capability model in Excel (L1–L4 columns, codes, descriptions and
APIs) to try **Capabilities → Import…** with. Imported into the SGQR project below, it shows exact matches,
possible duplicates, overlaps and code clashes.

`examples/sgqr-paynow-payments.specbuilder.json` is a Singapore QR (SGQR) payment service with PayNow. Import
it with **New project → Import from files…**.

- **Built from inventory objects.** `Money {value, currency}` and `Payer {accountId, name}` replace the
  separate `amount`/`currency` and `payerAccountId`/`payerName` fields in the requests, responses and
  events. `Merchant`, `QrCode`, `EmvPayload`, `AccountBalance`, `TransferLimits`, `RiskAssessment` and
  `FastSubmission` are inventory objects too. So a payment request looks like
  `{"qrId": "…", "amount": {"value": 4.80, "currency": "SGD"}, "payer": {"accountId": "0123456789"}}`,
  and the rules use `amount.value`, `amount.currency` and `payer.accountId`.
- **Error codes.** 35 codes, all linked:
  - `SGQR-1xxx` request validation, on fields such as `Money.value` and `Payer.accountId`;
  - `SGQR-2xxx` QR and merchant rules and `SGQR-3xxx` balance, limit and fraud refusals, picked by
    the Validate checks;
  - `SGQR-4xxx` not found;
  - `SGQR-5xxx` downstream failures, on each call and its resilience rule;
  - `SGQR-6xxx` storage and messaging.

  The Order Service sample has eight (`ORD-001`…`ORD-021`).
- **Two QR types.** A `PREFILLED` QR carries the amount, so the payer only confirms. For an
  `AMOUNT_ENTRY` QR, the payer types the amount.
- **QR Codes:** `POST /qr-codes`:
  - checks the sale: SGD only, EMV field limits, and an amount that is required for `PREFILLED` and must
    be left empty for `AMOUNT_ENTRY`;
  - sets the QR to expire `expiresInSeconds` after it is issued (`plusSeconds`);
  - looks up the merchant, which must be active;
  - has `qr-encoder` build the EMVCo payload with the PayNow template, and stores the QR.

  `GET /qr-codes/{qrId}` returns a QR and its status.
- **Payments:** `POST /payments` (with an `Idempotency-Key` header) runs these steps in `PaymentsServiceImpl`:
  1. validates the request;
  2. **QR validity:** loads the QR into the payment, then rejects with 422 if the QR has already been paid
     or cancelled, has expired, isn't SGD, or the merchant isn't active (404 if there is no such QR);
  3. **amount by QR type:** a `PREFILLED` QR's amount is used as is, and a different amount is rejected.
     An `AMOUNT_ENTRY` QR needs the payer's amount;
  4. **balance check** against `core-banking`: rejects if the account isn't active, isn't SGD, or has
     insufficient funds. Retried, with 503 if it stays down;
  5. **limit checks** against `limits-service`: the per-transaction PayNow limit, what's left of today's
     limit, and the number of transfers per day;
  6. **fraud screening** with `fraud-service`, rejecting declined payers;
  7. **step-up authentication** for payments over S$3,000 or flagged for review;
  8. **submission** of the FAST credit transfer through `paynow-gateway` (retried, 503 if it stays down);
  9. stores the payment, publishes `sgqr.payments.initiated`, and returns a `PaymentReceipt`. The receipt
     leaves out the balance, limit and risk data used for the checks.

  `GET /payments/{paymentId}` returns the receipt.
- **Settlement:** a Camel route takes FAST results from `fast.settlement.results`, branches on
  settled/rejected, updates the payment, tells merchants on `sgqr.payments.completed`, and dead-letters
  messages it can't process.

The generated project compiles; `GeneratedProjectCompilesTest` builds it. The real EMVCo/CRC encoding, FAST
messages and step-up flow live behind the `qr-encoder` and `paynow-gateway` services and TODO stubs. They
are not generated.

## Import and export

**New project → Import from files…** creates a project from either:

- **A project export** (`<name>.specbuilder.json`, from **Export project (JSON)** in project settings). It is
  the whole project as readable JSON: settings, payloads, endpoints and rules. Payloads are referred to by
  name everywhere, including in rule configs (`"targetPayload": "Order"`), so the file has no database ids.
  Each payload carries its `role`. The file also carries the `errorCodes`, and fields, rules and checks
  refer to them by code (`"errorCodes": ["PAY-001"]`, a check's `"errorCode": "PAY-001"`). The field inventory
  is in `fieldInventory`, in the same shape as payload fields; the capability tree is in `capabilities` (nested
  `children`, APIs by endpoint name) and custom level names in `capabilityLevels`. Exports made before roles existed still import: payloads that other
  payloads use as a field type become inventory objects. Imported OpenAPI/AsyncAPI schemas are sorted the
  same way.
- **OpenAPI 3.0/3.1 and AsyncAPI 2.x/3.0 files** (YAML or JSON). Several files, e.g. an OpenAPI and an
  AsyncAPI file, become one project. Payloads with the same name and fields in both are merged.
  - Schemas become payloads, keeping types, formats, required, length and value limits, patterns, allowed
    values, descriptions and examples. Inline objects become payloads named after where they appear.
  - Operations become REST endpoints (parameters, bodies, responses, tags, security) or async endpoints
    (channel, send/receive, protocol from the servers, message payload and headers).
  - Anything the model can't hold exactly is skipped or simplified and listed as a warning:
    - cookie parameters and status ranges such as `4XX`;
    - maps;
    - `oneOf`/`anyOf`, imported as the first option;
    - list or primitive bodies, which are wrapped in a payload;
    - names that aren't valid Java names, which are renamed.
  - Recursive schemas turn on "allow circular references".
  - SpecForge's own `x-error-codes` (the catalog and the codes on properties) come back as error codes
    linked to those fields.

Imports go through the normal validation in one transaction: an invalid file creates nothing, and each
error names the part of the file it's about (e.g. `payloads[2].fields[0].refPayload`). A taken project
name gets the next number (`Order Service 2` → `Order Service 3`).

Tests check both round trips:

- exporting a project and importing it gives the same export;
- importing the sample's own generated OpenAPI + AsyncAPI files gives back the same payloads and endpoints.

API: `GET /api/projects/{id}/export`, `POST /api/projects/import[?name=…]`,
`POST /api/projects/import/spec` (`{documents: [...], name?, basePackage?}`).

## Comparing generations

Each download of the code or spec zip saves a **snapshot** of the generated files, and **Save snapshot**
does the same on demand. Identical consecutive snapshots are skipped, and the last 10 per kind are kept.

In **Preview → Code** and the **OpenAPI/AsyncAPI** tabs, **Compare with…** picks a snapshot:

- the summary counts what changed (`3 changed, 1 added, 0 removed`);
- the file tree marks files **A**/**M**/**D** and can show changed files only;
- changed files open in Monaco's side-by-side diff.

API: `GET /api/projects/{id}/generations?kind=CODE|SPECS`, `POST …/generations` (`{kind}`),
`GET …/generations/{snapshotId}/diff`.

## Expression syntax

Rule conditions, branch conditions, mapping expressions and CUSTOM checks share one syntax. Code
generation will translate it to Java (implementation class) or Camel Simple (processing route).

| | |
|---|---|
| Fields | `status`, `customer.email`; a single name can also be an endpoint parameter |
| List items | `items[].quantity`, only inside `sum count min max any all` |
| Values | `'text'` (single quotes), `12`, `3.5`, `true`, `false`, `null` |
| Operators | `== != < <= > >=`, `+ - * / %`, `and or not`, `( )` |
| Functions | `now() today() randomUUID() sum() count() min() max() any() all() size() isEmpty() lower() upper() trim() concat() coalesce() oneOf() plusSeconds()` |

Examples: `status == 'PAID' and total > 100`, `sum(items[].quantity * items[].unitPrice)`,
`any(items[].quantity > 10)`, `oneOf(country, 'NL', 'BE')`, `plusSeconds(now(), expiresInSeconds)`.

Syntax errors (unknown function, `=` instead of `==`, a list path outside an aggregate, ...) block the save
with the character position. A field that doesn't exist, or a type mismatch such as text compared with a
number, shows as a rule-chain warning instead.

## API

All under `/api`. Errors are RFC 9457 problem documents. A `400` lists every problem as
`errors: [{field, message}]`. A blocked payload delete returns `409` with `usages`.

| Method & path | Does |
|---|---|
| `GET/POST /projects`, `GET/PUT/DELETE /projects/{id}` | project CRUD; delete removes everything inside |
| `POST /projects/samples/order-service` | add a fresh copy of the sample |
| `GET/POST …/{projectId}/payloads`, `GET/PUT/DELETE …/payloads/{id}` | payload CRUD; `role` is `OBJECT` or `STRUCTURE` (a new payload defaults to `STRUCTURE`, an update without it keeps the role) |
| `GET/POST …/{projectId}/capabilities`, `GET/PUT/DELETE …/capabilities/{id}` | the capability tree (in tree order); create with `{name, code?, description?, parentId?, position?}`; a change of `parentId` moves the sub-tree; delete removes the sub-tree |
| `GET/PUT …/capabilities/{id}/apis` | the API inventory at this capability (its own and everything below, with `direct` and `via`); `PUT {ids}` replaces its own links |
| `GET/PUT …/endpoints/{id}/capabilities` | the capabilities an API is linked to; `PUT {ids}` replaces them |
| `GET/POST …/{projectId}/capability-imports`, `GET/PUT/DELETE …/capability-imports/{id}`, `POST …/{id}/apply` | capability imports: upload `{fileName, content (base64)}` (Excel .xlsx, CSV/TSV, JSON) into a draft; the view compares each node with the map (`findings`: MATCH, SAME_CODE, SIMILAR_NAME, CONTAINED_NAME, SIMILAR_DESCRIPTION, API_ALREADY_LINKED, DUPLICATE_IN_FILE, TOO_DEEP, UNKNOWN_API); `PUT {targetParentId, nodes}` saves edits (each node's `resolution`: CREATE, MERGE with `mergeTargetId`, or SKIP); apply is all or nothing |
| `GET/PUT …/{projectId}/capability-levels` | the names of L1–L8 (`{levels: [8 names]}`, or `[]` for the defaults) |
| `GET …/payloads?role=OBJECT` | the object inventory (or `STRUCTURE` for the rest) |
| `GET/POST …/{projectId}/field-definitions`, `GET/PUT/DELETE …/field-definitions/{id}` | the field inventory; the body is a field (as in a payload), the answer `{ id, field, usedIn }` |
| `POST …/payloads/{id}/duplicate` · `GET …/payloads/{id}/usages` | copy (as `NameCopy`), and list where it is used |
| `GET/POST …/{projectId}/endpoints`, `GET/PUT/DELETE …/endpoints/{id}` | endpoint CRUD |
| `POST …/endpoints/{id}/duplicate` | copy with rules; a REST copy gets `/copy` added to its path |
| `GET …/endpoints/{id}/rules` | the chain: rules in order, a numbered plain-English summary, warnings |
| `POST …/rules` (optional `position`), `GET/PUT/DELETE …/rules/{ruleId}` | rule CRUD |
| `PUT …/rules/order` with `{ruleIds: [...]}` | reorder |
| `GET/POST …/{projectId}/error-codes`, `GET/PUT/DELETE …/error-codes/{id}` | error-code CRUD; each code lists its `links` (FIELD, RULE, CHECK). Delete unlinks it everywhere |
| `PUT …/error-codes/{id}/links` with `{links: [{kind: "FIELD", payloadId, field} \| {kind: "RULE", ruleId}]}` | link the code to exactly these fields and rules |
| payload fields' and rules' `errorCodeIds` | the codes they fail with, in order; left out on an update, they stay as they are |
| `GET /meta` | every enum's values, for building the editor's drop-downs |

### What is validated

- **Names that become Java code** are valid Java names: payloads are UpperCamelCase, endpoints
  lowerCamelCase, and fields are identifiers. Java keywords and class names the generated code relies on
  (`String`, `List`, …) are refused.
- **Unique**, ignoring letter case: project names, payload names in a project, endpoint names in a
  project, field names in a payload, and rule names in an endpoint.
- **REST routes**: method + path is unique, and `/orders/{id}` and `/orders/{orderId}` count as the same
  route. Path `{params}` and PATH parameters must match exactly. GET and HEAD cannot have a body. At
  least one response is required, and status codes must be valid and unique.
- **Payloads**: object fields need a referenced payload, arrays need an item type (no arrays of
  arrays), and each constraint must suit the field type. Patterns must be valid regular expressions and
  examples valid JSON.
- **No circular payload references** (including self-references) unless the project allows them. The
  error names the cycle, e.g. `Alpha → Beta → Alpha`.
- **Rule configs**: unknown properties and bad values are rejected, and referenced payloads must be in
  the project.
- **Error codes**: upper-case letters and digits separated by `-`, `_` or `.` (at most 40), unique in the
  project ignoring case, a message, and an error status (400–599) if one is set. Fields, rules and checks
  may only refer to the project's own codes.
- **Field inventory**: the same checks as a payload field, a name unique in the inventory ignoring case, and
  no `OBJECT` fields or arrays of objects.
- **Rule chain warnings** (the rule still saves): field paths that don't exist, checks that don't suit the
  field type, splitting something that isn't an array, an error handler aimed at a rule that doesn't
  exist, and an entity name that clashes with a payload.

## Layout

```
backend/src/main/java/dev/specbuilder/
  domain/        entities and enums; domain/rule/ holds the rule configs
  validation/    identifier rules, path templates, cycle detection, field-path resolution
  repository/    Spring Data repositories
  service/       business rules, rule-chain analysis, entity → API mapping
  api/           REST controllers, error handling, request/response records
  seed/          the Order Service sample
  expression/    the shared expression syntax: parser and type checker
  importexport/  the project JSON format, its import/export, and the OpenAPI/AsyncAPI importer
  generation/    snapshots of generated output and diffs against them
  generator/     ProjectSnapshot (read-only input), the Generator interface; generator/spec/ holds
                 the OpenAPI and AsyncAPI generators, the YAML/JSON writer and the validators;
                 generator/code/ builds the code model, translates expressions and renders templates;
                 generator/diagram/ builds the diagram models and writes Mermaid and PlantUML
backend/src/main/resources/codegen/spring-boot-4/  the code templates and their manifest
backend/src/main/resources/db/migration/   Flyway SQL (H2 + PostgreSQL)
frontend/src/
  api/           API client, types, React Query hooks
  pages/         routed pages (project, endpoint, payload)
  editors/       the form editors; editors/rules/ has one form per rule type
  components/    app shell, sidebar, dialogs, Monaco JSON editor
  lib/           helpers: field paths, path parameters, rule defaults and mapping
```
