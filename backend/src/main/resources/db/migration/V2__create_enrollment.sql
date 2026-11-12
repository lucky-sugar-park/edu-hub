CREATE TABLE enrollment (
    id                BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id        BIGINT      NOT NULL,
    employee_id       VARCHAR(20) NOT NULL,
    status            VARCHAR(20) NOT NULL,
    applied_at        DATETIME(6) NOT NULL,
    -- 활성 신청(CONFIRMED, WAITING)일 때만 session_id, 아니면 NULL. 유일 인덱스는 NULL을 여러 개 허용한다.
    active_session_id BIGINT AS (IF(status IN ('CONFIRMED', 'WAITING'), session_id, NULL)) PERSISTENT,
    CONSTRAINT fk_enrollment_session FOREIGN KEY (session_id) REFERENCES course_session (id),
    CONSTRAINT uk_enrollment_active UNIQUE (active_session_id, employee_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 정원 계산(회차별 확정 수)과 외래 키 인덱스를 겸한다.
CREATE INDEX idx_enrollment_session_status ON enrollment (session_id, status);
-- FR-07 내 신청 내역 조회용.
CREATE INDEX idx_enrollment_employee ON enrollment (employee_id);
