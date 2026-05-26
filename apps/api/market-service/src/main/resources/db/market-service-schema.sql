create table if not exists stock_price_snapshot (
    id bigint not null auto_increment,
    stock_id bigint not null,
    stock_code varchar(20) not null,
    current_price decimal(18,2) not null,
    change_amount decimal(18,2) not null default 0,
    change_rate decimal(8,4) not null default 0,
    volume bigint not null default 0,
    traded_at datetime null,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    primary key (id),
    constraint uk_stock_price_snapshot_code unique (stock_code),
    index idx_stock_price_snapshot_stock_id (stock_id),
    index idx_stock_price_snapshot_updated_at (updated_at)
);

create table if not exists stock_daily_price (
    id bigint not null auto_increment,
    stock_id bigint not null,
    stock_code varchar(20) not null,
    trade_date date not null,
    open_price decimal(18,2) not null,
    high_price decimal(18,2) not null,
    low_price decimal(18,2) not null,
    close_price decimal(18,2) not null,
    prev_close_price decimal(18,2) not null,
    volume bigint not null default 0,
    created_at datetime not null default current_timestamp,
    updated_at datetime null,
    primary key (id),
    constraint uk_stock_daily_price_code_date unique (stock_code, trade_date),
    index idx_stock_daily_price_stock_id_date (stock_id, trade_date),
    index idx_stock_daily_price_trade_date (trade_date)
);

create table if not exists stock_orderbook_snapshot (
    id bigint not null auto_increment,
    stock_id bigint not null,
    stock_code varchar(20) not null,
    side varchar(10) not null,
    level_no int not null,
    price decimal(18,2) not null,
    quantity bigint not null default 0,
    captured_at datetime not null default current_timestamp,
    primary key (id),
    constraint uk_stock_orderbook_snapshot_code_side_level unique (stock_code, side, level_no),
    index idx_stock_orderbook_snapshot_stock_id (stock_id),
    index idx_stock_orderbook_snapshot_code_side (stock_code, side, level_no)
);

create table if not exists market_index_snapshot (
    id bigint not null auto_increment,
    market varchar(20) not null,
    index_value decimal(12,2) not null,
    change_amount decimal(12,2) not null default 0,
    change_rate decimal(8,4) not null default 0,
    is_cached tinyint(1) not null default 0,
    captured_at datetime not null default current_timestamp,
    primary key (id),
    constraint uk_market_index_snapshot_market unique (market),
    index idx_market_index_snapshot_captured_at (captured_at)
);

create table if not exists market_ranking_snapshot (
    id bigint not null auto_increment,
    ranking_type varchar(20) not null,
    rank_no int not null,
    stock_code varchar(20) not null,
    market varchar(20) not null,
    price decimal(18,2) not null,
    change_amount decimal(18,2) not null default 0,
    change_rate decimal(8,4) not null default 0,
    volume bigint not null default 0,
    trading_amount decimal(20,2) not null default 0,
    captured_at datetime not null default current_timestamp,
    primary key (id),
    constraint uk_market_ranking_snapshot_type_rank unique (ranking_type, rank_no),
    index idx_market_ranking_snapshot_type (ranking_type, rank_no),
    index idx_market_ranking_snapshot_stock_code (stock_code)
);
