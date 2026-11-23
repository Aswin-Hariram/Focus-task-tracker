alter table tasks add column timer_daily_seconds text;
alter table tasks add column timer_session_started_at timestamp;
alter table task_time_logs add column daily_seconds text;
