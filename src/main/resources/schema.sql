CREATE TABLE IF NOT EXISTS tenants (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  slug VARCHAR(100) UNIQUE NOT NULL,
  plan VARCHAR(50) DEFAULT 'FREE',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  username VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role ENUM('ADMIN','USER') DEFAULT 'USER',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS report_schedules (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  name VARCHAR(255) NOT NULL,
  cron_expr VARCHAR(100) NOT NULL,
  report_type VARCHAR(50) NOT NULL,
  format ENUM('PDF','EXCEL','CSV') NOT NULL,
  template_id VARCHAR(100) NOT NULL,
  recipients JSON,
  params JSON,
  created_by BIGINT NOT NULL DEFAULT 0,
  status ENUM('ACTIVE','PAUSED') DEFAULT 'ACTIVE',
  last_run_at DATETIME,
  next_run_at DATETIME
);

CREATE TABLE IF NOT EXISTS audit_logs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  user_id BIGINT,
  action VARCHAR(100) NOT NULL,
  resource VARCHAR(255),
  detail TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS report_assignments (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id    BIGINT NOT NULL,
  created_by   BIGINT NOT NULL,
  assignee_id  BIGINT NOT NULL,
  template_id  VARCHAR(100) NOT NULL,
  notes        TEXT,
  status       ENUM('PENDING','COMPLETED') DEFAULT 'PENDING',
  document_id  VARCHAR(100),
  created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
  completed_at DATETIME,
  INDEX idx_ra_tenant (tenant_id),
  INDEX idx_ra_assignee_status (assignee_id, status)
);
