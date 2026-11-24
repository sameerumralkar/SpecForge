-- Payloads are either inventory objects (reusable building blocks) or structures (bodies).
-- Existing payloads that other payloads already use as a field type start out as objects.
-- (V4, not V3: SpecForge-Bitbucket's V3 adds its own table.)
alter table payload add column role varchar(10) default 'STRUCTURE' not null;
update payload set role = 'OBJECT'
 where id in (select f.ref_payload_id from payload_field f where f.ref_payload_id is not null and f.payload_id <> f.ref_payload_id);
