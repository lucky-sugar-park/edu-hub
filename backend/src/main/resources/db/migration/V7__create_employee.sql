CREATE TABLE employee (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    employee_no   VARCHAR(20)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_employee_no UNIQUE (employee_no),
    CONSTRAINT ck_employee_role CHECK (role IN ('EMPLOYEE', 'ADMIN'))
);
