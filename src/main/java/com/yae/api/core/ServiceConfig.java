package com.yae.api.core;

import java.util.Collections;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents configuration for a service.
 * Immutable configuration container with type-safe getters.
 */
public final class ServiceConfig {
    private final Map<String, Object> config;
    
    public ServiceConfig() {
        this(new HashMap<>());
    }
    
    public ServiceConfig(Map<String, Object> config) {
        this.config = Collections.unmodifiableMap(new HashMap<>(config));
    }
    
    /**
     * Gets a string value from the configuration
     * @param key the configuration key
     * @return the string value, or null if not found
     */
    @Nullable
    public String getString(@NotNull String key) {
        Object value = config.get(key);
        return value != null ? value.toString() : null;
    }
    
    /**
     * Gets a string value from the configuration with default
     * @param key the configuration key
     * @param defaultValue the default value if key not found
     * @return the string value, or default value
     */
    @NotNull
    public String getString(@NotNull String key, @NotNull String defaultValue) {
        String value = getString(key);
        return value != null ? value : defaultValue;
    }
    
    /**
     * Gets an integer value from the configuration
     * @param key the configuration key
     * @return the integer value, or null if not found or not valid
     */
    @Nullable
    public Integer getInt(@NotNull String key) {
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
    
    /**
     * Gets an integer value from the configuration with default
     * @param key the configuration key
     * @param defaultValue the default value if key not found
     * @return the integer value, or default value
     */
    public int getInt(@NotNull String key, int defaultValue) {
        Integer value = getInt(key);
        return value != null ? value : defaultValue;
    }
    
    /**
     * Gets a double value from the configuration
     * @param key the configuration key
     * @return the double value, or null if not found or not valid
     */
    @Nullable
    public Double getDouble(@NotNull String key) {
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
    
    /**
     * Gets a double value from the configuration with default
     * @param key the configuration key
     * @param defaultValue the default value if key not found
     * @return the double value, or default value
     */
    public double getDouble(@NotNull String key, double defaultValue) {
        Double value = getDouble(key);
        return value != null ? value : defaultValue;
    }
    
    /**
     * Gets a boolean value from the configuration
     * @param key the configuration key
     * @return the boolean value, or null if not found or not valid
     */
    @Nullable
    public Boolean getBoolean(@NotNull String key) {
        Object value = config.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            String str = (String) value;
            if (str.equalsIgnoreCase("true") || str.equalsIgnoreCase("false")) {
                return Boolean.parseBoolean(str);
            }
        }
        return null;
    }
    
    /**
     * Gets a boolean value from the configuration with default
     * @param key the configuration key
     * @param defaultValue the default value if key not found
     * @return the boolean value, or default value
     */
    public boolean getBoolean(@NotNull String key, boolean defaultValue) {
        Boolean value = getBoolean(key);
        return value != null ? value : defaultValue;
    }
    
    /**
     * Gets a raw object value from the configuration
     * @param key the configuration key
     * @return the object value, or null if not found
     */
    @Nullable
    public Object get(@NotNull String key) {
        return config.get(key);
    }
    
    /**
     * Checks if the configuration contains a key
     * @param key the configuration key
     * @return true if the key exists
     */
    public boolean contains(@NotNull String key) {
        return config.containsKey(key);
    }
    
    /**
     * Gets all configuration keys
     * @return unmodifiable set of configuration keys
     */
    @NotNull
    public java.util.Set<String> getKeys() {
        return config.keySet();
    }
    
    /**
     * Gets all configuration entries
     * @return unmodifiable map of configuration entries
     */
    @NotNull
    public Map<String, Object> getAll() {
        return new HashMap<>(config);
    }
    
    /**
     * Gets a long value from the configuration
     * @param key the configuration key
     * @return the long value, or null if not found or not valid
     */
    @Nullable
    public Long getLong(@NotNull String key) {
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
    
    /**
     * Gets a long value from the configuration with default
     * @param key the configuration key
     * @param defaultValue the default value if key not found
     * @return the long value, or default value
     */
    public long getLong(@NotNull String key, long defaultValue) {
        Long value = getLong(key);
        return value != null ? value : defaultValue;
    }
    
    /**
     * Gets a list of integers from the configuration
     * @param key the configuration key
     * @param defaultValue the default value if key not found
     * @return the integer list, or default value
     */
    @NotNull
    public List<Integer> getIntegerList(@NotNull String key, @NotNull List<Integer> defaultValue) {
        Object value = config.get(key);
        if (value instanceof List) {
            List<?> rawList = (List<?>) value;
            List<Integer> result = new ArrayList<>();
            for (Object item : rawList) {
                if (item instanceof Integer) {
                    result.add((Integer) item);
                } else if (item instanceof Number) {
                    result.add(((Number) item).intValue());
                } else if (item instanceof String) {
                    try {
                        result.add(Integer.parseInt((String) item));
                    } catch (NumberFormatException e) {
                        // Skip invalid items
                    }
                }
            }
            return result.isEmpty() ? defaultValue : result;
        }
        return defaultValue;
    }
    
    /**
     * Creates a new ServiceConfig with an additional key-value pair
     * @param key the key to add
     * @param value the value to add
     * @return a new ServiceConfig with the added key-value pair
        return config;
    }
    
    /**
     * Creates a new ServiceConfig with additional or updated values
     * @param updates map of updates
     * @return a new ServiceConfig with updated values
     */
    @NotNull
    public ServiceConfig withUpdates(@NotNull Map<String, Object> updates) {
        Map<String, Object> newConfig = new HashMap<>(this.config);
        newConfig.putAll(updates);
        return new ServiceConfig(newConfig);
    }
    
    /**
     * Creates a new ServiceConfig with additional or updated value
     * @param key the configuration key
     * @param value the configuration value
     * @return a new ServiceConfig with updated value
     */
    @NotNull
    public ServiceConfig with(@NotNull String key, @Nullable Object value) {
        Map<String, Object> newConfig = new HashMap<>(this.config);
        newConfig.put(key, value);
        return new ServiceConfig(newConfig);
    }
    
    /**
     * Creates a ServiceConfig from a map
     * @param config the configuration map
     * @return a new ServiceConfig
     */
    @NotNull
    public static ServiceConfig from(@NotNull Map<String, Object> config) {
        return new ServiceConfig(config);
    }
    
    /**
     * Creates an empty ServiceConfig
     * @return an empty ServiceConfig
     */
    @NotNull
    public static ServiceConfig empty() {
        return new ServiceConfig();
    }
}
