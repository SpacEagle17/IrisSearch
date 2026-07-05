package com.spaceagle17.irissearch.config;

/**
 * Represents a configuration entry with its metadata.
 * Used to define the schema for configuration options.
 */
public class ConfigEntry<T> {
    private final String category;
    private final String key;
    private final T defaultValue;
    private final String description;
    private final ConfigType type;
    private final String[] allowedValues;

    public ConfigEntry(String category, String key, T defaultValue, String description) {
        this(category, key, defaultValue, description, null);
    }

    public ConfigEntry(String category, String key, T defaultValue, String description, String[] allowedValues) {
        this.category = category;
        this.key = key;
        this.defaultValue = defaultValue;
        this.description = description;
        this.type = determineType(defaultValue);
        this.allowedValues = allowedValues;
    }

    private ConfigType determineType(Object value) {
        if (value instanceof Boolean) return ConfigType.BOOLEAN;
        if (value instanceof Integer) return ConfigType.INTEGER;
        if (value instanceof Double) return ConfigType.DOUBLE;
        return ConfigType.STRING; // Default fallback
    }

    public String getCategory() {
        return category;
    }

    public String getKey() {
        return key;
    }

    public T getDefaultValue() {
        return defaultValue;
    }

    public String getDescription() {
        return description;
    }

    public ConfigType getType() {
        return type;
    }

    public String[] getAllowedValues() {
        return allowedValues;
    }

    public String getAsString(Object value) {
        if (value != null) return value.toString();
        if (defaultValue != null) return defaultValue.toString();
        return "";
    }

    public Boolean getAsBoolean(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        if (defaultValue instanceof Boolean) return (Boolean) defaultValue;
        return false;
    }

    public Integer getAsInteger(Object value) {
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Long) return ((Long) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                if (defaultValue instanceof Integer) return (Integer) defaultValue;
                return 0;
            }
        }
        if (defaultValue instanceof Integer) return (Integer) defaultValue;
        return 0;
    }

    public Double getAsDouble(Object value) {
        if (value instanceof Double) return (Double) value;
        if (value instanceof Float) return ((Float) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                if (defaultValue instanceof Double) return (Double) defaultValue;
                return 0.0;
            }
        }
        if (defaultValue instanceof Double) return (Double) defaultValue;
        return 0.0;
    }

    public enum ConfigType {
        BOOLEAN,
        INTEGER,
        DOUBLE,
        STRING
    }
}
