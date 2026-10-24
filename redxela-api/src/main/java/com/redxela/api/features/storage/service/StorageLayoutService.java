package com.redxela.api.features.storage.service;

import com.redxela.api.features.simulation.config.SimulationProperties;
import com.redxela.api.features.storage.model.StorageLayout;
import com.redxela.api.features.storage.model.StorageLayout.Location;
import java.util.ArrayList;
import org.springframework.stereotype.Service;

@Service
public class StorageLayoutService {
    private final StorageLayout layout;
    public StorageLayoutService(SimulationProperties settings) {
        var locations = new ArrayList<Location>();
        for (int aisle = 1; aisle <= settings.aisles(); aisle++)
            for (int level = 1; level <= settings.levels(); level++)
                for (int position = 1; position <= settings.positionsPerLevel(); position++)
                    locations.add(new Location("A%02d-L%02d-P%02d".formatted(aisle, level, position),
                            aisle, level, position, settings.locationCapacity()));
        layout = new StorageLayout(settings.storageCapacity(), settings.locationCapacity(), locations);
    }
    public StorageLayout get() { return layout; }
}
