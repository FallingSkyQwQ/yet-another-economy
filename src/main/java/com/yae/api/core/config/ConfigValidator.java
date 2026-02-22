package com.yae.api.core.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Utility class for configuration validation.
 * Provides common validation methods for configuration values.
 */
public final class ConfigValidator {
    
    private static final Pattern STRING_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Pattern IP_PATTERN = Pattern.compile("^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");
    
    private ConfigValidator() {
        // Utility class, prevent instantiation
    }
    
    /**
     * Validate that a string value is not null and not empty or blank
     * @param value the value to validate
     * @param fieldName the field name for error messages
     * @throws Configuration.ConfigurationValidationException if validation fails
     */
    public static void requireNotBlank(@Nullable String value, @NotNull String fieldName) {
        Objects.requireNonNull(fieldName, "fieldName cannot be null");
        
        if (value == null || value.trim().isEmpty()) {
            throw new Configuration.ConfigurationValidationException(fieldName + " cannot be null or blank");
        }
    }
    
    /**
     * Validate that a string value matches a basic alphanumeric pattern
     * @param value the value to validate
     * @param fieldName the field name for error messages
     * @throws Configuration.ConfigurationValidationException if validation fails
     */
    public static void requireAlphanumeric(@Nullable String value, @NotNull String fieldName) {
        requireNotBlank(value, fieldName);
        
        if (!STRING_PATTERN.matcher(value).matches()) {
            throw new Configuration.ConfigurationValidationException(fieldName + " must contain only alphanumeric characters, underscores, and hyphens");
        }
    }
    
    /**
     * Validate that a numeric value is positive
     * @param value the value to validate
     * @param fieldName the field name for error messages
     * @throws Configuration.ConfigurationValidationException if validation fails
     */
    public static void requirePositive(double value, @NotNull String fieldName) {
        Objects.requireNonNull(fieldName, "fieldName cannot be null");
        
        if (value <= 0) {
            throw new Configuration.ConfigurationValidationException(fieldName + " must be positive");
        }
    }
    
    /**
     * Validate that a numeric value is positive
     * @param value the value to validate
     * @param fieldName the field name for error messages
     * @throws Configuration.ConfigurationValidationException if validation fails
     */
    public static void requirePositive(int value, @NotNull String fieldName) {
        Objects.requireNonNull(fieldName, "fieldName cannot be null");
        
        if (value <= 0) {
            throw new Configuration.ConfigurationValidationException(fieldName + " must be positive");
        }
    }
    
    /**
     * Validate that a numeric value is non-negative
     * @param value the value to validate
     * @param fieldName the field name for error messages
     * @throws Configuration.ConfigurationValidationException if validation fails
     */
    public static void requireNonNegative(double value, @NotNull String fieldName) {
        Objects.requireNonNull(fieldName, "fieldName cannot be null");
        
        if (value < 0) {
            throw new Configuration.ConfigurationValidationException(fieldName + " cannot be negative");
        }
    }
    
    /**
     * Validate that a numeric value is non-negative
     * @param value the value to validate
     * @param fieldName the field name for error messages
     * @throws Configuration.ConfigurationValidationException if validation fails
     */
    public static void requireNonNegative(int value, @NotNull String fieldName) {
        Objects.requireNonNull(fieldName, "fieldName cannot be null");
        
        if (value < 0) {
            throw new Configuration.ConfigurationValidationException(fieldName + " cannot be negative");
        }
    }
    
    /**
     * Validate that a numeric value is within a range
     * @param value the value to validate
     * @param min the minimum allowed value (inclusive)
     * @param max the maximum allowed value (inclusive)
     * @param fieldName the field name for error messages
     * @throws Configuration.ConfigurationValidationException if validation fails
     */
    public static void requireRange(double value, double min, double max, @NotNull String fieldName) {
        Objects.requireNonNull(fieldName, "fieldName cannot be null");
        
        if (value < min || value > max) {
            throw new Configuration.ConfigurationValidationException(fieldName + " must be between " + min + " and " + max);
        }
    }
    
    /**
     * Validate that a numeric value is within a range
     * @param value the value to validate
     * @param min the minimum allowed value (inclusive)
     * @param max the maximum allowed value (inclusive)
     * @param fieldName the field name for error messages
     * @throws Configuration.ConfigurationValidationException if validation fails
     */
    public static void requireRange(int value, int min, int max, @NotNull String fieldName) {
        Objects.requireNonNull(fieldName, "fieldName cannot be null");
        
        if (value < min || value > max) {
            throw new Configuration.ConfigurationValidationException(fieldName + " must be between " + min + " and " + max);
        }
    }
    
    /**
     * Validate that a string is not too long
     * @param value the value to validate
     * @param maxLength the maximum allowed length
     * @param fieldName the field name for error messages
     * @throws Configuration.ConfigurationValidationException if validation fails
     */
    public static void requireMaxLength(@Nullable String value, int maxLength, @NotNull String fieldName) {
        Objects.requireNonNull(fieldName, "fieldName cannot be null");
        
        if (value != null && value.length() > maxLength) {
            throw new Configuration.ConfigurationValidationException(fieldName + " cannot be longer than " + maxLength + " characters");
        }
    }
    
    /**
     * Validate that a port number is valid
     * @param port the port number to validate
     * @param fieldName the field name for error messages
     * @throws Configuration.ConfigurationValidationException if validation fails
     */
    public static void requireValidPort(int port, @NotNull String fieldName) {
        Objects.requireNonNull(fieldName, "fieldName cannot be null");
        
        if (port < 1 || port > 65535) {
            throw new Configuration.ConfigurationValidationException(fieldName + " must be a valid port number (1-65535)");
        }
    }
    
    /**
     * Validate that a string is a valid IPv4 address
     * @param value the value to validate
     * @param fieldName the field name for error messages
     * @throws Configuration.ConfigurationValidationException if validation fails
     */
    public static void requireValidIpAddress(@Nullable String value, @NotNull String fieldName) {
        Objects.requireNonNull(fieldName, "fieldName cannot be null");
        
        if (value == null || !IP_PATTERN.matcher(value).matches()) {
            throw new Configuration.ConfigurationValidationException(fieldName + " must be a valid IPv4 address");
        }
    }
    
    /**
     * Validate that a string is a valid email address (basic validation)
     * @param value the value to validate
     * @param fieldName the field name for error messages
     * @throws Configuration.ConfigurationValidationException if validation fails
     */
    public static void requireValidEmail(@Nullable String value, @NotNull String fieldName) {
        Objects.requireNonNull(fieldName, "fieldName cannot be null");
        
        if (value == null || !EMAIL_PATTERN.matcher(value).matches()) {
            throw new Configuration.ConfigurationValidationException(fieldName + " must be a valid email address");
        }
    }
    
    /**
     * Validate that a string matches a regex pattern
     * @param value the value to validate
     * @param pattern the regex pattern to match
     * @param fieldName the field name for error messages
     * @throws Configuration.ConfigurationValidationException if validation fails
     */
    public static void requirePatternMatch(@Nullable String value, @NotNull Pattern pattern, @NotNull String fieldName) {
        Objects.requireNonNull(pattern, "pattern cannot be null");
        Objects.requireNonNull(fieldName, "fieldName cannot be null");
        
        if (value == null || !pattern.matcher(value).matches()) {
            throw new Configuration.ConfigurationValidationException(fieldName + " must match the required pattern");
        }
    }
    
    /**
     * Custom validation method
     * @param value the value to validate
     * @param validator the custom validation function
     * @param fieldName the field name for error messages
     * @param <T> the type of value
     * @throws Configuration.ConfigurationValidationException if validation fails
     */
    public static <T> void customValidate(@Nullable T value, @NotNull Predicate<T> validator, @NotNull String fieldName) {
        Objects.requireNonNull(validator, "validator cannot be null");
        Objects.requireNonNull(fieldName, "fieldName cannot be null");
        
        if (!validator.test(value)) {
            throw new Configuration.ConfigurationValidationException(fieldName + " validation failed");
        }
    }
    
    /**
     * Validate a configuration path exists
     * @param config the configuration to check
     * @param path the configuration path
     * @param fieldName the field name for error messages
     * @throws Configuration.ConfigurationValidationException if validation fails
     */
    public static void requirePathExists(@NotNull ConfigFile config, @NotNull String path, @NotNull String fieldName) {
        Objects.requireNonNull(config, "config cannot be null");
        Objects.requireNonNull(path, "path cannot be null");
        Objects.requireNonNull(fieldName, "fieldName cannot be null");
        
        if (!config.contains(path)) {
            throw new Configuration.ConfigurationValidationException(fieldName + " configuration path not found: " + path);
        }
    }
    
    /**
     * Validate multiple conditions and collect all errors
     * @param validations the validations to perform
     * @return list of validation errors, empty list if all validations pass
     */
    @NotNull
    public static List<String> validateAll(@NotNull Validation... validations) {
        Objects.requireNonNull(validations, "validations cannot be null");
        
        List<String> errors = new ArrayList<>();
        
        for (Validation validation : validations) {
            try {
                validation.apply();
            } catch (Configuration.ConfigurationValidationException e) {
                errors.add(e.getMessage());
            }
        }
        
        return errors;
    }
    
    /**
     * Functional interface for validations
     */
    @FunctionalInterface
    public interface Validation {
        void apply() throws Configuration.ConfigurationValidationException;
    }
    
    /**
     * Builder pattern for complex validation chains
     */
    public static class ValidationChain {
        private final List<String> errors = new ArrayList<>();
        private boolean continueOnError = true;
        
        public ValidationChain continueOnError(boolean continueOnError) {
            this.continueOnError = continueOnError;
            return this;
        }
        
        public ValidationChain validate(@NotNull Validation validation) {
            try {
                validation.apply();
            } catch (Configuration.ConfigurationValidationException e) {
                errors.add(e.getMessage());
                if (!continueOnError) {
                    throw e;
                }
            }
            return this;
        }
        
        public ValidationChain requireNotBlank(@Nullable String value, @NotNull String fieldName) {
            return validate(() -> ConfigValidator.requireNotBlank(value, fieldName));
        }
        
        public ValidationChain requirePositive(double value, @NotNull String fieldName) {
            return validate(() -> ConfigValidator.requirePositive(value, fieldName));
        }
        
        public ValidationChain requirePositive(int value, @NotNull String fieldName) {
            return validate(() -> ConfigValidator.requirePositive(value, fieldName));
        }
        
        public ValidationChain requireNonNegative(double value, @NotNull String fieldName) {
            return validate(() -> ConfigValidator.requireNonNegative(value, fieldName));
        }
        
        public ValidationChain requireNonNegative(int value, @NotNull String fieldName) {
            return validate(() -> ConfigValidator.requireNonNegative(value, fieldName));
        }
        
        public ValidationChain requireRange(double value, double min, double max, @NotNull String fieldName) {
            return validate(() -> ConfigValidator.requireRange(value, min, max, fieldName));
        }
        
        public ValidationChain requireValidIpAddress(@NotNull String value, @NotNull String fieldName) {
            return validate(() -> ConfigValidator.requireValidIpAddress(value, fieldName));
        }
        
        public ValidationChain requireValidPort(int port, @NotNull String fieldName) {
            return validate(() -> ConfigValidator.requireValidPort(port, fieldName));
        }
        
        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }
        
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
        
        public void validate() throws Configuration.ConfigurationValidationException {
            if (hasErrors()) {
                throw new Configuration.ConfigurationValidationException("Configuration validation failed: " + String.join(", ", errors));
            }
        }
    }
    
    /**
     * Create a new validation chain
     * @return a new validation chain
     */
    @NotNull
    public static ValidationChain chain() {
        return new ValidationChain();
    }
}
