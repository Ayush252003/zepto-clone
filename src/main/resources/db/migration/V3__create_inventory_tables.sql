CREATE TABLE inventory (
    product_id BIGINT NOT NULL,
    on_hand INT NOT NULL DEFAULT 0,
    reserved INT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (product_id),
    CONSTRAINT ck_inventory_on_hand_nonnegative CHECK (on_hand >= 0),
    CONSTRAINT ck_inventory_reserved_nonnegative CHECK (reserved >= 0),
    CONSTRAINT ck_inventory_reserved_within_stock CHECK (reserved <= on_hand),
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO inventory (product_id, on_hand, reserved)
SELECT id, 0, 0 FROM products;

CREATE TABLE inventory_reservations (
    id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT ck_inventory_reservation_status CHECK (status IN ('RESERVED', 'COMMITTED', 'RELEASED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE inventory_reservation_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reservation_id VARCHAR(36) NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_inventory_reservation_product UNIQUE (reservation_id, product_id),
    CONSTRAINT ck_inventory_reservation_quantity_positive CHECK (quantity > 0),
    CONSTRAINT fk_inventory_reservation_items_reservation FOREIGN KEY (reservation_id)
        REFERENCES inventory_reservations (id),
    CONSTRAINT fk_inventory_reservation_items_product FOREIGN KEY (product_id)
        REFERENCES products (id),
    INDEX idx_inventory_reservation_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
