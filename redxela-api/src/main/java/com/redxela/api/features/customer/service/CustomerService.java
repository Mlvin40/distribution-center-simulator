package com.redxela.api.features.customer.service;

import com.redxela.api.features.customer.model.*;
import com.redxela.api.shared.exception.NotFoundException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

// No se exponen mapas mutables y el UUID se publica una sola vez con putIfAbsent
@Service
public class CustomerService {
    private final Map<UUID, Customer> customers = new ConcurrentHashMap<>();
    public Customer create(String name, int level) {
        if (name == null || name.isBlank() || name.strip().length() > 120)
            throw new IllegalArgumentException("El nombre debe contener entre 1 y 120 caracteres");
        var service = ServiceLevel.fromLevel(level);
        Customer customer;
        do { customer = new Customer(UUID.randomUUID(), name.strip(), service, Instant.now()); }
        while (customers.putIfAbsent(customer.id(), customer) != null);
        return customer;
    }
    public Customer get(UUID id) {
        var customer = customers.get(id);
        if (customer == null) throw new NotFoundException("Cliente no encontrado");
        return customer;
    }
    public List<Customer> recent(int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("El límite debe estar entre 1 y 500");
        return customers.values().stream().sorted(Comparator.comparing(Customer::createdAt).reversed()
                .thenComparing(Customer::id)).limit(limit).toList();
    }
    public List<Customer> list() {
        return customers.values().stream().sorted(Comparator.comparing(Customer::createdAt).thenComparing(Customer::id)).toList();
    }
}
