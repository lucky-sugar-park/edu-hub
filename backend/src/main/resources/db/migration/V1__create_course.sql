CREATE TABLE course (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    category    VARCHAR(100) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE course_session (
    id        BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT       NOT NULL,
    start_at  DATETIME(6)  NOT NULL,
    place     VARCHAR(200) NOT NULL,
    capacity  INT          NOT NULL,
    status    VARCHAR(20)  NOT NULL,
    CONSTRAINT fk_course_session_course FOREIGN KEY (course_id) REFERENCES course (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_course_session_course_id ON course_session (course_id);
