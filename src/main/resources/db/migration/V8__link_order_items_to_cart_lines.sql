ALTER TABLE order_items
    ADD COLUMN cart_item_id BIGINT NULL AFTER product_id,
    ADD CONSTRAINT fk_order_items_cart_item FOREIGN KEY (cart_item_id)
        REFERENCES cart_items (id) ON DELETE SET NULL;
