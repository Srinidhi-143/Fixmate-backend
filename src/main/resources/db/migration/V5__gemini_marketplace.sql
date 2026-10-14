ALTER TABLE ai_analysis ADD COLUMN can_self_fix BOOLEAN NULL,
    ADD COLUMN technician_required BOOLEAN NULL,
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'LEGACY';
CREATE TABLE ai_analysis_possible_causes (
    analysis_id BIGINT NOT NULL,
    cause_order INT NOT NULL,
    cause TEXT NOT NULL,
    PRIMARY KEY (analysis_id, cause_order),
    CONSTRAINT fk_ai_causes_analysis FOREIGN KEY (analysis_id) REFERENCES ai_analysis (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
