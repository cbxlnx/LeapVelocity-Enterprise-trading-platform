-- Seed data for analytics.dim_date
-- Dates needed by the example execution and trade fact rows

TRUNCATE TABLE analytics.dim_date RESTART IDENTITY CASCADE;

INSERT INTO analytics.dim_date
    (date_key, full_date, day, month, year, quarter)
VALUES
    (20260722, DATE '2026-07-22', 22, 7, 2026, 3),
    (20260727, DATE '2026-07-27', 27, 7, 2026, 3),
    (20260801, DATE '2026-08-01', 1, 8, 2026, 3),
    (20260806, DATE '2026-08-06', 6, 8, 2026, 3),
    (20260811, DATE '2026-08-11', 11, 8, 2026, 3),
    (20260816, DATE '2026-08-16', 16, 8, 2026, 3),
    (20260821, DATE '2026-08-21', 21, 8, 2026, 3),
    (20260826, DATE '2026-08-26', 26, 8, 2026, 3),
    (20260831, DATE '2026-08-31', 31, 8, 2026, 3),
    (20260905, DATE '2026-09-05', 5, 9, 2026, 3),
    (20260910, DATE '2026-09-10', 10, 9, 2026, 3),
    (20260920, DATE '2026-09-20', 20, 9, 2026, 3);