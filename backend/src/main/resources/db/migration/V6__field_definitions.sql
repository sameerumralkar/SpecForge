-- The field inventory: reusable field definitions (plain fields, not objects) that are copied into payloads.
create table field_definition (
    id             uuid primary key,
    project_id     uuid          not null references project (id) on delete cascade,
    name           varchar(100)  not null,
    type           varchar(20)   not null,
    item_type      varchar(20),
    ref_payload_id uuid references payload (id) on delete set null,
    required       boolean       not null,
    format         varchar(50),
    description    varchar(2000),
    example        varchar(1000),
    min_length     integer,
    max_length     integer,
    minimum        double precision,
    maximum        double precision,
    pattern        varchar(500),
    enum_values    varchar(4000),
    error_codes    varchar(2000),
    constraint uk_field_definition unique (project_id, name)
);
create index ix_field_definition_project on field_definition (project_id);
