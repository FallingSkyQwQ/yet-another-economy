package com.yae.api.core.event;

import com.yae.api.core.Service;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Collections;
import java.util.HashMap;
import java.util.Objects;

/**
 * Base event class for economy-related events.
 * Contains common functionality for balance changes, transfers, etc.
 */
public class EconomyEvent extends YAEEvent {
    
    private final Map<String, Object> eventData;
    
    public EconomyEvent(@NotNull String eventType, @NotNull Service service, @NotNull String description, @NotNull Map<String, Object> data) {
        super(eventType, service != null ? service.getName() : "unknown", description);
        this.eventData = Collections.unmodifiableMap(new HashMap<>(Objects.requireNonNull(data, "data cannot be null")));
    }
    
    public EconomyEvent(@NotNull String eventType, @NotNull Service service, @NotNull String description) {
        this(eventType, service, description, new HashMap<>());
    }
    
    /**
     * Get event data associated with this event
     * @return unmodifiable map of event data
     */
    @NotNull
    public Map<String, Object> getEventData() {
        return eventData;
    }
    
    /**
     * Get a specific data value
     * @param key the data key
     * @return the data value, or null if not found
     */
    public Object getData(@NotNull String key) {
        Objects.requireNonNull(key, "key cannot be null");
        return eventData.get(key);
    }
    
    /**
     * Get a specific data value with type casting
     * @param key the data key
     * @param clazz the expected class type
     * @param <T> the type
     * @return the data value, or null if not found or type mismatch
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T getData(@NotNull String key, @NotNull Class<T> clazz) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(clazz, "clazz cannot be null");
        
        Object value = eventData.get(key);
        if (value != null && clazz.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * Check if event data contains a specific key
     * @param key the data key
     * @return true if the key exists
     */
    public boolean hasData(@NotNull String key) {
        Objects.requireNonNull(key, "key cannot be null");
        return eventData.containsKey(key);
    }
    
    @Override
    @NotNull
    public EventSeverity getSeverity() {
        switch (getEventType()) {
            case "transfer":
            case "balance-change":
                return EventSeverity.INFO;
            case "payment-complete":
            case "transaction-success":
                return EventSeverity.INFO;
            case "insufficient-funds":
            case "transaction-failed":
                return EventSeverity.WARNING;
            case "database-error":
            case "service-error":
                return EventSeverity.ERROR;
            default:
                return EventSeverity.INFO;
        }
    }
}
