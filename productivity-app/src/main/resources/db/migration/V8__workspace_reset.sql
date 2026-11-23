create table workspace_settings (
    id integer primary key,
    demo_seed_disabled boolean not null
);

insert into workspace_settings (id, demo_seed_disabled) values (1, false);
