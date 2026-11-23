alter table task_activity add column progress_update_id bigint;
alter table task_activity add constraint fk_task_activity_progress_update
    foreign key (progress_update_id) references progress_updates(id) on delete set null;
