-- V1: raw ingested transactions
CREATE TABLE transactions (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    merchant_name    VARCHAR(255)  NOT NULL,
    amount           DECIMAL(19, 4) NOT NULL,
    currency         VARCHAR(3)    NOT NULL,
    transaction_date DATE          NOT NULL,
    source           VARCHAR(20)   NOT NULL,
    created_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_tx_merchant_name ON transactions (merchant_name);
CREATE INDEX idx_tx_transaction_date ON transactions (transaction_date);

