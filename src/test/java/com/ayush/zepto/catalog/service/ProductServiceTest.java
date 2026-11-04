package com.ayush.zepto.catalog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ayush.zepto.catalog.dto.PageResponse;
import com.ayush.zepto.catalog.dto.ProductResponse;
import com.ayush.zepto.catalog.dto.ProductRequest;
import com.ayush.zepto.catalog.entity.Category;
import com.ayush.zepto.catalog.entity.Product;
import com.ayush.zepto.catalog.repository.CategoryRepository;
import com.ayush.zepto.catalog.repository.ProductRepository;
import com.ayush.zepto.common.error.BadRequestException;
import com.ayush.zepto.inventory.service.InventoryService;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private InventoryService inventoryService;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, categoryRepository, inventoryService);
    }

    @Test
    void rejectsPageSizesOutsideTheAllowedRangeBeforeQuerying() {
        assertThrows(BadRequestException.class, () -> productService.list(null, null, 0, 51, null));

        verifyNoInteractions(productRepository, categoryRepository, inventoryService);
    }

    @Test
    void rejectsSortFieldsOutsideTheAllowlist() {
        assertThrows(BadRequestException.class, () -> productService.list(null, null, 0, 20, "sku,asc"));

        verifyNoInteractions(productRepository, categoryRepository, inventoryService);
    }

    @Test
    void appliesRequestedSortAndStableIdTieBreaker() {
        when(productRepository.findAll(ArgumentMatchers.<Specification<Product>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        PageResponse<ProductResponse> response = productService.list("banana", 4L, 0, 20, "price,desc");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(
                ArgumentMatchers.<Specification<Product>>any(), pageableCaptor.capture());
        Sort sort = pageableCaptor.getValue().getSort();
        assertEquals("price", sort.toList().get(0).getProperty());
        assertEquals(Sort.Direction.DESC, sort.toList().get(0).getDirection());
        assertEquals("id", sort.toList().get(1).getProperty());
        assertEquals(0, response.page());
        assertEquals(20, response.size());
    }

    @Test
    void initializesZeroStockWhenCreatingAProduct() {
        Category category = new Category("Fruit", "fruit");
        when(categoryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(category));
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(call -> call.getArgument(0));

        productService.create(new ProductRequest("FRUIT-1", "Banana", null, 1L, new BigDecimal("2.50")));

        verify(inventoryService).initialize(any(Product.class));
    }
}
