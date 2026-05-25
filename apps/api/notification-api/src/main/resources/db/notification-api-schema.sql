create table if not exists notification (
    id bigint not null auto_increment,
    member_id bigint not null,
    type varchar(50) not null,
    title varchar(200) not null,
    body text not null,
    is_read tinyint(1) not null default 0,
    read_at datetime null,
    target_type varchar(50) null,
    target_id bigint null,
    delivery_status varchar(20) not null default 'CREATED',
    retry_count int not null default 0,
    last_retry_at datetime null,
    delivery_failure_reason varchar(500) null,
    created_at datetime not null default current_timestamp,
    primary key (id),
    index idx_notification_member_created (member_id, created_at desc, id desc),
    index idx_notification_member_read (member_id, is_read)
);

create table if not exists notification_setting (
    id bigint not null auto_increment,
    member_id bigint not null,
    trade_alert tinyint(1) not null default 1,
    contest_alert tinyint(1) not null default 1,
    market_alert tinyint(1) not null default 1,
    trade_complete tinyint(1) not null default 1,
    order_cancel tinyint(1) not null default 1,
    pending_order tinyint(1) not null default 0,
    contest_start tinyint(1) not null default 1,
    contest_end tinyint(1) not null default 1,
    rank_change tinyint(1) not null default 0,
    market_open tinyint(1) not null default 0,
    market_close tinyint(1) not null default 0,
    updated_at datetime not null default current_timestamp,
    primary key (id),
    constraint uk_notification_setting_member unique (member_id),
    index idx_notification_setting_member (member_id)
);

alter table notification_setting
    add column if not exists trade_complete tinyint(1) not null default 1 after market_alert,
    add column if not exists order_cancel tinyint(1) not null default 1 after trade_complete,
    add column if not exists pending_order tinyint(1) not null default 0 after order_cancel,
    add column if not exists contest_start tinyint(1) not null default 1 after pending_order,
    add column if not exists contest_end tinyint(1) not null default 1 after contest_start,
    add column if not exists rank_change tinyint(1) not null default 0 after contest_end,
    add column if not exists market_open tinyint(1) not null default 0 after rank_change,
    add column if not exists market_close tinyint(1) not null default 0 after market_open;
