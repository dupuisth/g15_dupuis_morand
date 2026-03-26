package com.gr15.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProcessUtils {

    public static Process startApplicationInNewTerminal(List<String> arguments) throws IOException {
        List<String> command = new ArrayList<>();
        command.add("xterm");
        command.add("-e");
        command.addAll(JVMUtils.getLaunchCommandParts());
        command.addAll(arguments);

        return new ProcessBuilder(command).start();
    }
}