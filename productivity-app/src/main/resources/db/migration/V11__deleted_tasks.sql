create table deleted_tasks (
    task_id bigint primary key,
    task_name varchar(140) not null,
    deleted_at timestamp not null default current_timestamp,
    snapshot text not null
);
