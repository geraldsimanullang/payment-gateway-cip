CREATE TABLE transactions
(
    id                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    order_id           VARCHAR(100) NOT NULL,
    channel            VARCHAR(50)  NOT NULL,
    amount             DECIMAL(15, 2) NOT NULL,
    account            VARCHAR(50)  NOT NULL,
    currency           VARCHAR(10)  NOT NULL DEFAULT 'IDR',
    payment_method     VARCHAR(50)  NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    corebank_reference VARCHAR(100),
    biller_reference   VARCHAR(100),
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_transactions PRIMARY KEY (id),
    CONSTRAINT uq_transactions_order_id UNIQUE (order_id)
);

CREATE INDEX idx_transactions_order_id ON transactions (order_id);
CREATE INDEX idx_transactions_status ON transactions (status);