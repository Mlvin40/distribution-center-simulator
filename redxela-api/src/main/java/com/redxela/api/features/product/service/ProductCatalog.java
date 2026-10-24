package com.redxela.api.features.product.service;

import com.redxela.api.features.product.model.*;
import com.redxela.api.shared.exception.NotFoundException;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ProductCatalog {
    private final List<Product> products = List.of(
        new Product("PRD-001", "Cuaderno", "Papelería de manejo convencional", MerchandiseType.GENERAL),
        new Product("PRD-002", "Vaso de vidrio", "Vidrio con protección contra golpes", MerchandiseType.FRAGILE),
        new Product("PRD-003", "Saco de cemento", "Carga pesada paletizada", MerchandiseType.HEAVY),
        new Product("PRD-004", "Monitor", "Equipo electrónico delicado", MerchandiseType.ELECTRONICS),
        new Product("PRD-005", "Silla", "Mobiliario de gran volumen", MerchandiseType.BULKY));
    public List<Product> list() { return products; }
    public Product get(String id) {
        return products.stream().filter(p -> p.id().equals(id)).findFirst()
                .orElseThrow(() -> new NotFoundException("Producto no encontrado: " + id));
    }
}
