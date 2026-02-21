package com.gedavocat.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service de gestion du mode maintenance.
 * L'état initial peut être configuré via la propriété `maintenance.enabled` (défaut: false).
 */
@Service
public class MaintenanceService {

    private final AtomicBoolean maintenanceMode;

    public MaintenanceService(@Value("${maintenance.enabled:false}") boolean initialState) {
        this.maintenanceMode = new AtomicBoolean(initialState);
    }

    public boolean isMaintenanceEnabled() {
        return maintenanceMode.get();
    }

    public void setMaintenanceEnabled(boolean enabled) {
        maintenanceMode.set(enabled);
    }

    public synchronized boolean toggle() {
        boolean newValue = !maintenanceMode.get();
        maintenanceMode.set(newValue);
        return newValue;
    }
}
