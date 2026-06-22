-- V2: resolved merchant categories (one per merchant, linked by merchant_name)
CREATE TABLE merchant_categories (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    merchant_name    VARCHAR(255)  NOT NULL,
    category         VARCHAR(100)  NOT NULL,
    sub_category     VARCHAR(100),
    confidence_score DECIMAL(3, 2),
    categorised_at   DATETIME(6),
    strategy_used    VARCHAR(20),
    PRIMARY KEY (id),
    CONSTRAINT uq_mc_merchant_name UNIQUE (merchant_name),
    CONSTRAINT chk_mc_confidence CHECK (confidence_score >= 0.00 AND confidence_score <= 1.00)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

