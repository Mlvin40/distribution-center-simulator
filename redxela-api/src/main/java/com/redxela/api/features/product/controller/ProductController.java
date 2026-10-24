package com.redxela.api.features.product.controller;

import com.redxela.api.features.product.model.*;
import com.redxela.api.features.product.service.ProductCatalog;
import com.redxela.api.features.resource.model.ResourceType;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ProductController {
    private final ProductCatalog catalog;
    public ProductController(ProductCatalog catalog) { this.catalog = catalog; }
    @GetMapping("/products") public List<Product> list() { return catalog.list(); }
    @GetMapping("/products/{id}") public Product get(@PathVariable String id) { return catalog.get(id); }
    @GetMapping("/merchandise-types") public List<MerchandiseResponse> types() {
        return Arrays.stream(MerchandiseType.values()).map(type -> new MerchandiseResponse(type.name(),
                type.displayName(), type.description(), type.requiredResources())).toList();
    }
    public record MerchandiseResponse(String code, String name, String description, Set<ResourceType> requiredResources) {}
}
