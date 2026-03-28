package com.gr15.utils;

public class ThreadUtils {
    public static boolean safeSleep(int millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            return false;
        }
    }
}
