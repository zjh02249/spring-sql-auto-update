-- MySQL 引号转义测试
-- 测试目标：验证 SQL 标准引号转义（'' 双单引号）正确处理

INSERT INTO users (name, email) VALUES ('It''s a test', 'test@example.com');
INSERT INTO users (name, email) VALUES ('O''Reilly''s book', 'book@example.com');
INSERT INTO users (name, email) VALUES ('John''s "Special" Name', 'john@example.com');

INSERT INTO products (name, price, description) VALUES
    ('Product A', 10.00, 'This is a ''special'' product'),
    ('Product B', 20.00, 'O''Brien''s favorite'),
    ('Product C', 30.00, 'Contains "quotes" and ''apostrophes''');

INSERT INTO logs (action, details) VALUES ('INSERT', 'User ''admin'' created');
INSERT INTO logs (action, details) VALUES ('UPDATE', 'Changed ''name'' field');
INSERT INTO logs (action, details) VALUES ('DELETE', 'Removed ''test'' user''s data');