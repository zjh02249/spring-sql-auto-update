-- PostgreSQL PL/pgSQL 函数定义测试
-- 测试目标：验证 $$ 分隔符和函数定义正确分割

-- 使用 $$ 分隔符的函数
CREATE OR REPLACE FUNCTION get_user_count()
RETURNS INT AS $$ BEGIN
    RETURN (SELECT COUNT(*) FROM users);
END;
$$ LANGUAGE plpgsql;

-- 带参数的函数
CREATE OR REPLACE FUNCTION add_user(p_name VARCHAR(100), p_email VARCHAR(200))
RETURNS INT AS $$ DECLARE
    v_id INT;
BEGIN
    INSERT INTO users (name, email) VALUES (p_name, p_email)
    RETURNING id INTO v_id;

    INSERT INTO logs (user_id, action, details)
    VALUES (v_id, 'CREATE', 'User created');

    RETURN v_id;
END;
$$ LANGUAGE plpgsql;

-- 复杂函数（多语句、条件逻辑）
CREATE OR REPLACE FUNCTION complex_func(p_id INT)
RETURNS TABLE(user_name VARCHAR, log_count INT) AS $$ DECLARE
    v_name VARCHAR(100);
BEGIN
    SELECT name INTO v_name FROM users WHERE id = p_id;

    RETURN QUERY SELECT u.name, COUNT(l.id) AS cnt
    FROM users u
    LEFT JOIN logs l ON l.user_id = u.id
    WHERE u.id = p_id
    GROUP BY u.name;
END;
$$ LANGUAGE plpgsql;

-- 使用 RETURNS TABLE 的函数
CREATE OR REPLACE FUNCTION get_all_users()
RETURNS TABLE(id INT, name VARCHAR, email VARCHAR) AS $$ BEGIN
    RETURN QUERY SELECT u.id, u.name, u.email FROM users u;
END;
$$ LANGUAGE plpgsql;