package com.spaceagle17.irissearch.config;

import com.spaceagle17.irissearch.IrisSearch;
import com.spaceagle17.irissearch.logging.IrisSearchLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Config {
    // TOML config location
    public static final Path CONFIG_DIR = IrisSearch.configDirectory;
    private static final Path CONFIG_PATH = CONFIG_DIR.resolve("irisSearchSettings.toml");

    private static final Map<String, Object> configValues = new LinkedHashMap<>();
    private static final Map<String, ConfigEntry<?>> configSchema = new LinkedHashMap<>();
    private static final List<String> categoryOrder = new ArrayList<>();
    private static FileTime lastModified = null;
    private static boolean watcherActive = false;
    private static ScheduledExecutorService scheduler;

    private static void debugLog(String message) {
        IrisSearchLogger.debugLog("[Config] " + message);
    }

    public static void initialize() {
        loadConfig();
    }

    /**
     * Loads config values from TOML file
     */
    private static void loadConfig() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }

            if (!Files.exists(CONFIG_PATH)) {
                debugLog("No config file found, will create on first save");
                return;
            }

            // Simple TOML parser for [category] and key = value
            String currentCategory = "";
            for (String line : Files.readAllLines(CONFIG_PATH)) {
                line = line.trim();

                if (line.isEmpty() || line.startsWith("#")) continue;

                if (line.startsWith("[") && line.endsWith("]")) {
                    currentCategory = line.substring(1, line.length() - 1);
                } else if (line.contains("=")) {
                    int eqIdx = line.indexOf('=');
                    String key = line.substring(0, eqIdx).trim();
                    String value = line.substring(eqIdx + 1).trim();

                    // Remove quotes from strings
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                        // Unescape - must process backslash first to avoid corrupting other escapes
                        value = value.replace("\\\\", "\\").replace("\\\"", "\"").replace("\\n", "\n");
                    }

                    String fullKey = currentCategory.isEmpty() ? key : currentCategory + "." + key;
                    configValues.put(fullKey, parseValue(value));
                }
            }

            lastModified = Files.getLastModifiedTime(CONFIG_PATH);
            debugLog("Config loaded successfully");
        } catch (IOException e) {
            IrisSearch.log(3, "Error loading config: " + e.getMessage());
        }
    }

    /**
     * Parses a value string into the correct type
     */
    private static Object parseValue(String value) {
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        try {
            if (value.contains(".")) return Double.parseDouble(value);
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return value;
        }
    }

    /**
     * Sets the order in which categories should appear in the config file.
     * Categories not in this list will be appended at the end in the order they are first used.
     *
     * @param categories The ordered list of category names
     */
    public static void setConfigCategoryOrder(String... categories) {
        categoryOrder.clear();
        categoryOrder.addAll(Arrays.asList(categories));
        debugLog("Category order set: " + String.join(", ", categories));
    }

    /**
     * Reads or writes a config value with the given category, key, default value, and description
     * (without validation or custom migrations)
     */
    public static <T> T readWriteConfig(String category, String key, T defaultValue, String description) {
        return readWriteConfig(category, key, defaultValue, description, null, (TypeMigration[]) null);
    }

    /**
     * Reads or writes a config value with the given category, key, default value, description, and allowed values
     * (without custom migrations)
     * @param allowedValues Array of allowed string values (case-insensitive), or null for no validation
     */
    @SuppressWarnings("unused")
    public static <T> T readWriteConfig(String category, String key, T defaultValue, String description, String[] allowedValues) {
        return readWriteConfig(category, key, defaultValue, description, allowedValues, (TypeMigration[]) null);
    }

    /**
     * Reads or writes a config value with the given category, key, default value, description, allowed values, and custom type migrations
     * @param allowedValues Array of allowed string values (case-insensitive), or null for no validation
     * @param migrations Custom type migration mappings for when the stored type doesn't match the expected type
     */
    public static <T> T readWriteConfig(String category, String key, T defaultValue, String description, String[] allowedValues, TypeMigration... migrations) {
        ConfigEntry<T> entry = new ConfigEntry<>(category, key, defaultValue, description, allowedValues);

        // Store in schema for regeneration
        String schemaKey = category + "." + key;
        configSchema.put(schemaKey, entry);

        String categorizedKey = category + "." + key;
        Object value = configValues.get(categorizedKey);

        // Use default if not found
        if (value == null) {
            debugLog("Using default value for " + categorizedKey);
            value = defaultValue;
            configValues.put(categorizedKey, defaultValue);
        } else {
            // Check for type migration
            Object migratedValue = handleTypeMigration(categorizedKey, value, defaultValue, migrations);

            // Update configValues if type was corrected/migrated
            if (migratedValue != value) {
                configValues.put(categorizedKey, migratedValue);
                debugLog("Updated " + categorizedKey + " with migrated value: " + migratedValue);
            }

            value = migratedValue;
        }

        // Convert to proper type
        T result = convertValue(entry, value);

        // Validate if allowed values are specified
        if (allowedValues != null && allowedValues.length > 0 && result instanceof String strResult) {
            boolean isValid = false;
            String normalizedValue = null;
            for (String allowed : allowedValues) {
                if (allowed.equalsIgnoreCase(strResult)) {
                    isValid = true;
                    // Normalize to the canonical case from allowedValues
                    normalizedValue = allowed;
                    break;
                }
            }

            if (isValid) {
                @SuppressWarnings("unchecked")
                T typedValue = (T) normalizedValue;
                result = typedValue;
                configValues.put(categorizedKey, normalizedValue);
            } else {
                debugLog("Invalid value '" + strResult + "' for " + categorizedKey + ", using default: " + defaultValue);
                result = defaultValue;
                configValues.put(categorizedKey, defaultValue);
            }
        }

        return result;
    }

    /**
     * Handles type migration when the stored type doesn't match the expected type
     */
    private static Object handleTypeMigration(String key, Object oldValue, Object defaultValue, TypeMigration[] customMigrations) {
        // No migration needed if types match
        if (oldValue.getClass().equals(defaultValue.getClass())) {
            return oldValue;
        }

        // Try custom migrations
        if (customMigrations != null) {
            for (TypeMigration migration : customMigrations) {
                if (migration.matches(oldValue)) {
                    Object migratedValue = migration.getNewValue();
                    IrisSearch.log(0, "Migrated '" + key + "' from " + oldValue.getClass().getSimpleName() +
                            " (" + oldValue + ") to " + migratedValue.getClass().getSimpleName() +
                            " (" + migratedValue + ")");
                    configValues.put(key, migratedValue);
                    return migratedValue;
                }
            }
        }

        // No migration found, use default
        IrisSearch.log(0, "Type mismatch for '" + key + "': stored type " + oldValue.getClass().getSimpleName() +
                " does not match expected type " + defaultValue.getClass().getSimpleName() + ", using default");
        return defaultValue;
    }

    /**
     * Converts value to proper type using ConfigEntry
     */
    @SuppressWarnings("unchecked")
    private static <T> T convertValue(ConfigEntry<T> entry, Object value) {
        return switch (entry.getType()) {
            case BOOLEAN -> (T) entry.getAsBoolean(value);
            case INTEGER -> (T) entry.getAsInteger(value);
            case DOUBLE -> (T) entry.getAsDouble(value);
            default -> (T) entry.getAsString(value);
        };
    }

    /**
     * Regenerates the config file with current schema.
     * Preserves all user values but updates structure and descriptions.
     * Applies category ordering based on setConfigCategoryOrder.
     */
    public static void regenerateConfig() {
        try {
            // Collect current values from our map
            Map<String, Object> allValues = new LinkedHashMap<>(configValues);

            // Write TOML file manually with proper ordering
            writeTomlFile(allValues);

            // Reload config to sync with the new file
            loadConfig();

            debugLog("Config file regenerated with proper ordering and header");
        } catch (Exception e) {
            IrisSearch.log(3, "Error regenerating config: " + e.getMessage());
        }
    }

    /**
     * Manually writes the TOML file with proper ordering
     */
    private static void writeTomlFile(Map<String, Object> values) throws IOException {
        List<String> lines = new ArrayList<>();

        // Add header
        lines.add("# This file stores configuration options for the IrisSearch mod");
        lines.add("# Made for version " + IrisSearch.VERSION);
        lines.add("# Thank you for using IrisSearch - SpacEagle17");
        lines.add("");

        // Write entries in the correct category order
        for (int catIndex = 0; catIndex < categoryOrder.size(); catIndex++) {
            String category = categoryOrder.get(catIndex);

            // Add blank line before category (except first)
            if (catIndex > 0) {
                lines.add("");
            }

            // Add category header
            lines.add("[" + category + "]");

            // Find all entries for this category
            boolean firstEntry = true;
            for (Map.Entry<String, ConfigEntry<?>> entry : configSchema.entrySet()) {
                ConfigEntry<?> configEntry = entry.getValue();
                if (configEntry.getCategory().equals(category)) {
                    // Add blank line between entries (except first in category)
                    if (!firstEntry) {
                        lines.add("");
                    }
                    firstEntry = false;

                    String fullKey = entry.getKey();
                    Object value = values.getOrDefault(fullKey, configEntry.getDefaultValue());

                    // Add comment lines
                    String[] commentLines = formatDescription(configEntry.getDescription()).split("\n");

                    for (String commentLine : commentLines) {
                        lines.add("\t# " + commentLine);
                    }

                    // Add value line
                    String valueStr = formatValue(value);
                    lines.add("\t" + configEntry.getKey() + " = " + valueStr);
                }
            }
        }

        Files.write(CONFIG_PATH, lines);
        debugLog("TOML file written with proper ordering");
    }

    /**
     * Formats the description for TOML comments
     */
    private static String formatDescription(String description) {
        StringBuilder sb = new StringBuilder();
        String[] lines = description.split("\\n");
        for (String line : lines) {
            if (!sb.isEmpty()) sb.append("\n");
            sb.append(line.trim());
        }
        return sb.toString();
    }

    /**
     * Formats a value for TOML output
     */
    private static String formatValue(Object value) {
        if (value instanceof String str) {
            // Escape and quote strings
            str = str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
            return "\"" + str + "\"";
        } else if (value instanceof Boolean || value instanceof Number) {
            return value.toString();
        }
        return "\"" + value.toString() + "\"";
    }

    /**
     * Starts watching the config file for external changes
     */
    public static void startConfigWatcher() {
        if (watcherActive) return;

        watcherActive = true;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "IrisSearchConfigWatcher");
            thread.setDaemon(true);
            return thread;
        });

        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (Files.exists(CONFIG_PATH)) {
                    FileTime currentModified = Files.getLastModifiedTime(CONFIG_PATH);
                    if (!currentModified.equals(lastModified)) {
                        debugLog("Config file changed externally, reloading");
                        loadConfig();
                        ConfigHandler.configStuff();
                    }
                }
            } catch (IOException ignored) {}
        }, 10, 10, TimeUnit.SECONDS);

        debugLog("Config watcher started");
    }

    /**
     * Stops the config file watcher
     */
    public static void stopConfigWatcher() {
        if (watcherActive && scheduler != null) {
            scheduler.shutdown();
            watcherActive = false;
            debugLog("Config watcher stopped");
        }
    }

    /**
     * Represents a type migration mapping from an old value to a new value.
     * Used to define custom migration behavior when config value types change.
     */
    public static class TypeMigration {
        private final Object oldValue;
        private final Object newValue;

        private TypeMigration(Object oldValue, Object newValue) {
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        /**
         * Creates a migration mapping from oldValue to newValue.
         * Comparisons are case-insensitive for strings.
         */
        public static TypeMigration map(Object oldValue, Object newValue) {
            return new TypeMigration(oldValue, newValue);
        }

        @SuppressWarnings("unused")
        public Object getOldValue() {
            return oldValue;
        }

        public Object getNewValue() {
            return newValue;
        }

        /**
         * Checks if the given value matches this migration's old value (case-insensitive for strings)
         */
        public boolean matches(Object value) {
            if (oldValue == null) return value == null;
            if (value == null) return false;

            // Case-insensitive string comparison
            if (oldValue instanceof String && value instanceof String) {
                return ((String) oldValue).equalsIgnoreCase((String) value);
            }

            return oldValue.equals(value);
        }
    }
}
