package com.ayush.zepto.cart.repository;

import com.ayush.zepto.cart.entity.CartItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    @EntityGraph(attributePaths = {"product", "product.category"})
    List<CartItem> findAllByCart_IdOrderByIdAsc(Long cartId);

    @EntityGraph(attributePaths = {"product", "product.category"})
    List<CartItem> findAllByCart_IdOrderByProduct_IdAsc(Long cartId);

    Optional<CartItem> findByCart_IdAndProduct_Id(Long cartId, Long productId);

    void deleteByCart_IdAndProduct_Id(Long cartId, Long productId);

    long deleteByIdAndQuantity(Long id, int quantity);
}
