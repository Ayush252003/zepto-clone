package com.ayush.zepto.catalog.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ayush.zepto.catalog.entity.Category;
import com.ayush.zepto.catalog.repository.CategoryRepository;
import com.ayush.zepto.catalog.repository.ProductRepository;
import com.ayush.zepto.common.error.ResourceConflictException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Test
    void refusesToDeactivateCategoryWithActiveProducts() {
        Category category = new Category("Fruit", "fruit");
        when(categoryRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(category));
        when(productRepository.existsByCategory_IdAndActiveTrue(7L)).thenReturn(true);
        CategoryService categoryService = new CategoryService(categoryRepository, productRepository);

        assertThrows(ResourceConflictException.class, () -> categoryService.deactivate(7L));

        verify(categoryRepository, never()).saveAndFlush(category);
    }
}
