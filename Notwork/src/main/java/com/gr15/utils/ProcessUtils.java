package com.gr15.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ProcessUtils {

    public static Process startApplicationInNewTerminal(List<String> arguments) throws IOException {
        List<String> command = new ArrayList<>();

        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            // Windows
            command.add("cmd.exe");
            command.add("/c");
            command.add("start");
            command.add("cmd.exe");
            command.add("/k"); // Keep the window open
        } else {
            // Linux / Unix
            command.add("xterm");
            command.add("-e");
        }

        command.addAll(JVMUtils.getLaunchCommandParts());
        command.addAll(arguments);

        return new ProcessBuilder(command).start();
    }
}