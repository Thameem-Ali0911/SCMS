-- V1__baseline_schema.sql
--
-- MENTOR NOTE — replacing ddl-auto=update (v1.3 finding, Backend Engineering
-- + Database Design): "ddl-auto=update will silently alter tables if entity
-- fields change... orphan columns accumulate in production." Flyway makes
-- every schema change an explicit, versioned, reviewable SQL file instead.
-- spring.jpa.hibernate.ddl-auto is now `validate` everywhere — Hibernate
-- checks the schema matches the entities at startup and refuses to start
-- if they've drifted, but it NEVER writes DDL itself again.
--
-- baseline-on-migrate=true (see application.properties) means this file is
-- also safe to point at a database that already has these tables from a
-- prior ddl-auto=update run — Flyway will baseline at V1 instead of trying
-- to re-create existing tables.

CREATE TABLE roles (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL,
    description VARCHAR(255),
    CONSTRAINT uq_roles_name UNIQUE (name)
) ENGINE=InnoDB;

CREATE TABLE users (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    first_name             VARCHAR(50)  NOT NULL,
    last_name              VARCHAR(50)  NOT NULL,
    email                  VARCHAR(100) NOT NULL,
    password_hash          VARCHAR(255) NOT NULL,
    phone                  VARCHAR(20),
    is_active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at             DATETIME,
    failed_login_attempts  INT          NOT NULL DEFAULT 0,
    account_locked_until   DATETIME,
    token_version          INT          NOT NULL DEFAULT 0,
    CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE=InnoDB;

CREATE INDEX idx_users_email ON users (email);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id INT    NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE categories (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME,
    CONSTRAINT uq_categories_name UNIQUE (name)
) ENGINE=InnoDB;

CREATE TABLE complaints (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    subject       VARCHAR(255) NOT NULL,
    description   TEXT         NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    priority      VARCHAR(10)  NOT NULL,
    category_id   BIGINT       NOT NULL,
    submitted_by  BIGINT       NOT NULL,
    assigned_to   BIGINT,
    submitted_at  DATETIME,
    updated_at    DATETIME,
    resolved_at   DATETIME,
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at    DATETIME,
    deleted_by    BIGINT,
    version       INT          NOT NULL DEFAULT 1,
    CONSTRAINT fk_complaints_category     FOREIGN KEY (category_id)  REFERENCES categories(id),
    CONSTRAINT fk_complaints_submitted_by FOREIGN KEY (submitted_by) REFERENCES users(id),
    CONSTRAINT fk_complaints_assigned_to  FOREIGN KEY (assigned_to)  REFERENCES users(id)
) ENGINE=InnoDB;

CREATE INDEX idx_complaints_submitted_by ON complaints (submitted_by);
CREATE INDEX idx_complaints_assigned_to  ON complaints (assigned_to);
CREATE INDEX idx_complaints_status       ON complaints (status);
CREATE INDEX idx_complaints_submitted_at ON complaints (submitted_at);
CREATE INDEX idx_complaints_category_id  ON complaints (category_id);

CREATE TABLE complaint_versions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    complaint_id    BIGINT       NOT NULL,
    version_number  INT          NOT NULL,
    subject         VARCHAR(255),
    description     TEXT,
    status          VARCHAR(30),
    priority        VARCHAR(15),
    category        VARCHAR(255),
    assigned_to     BIGINT,
    changed_by      BIGINT       NOT NULL,
    changed_at      DATETIME,
    change_reason   VARCHAR(500),
    change_type     VARCHAR(20),
    previous_status VARCHAR(30),
    new_status      VARCHAR(30),
    CONSTRAINT fk_versions_complaint  FOREIGN KEY (complaint_id) REFERENCES complaints(id) ON DELETE CASCADE,
    CONSTRAINT fk_versions_assignee   FOREIGN KEY (assigned_to)  REFERENCES users(id),
    CONSTRAINT fk_versions_changed_by FOREIGN KEY (changed_by)   REFERENCES users(id)
) ENGINE=InnoDB;

CREATE INDEX idx_complaint_versions_complaint_id ON complaint_versions (complaint_id);

CREATE TABLE audit_logs (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_type  VARCHAR(50)  NOT NULL,
    entity_id    BIGINT       NOT NULL,
    action       VARCHAR(50)  NOT NULL,
    performed_by BIGINT,
    performed_at DATETIME,
    old_values   TEXT,
    new_values   TEXT,
    ip_address   VARCHAR(45),
    user_agent   VARCHAR(500)
) ENGINE=InnoDB;

CREATE INDEX idx_audit_logs_entity       ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_logs_performed_by ON audit_logs (performed_by);
CREATE INDEX idx_audit_logs_performed_at ON audit_logs (performed_at);

CREATE TABLE login_throttle (
    ip            VARCHAR(64) PRIMARY KEY,
    attempt_count INT      NOT NULL,
    window_start  DATETIME NOT NULL,
    locked_until  DATETIME
) ENGINE=InnoDB;
