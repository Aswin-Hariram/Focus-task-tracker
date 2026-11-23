create function record_task_activity() returns trigger language plpgsql as $$
declare
    snapshot tasks%rowtype;
    event_action varchar(20);
    event_time timestamp;
    prior_status varchar(30);
    event_note varchar(2000);
begin
    if tg_op = 'DELETE' then
        snapshot := old;
        event_action := 'DELETED';
        event_time := clock_timestamp()::timestamp;
    else
        snapshot := new;
        if tg_op = 'INSERT' then
            insert into task_activity (task_id, task_name, category_id, category_name, category_color, action, status, occurred_at)
            select new.id, new.name, c.id, c.name, c.color, 'CREATED', new.status, new.created_at
            from categories c where c.id = new.category_id;
            if new.completed_at is null then
                return null;
            end if;
            event_action := 'COMPLETED';
            event_time := new.completed_at;
            event_note := new.completion_note;
        else
            event_time := new.updated_at;
            if new.status is distinct from old.status then
                prior_status := old.status;
                if new.status = 'COMPLETED' then
                    event_action := 'COMPLETED';
                    event_time := coalesce(new.completed_at, event_time);
                    event_note := new.completion_note;
                else
                    event_action := 'STATUS_CHANGED';
                end if;
            elsif row(new.name, new.description, new.planned_date, new.deadline, new.priority,
                      new.category_id, new.completion_note, new.iteration, new.archived)
               is distinct from
                  row(old.name, old.description, old.planned_date, old.deadline, old.priority,
                      old.category_id, old.completion_note, old.iteration, old.archived) then
                event_action := 'UPDATED';
            else
                return null;
            end if;
        end if;
    end if;
    insert into task_activity (task_id, task_name, category_id, category_name, category_color,
                               action, status, previous_status, occurred_at, note)
    select snapshot.id, snapshot.name, c.id, c.name, c.color,
           event_action, snapshot.status, prior_status, event_time, event_note
    from categories c where c.id = snapshot.category_id;
    return null;
end
$$;

create trigger task_activity_trigger after insert or update or delete on tasks
for each row execute function record_task_activity();
