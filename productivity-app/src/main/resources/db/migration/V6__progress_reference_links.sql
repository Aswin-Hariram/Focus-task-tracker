alter table reference_links add column progress_update_id bigint;
create index idx_reference_links_progress_update on reference_links (progress_update_id);
