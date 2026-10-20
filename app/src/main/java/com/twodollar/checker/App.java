package com.twodollar.checker;

import android.app.Application;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Date;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        final Thread.UncaughtExceptionHandler defaultHandler =
                Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                File f = new File(getFilesDir(), "crash.log");
                PrintWriter pw = new PrintWriter(new FileWriter(f, true));
                pw.println("=== " + new Date() + " ===");
                Throwable t = throwable;
                while (t != null) {
                    t.printStackTrace(pw);
                    t = t.getCause();
                    if (t != null) pw.println("Caused by:");
                }
                pw.flush();
                pw.close();
            } catch (Exception ignored) {
                // couldn't write the log — nothing more we can do
            }

            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            } else {
                System.exit(2);
            }
        });
    }
}
