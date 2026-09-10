package com.yourday.app.app;

/** Launcher de linha de comando que evita o erro "JavaFX runtime components are missing" de {@code java -jar}. */
public final class MainLauncher {
    public static void main(String[] args) {
        YourDayApp.main(args);
    }

    private MainLauncher() {
    }
}