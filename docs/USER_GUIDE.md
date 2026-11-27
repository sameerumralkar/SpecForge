# SpecForge user guide

SpecForge lets you design an API once and get everything else from it: an OpenAPI 3.1 and AsyncAPI 3.0
specification, a Spring Boot + Apache Camel project that compiles, and sequence and class diagrams. You
describe the data (fields and objects), the endpoints, the error codes, and the steps each endpoint runs
(processing rules). SpecForge keeps them consistent and generates the rest.

This guide is task by task. For the technical reference (the model, the generated code, the API) see the
[README](../README.md).

**Contents**

1. [Start SpecForge](#1-start-specforge)
2. [A quick tour](#2-a-quick-tour), and [help on every screen](#help-on-every-screen)
3. [Create a project](#3-create-a-project)
4. [Build the field inventory](#4-build-the-field-inventory)
5. [Build objects and structures](#5-build-objects-and-structures)
6. [Create endpoints](#6-create-endpoints)
7. [Add processing rules](#7-add-processing-rules)
8. [Map capabilities](#8-map-capabilities)
9. [Manage error codes](#9-manage-error-codes)
10. [Check the API spec](#10-check-the-api-spec)
11. [Generate code](#11-generate-code)
12. [Diagrams](#12-diagrams)
13. [Compare with earlier output](#13-compare-with-earlier-output)
14. [Import and export](#14-import-and-export)
15. [Reference: expressions](#15-reference-expressions)
16. [Reference: rule types](#16-reference-rule-types)
17. [Tips and troubleshooting](#17-tips-and-troubleshooting)

---

## 1. Start SpecForge

You need Java 21, Maven 3.9+ and Node 22+.

```bash
cd backend && mvn spring-boot:run          # the API, on http://localhost:8080
cd frontend && npm install && npm run dev  # the app, on http://localhost:5173
```

Open **http://localhost:5173**. Projects are saved in a local database file (`backend/data/`), so they
survive restarts. To use PostgreSQL instead, run `docker compose up -d` and start the backend with
`-Dspring-boot.run.profiles=postgres`.

## 2. A quick tour

- **Top bar:**
  - the **project picker**;
  - **Preview**, which opens the generated spec, code or diagrams beside the editor;
  - **Generate**, which downloads specs, code or diagrams as a zip;
  - **New project**.
- **Left menu, at the top:** full pages for the project.

  | Page | What it is for |
  |---|---|
  | **Overview & settings** | name, base Java package, version, implementation style, export, delete |
  | **Capabilities** | your functional capability model (L1 to L8) and the APIs that realise each capability |
  | **Field inventory** | reusable plain fields (ids, amounts, currencies, e-mails…) |
  | **Object inventory** | reusable objects built from fields (Money, Address, Customer…) |
  | **Error codes** | every error the API can answer with, and where each is used |
  | **API spec** | the generated OpenAPI and AsyncAPI documents, with their validation problems |
  | **Generated code** | the generated Spring Boot project, file by file |
  | **Diagrams** | sequence and class diagrams, in Mermaid or PlantUML |

- **Left menu, below:** the **endpoints**, grouped by tag or channel, and the **payloads**, split into
  inventory objects and structures. Each item's **⋮** menu has **Duplicate** and **Delete**.
- **Saving:** each editor has **Save** and **Discard**. Unsaved changes are flagged, and SpecForge asks
  before you leave a page with unsaved edits. Validation errors appear next to the field they are about,
  and in a list at the top.

### Help on every screen

- **The ? next to each screen's title:**
  - hover over it for a one-line description of the screen;
  - click it for a panel explaining what the screen is, what you can do there, and tips.
- **Field tips:** hover over any field, or tab into it, to see what it is for. For example, hovering over
  **Pattern (regex)** explains that the whole value must match the expression. The **Field tips** button in
  the top bar turns them off and on, and your choice is remembered in this browser.

## 3. Create a project

**New project** (top bar) offers three ways in:

- **Empty project…:** give a name, a base Java package (e.g. `com.acme.payments`), a version, and the
  default implementation style (see [Implementation styles](#implementation-styles)).
- **Copy of the Order Service sample:** a complete example with REST and Kafka endpoints, rules, error codes
  and inventories. It's a good place to look around.
- **Import from files…:** start from an OpenAPI or AsyncAPI file, or from a SpecForge project export (see
  [Import and export](#14-import-and-export)). The SGQR PayNow example is in
  `examples/sgqr-paynow-payments.specbuilder.json`.

Settings live under **Overview & settings**. **Allow circular references** lets payloads refer to each
other in a loop (e.g. a `Category` with child `Category` items). It is off by default, because a loop is
usually a mistake.

## 4. Build the field inventory

Most APIs repeat the same fields: `id`, `amount`, `currency`, `createdAt`, `email`. Define each once in
the **Field inventory**, with its constraints and error codes, and copy it wherever you need it.

1. Open **Field inventory** and choose **New field**.
2. Give the field a **name** (lowerCamelCase) and a **type**:
   - `STRING`, `INTEGER`, `LONG`, `NUMBER`, `DECIMAL`, `BOOLEAN`, `DATE`, `DATE_TIME`, `UUID`;
   - or `ARRAY` of one of those.

   Objects can't be added here; they belong in the object inventory.
3. Tick **Required** if the field must always be present.
4. Add constraints, whichever apply to the type:
   - **description**, **example** and **format** (e.g. `email`);
   - **min/max length**, or the item count for an array;
   - **minimum/maximum**;
   - a **pattern** (a regular expression);
   - **allowed values**;
   - **error codes**.
5. **Add to inventory.**

Other ways to fill the inventory:

- **From a payload:** in the payload editor, each plain field has a **bookmark** button that saves it to the
  field inventory. The bookmark is filled in when the inventory already has that name.
- **Duplicate** an entry and change the copy.

The **Payload fields with this name** column shows where a field with the same name is used. Deleting an
inventory entry never changes payloads.

## 5. Build objects and structures

Payloads are the shapes of your data. There are two kinds:

- **Inventory objects:** reusable building blocks such as `Money {value, currency}` or `Address`. They
  appear in the object inventory panel, ready to drag in.
- **Structures:** the request, response and message bodies, built from fields and objects.

**Create a payload.** Use **+** next to **Payloads** in the left menu, or **New object** on the Object
inventory page. In the payload editor:

1. Set the **name** (UpperCamelCase). Renaming updates every reference automatically.
2. Choose **Inventory object** or **Structure**.
3. Add fields:
   - **From the field inventory panel** (right): drag a field onto the **Fields** list, or click its **+**.
     You get a copy with all its constraints and error codes. If the name is already taken, the copy gets a
     number (`currency2`). Rename it if you like.
   - **From the object inventory panel:** drag an object onto the field list to add a field of that type,
     named after the object (`money`).
   - **Add field** for a new field typed by hand. For an object field choose type `OBJECT` and pick the
     object; for a list choose `ARRAY` and an item type.
4. Open a field's **constraints** (the tune icon) for limits, pattern, allowed values, description,
   example, format and **error codes**. The number on the icon counts what is set.
5. Reorder fields with the arrows, and add a **JSON example** at the bottom; it goes into the specs.
6. **Save.**

**Used by** at the top lists every place the payload is used. A payload in use can't be deleted: SpecForge
lists the uses so you can change them first. On the Object inventory page you can also move a payload
between the inventory and the structures.

## 6. Create endpoints

Choose **+** next to **Endpoints**. An endpoint is either **REST** (an HTTP operation) or **async** (a
message sent to or received from a channel). The endpoint editor has five tabs.

**Definition**
- **Name:** the operation id, e.g. `createOrder`.
- **Summary**, **description** and **tags.** The first tag groups the endpoint in the menu and in the
  generated code.
- **REST:** method and path (e.g. `POST /orders/{orderId}/items`). Path parameters are created from the
  `{name}`s when you leave the path field.
- **Async:** channel (topic or queue), **SEND** or **RECEIVE**, and protocol (Kafka, JMS or AMQP).
- **Switching kind:** you can switch between REST and async; the request body becomes the message and
  back.

**Request / Response**
- **REST:** path, query and header parameters; the request body; one response per status code, each with
  an optional body; and security schemes.
- **Async:** the message payload and its headers.
- **Inventory panel:** drag an object from it onto the request body, a response or the message to use it
  there. While you drag, each drop zone says what dropping does.

**Processing rules:** see the [next section](#7-add-processing-rules).

**Implementation**
- Chooses how the endpoint is generated; see [Implementation styles](#implementation-styles).
- Shows, rule by rule, what each rule turns into in that style.

**Diagrams:** this endpoint's sequence diagram and class diagram.

### Implementation styles

| Style | Generated as | Use it when |
|---|---|---|
| **Implementation class** | a Spring service method, with one private method per rule | the logic is mostly checks and calls, and the team prefers plain Java |
| **Processing route** | an Apache Camel route, with one EIP step per rule | you want integration patterns: content-based routing, split/aggregate, retries, dead letters |

The project sets the default, and each endpoint can override it.

## 7. Add processing rules

Rules are the steps an endpoint runs, in order. Open the endpoint's **Processing rules** tab and choose
**Add rule**.

1. Pick the **type** (see [Rule types](#16-reference-rule-types)) and give the rule a **name** such as
   "Check the balance".
2. Optionally set a **condition**, e.g. `amount.value > 3000`. The rule then runs only when the condition is
   true.
3. Fill in the type's settings. A **Validate** rule, for example, has a list of checks: the field, the kind
   of check (required, pattern, min…), the value, and an optional message and error code.
4. Optionally pick the **error codes of this step**.
5. **Save.**

The tab shows the chain as cards you can drag to reorder (or use ↑/↓), a numbered **plain-English summary**
of what the endpoint does, and **warnings**. A warning means, for example, a field that doesn't exist or a
text value compared with a number. Warnings don't stop you saving, but code generation waits until they are
fixed.

Field paths use dots for nested objects and `[]` for list items: `payer.accountId`, `items[].quantity`.
A single name can also be one of the endpoint's parameters, such as `orderId`.

## 8. Map capabilities

A **functional capability** is something the business does: "Payments", "Card payments", "Issue a
refund". The **Capabilities** page holds your capability model as a tree of up to eight levels. **L1** is
the broadest level, such as a business domain, and **L8** the most detailed, such as a task. You can also
link each capability to the APIs (endpoints) that realise it.

**Build the tree**
1. Open **Capabilities** and choose **New L1 capability**. Give it a **name**, and optionally a **code**
   from your capability model (e.g. `PAY.02`) and a **description**.
2. Select it in the **Capability map** on the left, and choose **Add L2 capability**. Repeat at each level,
   down to L8.
3. To rearrange the tree:
   - **Edit** a capability and change its **parent capability** to move it. Everything below it moves too,
     and the levels adjust.
   - Use the arrows to reorder capabilities at the same level.
   - **Delete** removes a capability and everything below it. The APIs stay.
4. **Level names:** call the eight levels what your organisation calls them. The defaults are Business
   domain, Capability area, Capability, Sub-capability, Function, Process, Activity and Task.

**Link the APIs.** Either:
- select a capability and choose **Link APIs…**, then tick the APIs that realise it; or
- open an API's **Definition** tab and pick its **Linked capabilities**. This saves straight away.

**The API inventory at each level.** Select any capability to see its **API inventory**: the APIs linked to
it, plus every API linked to a capability below it. An L1 capability therefore lists everything that serves
that domain. Each row says where the API is linked: **this capability**, or a chip such as "L3 Capture".
Click the chip to go there.

**Find gaps:**
- the **Overview** at the top of the map lists the APIs that are **not mapped** to any capability yet;
- a capability with **0** APIs is something the business does that no API supports yet.

Capabilities, their links and the level names are part of the project export, so they travel with the
project.

### Import capabilities from Excel, CSV or JSON

If your organisation already keeps its capability model in a spreadsheet or another tool, import it instead
of typing it in. Nothing in the map changes until you apply the import.

1. On **Capabilities**, choose **Import…** and pick the file. You can use:
   - **Excel** (`.xlsx`): the first sheet with capability columns is read;
   - **CSV** or **TSV**;
   - **JSON**.

   Older `.xls` workbooks must be saved as `.xlsx` first. The dialog has CSV and JSON templates.
2. Use one of these layouts. SpecForge recognises it from the header row:

   | Layout | Columns |
   |---|---|
   | **Level columns** | **L1**, **L2** … **L8**, one row per capability. A blank cell takes the value from the row above, as with merged cells. Optionally **L2 code**, **L2 description** and so on. |
   | **Parent column** | **Name** and **Parent** (the parent's code or name) |
   | **Level column** | **Name** and **Level** (1–8 or L1–L8), in outline order |
   | **JSON tree** | `name`, `code`, `description`, `apis`, `children`. A SpecForge project export works too. |

   In every layout, **Code**, **Description** and **APIs** columns are optional. **APIs** lists endpoint
   names, separated by commas, to link to the capability.
3. The file opens as a **draft**. Drafts are saved, so you can leave and reopen them from the Capabilities
   overview. On the left is the imported tree; on the right, your existing map. Each imported capability is
   compared with the map:

   | Shown as | Means |
   |---|---|
   | **in the map** | the same capability is already at the same place; it is set to merge into it |
   | **possible duplicate** | a very similar name, or the same code, elsewhere in the map; or the same capability twice in the file |
   | **overlap** | one name contains the other, the descriptions are much alike, or an API in the row is already linked to another capability |
   | **blocks applying** | it would be deeper than L8, its code is already used by another capability, or its merge target no longer exists |

   Select a row to highlight, in your map, the capabilities it was compared with. The filters show only
   duplicates, overlaps, blocking problems, or new or merging capabilities.
4. Decide what happens to each capability:
   - **Create as new**;
   - **Merge into an existing one**: no second capability is made, and its children go below the existing
     one. **Merge** on a finding does this in one click.
   - **Skip**: the capability and everything below it are left out.

   You can also:
   - edit names and codes in place;
   - edit the details (description and APIs), move a capability, add one, or remove one;
   - choose **Import under** to place the whole import below an existing capability;
   - use **Merge the … already in the map**, which handles every exact match at once.
5. Choose **Apply import**. Merging fills in a missing code or description on the existing capability and
   links the APIs; it never renames or moves it. The draft then shows what was created, merged, skipped and
   linked. **Discard** deletes a draft without changing the map.

## 9. Manage error codes

The **Error codes** page is the inventory of every error your API can answer with.

**Create a code**
1. Choose **New error code**.
2. Enter a **code** in upper case, e.g. `PAY-001` or `SGQR-3003`.
3. Choose a **category**. Each has a default HTTP status:

   | Category | Default status |
   |---|---|
   | Validation | 400 |
   | Business | 422 |
   | Not found | 404 |
   | Conflict | 409 |
   | Security | 403 |
   | Downstream | 503 |
   | Timeout | 504 |
   | Persistence, Messaging, Technical | 500 |

4. Override the **HTTP status** only if the default doesn't fit.
5. Write the **message** callers see, and optionally a **description**: when it happens and what the
   caller can do.

**Link codes to fields and rules**
- **From the code:** **Link** opens a dialog where you add payload fields (payload → field) and rules
  (endpoint → rule). It opens straight after you create a code.
- **From the field:** use the **Error codes** picker in the field's constraints.
- **From the rule:** use **Error codes of this step**. Each Validate check can also pick its own code.
- **Creating on the spot:** every picker has **New error code…**.

**Which code a failed check uses:** the check's own code, else the first code on the field it checks, else
the rule's first code.

**Finding and removing codes**
- Search the page, or filter it by category. Each row shows the code's status, its message and every place
  it is linked.
- Deleting a code removes it from every field, rule and check.

The codes appear in the specs as `x-error-codes`, and in the generated code as an `ErrorCatalog` and in
every error response: `{"status": 422, "detail": "Insufficient funds", "code": "PAY-010"}`.

## 10. Check the API spec

**API spec** (left menu) shows the generated **OpenAPI 3.1** (REST endpoints) and **AsyncAPI 3.0** (async
endpoints) as YAML or JSON. **Preview → Spec** shows the same beside the editor, scrolled to the endpoint you
are editing.

**What it reflects:** the saved project. It refreshes after every save.

**Problems:** they are listed under the document. Each spec is checked against the official JSON Schema of
the spec, with swagger-parser (OpenAPI), and by resolving every `$ref`.

**Download:** **Generate → Spec** downloads both documents as a zip.

## 11. Generate code

**Generated code** (left menu, or **Preview → Code**) shows the generated Maven project file by file,
opened at the endpoint you are editing. **Generate → Code** downloads it as a zip once nothing blocks it.
Generation is blocked while a rule chain has warnings or the generator finds a problem, and the page lists
what to fix.

The project targets Spring Boot 4 and Apache Camel 4 with Java 21, and compiles as is (`mvn compile`).
It contains:

- DTOs with Bean Validation;
- controllers, and service interfaces and implementations, or Camel routes;
- Kafka/JMS/RabbitMQ listeners and a publisher;
- JPA entities and repositories for the Persist rules;
- REST clients for the Enrich rules;
- MapStruct mappers;
- an error handler that returns problem responses carrying your error codes.

A `README` in the zip lists the TODOs left for you, such as the business logic behind a CUSTOM check, and
real URLs and credentials.

## 12. Diagrams

**Diagrams** draws, for every endpoint, a **sequence diagram** (the steps, branches, retries and every error
exit) and a **class diagram**, plus an overview.

- **Notation:** switch between **Mermaid** and **PlantUML**, and between the drawing and its source.
- **View:** zoom, or fit to the page width.
- **Export:** one diagram as `.mmd`, `.puml`, `.svg` or `.png`.
- **Generate → Diagrams:** downloads all of them in both notations.

The diagrams use the same names as the generated code, so they match it.

## 13. Compare with earlier output

Every download of the code or spec zip saves a **snapshot**, and **Save snapshot** saves one on demand. The
last 10 of each kind are kept.

1. In **Preview → Code**, or in the OpenAPI or AsyncAPI tab, choose **Compare with…** and pick a snapshot.
2. Read the summary, e.g. "3 changed, 1 added, 0 removed".
3. In the file tree, files are marked **A** (added), **M** (modified) or **D** (deleted). You can show
   changed files only.
4. Open a changed file to see a side-by-side diff.

Use it to see exactly what a design change does to the code before you hand it to developers.

## 14. Import and export

**Export:** **Overview & settings → Export project (JSON)** saves the whole project as readable JSON:
settings, inventories, payloads, endpoints, rules, error codes and capabilities. Everything is referred to by name, so
the file can go in Git, be reviewed in a pull request, and be imported anywhere.

**Import:** **New project → Import from files…** accepts:

- **A SpecForge project export.**
- **OpenAPI 3.0/3.1 and AsyncAPI 2.x/3.0 files**, in YAML or JSON. Several files can become one project,
  for example an OpenAPI file and an AsyncAPI file.
  - Schemas become payloads.
  - Operations become endpoints.
  - SpecForge's own `x-error-codes` come back as linked error codes.
  - Anything that can't be held exactly is simplified and listed as a warning, for example `oneOf`, maps,
    cookie parameters, and status ranges like `4XX`.

An import either succeeds completely or creates nothing. Each error names the part of the file it is about.

## 15. Reference: expressions

Conditions, branch conditions, mapping expressions and CUSTOM checks share one syntax.

| | |
|---|---|
| Fields | `status`, `customer.email`, or an endpoint parameter such as `orderId` |
| List items | `items[].quantity`, only inside `sum count min max any all` |
| Values | `'text'` (single quotes), `12`, `3.5`, `true`, `false`, `null` |
| Operators | `== != < <= > >=`, `+ - * / %`, `and or not`, `( )` |
| Functions | `now() today() randomUUID() sum() count() min() max() any() all() size() isEmpty() lower() upper() trim() concat() coalesce() oneOf() plusSeconds()` |

Examples:
- `status == 'PAID' and total > 100`
- `sum(items[].quantity * items[].unitPrice)`
- `any(items[].quantity > 10)`
- `oneOf(country, 'NL', 'BE')`
- `plusSeconds(now(), expiresInSeconds)`

A syntax error (such as `=` instead of `==`) blocks the save and shows the position. A field that doesn't
exist shows as a warning instead.

## 16. Reference: rule types

| Type | What it does | Main settings |
|---|---|---|
| **Validate** | Checks the input; the first failing check answers with its error | checks: field, kind (`REQUIRED NOT_BLANK PATTERN MIN MAX MIN_LENGTH MAX_LENGTH CUSTOM`), value, message, error code; failure status |
| **Transform / map** | Turns the current payload into another one, which later steps work on | target payload; mappings (target ← source field or expression); other fields map by name |
| **Enrich** | Calls another service and puts the answer into a field | service, method, URL, response payload, target field, timeout, enrich or poll-enrich |
| **Conditional route** | Takes one of several branches | branches (name + condition), otherwise |
| **Persist** | Saves, updates, deletes or loads a record | entity, operation, id field, target field (for loading into a field) |
| **Publish event** | Sends a message to Kafka | destination, event payload, key field |
| **Split / aggregate** | Processes each item of a list, then combines the results | list field, parallel, aggregation (collect, sum, count, none) |
| **Error handling** | Retries, falls back, or dead-letters failures of one step or the whole chain | which errors, target rule, retry (attempts, back-off), fallback (error or default response), dead-letter destination |

## 17. Tips and troubleshooting

**Tips**
- **Start from the inventories.** Define the fields first, then the objects built from them, then the
  structures. A change of rule, such as a longer account number, is then made in one place.
- **Give every error case a code.** Callers handle a code much better than a message.
- **Read the plain-English summary** of each rule chain. It is the quickest way to spot a step in the
  wrong place.
- **Export the project to Git** for history and reviews, and compare snapshots to see what a change does
  to the code.

**Troubleshooting**

| Problem | What to do |
|---|---|
| **Generate → Code** is greyed out | Open **Generated code**: it lists what blocks generation, usually rule-chain warnings. |
| "Cannot delete: in use" | The dialog lists every use. Change or remove those first. |
| "This would make payloads refer to each other in a circle" | Remove the loop, or turn on **Allow circular references** in the settings if it is intended. |
| An imported spec lost something | Read the import warnings. They say what was simplified, and why. |
| Diagrams show "Mermaid could not draw" | Switch to PlantUML, or show the source to see the line that failed. |
