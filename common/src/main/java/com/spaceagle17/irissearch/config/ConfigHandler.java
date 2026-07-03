package com.spaceagle17.irissearch.config;

import com.spaceagle17.irissearch.IrisSearch;
import com.spaceagle17.irissearch.logging.IrisSearchLogger;

import java.util.Locale;

public class ConfigHandler {
    // Config Options
    public static boolean doDebugLogging = false;
    public static int minPacksForSelectionSearch = 6;

    public static void configStuff() {
        Config.initialize();

        // Set category order - categories will appear in this order in the TOML file
        // Any categories used but not listed here will appear at the end
        Config.setConfigCategoryOrder("behavior", "debug");

        /*
        How to use: Cast to desired data type, then call readWriteConfig with category
        Parameters: category, key, defaultValue, description (supports multiline with "\n")
        Config will be organized into [category] sections in the TOML file

        Optional Parameters: allowedValues (String array) for string options with limited choices
        TypeMigration for changing option types while preserving user settings
        How Does TypeMigration work?
        If an option's type is changed (e.g., boolean to string), the TypeMigration parameter
        tells the config system how to convert existing user values to the new type.
        Example: Config.TypeMigration.map(false, "none") means: if the old boolean value was false,
        set the new string value to "none". Can be chained for multiple mappings.
        */

        minPacksForSelectionSearch = Config.readWriteConfig("behavior", "minPacksForSelectionSearch",6,
                "Minimum number of shader packs required for the search bar to appear in the shader selection menu." +
                        "\nDefault = 6");

        // Type is auto-detected from the default value - no casting needed!
        boolean configDebugLogging = Config.readWriteConfig("debug", "doDebugLogging", false,
                "Option that enables or disables debug logging. Alternatively, one can also set the JVM argument -DebugIrisSearch=true/false which takes priority over this setting." +
                        "\nDefault = false");
        handleJVMArgumentDebugLogging(configDebugLogging);

        // Regenerate config to apply proper ordering and ensure header is present
        Config.regenerateConfig();

        Config.startConfigWatcher();
    }

    private static void handleJVMArgumentDebugLogging(boolean configDebugLogging) {
        // Check for JVM argument -DebugIrisSearch=true/false which takes priority over config
        String jvmDebugArg = System.getProperty("ebugIrisSearch");
        if (jvmDebugArg != null) {
            String argLower = jvmDebugArg.trim().toLowerCase(Locale.ROOT);
            if ("true".equals(argLower) || "false".equals(argLower)) {
                doDebugLogging = Boolean.parseBoolean(argLower);
                debugLog("Debug logging set via JVM argument -DebugIrisSearch=" + jvmDebugArg + " (overriding config value)");
            } else {
                IrisSearch.log(2, "Invalid value for -DebugIrisSearch: " + jvmDebugArg + ". Only 'true' or 'false' are accepted. Using config value.");
                doDebugLogging = configDebugLogging;
            }
        } else {
            doDebugLogging = configDebugLogging;
        }
    }

    private static void debugLog(String message) {
        IrisSearchLogger.debugLog("[ConfigHandler] " + message);
    }
}
