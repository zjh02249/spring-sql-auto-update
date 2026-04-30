-- PostgreSQL 混合复杂场景测试
-- 测试目标：验证多种边界情况的组合处理

-- 场景1: 注释中的分号不应分割
-- 这是一个注释; 包含分号，不应影响分割
INSERT INTO users (name) VALUES ('Comment test');
/* 块注释
   也包含分号;
   不应分割语句
*/
INSERT INTO logs (action) VALUES ('Block comment test');

-- 场景2: 字符串中的分号和引号转义
INSERT INTO products (name, description) VALUES ('Test;Product', 'Description with;multiple;semicolons');
INSERT INTO products (name, description) VALUES ('Quote''s Test', 'It''s a test with ''quotes''');

-- 场景3: 函数与 DO 匿名块混合
CREATE OR REPLACE FUNCTION mixed_func()
RETURNS VOID AS $$ BEGIN
    INSERT INTO logs (action) VALUES ('MIXED_FUNC');
END;
$$ LANGUAGE plpgsql;

DO $$ BEGIN
    INSERT INTO logs (action) VALUES ('MIXED_DO');
END $$;

-- 场景4: 复杂函数（包含多种语法）
CREATE OR REPLACE FUNCTION complex_mixed(p_type INT)
RETURNS VOID AS $$ DECLARE
    v_msg TEXT;
BEGIN
    IF p_type = 1 THEN
        v_msg := 'Type one''s message';
    ELSEIF p_type = 2 THEN
        v_msg := 'Type two; with semicolon';
    ELSE
        v_msg := 'Other type';
    END IF;

    INSERT INTO logs (action, details) VALUES ('COMPLEX', v_msg);

    -- 嵌套 BEGIN END
    BEGIN
        INSERT INTO logs (action) VALUES ('COMPLEX_NESTED');
    END;
END;
$$ LANGUAGE plpgsql;

-- 场景5: 多个 DO 匿名块连续执行
DO $$ BEGIN INSERT INTO logs (action) VALUES ('SEQ_1'); END $$;
DO $$ BEGIN INSERT INTO logs (action) VALUES ('SEQ_2'); END $$;
DO $$ BEGIN INSERT INTO logs (action) VALUES ('SEQ_3'); END $$;

-- 场景6: 普通语句
INSERT INTO users (name, email) VALUES ('Final test', 'final@test.com');
INSERT INTO logs (action, details) VALUES ('FINAL', 'All tests completed');