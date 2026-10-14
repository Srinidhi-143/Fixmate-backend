CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(254) NOT NULL,
    phone VARCHAR(32) NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE service_categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_service_categories_name (name),
    UNIQUE KEY uq_service_categories_slug (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE service_providers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    experience_years INT NOT NULL,
    location VARCHAR(500) NOT NULL,
    latitude DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL,
    service_area_km DOUBLE NOT NULL DEFAULT 50.0,
    availability_status VARCHAR(20) NOT NULL,
    description VARCHAR(2000) NULL,
    rating DECIMAL(3,2) NOT NULL DEFAULT 0.00,
    completed_jobs INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_service_providers_user (user_id),
    CONSTRAINT fk_service_providers_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE provider_skill_names (
    provider_id BIGINT NOT NULL,
    skill VARCHAR(100) NOT NULL,
    PRIMARY KEY (provider_id, skill),
    CONSTRAINT fk_provider_skill_names_provider FOREIGN KEY (provider_id)
        REFERENCES service_providers (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE provider_skills (
    provider_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    PRIMARY KEY (provider_id, category_id),
    CONSTRAINT fk_provider_skills_provider FOREIGN KEY (provider_id)
        REFERENCES service_providers (id),
    CONSTRAINT fk_provider_skills_category FOREIGN KEY (category_id)
        REFERENCES service_categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE service_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    assigned_provider_id BIGINT NULL,
    title VARCHAR(180) NOT NULL,
    description TEXT NOT NULL,
    location VARCHAR(500) NOT NULL,
    latitude DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL,
    preferred_date DATE NULL,
    preferred_time TIME NULL,
    priority VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    ai_analysis_id BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_requests_user_status_created (user_id, status, created_at),
    KEY idx_requests_category_status (category_id, status),
    KEY idx_requests_assigned_provider (assigned_provider_id, status, updated_at),
    CONSTRAINT fk_service_requests_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_service_requests_category FOREIGN KEY (category_id) REFERENCES service_categories (id),
    CONSTRAINT fk_service_requests_provider FOREIGN KEY (assigned_provider_id) REFERENCES service_providers (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_analysis (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_id BIGINT NOT NULL,
    predicted_category VARCHAR(100) NOT NULL,
    possible_problem TEXT NOT NULL,
    recommended_service VARCHAR(100) NOT NULL,
    safety_warning TEXT NULL,
    priority VARCHAR(20) NOT NULL,
    confidence DECIMAL(5,4) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ai_analysis_request (request_id),
    CONSTRAINT fk_ai_analysis_request FOREIGN KEY (request_id) REFERENCES service_requests (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE service_requests
    ADD CONSTRAINT fk_service_requests_ai_analysis FOREIGN KEY (ai_analysis_id) REFERENCES ai_analysis (id);

CREATE TABLE ai_analysis_troubleshooting_steps (
    analysis_id BIGINT NOT NULL,
    step_order INT NOT NULL,
    step TEXT NOT NULL,
    PRIMARY KEY (analysis_id, step_order),
    CONSTRAINT fk_ai_analysis_steps_analysis FOREIGN KEY (analysis_id) REFERENCES ai_analysis (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE service_request_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_id BIGINT NOT NULL,
    storage_path VARCHAR(512) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_request_images_storage_path (storage_path),
    KEY idx_request_images_request (request_id),
    CONSTRAINT fk_request_images_request FOREIGN KEY (request_id) REFERENCES service_requests (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_id BIGINT NOT NULL,
    rating INT NOT NULL,
    review VARCHAR(3000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_reviews_request (request_id),
    KEY idx_reviews_created (created_at),
    CONSTRAINT fk_reviews_request FOREIGN KEY (request_id) REFERENCES service_requests (id),
    CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    request_id BIGINT NULL,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(180) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    deduplication_key VARCHAR(200) NULL,
    read_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_notifications_deduplication_key (deduplication_key),
    KEY idx_notifications_user_created (user_id, created_at),
    KEY idx_notifications_request (request_id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_notifications_request FOREIGN KEY (request_id) REFERENCES service_requests (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;