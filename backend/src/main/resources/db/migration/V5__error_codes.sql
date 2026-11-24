-- The error-code inventory. Fields and rules list the codes they can fail with, by id, as a JSON array.
create table error_code (
    id          uuid primary key,
    project_id  uuid          not null references project (id) on delete cascade,
    code        varchar(40)   not null,
    message     varchar(500)  not null,
    http_status integer,
    category    varchar(20)   not null,
    description varchar(2000),
    constraint uk_error_code unique (project_id, code)
);
create index ix_error_code_project on error_code (project_id);

alter table payload_field add column error_codes varchar(2000);
alter table processing_rule add column error_codes varchar(2000);
