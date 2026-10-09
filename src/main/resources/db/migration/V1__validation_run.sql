create table validation_run (
    id                   bigserial   primary key,
    validation_report_id text        not null unique,
    correlation_id       text        not null,
    codespace            text        not null,
    referential          text        not null,
    stage                text        not null,
    status               text        not null,
    started_at           timestamptz,
    completed_at         timestamptz,
    updated_at           timestamptz not null
);

create index validation_run_correlation_stage_idx on validation_run (correlation_id, stage);
