-- Status events, appended as they arrive. Runs are folded from these on read, so arrival order
-- never matters and a redelivered event cannot corrupt a run.
create table validation_event (
    id                   bigserial   primary key,
    validation_report_id text        not null,
    correlation_id       text        not null,
    codespace            text        not null,
    referential          text        not null,
    stage                text        not null,
    status               text        not null,
    published_at         timestamptz not null,
    received_at          timestamptz not null,

    -- A redelivery repeats the publish time exactly, so this drops duplicates while keeping two
    -- genuinely different events for one run.
    unique (validation_report_id, status, published_at)
);

create index validation_event_correlation_idx on validation_event (correlation_id);
