-- Functional capabilities: a tree of up to eight levels (L1 to L8), and the APIs (endpoints) each one is
-- realised by. Version 8 because SpecForge Agents uses version 7 for its agent runs.
create table capability (
    id          uuid primary key,
    project_id  uuid          not null references project (id) on delete cascade,
    parent_id   uuid          references capability (id) on delete cascade,
    level       integer       not null,
    position    integer       not null,
    name        varchar(200)  not null,
    code        varchar(40),
    description varchar(4000)
);
create index ix_capability_project on capability (project_id);
create index ix_capability_parent on capability (parent_id);

create table capability_endpoint (
    capability_id uuid not null references capability (id) on delete cascade,
    endpoint_id   uuid not null references endpoint (id) on delete cascade,
    primary key (capability_id, endpoint_id)
);
create index ix_capability_endpoint_endpoint on capability_endpoint (endpoint_id);

-- The names of the eight levels, per project (empty: the defaults).
alter table project add column capability_levels varchar(2000);
