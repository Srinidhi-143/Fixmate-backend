ALTER TABLE password_reset_tokens ADD COLUMN otp_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE service_requests ADD COLUMN client_request_key VARCHAR(64) NULL,
    ADD CONSTRAINT uq_request_user_client_key UNIQUE (user_id, client_request_key);
ALTER TABLE ai_analysis ADD COLUMN image_analysis_status VARCHAR(30) NOT NULL DEFAULT 'NOT_PROVIDED';
