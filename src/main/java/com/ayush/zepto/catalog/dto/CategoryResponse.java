package com.ayush.zepto.catalog.dto;

public record CategoryResponse(Long id, String name, String slug, boolean active) implements java.io.Serializable {
}
