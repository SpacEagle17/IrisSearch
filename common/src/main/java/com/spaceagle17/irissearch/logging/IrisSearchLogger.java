package com.spaceagle17.irissearch.logging;


import com.spaceagle17.irissearch.IrisSearch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.*;

public class IrisSearchLogger {
    private static Logger logger;
    private static boolean log4jAvailable = true;

    private static final int SPAM_PROTECTION_QUEUE_SIZE = 100; // Maximum number of messages to track for spam protection
    private final LogMessageQueue logMessageQueue = new LogMessageQueue(SPAM_PROTECTION_QUEUE_SIZE);
    private static boolean isDebugEnabled;

    static {
        try {
            logger = LogManager.getLogger("iris_search");
        } catch (NoClassDefFoundError | Exception e) {
            // Log4j not available (e.g., running DevPatchGenerator)
            log4jAvailable = false;
            System.out.println("[IrisSearch] Log4j not available, using System.out fallback");
        }
    }

    public IrisSearchLogger() {

        isDebugLoggingEnabled();

    }

    /**
     * Main logging method with custom fade timer
     */
    public void log(int messageLevel, String message) {
        if (shouldSuppressMessage(message)) {
            return;
        }
        String loggingMessage = "IrisSearch: " + message;
        if (messageLevel == -1) loggingMessage = "\n \n" + loggingMessage + "\n\n ";

        if (messageLevel == 3) loggingMessage = "Report this issue on GitHub or Discord: https://github.com/SpacEagle17/IrisSearch/issues | https://www.euphoriapatches.com/discord: " + loggingMessage;

        switch (messageLevel) {
            case 0:
            case 1:
                if (log4jAvailable && logger != null) {
                    logger.info(loggingMessage);
                } else {
                    System.out.println("[INFO] " + loggingMessage);
                }
                break;
            case 2:
                if (log4jAvailable && logger != null) {
                    logger.warn(loggingMessage);
                } else {
                    System.out.println("[WARN] " + loggingMessage);
                }
                break;
            case 3:
                if (log4jAvailable && logger != null) {
                    logger.error(loggingMessage);
                } else {
                    System.err.println("[ERROR] " + loggingMessage);
                }
                break;
            default:
                System.out.println(loggingMessage);
                break;
        }
    }
    /**
     * Public entry point for debug logging
     */
    public static void debugLog(String message) {
        if (isDebugEnabled) {
            IrisSearch.log(0, message);
        }
    }

    /**
     * Checks if a message should be suppressed based on recent duplicates within the defined time window
     */
    private boolean shouldSuppressMessage(String message) {
        LogMessage logMessage = new LogMessage(message);
        int count = logMessageQueue.getOccurrenceCount(logMessage);

        if (count == -1) {
            logMessageQueue.add(logMessage);
            return false;
        }

        return count > 3;
    }

    /**
     * Helper method to determine if debug logging is enabled via JVM flags
     */
    private void isDebugLoggingEnabled() {
        try {
            String jvmDebugArg = System.getProperty("ebugIrisSearch");

            if (jvmDebugArg == null) {
                isDebugEnabled = false;
                return;
            }

            String argLower = jvmDebugArg.trim().toLowerCase(Locale.ROOT);
            if ("true".equals(argLower)) {
                IrisSearch.log(0, "Debug logging enabled via JVM argument: " + jvmDebugArg);
                isDebugEnabled = true;
            } else if ("false".equals(argLower)) {
                isDebugEnabled = false;
            } else {
                // Log a warning about the invalid argument format, using IrisSearch.log directly
                // to safely avoid looping back into debug log evaluations.
                IrisSearch.log(2, "Invalid value for -DebugIrisSearch: " + jvmDebugArg + ". Only 'true' or 'false' are accepted. Defaulting to false.");
                isDebugEnabled = false;
            }
        } catch (Exception e) {
            isDebugEnabled = false;
        }
    }

    public static String getStackTrace(Exception e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.getClass().getName()).append(": ").append(e.getMessage()).append("\n");
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append("  at ").append(element.toString()).append("\n");
        }
        return sb.toString();
    }
}
