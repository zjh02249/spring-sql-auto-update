-- PostgreSQL DO 匿名块测试
-- 测试目标：验证 DO $$ BEGIN END $$ 结构正确分割

-- 简单 DO 匿名块
DO $$ BEGIN
    INSERT INTO logs (action, details) VALUES ('DO_BLOCK', 'Anonymous block executed');
END $$;

-- 带变量的 DO 匿名块
DO $$ DECLARE
    v_count INT;
BEGIN
    SELECT COUNT(*) INTO v_count FROM users;
    INSERT INTO logs (action, details)
    VALUES ('DO_COUNT', CONCAT('Total users: ', v_count));
END $$;

-- 带循环的 DO 匿名块
DO $$ DECLARE
    i INT DEFAULT 0;
BEGIN
    WHILE i < 3 DO
        INSERT INTO logs (action, details)
        VALUES ('DO_LOOP', CONCAT('Iteration ', i));
        i := i + 1;
    END LOOP;
END $$;

-- 带条件逻辑的 DO 匿名块
DO $$ DECLARE
    v_exists BOOLEAN;
BEGIN
    SELECT EXISTS(SELECT 1 FROM users WHERE name = 'admin') INTO v_exists;

    IF v_exists THEN
        INSERT INTO logs (action, details) VALUES ('DO_CHECK', 'Admin exists');
    ELSE
        INSERT INTO logs (action, details) VALUES ('DO_CHECK', 'Admin not found');
    END IF;
END $$;

-- 嵌套结构的 DO 匿名块
DO $$ BEGIN
    BEGIN
        INSERT INTO logs (action) VALUES ('DO_NESTED_1');
    END;

    BEGIN
        INSERT INTO logs (action) VALUES ('DO_NESTED_2');
    END;
END $$;