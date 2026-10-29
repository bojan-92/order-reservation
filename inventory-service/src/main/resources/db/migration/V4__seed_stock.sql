-- Demo stock so the system is usable right after `docker compose up`.
INSERT INTO stock_items (item_id, available, updated_at)
VALUES ('item-1', 10, now()),
       ('item-2', 5, now()),
       ('item-3', 0, now());
