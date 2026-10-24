package com.redxela.api.features.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

public record CreateOrderRequest(@NotNull UUID customerId,
                                @NotEmpty List<@NotNull @Valid Item> lines) {
    public record Item(@NotBlank String productId, @NotNull @Positive Integer quantity) {}
}
