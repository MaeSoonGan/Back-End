insert into stock_price_snapshot
(stock_id, stock_code, current_price, change_amount, change_rate, volume, traded_at, updated_at)
select id, code, 75400, 1200, 1.6200, 12300000, current_timestamp, current_timestamp
from stock
where code = '005930'
on duplicate key update
    current_price = values(current_price),
    change_amount = values(change_amount),
    change_rate = values(change_rate),
    volume = values(volume),
    traded_at = values(traded_at),
    updated_at = values(updated_at);

insert into stock_price_snapshot
(stock_id, stock_code, current_price, change_amount, change_rate, volume, traded_at, updated_at)
select id, code, 182500, 4900, 2.7600, 5678901, current_timestamp, current_timestamp
from stock
where code = '000660'
on duplicate key update
    current_price = values(current_price),
    change_amount = values(change_amount),
    change_rate = values(change_rate),
    volume = values(volume),
    traded_at = values(traded_at),
    updated_at = values(updated_at);

insert into stock_price_snapshot
(stock_id, stock_code, current_price, change_amount, change_rate, volume, traded_at, updated_at)
select id, code, 134000, -400, -0.3000, 980112, current_timestamp, current_timestamp
from stock
where code = '028260'
on duplicate key update
    current_price = values(current_price),
    change_amount = values(change_amount),
    change_rate = values(change_rate),
    volume = values(volume),
    traded_at = values(traded_at),
    updated_at = values(updated_at);

insert into stock_price_snapshot
(stock_id, stock_code, current_price, change_amount, change_rate, volume, traded_at, updated_at)
select id, code, 218000, -1500, -0.6800, 2234100, current_timestamp, current_timestamp
from stock
where code = '035420'
on duplicate key update
    current_price = values(current_price),
    change_amount = values(change_amount),
    change_rate = values(change_rate),
    volume = values(volume),
    traded_at = values(traded_at),
    updated_at = values(updated_at);

insert into stock_daily_price
(stock_id, stock_code, trade_date, open_price, high_price, low_price, close_price, prev_close_price, volume, updated_at)
select id, code, current_date, 74200, 75900, 74100, 75400, 74200, 12300000, current_timestamp
from stock
where code = '005930'
on duplicate key update
    open_price = values(open_price),
    high_price = values(high_price),
    low_price = values(low_price),
    close_price = values(close_price),
    prev_close_price = values(prev_close_price),
    volume = values(volume),
    updated_at = values(updated_at);

insert into stock_daily_price
(stock_id, stock_code, trade_date, open_price, high_price, low_price, close_price, prev_close_price, volume, updated_at)
select id, code, current_date, 178000, 183600, 177500, 182500, 177600, 5678901, current_timestamp
from stock
where code = '000660'
on duplicate key update
    open_price = values(open_price),
    high_price = values(high_price),
    low_price = values(low_price),
    close_price = values(close_price),
    prev_close_price = values(prev_close_price),
    volume = values(volume),
    updated_at = values(updated_at);

insert into stock_daily_price
(stock_id, stock_code, trade_date, open_price, high_price, low_price, close_price, prev_close_price, volume, updated_at)
select id, code, current_date, 134500, 135000, 133100, 134000, 134400, 980112, current_timestamp
from stock
where code = '028260'
on duplicate key update
    open_price = values(open_price),
    high_price = values(high_price),
    low_price = values(low_price),
    close_price = values(close_price),
    prev_close_price = values(prev_close_price),
    volume = values(volume),
    updated_at = values(updated_at);

insert into stock_orderbook_snapshot
(stock_id, stock_code, side, level_no, price, quantity, captured_at)
select id, code, 'ASK', 1, 75800, 3214, current_timestamp from stock where code = '005930'
on duplicate key update price = values(price), quantity = values(quantity), captured_at = values(captured_at);
insert into stock_orderbook_snapshot
(stock_id, stock_code, side, level_no, price, quantity, captured_at)
select id, code, 'ASK', 2, 75700, 5892, current_timestamp from stock where code = '005930'
on duplicate key update price = values(price), quantity = values(quantity), captured_at = values(captured_at);
insert into stock_orderbook_snapshot
(stock_id, stock_code, side, level_no, price, quantity, captured_at)
select id, code, 'ASK', 3, 75600, 7441, current_timestamp from stock where code = '005930'
on duplicate key update price = values(price), quantity = values(quantity), captured_at = values(captured_at);
insert into stock_orderbook_snapshot
(stock_id, stock_code, side, level_no, price, quantity, captured_at)
select id, code, 'ASK', 4, 75500, 4122, current_timestamp from stock where code = '005930'
on duplicate key update price = values(price), quantity = values(quantity), captured_at = values(captured_at);
insert into stock_orderbook_snapshot
(stock_id, stock_code, side, level_no, price, quantity, captured_at)
select id, code, 'ASK', 5, 75400, 9567, current_timestamp from stock where code = '005930'
on duplicate key update price = values(price), quantity = values(quantity), captured_at = values(captured_at);

insert into stock_orderbook_snapshot
(stock_id, stock_code, side, level_no, price, quantity, captured_at)
select id, code, 'BID', 1, 75300, 6234, current_timestamp from stock where code = '005930'
on duplicate key update price = values(price), quantity = values(quantity), captured_at = values(captured_at);
insert into stock_orderbook_snapshot
(stock_id, stock_code, side, level_no, price, quantity, captured_at)
select id, code, 'BID', 2, 75200, 4891, current_timestamp from stock where code = '005930'
on duplicate key update price = values(price), quantity = values(quantity), captured_at = values(captured_at);
insert into stock_orderbook_snapshot
(stock_id, stock_code, side, level_no, price, quantity, captured_at)
select id, code, 'BID', 3, 75100, 8123, current_timestamp from stock where code = '005930'
on duplicate key update price = values(price), quantity = values(quantity), captured_at = values(captured_at);
insert into stock_orderbook_snapshot
(stock_id, stock_code, side, level_no, price, quantity, captured_at)
select id, code, 'BID', 4, 75000, 3456, current_timestamp from stock where code = '005930'
on duplicate key update price = values(price), quantity = values(quantity), captured_at = values(captured_at);
insert into stock_orderbook_snapshot
(stock_id, stock_code, side, level_no, price, quantity, captured_at)
select id, code, 'BID', 5, 74900, 7789, current_timestamp from stock where code = '005930'
on duplicate key update price = values(price), quantity = values(quantity), captured_at = values(captured_at);

insert into market_index_snapshot
(market, index_value, change_amount, change_rate, is_cached, captured_at)
values
('KOSPI', 2847.00, 15.42, 0.5400, 0, current_timestamp),
('KOSDAQ', 874.22, -3.18, -0.3600, 0, current_timestamp)
on duplicate key update
    index_value = values(index_value),
    change_amount = values(change_amount),
    change_rate = values(change_rate),
    is_cached = values(is_cached),
    captured_at = values(captured_at);

insert into market_ranking_snapshot
(ranking_type, rank_no, stock_code, market, price, change_amount, change_rate, volume, trading_amount, captured_at)
values
('TRADING_VALUE', 1, '005930', 'KOSPI', 75400, 1200, 1.6200, 12345678, 930864121200, current_timestamp),
('TRADING_VALUE', 2, '000660', 'KOSPI', 182500, 4900, 2.7600, 5678901, 1036399432500, current_timestamp),
('TRADING_VALUE', 3, '035420', 'KOSPI', 218000, -1500, -0.6800, 2234100, 487033800000, current_timestamp),
('RISE', 1, '000660', 'KOSPI', 182500, 4900, 2.7600, 5678901, 1036399432500, current_timestamp),
('RISE', 2, '005930', 'KOSPI', 75400, 1200, 1.6200, 12345678, 930864121200, current_timestamp),
('FALL', 1, '035420', 'KOSPI', 218000, -1500, -0.6800, 2234100, 487033800000, current_timestamp),
('FALL', 2, '028260', 'KOSPI', 134000, -400, -0.3000, 980112, 131335008000, current_timestamp)
on duplicate key update
    stock_code = values(stock_code),
    market = values(market),
    price = values(price),
    change_amount = values(change_amount),
    change_rate = values(change_rate),
    volume = values(volume),
    trading_amount = values(trading_amount),
    captured_at = values(captured_at);
