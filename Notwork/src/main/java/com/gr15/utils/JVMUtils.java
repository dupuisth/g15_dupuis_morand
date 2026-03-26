package com.gr15.utils;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.List;

public class JVMUtils {
    /**
     * Return the command used to start the program, without the additional arguments
     */
    public static List<String> getLaunchCommandParts() {
        RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
        List<String> jvmArgs = bean.getInputArguments();

        List<String> result = new ArrayList<>();

        // ✅ Use the SAME java executable
        String javaBin = System.getProperty("java.home")
                + File.separator + "bin"
                + File.separator + "java";
        result.add(javaBin);

        result.addAll(jvmArgs);

        result.add("-classpath");
        result.add(System.getProperty("java.class.path"));

        String command = System.getProperty("sun.java.command");
        int separator = command.indexOf(' ');
        String entryPoint = (separator == -1) ? command : command.substring(0, separator);

        result.add(entryPoint);

        return result;
    }
}