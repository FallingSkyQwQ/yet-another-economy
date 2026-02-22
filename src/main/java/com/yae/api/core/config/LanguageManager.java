package com.yae.api.core.config;

import com.yae.api.core.YAECore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Language manager responsible for handling multi-language text and translations.
 * Loads language files and provides methods for retrieving localized messages.
 */
public final class LanguageManager {
    
    private static final String LANG_FILE = "lang.yml";
    private static final String DEFAULT_LANGUAGE = "zh_cn";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)\\}");
    
    private final YAECore plugin;
    private final Logger logger;
    private final ConfigManager configManager;
    private final Map<String, String> translations;
    private final ReadWriteLock lock;
    private ConfigFile languageConfig;
    private String currentLanguage;
    
    public LanguageManager(@NotNull YAECore plugin, @NotNull ConfigManager configManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.logger = plugin.getLogger();
        this.configManager = Objects.requireNonNull(configManager, "configManager cannot be null");
        this.translations = new ConcurrentHashMap<>();
        this.lock = new ReentrantReadWriteLock();
        this.currentLanguage = DEFAULT_LANGUAGE;
    }
    
    /**
     * Initialize the language manager
     * @throws ConfigManager.ConfigLoadException if the language file cannot be loaded
     */
    public void initialize() throws ConfigManager.ConfigLoadException {
        logger.log(Level.INFO, "Initializing language manager...");
        
        // Load language configuration
        languageConfig = configManager.loadConfig(LANG_FILE);
        
        // Determine current language
        ConfigFile mainConfig = configManager.getConfig("config.yml");
        if (mainConfig != null) {
            String language = mainConfig.getString("messages.language", DEFAULT_LANGUAGE);
            setLanguage(language);
        } else {
            setLanguage(DEFAULT_LANGUAGE);
        }
        
        logger.log(Level.INFO, "Language manager initialized with language: {0}", currentLanguage);
    }
    
    /**
     * Reload the language configuration
     * @throws ConfigManager.ConfigLoadException if the language file cannot be reloaded
     */
    public void reload() throws ConfigManager.ConfigLoadException {
        logger.log(Level.INFO, "Reloading language manager...");
        
        configManager.reloadConfig(LANG_FILE);
        languageConfig = configManager.getConfig(LANG_FILE);
        
        if (languageConfig != null) {
            String language = languageConfig.getString("language-info.code", DEFAULT_LANGUAGE);
            setLanguage(language);
        }
        
        logger.log(Level.INFO, "Language manager reloaded");
    }
    
    /**
     * Set the current language
     * @param language the language code (e.g., "zh_cn", "en_us")
     */
    public void setLanguage(@NotNull String language) {
        Objects.requireNonNull(language, "language cannot be null");
        
        lock.writeLock().lock();
        try {
            this.currentLanguage = language;
            loadTranslations();
            logger.log(Level.INFO, "Language set to: {0}", language);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get the current language code
     * @return the current language code
     */
    @NotNull
    public String getCurrentLanguage() {
        lock.readLock().lock();
        try {
            return currentLanguage;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get a translated message with optional placeholders
     * @param key the translation key
     * @return the translated message, or the key if translation not found
     */
    @NotNull
    public String get(@NotNull String key) {
        return get(key, Collections.emptyMap());
    }
    
    /**
     * Get a translated message with placeholders
     * @param key the translation key
     * @param placeholders map of placeholder replacements
     * @return the translated message, or the key if translation not found
     */
    @NotNull
    public String get(@NotNull String key, @NotNull Map<String, Object> placeholders) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(placeholders, "placeholders cannot be null");
        
        lock.readLock().lock();
        try {
            String message = translations.get(key);
            if (message == null) {
                logger.log(Level.WARNING, "Translation not found for key: {0}", key);
                return key;
            }
            
            // Replace placeholders
            if (!placeholders.isEmpty()) {
                message = replacePlaceholders(message, placeholders);
            }
            
            return message;
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get a translated message with a single placeholder
     * @param key the translation key
     * @param placeholder the placeholder name
     * @param value the placeholder value
     * @return the translated message, or the key if translation not found
     */
    @NotNull
    public String get(@NotNull String key, @NotNull String placeholder, @Nullable Object value) {
        return get(key, Collections.singletonMap(placeholder, value));
    }
    
    /**
     * Get a translated message with multiple placeholders (varargs version)
     * @param key the translation key
     * @param placeholders alternating placeholder names and values
     * @return the translated message, or the key if translation not found
     */
    @NotNull
    public String get(@NotNull String key, @NotNull Object... placeholders) {
        Objects.requireNonNull(placeholders, "placeholders cannot be null");
        
        if (placeholders.length % 2 != 0) {
            throw new IllegalArgumentException("Placeholders must be provided in name-value pairs");
        }
        
        Map<String, Object> placeholderMap = new HashMap<>();
        for (int i = 0; i < placeholders.length; i += 2) {
            String placeholderName = String.valueOf(placeholders[i]);
            Object placeholderValue = placeholders[i + 1];
            placeholderMap.put(placeholderName, placeholderValue);
        }
        
        return get(key, placeholderMap);
    }
    
    /**
     * Get a translated message with formatted number
     * @param key the translation key
     * @param placeholders map of placeholder replacements
     * @return the translated message, or the key if translation not found
     */
    @NotNull
    public String getFormatted(@NotNull String key, @NotNull Map<String, Object> placeholders) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(placeholders, "placeholders cannot be null");
        
        // Format number values
        Map<String, Object> formattedPlaceholders = new HashMap<>();
        for (Map.Entry<String, Object> entry : placeholders.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Number) {
                formattedPlaceholders.put(entry.getKey(), formatNumber((Number) value));
            } else {
                formattedPlaceholders.put(entry.getKey(), value);
            }
        }
        
        return get(key, formattedPlaceholders);
    }
    
    /**
     * Get a translated message with formatted currency amount
     * @param key the translation key
     * @param amount the currency amount
     * @param currency the currency name
     * @param placeholders additional placeholders
     * @return the translated message, or the key if translation not found
     */
    @NotNull
    public String getCurrency(@NotNull String key, double amount, @NotNull String currency, @NotNull Map<String, Object> placeholders) {
        Objects.requireNonNull(currency, "currency cannot be null");
        Objects.requireNonNull(placeholders, "placeholders cannot be null");
        
        Map<String, Object> allPlaceholders = new HashMap<>(placeholders);
        allPlaceholders.put("amount", formatCurrency(amount));
        allPlaceholders.put("currency", currency);
        
        return get(key, allPlaceholders);
    }
    
    /**
     * Get a translated message with formatted currency amount
     * @param key the translation key
     * @param amount the currency amount
     * @param currency the currency name
     * @return the translated message, or the key if translation not found
     */
    @NotNull
    public String getCurrency(@NotNull String key, double amount, @NotNull String currency) {
        Map<String, Object> placeholders = new HashMap<>();
        placeholders.put("amount", formatCurrency(amount));
        placeholders.put("currency", currency);
        
        return get(key, placeholders);
    }
    
    /**
     * Check if a translation key exists
     * @param key the translation key
     * @return true if the translation exists
     */
    public boolean hasTranslation(@NotNull String key) {
        Objects.requireNonNull(key, "key cannot be null");
        
        lock.readLock().lock();
        try {
            return translations.containsKey(key);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get all available translation keys
     * @return set of translation keys
     */
    @NotNull
    public Set<String> getTranslationKeys() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableSet(new HashSet<>(translations.keySet()));
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get language information
     * @return language information map
     */
    @NotNull
    public Map<String, String> getLanguageInfo() {
        Map<String, String> info = new HashMap<>();
        
        lock.readLock().lock();
        try {
            if (languageConfig != null) {
                info.put("name", languageConfig.getString("language-info.name", "Unknown"));
                info.put("code", currentLanguage);
                info.put("author", languageConfig.getString("language-info.author", "Unknown"));
                info.put("version", languageConfig.getString("language-info.version", "1.0.0"));
            }
        } finally {
            lock.readLock().unlock();
        }
        
        return info;
    }
    
    // Private methods
    
    private void loadTranslations() {
        if (languageConfig == null) {
            logger.log(Level.WARNING, "Language configuration not loaded");
            return;
        }
        
        translations.clear();
        
        // Load all translation keys and values
        Map<String, Object> data = languageConfig.getData();
        flattenTranslations("", data, translations);
        
        logger.log(Level.INFO, "Loaded {0} translation entries", translations.size());
    }
    
    @SuppressWarnings("unchecked")
    private void flattenTranslations(String prefix, Map<String, Object> data, Map<String, String> translations) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            
            if (value instanceof String) {
                translations.put(key, (String) value);
            } else if (value instanceof Map) {
                flattenTranslations(key, (Map<String, Object>) value, translations);
            }
        }
    }
    
    @NotNull
    private String replacePlaceholders(@NotNull String message, @NotNull Map<String, Object> placeholders) {
        String result = message;
        
        for (Map.Entry<String, Object> entry : placeholders.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String value = String.valueOf(entry.getValue());
            result = result.replace(placeholder, value);
        }
        
        return result;
    }
    
    @NotNull
    private String formatNumber(@NotNull Number number) {
        // Format numbers based on locale settings (currently using Chinese format)
        return String.format("%,.2f", number.doubleValue());
    }
    
    @NotNull
    private String formatCurrency(double amount) {
        // Get currency formatting from translations
        String format = languageConfig != null ? languageConfig.getString("number.currency-format", "{symbol}{amount}") : "{symbol}{amount}";
        
        // For now, use a simple format - this could be enhanced with currency symbol from config
        String symbol = languageConfig != null ? languageConfig.getString("common.currency-symbol", "§6💰") : "§6💰";
        String formattedAmount = formatNumber(amount);
        
        return format.replace("{symbol}", symbol).replace("{amount}", formattedAmount);
    }
}
