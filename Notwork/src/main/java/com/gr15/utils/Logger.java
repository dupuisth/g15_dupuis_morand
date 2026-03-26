package com.gr15.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Generated using AI
 */
public final class Logger {

    public enum Level {
        DEBUG(0, "\u001B[37m"), // blanc / gris
        INFO(1, "\u001B[32m"),  // vert
        WARN(2, "\u001B[33m"),  // jaune
        ERROR(3, "\u001B[31m"), // rouge
        NONE(4, "");            // rien affiché

        private final int priority;
        private final String color;

        Level(int priority, String color) {
            this.priority = priority;
            this.color = color;
        }

        public int getPriority() {
            return priority;
        }

        public String getColor() {
            return color;
        }
    }

    private static final String ANSI_RESET = "\u001B[0m";

    private static Level currentLevel = Level.INFO;
    private static boolean showTimestamp = true;
    private static boolean showClassName = true;
    private static boolean useColors = true;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Logger() {
        // empêche l'instanciation
    }

    // ================================
    // CONFIGURATION
    // ================================

    public static void setLevel(Level level) {
        if (level == null) {
            throw new IllegalArgumentException("level cannot be null");
        }
        currentLevel = level;
    }

    public static void enableTimestamp(boolean enable) {
        showTimestamp = enable;
    }

    public static void enableClassName(boolean enable) {
        showClassName = enable;
    }

    public static void enableColors(boolean enable) {
        useColors = enable;
    }

    // ================================
    // MÉTHODES PUBLIQUES
    // ================================

    public static void debug(String message) {
        log(Level.DEBUG, message, null);
    }

    public static void info(String message) {
        log(Level.INFO, message, null);
    }

    public static void warn(String message) {
        log(Level.WARN, message, null);
    }

    public static void error(String message) {
        log(Level.ERROR, message, null);
    }

    public static void error(String message, Throwable throwable) {
        log(Level.ERROR, message, throwable);
    }

    // ================================
    // COEUR DU Logger
    // ================================

    private static void log(Level level, String message, Throwable throwable) {
        if (level.getPriority() < currentLevel.getPriority()) {
            return;
        }

        StringBuilder output = new StringBuilder();

        if (showTimestamp) {
            output.append("[")
                    .append(LocalDateTime.now().format(FORMATTER))
                    .append("] ");
        }

        output.append("[")
                .append(level.name())
                .append("] ");

        if (showClassName) {
            output.append("[")
                    .append(getCallerClassName())
                    .append("] ");
        }

        output.append(message);

        String finalMessage = output.toString();

        if (useColors && level != Level.NONE) {
            finalMessage = level.getColor() + finalMessage + ANSI_RESET;
        }

        if (level == Level.ERROR) {
            System.err.println(finalMessage);
            if (throwable != null) {
                throwable.printStackTrace(System.err);
            }
        } else {
            System.out.println(finalMessage);
            if (throwable != null) {
                throwable.printStackTrace(System.out);
            }
        }
    }

    private static String getCallerClassName() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();

        for (StackTraceElement element : stack) {
            String className = element.getClassName();

            if (!className.equals(Logger.class.getName())
                    && !className.equals(Thread.class.getName())) {
                return simplifyClassName(className);
            }
        }

        return "UnknownClass";
    }

    private static String simplifyClassName(String fullClassName) {
        int lastDot = fullClassName.lastIndexOf('.');
        if (lastDot == -1) {
            return fullClassName;
        }
        return fullClassName.substring(lastDot + 1);
    }
}