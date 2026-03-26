package com.gr15.utils;

import java.io.IOException;

public class ProcessUtils {
    public static Process startApplicationInNewTerminal(String arguments) throws IOException {
        return Runtime.getRuntime().exec("xterm -e java -classpath /home/thibaut/data/repos/g15_dupuis_morand/Notwork/target/classes com.gr15.Application " + arguments);
    }
}
