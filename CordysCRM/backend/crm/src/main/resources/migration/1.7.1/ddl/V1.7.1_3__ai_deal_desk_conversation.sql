CREATE TABLE ai_deal_desk_conversation
(
    id                   varchar(32)  NOT NULL,
    organization_id      varchar(32)  NOT NULL,
    user_id              varchar(32)  NOT NULL,
    title                varchar(255) NOT NULL,
    dify_conversation_id varchar(128) DEFAULT NULL,
    bound_object_json    text         DEFAULT NULL,
    last_message_text    text         DEFAULT NULL,
    message_count        int          DEFAULT 0,
    create_user          varchar(32)  DEFAULT NULL,
    update_user          varchar(32)  DEFAULT NULL,
    create_time          bigint       DEFAULT NULL,
    update_time          bigint       DEFAULT NULL,
    deleted              tinyint(1)   DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_ai_deal_desk_conversation_user
    ON ai_deal_desk_conversation (organization_id, user_id, deleted, update_time DESC);

CREATE TABLE ai_deal_desk_message
(
    id                  varchar(32) NOT NULL,
    conversation_id     varchar(32) NOT NULL,
    organization_id     varchar(32) NOT NULL,
    user_id             varchar(32) NOT NULL,
    role                varchar(16) NOT NULL,
    content             longtext    DEFAULT NULL,
    references_json     text        DEFAULT NULL,
    process_events_json longtext    DEFAULT NULL,
    writeback_json      longtext    DEFAULT NULL,
    bound_object_json   longtext    DEFAULT NULL,
    dify_message_id     varchar(128) DEFAULT NULL,
    status              varchar(32) DEFAULT 'default',
    create_user         varchar(32) DEFAULT NULL,
    update_user         varchar(32) DEFAULT NULL,
    create_time         bigint      DEFAULT NULL,
    update_time         bigint      DEFAULT NULL,
    deleted             tinyint(1)  DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_ai_deal_desk_message_conversation
    ON ai_deal_desk_message (conversation_id, organization_id, user_id, deleted, create_time ASC);
