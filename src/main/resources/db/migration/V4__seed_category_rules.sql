-- V4: seed starter categorisation rules.
-- Lower priority value = higher precedence. "UBER EATS" (5) must beat "UBER" (20).
INSERT INTO category_rules (keyword, category, sub_category, priority, is_active) VALUES
    ('UBER EATS', 'Food & Drink',   'Delivery',   5,  TRUE),
    ('STARBUCKS', 'Food & Drink',   'Coffee',     10, TRUE),
    ('MCDONALD',  'Food & Drink',   'Fast Food',  10, TRUE),
    ('UBER',      'Transport',      'Rideshare',  20, TRUE),
    ('LYFT',      'Transport',      'Rideshare',  20, TRUE),
    ('SHELL',     'Transport',      'Fuel',       20, TRUE),
    ('AMAZON',    'Shopping',       'Online',     30, TRUE),
    ('WALMART',   'Shopping',       'Retail',     30, TRUE),
    ('NETFLIX',   'Entertainment',  'Streaming',  40, TRUE),
    ('SPOTIFY',   'Entertainment',  'Streaming',  40, TRUE);

