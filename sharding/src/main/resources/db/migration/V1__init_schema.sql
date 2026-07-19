CREATE TABLE t_order (
    order_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    status VARCHAR(50)
);