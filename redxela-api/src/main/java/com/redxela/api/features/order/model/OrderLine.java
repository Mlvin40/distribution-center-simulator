package com.redxela.api.features.order.model;

import com.redxela.api.features.product.model.Product;

public record OrderLine(Product product, int quantity) {}
