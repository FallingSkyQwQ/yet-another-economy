package com.yae.api.core.config;

import com.yae.api.core.ServiceConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Represents a configuration file with thread-safe access and modification.
 * Wraps the configuration data and provides methods for accessing and updating values.
 */
public final class ConfigFile {
    
    private final String fileName;
    private final File file;
    private final ReadWriteLock lock;
    private Map<String, Object> data;
    private ServiceConfig cachedServiceConfig;
    
    public ConfigFile(@NotNull String fileName, @NotNull Map<String, Object> data, @NotNull File file) {
        this.fileName = Objects.requireNonNull(fileName, "fileName cannot be null");
        this.data = Collections.unmodifiableMap(new HashMap<>(Objects.requireNonNull(data, "data cannot be null")));
        this.file = Objects.requireNonNull(file, "file cannot be null");
        this.lock = new ReentrantReadWriteLock();
        this.cachedServiceConfig = ServiceConfig.from(this.data);
    }
    
    /**
     * Get the name of this configuration file
     * @return the file name
     */
    @NotNull
    public String getFileName() {
        return fileName;
    }
    
    /**
     * Get the file object for this configuration
     * @return the file object
     */
    @NotNull
    public File getFile() {
        return file;
    }
    
    /**
     * Get the configuration data as a map
     * @return unmodifiable copy of the configuration data
     */
    @NotNull
    public Map<String, Object> getData() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableMap(new HashMap<>(data));
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get the configuration as a ServiceConfig object
     * @return a ServiceConfig wrapping this configuration's data
     */
    @NotNull
    public ServiceConfig asServiceConfig() {
        lock.readLock().lock();
        try {
            return cachedServiceConfig;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get a string value from the configuration
     * @param key the key to look up
     * @return the string value, or null if not found
     */
    @Nullable
    public String getString(@NotNull String key) {
        return asServiceConfig().getString(key);
    }
    
    /**
     * Get a string value from the configuration with default
     * @param key the key to look up
     * @param defaultValue the default value if key not found
     * @return the string value, or default value
     */
    @NotNull
    public String getString(@NotNull String key, @NotNull String defaultValue) {
        return asServiceConfig().getString(key, defaultValue);
    }
    
    /**
     * Get an integer value from the configuration
     * @param key the key to look up
     * @return the integer value, or null if not found or invalid
     */
    @Nullable
    public Integer getInt(@NotNull String key) {
        return asServiceConfig().getInt(key);
    }
    
    /**
     * Get an integer value from the configuration with default
     * @param key the key to look up
     * @param defaultValue the default value if key not found
     * @return the integer value, or default value
     */
    public int getInt(@NotNull String key, int defaultValue) {
        return asServiceConfig().getInt(key, defaultValue);
    }
    
    /**
     * Get a double value from the configuration
     * @param key the key to look up
     * @return the double value, or null if not found or invalid
     */
    @Nullable
    public Double getDouble(@NotNull String key) {
        return asServiceConfig().getDouble(key);
    }
    
    /**
     * Get a double value from the configuration with default
     * @param key the key to look up
     * @param defaultValue the default value if key not found
     * @return the double value, or default value
     */
    public double getDouble(@NotNull String key, double defaultValue) {
        return asServiceConfig().getDouble(key, defaultValue);
    }
    
    /**
     * Get a boolean value from the configuration
     * @param key the key to look up
     * @return the boolean value, or null if not found or invalid
     */
    @Nullable
    public Boolean getBoolean(@NotNull String key) {
        return asServiceConfig().getBoolean(key);
    }
    
    /**
     * Get a boolean value from the configuration with default
     * @param key the key to look up
     * @param defaultValue the default value if key not found
     * @return the boolean value, or default value
     */
    public boolean getBoolean(@NotNull String key, boolean defaultValue) {
        return asServiceConfig().getBoolean(key, defaultValue);
    }
    
    /**
     * Get a long value from the configuration
     * @param key the key to look up
     * @return the long value, or null if not found or invalid
     */
    @Nullable
    public Long getLong(@NotNull String key) {
        return asServiceConfig().getLong(key);
    }
    
    /**
     * Get a long value from the configuration with default
     * @param key the key to look up
     * @param defaultValue the default value if key not found
     * @return the long value, or default value
     */
    public long getLong(@NotNull String key, long defaultValue) {
        return asServiceConfig().getLong(key, defaultValue);
    }
    
    /**
     * Get a list of integers from the configuration
     * @param key the key to look up
     * @param defaultValue the default value if key not found
     * @return the integer list, or default value
     */
    @NotNull
    public List<Integer> getIntegerList(@NotNull String key, @NotNull List<Integer> defaultValue) {
        return asServiceConfig().getIntegerList(key, defaultValue);
    }
    
    /**
     * Get a nested configuration section
     * @param key the key to the section
     * @return a new ConfigFile containing the nested section, or null if not found
     */
    @Nullable
    public ConfigFile getSection(@NotNull String key) {
        lock.readLock().lock();
        try {
            Object value = data.get(key);
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> sectionData = (Map<String, Object>) value;
                return new ConfigFile(fileName + "/" + key, sectionData, file);
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get a list value from the configuration
     * @param key the key to look up
     * @return the list value, or null if not found or not a list
     */
    @Nullable
    public List<?> getList(@NotNull String key) {
        lock.readLock().lock();
        try {
            Object value = data.get(key);
            if (value instanceof List) {
                return Collections.unmodifiableList(new ArrayList<>((List<?>) value));
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Check if the configuration contains a key
     * @param key the key to check
     * @return true if the key exists in the configuration
     */
    public boolean contains(@NotNull String key) {
        return asServiceConfig().contains(key);
    }
    
    /**
     * Set a value in the configuration (modifies the in-memory copy only)
     * @param key the key to set
     * @param value the value to set
     */
    public void set(@NotNull String key, @Nullable Object value) {
        lock.writeLock().lock();
        try {
            Map<String, Object> newData = new HashMap<>(this.data);
            if (value == null) {
                newData.remove(key);
            } else {
                newData.put(key, value);
            }
            this.data = Collections.unmodifiableMap(newData);
            this.cachedServiceConfig = ServiceConfig.from(this.data);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Update the configuration data (used during reload)
     * @param newData the new configuration data
     */
    public void updateData(@NotNull Map<String, Object> newData) {
        Objects.requireNonNull(newData, "newData cannot be null");
        
        lock.writeLock().lock();
        try {
            this.data = Collections.unmodifiableMap(new HashMap<>(newData));
            this.cachedServiceConfig = ServiceConfig.from(this.data);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get all keys in this configuration
     * @return unmodifiable set of configuration keys
     */
    @NotNull
    public Set<String> getKeys() {
        return asServiceConfig().getKeys();
    }
    
    /**
     * Create a new ConfigFile with updated values
     * @param updates map of updates to apply
     * @return a new ConfigFile with updated values
     */
    @NotNull
    public ConfigFile withUpdates(@NotNull Map<String, Object> updates) {
        lock.readLock().lock();
        try {
            Map<String, Object> newData = new HashMap<>(this.data);
            newData.putAll(updates);
            return new ConfigFile(fileName, newData, file);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Create a new ConfigFile with an updated value
     * @param key the key to update
     * @param value the value to set
     * @return a new ConfigFile with the updated value
     */
    @NotNull
    public ConfigFile with(@NotNull String key, @Nullable Object value) {
        lock.readLock().lock();
        try {
            Map<String, Object> newData = new HashMap<>(this.data);
            if (value == null) {
                newData.remove(key);
            } else {
                newData.put(key, value);
            }
            return new ConfigFile(fileName, newData, file);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        ConfigFile that = (ConfigFile) o;
        return Objects.equals(fileName, that.fileName) &&
               Objects.equals(data, that.data);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(fileName, data);
    }
    
    @Override
    public String toString() {
        return "ConfigFile{" +
               "fileName='" + fileName + '\'' +
               ", keys=" + data.size() +
               '}';
    }
    
    // Custom ArrayList implementation for generic type safety
    private static class ArrayList<E> extends java.util.ArrayList<E> {
        public ArrayList() {
            super();
        }
        
        public ArrayList(Collection<? extends E> c) {
            super(c);
        }
    }
}
