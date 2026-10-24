package com.redxela.api.features.customer.controller;

import com.redxela.api.features.customer.dto.CreateCustomerRequest;
import com.redxela.api.features.customer.model.*;
import com.redxela.api.features.customer.service.CustomerService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// HTTP únicamente valida y delega; las cuentas son instantáneas inmutables
@RestController
@RequestMapping("/api")
public class CustomerController {
    private final CustomerService customers;
    public CustomerController(CustomerService customers) { this.customers = customers; }
    @PostMapping("/customers")
    public ResponseEntity<Customer> create(@Valid @RequestBody CreateCustomerRequest request) {
        var customer = customers.create(request.name(), request.serviceLevel());
        return ResponseEntity.created(URI.create("/api/customers/" + customer.id())).body(customer);
    }
    @GetMapping("/customers") public List<Customer> list(@RequestParam(required = false) Integer limit) { return limit == null ? customers.list() : customers.recent(limit); }
    @GetMapping("/customers/{id}") public Customer get(@PathVariable UUID id) { return customers.get(id); }
    @GetMapping("/service-levels") public List<ServiceLevelResponse> levels() {
        return Arrays.stream(ServiceLevel.values()).map(level -> new ServiceLevelResponse(
                level.name(), level.level(), level.displayName(), level.targetWaitMinutes())).toList();
    }
    public record ServiceLevelResponse(String code, int level, String name, int targetWaitSimulatedMinutes) {}
}
