package com.gr15.utils;

public class ThreadUtils {
    public static boolean safeSleep(int millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            // Set the flag again
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
