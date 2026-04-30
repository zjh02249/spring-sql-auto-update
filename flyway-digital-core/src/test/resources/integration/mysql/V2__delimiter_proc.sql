-- MySQL DELIMITER 存储过程测试
-- 测试目标：验证 DELIMITER 语法正确分割存储过程定义

DELIMITER ;;
CREATE PROCEDURE get_user_count()
BEGIN
    SELECT COUNT(*) AS total_users FROM users;
END;;
DELIMITER ;

DELIMITER ;;
CREATE PROCEDURE add_user(IN p_name VARCHAR(100), IN p_email VARCHAR(200))
BEGIN
    INSERT INTO users (name, email) VALUES (p_name, p_email);
    INSERT INTO logs (user_id, action, details) VALUES (LAST_INSERT_ID(), 'CREATE', 'User created');
END;;
DELIMITER ;

DELIMITER ;;
CREATE PROCEDURE complex_proc(IN p_id INT, OUT p_count INT)
BEGIN
    DECLARE v_total INT DEFAULT 0;
    DECLARE v_name VARCHAR(100);

    SELECT name INTO v_name FROM users WHERE id = p_id;

    SELECT COUNT(*) INTO v_total FROM logs WHERE user_id = p_id;

    SET p_count = v_total;

    INSERT INTO logs (user_id, action, details)
    VALUES (p_id, 'QUERY', CONCAT('Queried user: ', v_name));
END;;
DELIMITER ;