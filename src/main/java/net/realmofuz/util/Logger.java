package net.realmofuz.util;

public class Logger {
    public static void debug(String debug) {
        System.out.println("[DEBUG] " + debug);
    }

    public static void debug(Object debug) {
        System.out.println("[DEBUG] " + debug);
    }
}
