-- Capability imports: a file (Excel, CSV, JSON) read into a draft tree that is reviewed, edited and compared
-- with the existing capabilities before it is applied.
create table capability_import (
    id                uuid primary key,
    project_id        uuid             not null references project (id) on delete cascade,
    file_name         varchar(300)     not null,
    format            varchar(20)      not null,
    sheet             varchar(200),
    status            varchar(20)      not null,
    target_parent_id  uuid,
    nodes             varchar(1000000) not null,
    warnings          varchar(100000),
    result            varchar(4000),
    created_at        timestamp(6) with time zone not null,
    updated_at        timestamp(6) with time zone not null
);
create index ix_capability_import_project on capability_import (project_id, created_at);
