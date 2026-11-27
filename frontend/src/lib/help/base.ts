import type { HelpBundle } from './types'

/** Help for every SpecForge screen and field. */
export const BASE_HELP: HelpBundle = {
  screens: {
    home: {
      title: 'Projects',
      summary: 'Start here: open a project, or create one from scratch, from the sample, or from existing API files.',
      sections: [
        { heading: 'What you can do', items: [
          'Add the sample: a complete Order Service with REST and Kafka endpoints, rules, error codes, inventories and capabilities.',
          'Empty project: start from nothing, with a name and a base Java package.',
          'Import from files: OpenAPI 3.0/3.1 or AsyncAPI 2.x/3.0 (YAML or JSON), or a SpecForge project export.',
        ] },
      ],
      guide: '§ 3 Create a project',
    },
    overview: {
      title: 'Overview & settings',
      summary: 'The project as a whole: its counts, its settings (name, Java package, version, implementation style), export and delete.',
      sections: [
        { heading: 'What this screen is', items: [
          'The settings here apply to everything generated from the project: the Java package of the code, the version in the specs, and the default implementation style of new endpoints.',
        ] },
        { heading: 'What you can do', items: [
          'Change the settings, then Save.',
          'Export project (JSON): the whole project as one readable file, for Git or for importing elsewhere.',
          'Delete project: removes the project and everything in it.',
        ] },
      ],
      tips: ['Export the project to Git after each design session: the file reads well in a pull request.'],
      guide: '§ 3 Create a project, § 14 Import and export',
    },
    capabilities: {
      title: 'Capabilities',
      summary: 'Your functional capability model, from L1 (the broadest, e.g. a business domain) to L8 (the most detailed), and the APIs that realise each capability.',
      sections: [
        { heading: 'What this screen is', items: [
          'A tree of what the business does. Each capability sits at a level: L1 at the top, then L2 below it, down to L8.',
          'Each capability can be linked to the APIs (endpoints) that realise it.',
          'The API inventory of a capability shows the APIs linked to it and to every capability below it, so each level shows everything that serves it.',
        ] },
        { heading: 'What you can do', items: [
          'New L1 capability, then "Add L2 capability" and so on from the selected capability.',
          'Edit a capability, or move it under another one (its sub-tree moves with it), and reorder siblings with the arrows.',
          'Link APIs… to choose the APIs of the selected capability. You can also link from an API\'s Definition tab.',
          'Level names: call the levels what your organisation calls them (Domain, Value stream, Capability…).',
          'Overview: see the levels and the APIs that are not mapped to any capability yet.',
        ] },
      ],
      tips: [
        'Link an API at the most specific capability it realises: the levels above show it automatically.',
        'A capability with no APIs shows a gap: something the business does that no API supports yet.',
      ],
      guide: '§ 8 Map capabilities',
    },
    'capability-import': {
      title: 'Import capabilities',
      summary: 'A capability file (Excel, CSV or JSON) read into a draft: compared with your capability map, with possible duplicates and overlaps highlighted, editable, and applied only when you say so.',
      sections: [
        { heading: 'What this screen is', items: [
          'The imported capabilities as a tree (left) beside your existing map (right). Nothing in the map changes until you apply the import.',
          'Each imported capability is compared with the map: already there at the same place, the same code, a similar name (possible duplicate), a name that contains another or a similar description (overlap), an API already linked elsewhere, a duplicate within the file, or deeper than L8.',
          'Drafts are saved: leave and come back from the Capabilities overview.',
        ] },
        { heading: 'What you can do', items: [
          'For each capability choose what applying does: create it as new, merge it into an existing capability (its children go below that one), or skip it with everything below.',
          'Use Merge on a finding to merge into the capability it points at; "Merge the … already in the map" does it for every exact match.',
          'Edit names and codes in place; edit details, move, add or remove capabilities; choose where the import goes (Import under).',
          'Select a row to highlight, in your map, what it was compared with. Filter by duplicates, overlaps or blocking problems.',
          'Apply import when nothing is marked "blocks applying"; or Discard the draft.',
        ] },
      ],
      tips: [
        'Merging fills in a missing code or description of the existing capability and links the APIs; it never renames or moves it.',
        'Templates for Excel (CSV) and JSON are in the import dialog.',
      ],
      guide: '§ 8 Map capabilities (Import capabilities)',
    },
    fields: {
      title: 'Field inventory',
      summary: 'Reusable plain fields (ids, amounts, currencies, e-mails…) with their constraints and error codes, ready to copy into objects and bodies.',
      sections: [
        { heading: 'What this screen is', items: [
          'Fields that recur across payloads, defined once. Adding one to a payload copies it, with its type, constraints and error codes.',
          'Objects are not fields: they live in the object inventory.',
        ] },
        { heading: 'What you can do', items: [
          'New field, Edit, Duplicate, Delete. Deleting an entry leaves the payloads it was copied into alone.',
          'See which payload fields share each name.',
          'In a payload, drag a field from the field inventory panel onto the field list, or click its +.',
        ] },
      ],
      guide: '§ 4 Build the field inventory',
    },
    inventory: {
      title: 'Object inventory',
      summary: 'Reusable objects (Money, Address, Customer…) to build request and response bodies from, plus the structures (the bodies themselves).',
      sections: [
        { heading: 'What this screen is', items: [
          'Objects are building blocks: drag them into a payload\'s fields, or onto a request, response or message body.',
          'Structures are the bodies endpoints send and receive.',
        ] },
        { heading: 'What you can do', items: [
          'New object, Edit, Duplicate, Delete (refused while something uses it; the dialog lists the uses).',
          'Move a payload between the inventory and the structures.',
        ] },
      ],
      guide: '§ 5 Build objects and structures',
    },
    errors: {
      title: 'Error codes',
      summary: 'Every error the API can answer with: a code, a message, a category and an HTTP status, and the fields and rules each one is linked to.',
      sections: [
        { heading: 'What this screen is', items: [
          'The error-code inventory. Codes appear in the specs (x-error-codes), in the generated ErrorCatalog and in every error response.',
        ] },
        { heading: 'What you can do', items: [
          'New error code; filter by category; search.',
          'Link: tie a code to payload fields and processing rules.',
          'Edit or delete a code. Deleting it removes it from every field, rule and check.',
        ] },
      ],
      tips: ['A failed check uses its own code, else the first code of the field it checks, else the rule\'s first code.'],
      guide: '§ 9 Manage error codes',
    },
    spec: {
      title: 'API spec',
      summary: 'The OpenAPI 3.1 (REST) and AsyncAPI 3.0 (async) documents generated from the saved project, with their validation problems.',
      sections: [
        { heading: 'What this screen is', items: [
          'A read-only view of the specs. They change when you save changes to the project.',
          'Each document is checked against the official JSON Schema, by swagger-parser (OpenAPI) and by resolving every $ref; problems are listed underneath.',
        ] },
        { heading: 'What you can do', items: [
          'Switch between YAML and JSON, and between OpenAPI and AsyncAPI.',
          'Download a document, or Generate → Spec for both as a zip.',
          'Compare with an earlier snapshot to see what a change did.',
        ] },
      ],
      guide: '§ 10 Check the API spec',
    },
    code: {
      title: 'Generated code',
      summary: 'The Spring Boot + Apache Camel project generated from the design, file by file. It compiles as is.',
      sections: [
        { heading: 'What this screen is', items: [
          'A read-only view of the generated Maven project: DTOs, controllers, services or Camel routes, listeners, entities, clients, mappers and error handling.',
          'Generation is blocked while rule chains have warnings or the generator finds a problem; they are listed here.',
        ] },
        { heading: 'What you can do', items: [
          'Browse the files; Generate → Code downloads the zip.',
          'Save snapshot, and Compare with an earlier one to review what a design change does to the code.',
        ] },
      ],
      guide: '§ 11 Generate code',
    },
    diagrams: {
      title: 'Diagrams',
      summary: 'Sequence and class diagrams for every endpoint, and an overview, in Mermaid or PlantUML.',
      sections: [
        { heading: 'What you can do', items: [
          'Switch notation (Mermaid or PlantUML) and between the drawing and its source.',
          'Zoom, fit to width, and export one diagram as .mmd, .puml, .svg or .png.',
          'Generate → Diagrams downloads all of them in both notations.',
        ] },
      ],
      guide: '§ 12 Diagrams',
    },
    payload: {
      title: 'Payload editor',
      summary: 'One payload: an inventory object or a structure (a body), its fields in order with their constraints and error codes, and a JSON example.',
      sections: [
        { heading: 'What this screen is', items: [
          'Payloads are the shapes of your data. Renaming one updates every reference.',
          'The panels on the right are the field inventory and the object inventory: drag from them onto the field list.',
        ] },
        { heading: 'What you can do', items: [
          'Add fields by hand, or from the inventories; reorder and remove them.',
          'Open a field\'s constraints (the tune icon) for limits, pattern, allowed values, format, description, example and error codes.',
          'Save a plain field to the field inventory with its bookmark button.',
          'Used by lists every place the payload is used.',
        ] },
      ],
      guide: '§ 5 Build objects and structures',
    },
    'endpoint-definition': {
      title: 'Endpoint: Definition',
      summary: 'What the endpoint is: its name, summary, tags and kind (REST or async), its method and path or its channel, and the capabilities it realises.',
      sections: [
        { heading: 'What you can do', items: [
          'Name it (the operation id), describe it, and tag it (the first tag groups it in the menu and the generated code).',
          'REST: choose the method and path; path parameters follow the {names} in the path.',
          'Async: choose the channel, SEND or RECEIVE, and the protocol.',
          'Link it to the capabilities it realises (saved straight away).',
        ] },
      ],
      guide: '§ 6 Create endpoints',
    },
    'endpoint-io': {
      title: 'Endpoint: Request / Response',
      summary: 'What goes in and out: parameters, request body, responses per status and security for REST; the message and headers for async.',
      sections: [
        { heading: 'What you can do', items: [
          'Add path, query and header parameters.',
          'Pick the request body and a body for each response, or drag an object from the inventory panel onto them.',
          'Add a response for every status the endpoint can answer with, errors included.',
        ] },
      ],
      guide: '§ 6 Create endpoints',
    },
    'endpoint-rules': {
      title: 'Endpoint: Processing rules',
      summary: 'The steps the endpoint runs, in order: validate, transform, enrich, route, persist, publish, split/aggregate and error handling.',
      sections: [
        { heading: 'What you can do', items: [
          'Add rule, then edit, reorder (drag, or ↑/↓) and delete rules.',
          'Give a rule a condition so it runs only when it is true.',
          'Read the plain-English summary of the chain, and fix its warnings: code generation waits for them.',
        ] },
      ],
      guide: '§ 7 Add processing rules',
    },
    'endpoint-implementation': {
      title: 'Endpoint: Implementation',
      summary: 'How the endpoint is generated: a plain Spring service method, or an Apache Camel route; and what each rule becomes in that style.',
      sections: [
        { heading: 'What you can do', items: [
          'Choose the style for this endpoint, or keep the project default.',
          'Read, rule by rule, what will be generated.',
        ] },
      ],
      guide: '§ 6 Implementation styles',
    },
    'endpoint-diagrams': {
      title: 'Endpoint: Diagrams',
      summary: 'This endpoint\'s sequence diagram (its steps, branches, retries and error exits) and its class diagram.',
      sections: [{ heading: 'What you can do', items: ['Switch between sequence and class diagrams, and between Mermaid and PlantUML.'] }],
      guide: '§ 12 Diagrams',
    },
  },

  fields: {
    // Projects
    'name': 'The name you see for this item everywhere. Names must be unique where they are used.',
    'project name (optional)': 'The name of the new project. Leave it empty to use the name in the imported file.',
    'base package': 'The Java package of the generated code, e.g. com.acme.payments.',
    'base package (optional)': 'The Java package of the generated code. Leave it empty to derive one from the imported file.',
    'version': 'The API version, written into the specs and the generated project (e.g. 1.0.0).',
    'description': 'Free text: what this is for and anything a reader should know. It goes into the specs and the generated code where it fits.',
    'description (optional)': 'Free text: what this is for and anything a reader should know.',
    'default implementation style': 'How new endpoints are generated unless they choose otherwise: plain Spring services, or Apache Camel routes.',
    'allow payloads to refer to each other in a circle (e.g. a tree of categories)': 'Off by default, because a loop between payloads is usually a mistake. Turn it on for recursive data such as a category tree.',
    'files to import': 'OpenAPI 3.0/3.1 or AsyncAPI 2.x/3.0 files (YAML or JSON), or one SpecForge project export. Several spec files become one project.',
    'project': 'The project you are working on. Switch projects here.',

    // Fields and payloads
    'type': 'The kind of value. OBJECT holds another payload; ARRAY holds a list of an item type.',
    'item type': 'For an ARRAY field: the type of each item.',
    'object': 'For an OBJECT field, or a list of objects: the payload it holds.',
    'required': 'Ticked: the field must always be present. Checks and Bean Validation enforce it.',
    'format': 'A standard format for the value, e.g. email, uri, date-time or iso-4217. It goes into the specs.',
    'example': 'A typical value. It goes into the specs as an example.',
    'min length': 'The fewest characters allowed.',
    'max length': 'The most characters allowed.',
    'min items': 'The fewest items the list may hold.',
    'max items': 'The most items the list may hold.',
    'minimum': 'The smallest value allowed.',
    'maximum': 'The largest value allowed.',
    'pattern (regex)': 'A regular expression the whole value must match, e.g. [A-Z]{3} for a currency code.',
    'allowed values': 'The only values allowed. Type a value and press Enter for each. Leave empty to allow any text.',
    'error codes': 'The error codes this field (or step) can fail with. Checks on the field use the first one.',
    'kind of payload': 'Inventory object: a reusable building block. Structure: a request, response or message body.',
    'linked capabilities': 'The capabilities this API realises. It then shows in their API inventory and in that of every level above.',

    // Endpoints
    'name (operation id)': 'The operation id, e.g. createOrder: the method name in the generated code and the operationId in the spec.',
    'summary': 'One line saying what it does. Shown in the specs and the menu.',
    'tags': 'Groups for the endpoint. The first tag groups it in the menu, the specs and the generated code.',
    'method': 'The HTTP method: GET reads, POST creates, PUT replaces, PATCH changes part, DELETE removes.',
    'path': 'The URL path, e.g. /orders/{orderId}. Each {name} becomes a path parameter.',
    'channel / topic': 'The topic or queue the message goes to or comes from, e.g. orders.created.',
    'operation': 'SEND: this service publishes the message. RECEIVE: it listens for it.',
    'protocol': 'The messaging system: Kafka, JMS or AMQP (RabbitMQ).',
    'request body': 'The payload the caller sends. Drag an object from the inventory panel onto it, or pick one.',
    'message payload': 'The payload of the message. Drag an object from the inventory panel onto it, or pick one.',
    'security': 'How callers authenticate: a bearer JWT, an API key, basic auth or OAuth 2.',
    'payload': 'The payload (body) used here.',

    // Rules
    'condition': 'Optional: an expression; the rule runs only when it is true, e.g. amount.value > 3000.',
    'only when (optional)': 'Optional: an expression; the rule runs only when it is true, e.g. amount.value > 3000.',
    'field': 'The field this applies to. Use dots for nested fields (payer.accountId) and [] for list items (items[].quantity).',
    'check': 'The kind of check: required, not blank, pattern, minimum, maximum, length, or a custom expression.',
    'kind': 'What sort of item this is.',
    'value': 'The value the check compares with: a number, a length, a pattern, or (for CUSTOM) an expression that must be true.',
    'message (optional)': 'What the caller sees when this fails. Leave empty to use the error code\'s message.',
    'message': 'The text callers see.',
    'error code (optional)': 'The error code this check answers with. Leave empty to use the field\'s first code, else the rule\'s.',
    'error codes of this step': 'The error codes this step can fail with. A failed check without its own code uses the first one.',
    'http status on failure': 'The HTTP status a failed check answers with when its error code doesn\'t set one (usually 400).',
    'map to payload': 'The payload this step turns the current data into. Later steps work on it.',
    'expression': 'A calculation using fields, values, operators and functions, e.g. sum(items[].quantity * items[].unitPrice).',
    'expression syntax': 'Fields, values, operators (== != < > and or not + - * /) and functions such as now(), sum() and oneOf().',
    'service name': 'The other service to call, e.g. customer-service. A REST client is generated for it.',
    'url': 'The path to call on that service, e.g. /customers/{customerId}; {names} come from fields or parameters.',
    'response payload (optional)': 'The payload the other service answers with.',
    'store result in field': 'The field of the current payload that receives the answer.',
    'timeout (ms)': 'How long to wait for the other service before failing with a timeout.',
    'camel mode': 'enrich: call and merge the answer. pollEnrich: poll a source (such as a queue) for a message to merge.',
    'if': 'The condition for this branch: an expression such as status == \'PAID\'. The first true branch is taken.',
    'branch name': 'A short name for this branch, used in the generated code and diagrams.',
    'then': 'What happens on this branch, in words. The generated code leaves a TODO for it.',
    'otherwise (optional)': 'What happens when no branch condition is true.',
    'what happens (optional)': 'What happens on this branch, in words.',
    'entity name': 'The name of the stored record, e.g. OrderEntity. A JPA entity and repository are generated for it.',
    'id field': 'The field that holds the record\'s id.',
    'load into field (optional)': 'Only for loading a record: put it into this field and keep the current payload. Empty: the record replaces the payload.',
    'topic / destination': 'Where the event is published, e.g. orders.created.',
    'event payload': 'The payload published. Default: the current payload.',
    'message key field': 'The field used as the message key, so related messages keep their order (e.g. orderId).',
    'list to split': 'The list field whose items are processed one by one.',
    'process items in parallel': 'Process the items at the same time instead of one after another.',
    'item field to add up': 'For "add up a field": the field of each result to total.',
    'on errors': 'Which kinds of failure this handles: any, validation, a service that is down, a timeout, storage or messaging.',
    'applies to': 'The step whose failures are handled, or the whole chain.',
    'attempts': 'How many times to try in total before giving up.',
    'backoff (ms)': 'How long to wait before the first retry.',
    'backoff multiplier': 'Each retry waits this many times longer than the one before (2 doubles the wait).',
    'retry': 'Try again when the step fails, with a growing wait between attempts.',
    'fallback': 'What to answer when retries are used up: an error response, or a default response.',
    'dead-letter destination (optional)': 'Where messages that still fail are sent, so none are lost, e.g. payments.completed.DLT.',
    'http status': 'The HTTP status callers get. Leave empty to use the category\'s default.',

    // Error codes
    'code': 'A stable code in upper case, e.g. PAY-001. Callers handle the code; the message may change.',
    'category': 'The kind of error. Each has a default HTTP status: validation 400, business 422, not found 404, conflict 409, security 403, downstream 503, timeout 504, others 500.',
    'filter by category': 'Show only the codes of one category.',

    // Capabilities
    'capability file': 'An Excel workbook (.xlsx), CSV/TSV or JSON file of capabilities. It is read into a draft you review before anything changes.',
    'import under': 'Where the import goes: at the top of the map (its top capabilities become L1), or below an existing capability.',
    'place in the import': 'The imported capability this one sits below, or the top of the import.',
    'apis to link': 'Endpoint names linked to this capability when the import is applied. Names that are not in the project are ignored.',
    'show': 'Show all imported capabilities, or only those that need a look: possible duplicates, overlaps, blocking problems, new or merging ones.',
    'find in the guide': 'Show only the sections of the guide that mention these words.',
    'find an imported capability': 'Filter the import by name or code.',
    'parent capability': 'Where this capability sits. None makes it an L1 capability; under an Ln capability it becomes L(n+1). There are 8 levels.',
    'code (optional)': 'An id from your capability model, e.g. PAY.02.03. Must be unique in the project.',

    // Field inventory and output
    'generated files': 'The files of the generated project. Click one to read it.',
    'compare with': 'Pick an earlier snapshot to see what changed since: added (A), modified (M) and deleted (D) files.',
    'diagram': 'The diagram to show.',
  },

  patterns: [
    [/^field \d+ name$/, 'The field name, in lowerCamelCase. It becomes the JSON property and the Java field.'],
    [/^field \d+ type$/, 'The kind of value. OBJECT holds another payload; ARRAY holds a list.'],
    [/^field \d+ object$/, 'The payload this field holds.'],
    [/^field \d+ required$/, 'Ticked: the field must always be present.'],
    [/^parameter \d+ name$/, 'The parameter name. Path parameters must match a {name} in the path.'],
    [/^parameter \d+ type$/, 'The type of the parameter value.'],
    [/^response \d+ status$/, 'The HTTP status code of this response, e.g. 200, 201, 400 or 404.'],
    [/^imported name \d+$/, 'The name the capability gets. Press Enter or leave the field to save.'],
    [/^imported code \d+$/, 'An id from your capability model, e.g. PAY.02.03. It must not clash with a code already in the map when the capability is created.'],
    [/^resolution \d+$/, 'What applying does: create it as a new capability, merge it into an existing one (no second capability is made), or skip it with everything below.'],
    [/^merge target \d+$/, 'The existing capability this one merges into. Its children go below that capability; a missing code or description is filled in.'],
    [/^l\d name$/, 'What this level is called in your capability model, e.g. Domain, Capability or Process.'],
    [/^(min|max) (length|items)$/, 'A length limit: characters for text, items for a list.'],
  ],
}
