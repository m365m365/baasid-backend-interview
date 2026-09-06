CREATE TABLE IF NOT EXISTS products (
                                        id          BIGSERIAL PRIMARY KEY,
                                        name        VARCHAR(255),
    price       NUMERIC(19, 2),
    stock       INT,
    version     BIGINT NOT NULL DEFAULT 0,
    created_at  TIMESTAMP DEFAULT now()
    );

CREATE TABLE IF NOT EXISTS users (
                                     id          BIGSERIAL PRIMARY KEY,
                                     username    VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    role        VARCHAR(50) NOT NULL
    );

CREATE TABLE IF NOT EXISTS orders (
                                      id          BIGSERIAL PRIMARY KEY,
                                      user_id     BIGINT,
                                      product_id  BIGINT,
                                      quantity    INT,
                                      total_price NUMERIC(19, 2),
    created_at  TIMESTAMP DEFAULT now()
    );

CREATE TABLE IF NOT EXISTS audit_logs (
                                          id BIGSERIAL PRIMARY KEY,
                                          operator VARCHAR(100) NOT NULL,
    action VARCHAR(20) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT,
    before_data JSONB,
    after_data JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );