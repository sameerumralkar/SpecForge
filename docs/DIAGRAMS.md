# SpecForge diagrams

Three views of SpecForge itself:

1. **Workflow:** how you go from an idea (or an existing spec) to generated code.
2. **Sequence diagrams:** what happens between the browser, the backend and the database in the main tasks.
3. **Entity relationships:** the database tables and how they refer to each other.

The diagrams are written in Mermaid, so GitHub and the app's **User guide** page draw them. To change one,
edit the text below. For the tasks themselves, see the [user guide](USER_GUIDE.md).

---

## 1. Workflow

From a new project to generated code. A diamond is a decision; a dashed arrow means going back to fix
something.

```mermaid
flowchart TD
    start([Start]) --> how{Starting from?}
    how -->|nothing| empty[Empty project:<br/>name, Java package, version, implementation style]
    how -->|an example| sample[Copy of the Order Service sample]
    how -->|an existing API| spec[Import OpenAPI 3.0/3.1<br/>or AsyncAPI 2.x/3.0 files]
    how -->|a SpecForge file| export[Import a project export]
    empty --> fields
    sample --> fields
    spec --> fields
    export --> fields

    subgraph data [Model the data]
        fields[Field inventory:<br/>reusable plain fields with constraints] --> objects[Object inventory:<br/>reusable objects built from fields]
        objects --> bodies[Structures:<br/>request, response and message bodies]
    end

    subgraph apis [Design the APIs]
        endpoints[Endpoints:<br/>REST method and path, or async channel] --> io[Request / Response:<br/>parameters, bodies, responses, security]
        io --> rules[Processing rules:<br/>validate, transform, enrich, route,<br/>persist, publish, split, error handling]
        rules --> codes[Error codes:<br/>linked to fields, rules and checks]
    end

    subgraph caps [Map the capabilities]
        have{Capability model<br/>kept elsewhere?}
        have -->|yes| upload[Import Excel / CSV / JSON]
        upload --> review[Review the draft beside the map:<br/>matches, duplicates, overlaps]
        review --> decide[Per capability:<br/>create, merge or skip]
        decide --> apply[Apply the import]
        have -->|no| tree[Build the L1 to L8 tree]
        apply --> link[Link each API to the<br/>capability it realises]
        tree --> link
    end

    bodies --> endpoints
    codes --> have
    link --> check[Check the API spec:<br/>OpenAPI 3.1 and AsyncAPI 3.0]
    check --> valid{Validation<br/>problems?}
    valid -.->|yes: fix the design| endpoints
    valid -->|no| code[Generated code:<br/>Spring Boot + Apache Camel]
    code --> ready{Rule warnings or<br/>generator problems?}
    ready -.->|yes: fix the rules| rules
    ready -->|no| out[Download specs, code and diagrams]
    out --> compare[Compare with earlier snapshots]
    out --> git[Export the project JSON to Git]
    compare --> done([Hand over to development])
    git --> done
```

---

## 2. Sequence diagrams

### Save a design change and see the spec

Every save goes through the same validation, and the specs are generated fresh from the saved project.

```mermaid
sequenceDiagram
    autonumber
    actor U as Designer
    participant UI as SpecForge UI (React)
    participant PC as PayloadController
    participant PS as PayloadService
    participant EC as ErrorCodeService
    participant DB as Database (H2 / PostgreSQL)
    participant SC as SpecController
    participant SS as SpecService
    participant SN as SnapshotService
    participant GEN as OpenAPI / AsyncAPI generators
    participant VAL as SpecValidator

    U->>UI: Edit fields, then Save
    UI->>PC: PUT /api/projects/{id}/payloads/{payloadId}
    PC->>PS: update(projectId, payloadId, request)
    PS->>PS: Check names, types, constraints, references and cycles
    PS->>EC: checkIds(error codes of each field)
    alt something is invalid
        PS-->>PC: ValidationException
        PC-->>UI: 400 problem with errors [field, message]
        UI-->>U: Each error beside its field
    else valid
        PS->>DB: Save the payload and its fields
        PC-->>UI: 200 payload
        UI->>UI: Refresh the cached project data
        UI->>SC: GET /api/projects/{id}/specs
        SC->>SS: specs(projectId)
        SS->>SN: load(projectId)
        SN->>DB: Read payloads, endpoints, rules, error codes
        SN-->>SS: ProjectSnapshot (read-only)
        SS->>GEN: Generate OpenAPI 3.1 and AsyncAPI 3.0
        SS->>VAL: Official JSON Schemas, swagger-parser, $ref resolution
        SS-->>UI: YAML / JSON with problems
        UI-->>U: Spec preview and its problems
    end
```

### Generate and download the code

Generation is blocked while anything is wrong. Each download is kept as a snapshot to compare with later.

```mermaid
sequenceDiagram
    autonumber
    actor U as Designer
    participant UI as SpecForge UI (React)
    participant SC as SpecController
    participant CS as CodeService
    participant SN as SnapshotService
    participant RS as RuleService
    participant CG as CodeGenerator (FreeMarker)
    participant GS as GenerationService
    participant GC as GenerationController
    participant DB as Database

    U->>UI: Open Generated code
    UI->>SC: GET /api/projects/{id}/code
    SC->>CS: preview(projectId)
    CS->>SN: load(projectId)
    CS->>RS: Rule-chain warnings
    CS->>CG: render(snapshot), templates from the target's manifest
    CG-->>CS: Files and problems
    CS-->>UI: Files, problems, ready
    alt not ready
        UI-->>U: What blocks generation, and where
    else ready
        U->>UI: Generate, then Code
        UI->>SC: GET /api/projects/{id}/generate/code.zip
        SC->>CS: zip(projectId)
        CS->>GS: record(CODE, DOWNLOAD, files)
        GS->>DB: Save a generation snapshot (identical ones skipped, last 10 kept)
        CS-->>UI: The Maven project as a zip
        opt Compare with an earlier snapshot
            U->>UI: Compare with...
            UI->>GC: GET /api/projects/{id}/generations/{snapshotId}/diff
            GC-->>UI: Added, changed and removed files
            UI-->>U: Side-by-side diff
        end
    end
```

### Import capabilities from a spreadsheet

The file becomes a saved draft. Nothing in the capability map changes until the draft is applied.

```mermaid
sequenceDiagram
    autonumber
    actor U as Designer
    participant UI as SpecForge UI (React)
    participant IC as CapabilityImportController
    participant IS as CapabilityImportService
    participant FP as CapabilityFileParser and TableReader
    participant CAP as CapabilityService
    participant DB as Database

    U->>UI: Capabilities, Import..., choose a file
    UI->>IC: POST /api/projects/{id}/capability-imports {fileName, content as base64}
    IC->>IS: upload(projectId, file)
    IS->>FP: parse(fileName, bytes)
    FP->>FP: Read .xlsx XML, CSV / TSV or JSON, find the layout from the headers
    FP-->>IS: Draft tree and notes
    IS->>CAP: list(projectId), the existing map
    IS->>IS: Same path already in the map: merge into it
    IS->>DB: Save capability_import (DRAFT)
    IS-->>UI: Draft with findings (matches, codes, similar names, overlaps)
    UI-->>U: Review page beside the existing map
    loop Each edit
        U->>UI: Rename, move, merge, skip, add or remove
        UI->>IC: PUT /capability-imports/{importId} {targetParentId, nodes}
        IC->>IS: update(...), validate and save
        IS-->>UI: Findings worked out again
    end
    U->>UI: Apply import
    UI->>IC: POST /capability-imports/{importId}/apply
    IC->>IS: apply(projectId, importId), one transaction
    alt something blocks applying
        IS-->>UI: 400 listing what to fix
    else
        loop Each capability, in tree order
            IS->>CAP: create, or update the merge target (fill gaps), or skip
            IS->>CAP: setApis(link the APIs named in the file)
        end
        IS->>DB: Mark the draft APPLIED, with the result
        IS-->>UI: Created, merged, skipped, APIs linked
    end
```

### A request to the generated service

What the generated Spring Boot project does at run time for a REST endpoint built with processing rules.
It's shown here because it is what the design produces.

```mermaid
sequenceDiagram
    autonumber
    actor C as API caller
    participant CTL as Generated controller
    participant SVC as Service implementation or Camel route
    participant EXT as Other services (Enrich)
    participant REPO as JPA repository (Persist)
    participant K as Kafka / JMS / AMQP (Publish)
    participant EH as ApiExceptionHandler

    C->>CTL: POST /orders (JSON body)
    CTL->>CTL: Bean Validation from the field constraints
    CTL->>SVC: createOrder(request)
    SVC->>SVC: Validate rule: each check, with its error code
    opt Enrich rule
        SVC->>EXT: RestClient call (timeout, retries from Error handling)
        EXT-->>SVC: Response mapped into a field
    end
    SVC->>REPO: Persist rule: save, update, delete or find
    SVC->>K: Publish rule: event with a key field
    SVC-->>CTL: Response body
    CTL-->>C: 201 Created
    alt a check fails, or a downstream call stays down
        SVC-->>EH: RuleViolationException(status, code, message)
        EH-->>C: Problem response {status, detail, code}
    end
```

---

## 3. Entity relationships

The database tables (Flyway migrations in `backend/src/main/resources/db/migration`).

- **Solid lines** are foreign keys. Deleting a project deletes everything in it.
- **Dashed lines** are references kept as ids inside a JSON column. They have no foreign key; the services
  keep them consistent, for example by removing a deleted error code from every field, rule and check.
- **Element tables** (`payload_field`, `endpoint_tag`, `endpoint_security`, `endpoint_parameter`,
  `endpoint_response`) are ordered lists, keyed by their owner and an `ordinal`.

```mermaid
erDiagram
    PROJECT ||--o{ PAYLOAD : "has"
    PROJECT ||--o{ ENDPOINT : "has"
    PROJECT ||--o{ ERROR_CODE : "has"
    PROJECT ||--o{ FIELD_DEFINITION : "has"
    PROJECT ||--o{ CAPABILITY : "has"
    PROJECT ||--o{ CAPABILITY_IMPORT : "has"
    PROJECT ||--o{ GENERATION_SNAPSHOT : "keeps"

    PAYLOAD ||--o{ PAYLOAD_FIELD : "has fields"
    PAYLOAD_FIELD }o--o| PAYLOAD : "holds (OBJECT field)"
    ENDPOINT }o--o| PAYLOAD : "request or message body"
    ENDPOINT ||--o{ ENDPOINT_TAG : "tagged"
    ENDPOINT ||--o{ ENDPOINT_SECURITY : "secured by"
    ENDPOINT ||--o{ ENDPOINT_PARAMETER : "takes"
    ENDPOINT ||--o{ ENDPOINT_RESPONSE : "answers with"
    ENDPOINT_RESPONSE }o--o| PAYLOAD : "body"
    ENDPOINT ||--o{ PROCESSING_RULE : "runs, in order"

    PAYLOAD_FIELD }o..o{ ERROR_CODE : "error_codes (ids)"
    PROCESSING_RULE }o..o{ ERROR_CODE : "error_codes, checks (ids)"
    PROCESSING_RULE }o..o{ PAYLOAD : "config refers to (ids)"
    FIELD_DEFINITION }o..o{ ERROR_CODE : "error_codes (ids)"

    CAPABILITY |o--o{ CAPABILITY : "parent of (L1 to L8)"
    CAPABILITY ||--o{ CAPABILITY_ENDPOINT : "realised by"
    ENDPOINT ||--o{ CAPABILITY_ENDPOINT : "realises"
    CAPABILITY_IMPORT }o..o| CAPABILITY : "imports under, merges into (ids)"

    PROJECT {
        uuid id PK
        varchar name UK
        varchar base_package
        varchar version
        varchar default_implementation_style
        boolean allow_circular_references
        varchar capability_levels "names of L1 to L8, JSON"
        timestamp created_at
        timestamp updated_at
    }
    PAYLOAD {
        uuid id PK
        uuid project_id FK
        varchar name "unique in the project"
        varchar role "OBJECT or STRUCTURE"
        varchar description
        varchar example "JSON"
    }
    PAYLOAD_FIELD {
        uuid payload_id PK, FK
        int ordinal PK
        varchar name
        varchar type
        varchar item_type
        uuid ref_payload_id FK
        boolean required
        varchar format
        int min_length
        int max_length
        double minimum
        double maximum
        varchar pattern
        varchar enum_values "JSON"
        varchar error_codes "error code ids, JSON"
    }
    FIELD_DEFINITION {
        uuid id PK
        uuid project_id FK
        varchar name "unique in the project"
        varchar type "no OBJECT"
        varchar item_type
        boolean required
        varchar format
        varchar pattern
        varchar enum_values "JSON"
        varchar error_codes "error code ids, JSON"
    }
    ENDPOINT {
        uuid id PK
        uuid project_id FK
        varchar kind "REST or ASYNC"
        varchar name "operation id, unique in the project"
        varchar implementation_style
        varchar http_method
        varchar path
        varchar path_key "unique per method"
        uuid request_payload_id FK
        varchar channel
        varchar async_operation "SEND or RECEIVE"
        varchar protocol "KAFKA, JMS or AMQP"
        uuid message_payload_id FK
    }
    ENDPOINT_TAG {
        uuid endpoint_id PK, FK
        int ordinal PK
        varchar tag
    }
    ENDPOINT_SECURITY {
        uuid endpoint_id PK, FK
        int ordinal PK
        varchar scheme
    }
    ENDPOINT_PARAMETER {
        uuid endpoint_id PK, FK
        int ordinal PK
        varchar name
        varchar location "PATH, QUERY or HEADER"
        varchar type
        boolean required
    }
    ENDPOINT_RESPONSE {
        uuid endpoint_id PK, FK
        int ordinal PK
        varchar status_code
        varchar description
        uuid payload_id FK
    }
    PROCESSING_RULE {
        uuid id PK
        uuid endpoint_id FK
        int ordinal
        varchar type
        varchar name
        varchar condition_expression
        varchar config "JSON, per rule type"
        varchar error_codes "error code ids, JSON"
    }
    ERROR_CODE {
        uuid id PK
        uuid project_id FK
        varchar code "unique in the project"
        varchar message
        int http_status "null: the category's default"
        varchar category
        varchar description
    }
    CAPABILITY {
        uuid id PK
        uuid project_id FK
        uuid parent_id FK "null for L1"
        int level "1 to 8"
        int position "among its siblings"
        varchar name
        varchar code
        varchar description
    }
    CAPABILITY_ENDPOINT {
        uuid capability_id PK, FK
        uuid endpoint_id PK, FK
    }
    CAPABILITY_IMPORT {
        uuid id PK
        uuid project_id FK
        varchar file_name
        varchar format "EXCEL, CSV, TSV or JSON"
        varchar status "DRAFT or APPLIED"
        uuid target_parent_id "capability id"
        varchar nodes "the draft tree, JSON"
        varchar warnings "JSON"
        varchar result "JSON"
    }
    GENERATION_SNAPSHOT {
        uuid id PK
        uuid project_id FK
        varchar kind "CODE or SPECS"
        varchar reason "DOWNLOAD or MANUAL"
        timestamp created_at
        int file_count
        varchar content_hash
        varchar content "gzipped JSON of the files"
    }
```
