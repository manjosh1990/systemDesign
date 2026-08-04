-- Schema for consistent-hashing POC
-- This SQL runs on BOTH ds_0 and ds_1 via FlywayMigrationRunner at startup.
-- Same table structure as the sharding project — so you can compare routing behaviour directly.
--
-- user_id is the shard key:
--   Sharding project: routes via user_id % 2
--   This project:     routes via hash(user_id) → ConsistentHashRing → ds0 or ds1

CREATE TABLE t_order (
    order_id BIGINT PRIMARY KEY,
    user_id  BIGINT       NOT NULL,
    status   VARCHAR(50)
);
