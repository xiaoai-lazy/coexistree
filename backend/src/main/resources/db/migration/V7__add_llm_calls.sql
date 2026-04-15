-- LLM call tracking table
CREATE TABLE llm_calls (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    scenario         VARCHAR(128) NOT NULL,
    user_id          BIGINT NULL,
    document_id      BIGINT NULL,
    system_id        BIGINT NULL,
    model            VARCHAR(64) NOT NULL,
    temperature      DOUBLE NULL,
    input_tokens     INT NULL,
    output_tokens    INT NULL,
    reasoning_tokens INT NULL,
    total_tokens     INT NULL,
    elapsed_ms       BIGINT NOT NULL,
    success          BOOLEAN NOT NULL DEFAULT TRUE,
    error_message    TEXT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_llm_calls_document_id ON llm_calls(document_id);
CREATE INDEX idx_llm_calls_system_id ON llm_calls(system_id);
CREATE INDEX idx_llm_calls_scenario ON llm_calls(scenario);
CREATE INDEX idx_llm_calls_created_at ON llm_calls(created_at);
