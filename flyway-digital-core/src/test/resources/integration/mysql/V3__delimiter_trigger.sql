-- MySQL DELIMITER 触发器测试
-- 测试目标：验证 DELIMITER 语法正确分割触发器定义

DELIMITER ;;
CREATE TRIGGER before_user_insert
BEFORE INSERT ON users
FOR EACH ROW
BEGIN
    IF NEW.name IS NULL OR NEW.name = '' THEN
        SET NEW.name = 'Anonymous';
    END IF;
    INSERT INTO logs (action, details) VALUES ('PRE_INSERT', 'Trigger executed');
END;;
DELIMITER ;

DELIMITER ;;
CREATE TRIGGER after_user_update
AFTER UPDATE ON users
FOR EACH ROW
BEGIN
    INSERT INTO logs (user_id, action, details)
    VALUES (NEW.id, 'UPDATE', CONCAT('Updated from ', OLD.name, ' to ', NEW.name));
END;;
DELIMITER ;

DELIMITER ;;
CREATE TRIGGER before_product_insert
BEFORE INSERT ON products
FOR EACH ROW
BEGIN
    IF NEW.price < 0 THEN
        SET NEW.price = 0;
    END IF;
    IF NEW.description IS NULL THEN
        SET NEW.description = 'No description';
    END IF;
END;;
DELIMITER ;