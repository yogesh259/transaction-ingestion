-- V3: configurable keyword-based categorisation rules
CREATE TABLE category_rules (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    keyword      VARCHAR(255) NOT NULL,
    category     VARCHAR(100) NOT NULL,
    sub_category VARCHAR(100),
    priority     INT          NOT NULL,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT uq_rule_keyword UNIQUE (keyword)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

