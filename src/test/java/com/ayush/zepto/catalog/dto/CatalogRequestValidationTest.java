package com.ayush.zepto.catalog.dto;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CatalogRequestValidationTest {

    @Test
    void rejectsBlankNameInvalidSkuNonPositiveCategoryAndOverPrecisePrice() {
        ProductRequest request = new ProductRequest("not a sku", " ", null, 0L, new BigDecimal("0.001"));

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<String> invalidFields = validator.validate(request).stream()
                    .map(violation -> violation.getPropertyPath().toString())
                    .collect(Collectors.toSet());

            assertTrue(invalidFields.contains("sku"));
            assertTrue(invalidFields.contains("name"));
            assertTrue(invalidFields.contains("categoryId"));
            assertTrue(invalidFields.contains("price"));
        }
    }
}
