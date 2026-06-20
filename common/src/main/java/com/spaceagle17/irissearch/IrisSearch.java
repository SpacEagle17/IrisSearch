package com.spaceagle17.irissearch;

import com.spaceagle17.irissearch.logging.IrisSearchLogger;

import java.nio.file.Path;

public class IrisSearch {
    public static final String VERSION = "1.0.0";

    // Get necessary paths
    public static Path configDirectory = ModLoaderSpecifics.configDirectory();

    // Global Variables and Objects
    private static IrisSearch instance;
    private static IrisSearchLogger loggerInstance;

    public IrisSearch() {
        instance = this;

        loggerInstance = new IrisSearchLogger();
        log(0, "IrisSearch v" + VERSION + " initialized successfully.");
    }

    public static IrisSearch getInstance() {
        return instance;
    }

    public static void log(int messageLevel, String message) {
        if (loggerInstance == null) {
            System.out.println("IrisSearch (early log): " + message);
            return;
        }
        loggerInstance.log(messageLevel, message);
    }

    private static void debugLog(String message) {
        IrisSearchLogger.debugLog("[IrisSearch] " + message);
    }
}
