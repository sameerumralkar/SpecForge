-- What was generated at a point in time (a download, or a snapshot taken on purpose), so later
-- output can be compared with it. Files are stored as base64 of gzipped JSON {path: content}.
create table generation_snapshot (
    id           uuid primary key,
    project_id   uuid           not null references project (id) on delete cascade,
    kind         varchar(10)    not null,
    reason       varchar(10)    not null,
    created_at   timestamp(6) with time zone not null,
    file_count   integer        not null,
    content_hash varchar(64)    not null,
    content      varchar(1000000) not null
);
create index ix_generation_snapshot_project on generation_snapshot (project_id, kind, created_at);
