-- MySQL 混合复杂场景测试
-- 测试目标：验证多种边界情况的组合处理

-- 场景1: 注释中的分号不应分割
-- 这是一个注释; 包含分号，不应影响分割
INSERT INTO users (name) VALUES ('Comment test');
/* 块注释
   也包含分号;
   不应分割语句
*/
INSERT INTO logs (action) VALUES ('Block comment test');

-- 场景2: 字符串中的分号
INSERT INTO products (name, description) VALUES ('Test;Product', 'Description with;multiple;semicolons');

-- 场景3: DELIMITER 与引号转义混合
DELIMITER ;;
CREATE PROCEDURE mixed_proc(IN p_msg VARCHAR(200))
BEGIN
    DECLARE v_text VARCHAR(200);
    SET v_text = 'It''s a message with ''quotes'' and ; semicolons';
    INSERT INTO logs (action, details) VALUES ('PROC', v_text);
    INSERT INTO logs (action, details) VALUES ('PROC', p_msg);
END;;
DELIMITER ;

-- 场景4: 多层嵌套 BEGIN END
DELIMITER ;;
CREATE PROCEDURE nested_proc()
BEGIN
    DECLARE i INT DEFAULT 0;
    WHILE i < 3 DO
        BEGIN
            INSERT INTO logs (action, details) VALUES ('NESTED', CONCAT('Iteration ', i));
            SET i = i + 1;
        END;
    END WHILE;
END;;
DELIMITER ;

-- 场景5: IF ELSE 结构
DELIMITER ;;
CREATE PROCEDURE conditional_proc(IN p_type INT)
BEGIN
    IF p_type = 1 THEN
        INSERT INTO logs (action) VALUES ('TYPE_1');
    ELSEIF p_type = 2 THEN
        INSERT INTO logs (action) VALUES ('TYPE_2');
    ELSE
        INSERT INTO logs (action) VALUES ('TYPE_OTHER');
    END IF;
END;;
DELIMITER ;

-- 场景6: 恢复默认分隔符后的普通语句
INSERT INTO users (name) VALUES ('After delimiter test');
INSERT INTO logs (action) VALUES ('Final test');