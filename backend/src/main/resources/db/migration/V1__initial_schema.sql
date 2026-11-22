-- Portable between H2 (local dev, tests) and PostgreSQL.

create table project (
    id                           uuid primary key,
    name                         varchar(100)  not null,
    base_package                 varchar(255)  not null,
    version                      varchar(50)   not null,
    description                  varchar(4000),
    default_implementation_style varchar(30)   not null,
    allow_circular_references    boolean       not null default false,
    created_at                   timestamp(6) with time zone not null,
    updated_at                   timestamp(6) with time zone not null,
    constraint uk_project_name unique (name)
);

create table payload (
    id          uuid primary key,
    project_id  uuid          not null references project (id) on delete cascade,
    name        varchar(100)  not null,
    description varchar(4000),
    example     varchar(20000),
    constraint uk_payload_name unique (project_id, name)
);

create table payload_field (
    payload_id     uuid          not null references payload (id) on delete cascade,
    ordinal        integer       not null,
    name           varchar(100)  not null,
    type           varchar(20)   not null,
    item_type      varchar(20),
    ref_payload_id uuid references payload (id),
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
    primary key (payload_id, ordinal)
);
create index ix_payload_field_ref on payload_field (ref_payload_id);

create table endpoint (
    id                   uuid primary key,
    project_id           uuid          not null references project (id) on delete cascade,
    kind                 varchar(10)   not null,
    name                 varchar(100)  not null,
    summary              varchar(500),
    description          varchar(4000),
    implementation_style varchar(30),
    http_method          varchar(10),
    path                 varchar(500),
    path_key             varchar(500),
    request_payload_id   uuid references payload (id),
    channel              varchar(255),
    async_operation      varchar(10),
    protocol             varchar(10),
    message_payload_id   uuid references payload (id),
    constraint uk_endpoint_name unique (project_id, name),
    -- Null for async endpoints; nulls never collide, so only REST routes are checked.
    constraint uk_endpoint_route unique (project_id, http_method, path_key)
);
create index ix_endpoint_request_payload on endpoint (request_payload_id);
create index ix_endpoint_message_payload on endpoint (message_payload_id);

create table endpoint_tag (
    endpoint_id uuid         not null references endpoint (id) on delete cascade,
    ordinal     integer      not null,
    tag         varchar(100) not null,
    primary key (endpoint_id, ordinal)
);

create table endpoint_security (
    endpoint_id uuid        not null references endpoint (id) on delete cascade,
    ordinal     integer     not null,
    scheme      varchar(20) not null,
    primary key (endpoint_id, ordinal)
);

create table endpoint_parameter (
    endpoint_id uuid          not null references endpoint (id) on delete cascade,
    ordinal     integer       not null,
    name        varchar(100)  not null,
    location    varchar(10)   not null,
    type        varchar(20)   not null,
    required    boolean       not null,
    description varchar(2000),
    primary key (endpoint_id, ordinal)
);

create table endpoint_response (
    endpoint_id uuid          not null references endpoint (id) on delete cascade,
    ordinal     integer       not null,
    status_code varchar(7)    not null,
    description varchar(2000),
    payload_id  uuid references payload (id),
    primary key (endpoint_id, ordinal)
);
create index ix_endpoint_response_payload on endpoint_response (payload_id);

create table processing_rule (
    id                   uuid primary key,
    endpoint_id          uuid           not null references endpoint (id) on delete cascade,
    ordinal              integer        not null,
    type                 varchar(30)    not null,
    name                 varchar(100)   not null,
    condition_expression varchar(500),
    config               varchar(20000) not null
);
create index ix_processing_rule_endpoint on processing_rule (endpoint_id);
