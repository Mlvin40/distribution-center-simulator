package com.redxela.api.features.storage.controller;

import com.redxela.api.features.storage.model.StorageLayout;
import com.redxela.api.features.storage.service.StorageLayoutService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/storage/layout")
public class StorageLayoutController {
    private final StorageLayoutService storage;
    public StorageLayoutController(StorageLayoutService storage) { this.storage = storage; }
    @GetMapping public StorageLayout get() { return storage.get(); }
}
