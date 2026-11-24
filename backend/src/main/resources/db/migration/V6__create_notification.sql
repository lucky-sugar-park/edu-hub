CREATE TABLE notification (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id VARCHAR(20)  NOT NULL,
    type        VARCHAR(30)  NOT NULL,
    message     VARCHAR(500) NOT NULL,
    created_at  DATETIME(6)  NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 나중의 "내 알림" 조회용.
CREATE INDEX idx_notification_employee ON notification (employee_id);
