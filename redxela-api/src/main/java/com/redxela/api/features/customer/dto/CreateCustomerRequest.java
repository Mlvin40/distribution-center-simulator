package com.redxela.api.features.customer.dto;

import jakarta.validation.constraints.*;

public record CreateCustomerRequest(@NotBlank @Size(max = 120) String name,
                                    @NotNull @Min(1) @Max(5) Integer serviceLevel) {}
