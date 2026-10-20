package com.twodollar.checker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {

    private static final String API =
            "https://api.github.com/repos/Predator04/TwoDollarChecker/releases/latest";

    public static class Update {
        public boolean available;
        public String latestVersion;
        public String notes;
        public String apkUrl;
    }

    /** Background check on app launch — only bothers the user if an update exists. */
    public static void checkOnBoot(Activity a) {
        new Thread(() -> {
            Update u = check();
            if (u != null && u.available) {
                a.runOnUiThread(() -> showDialog(a, u));
            }
        }).start();
    }

    /** Manual check (button press) — always reports a result. */
    public static void checkManual(Activity a) {
        Toast.makeText(a, "Checking for updates…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            Update u = check();
            a.runOnUiThread(() -> {
                if (u == null) {
                    Toast.makeText(a, "Couldn't reach GitHub — check your connection",
                            Toast.LENGTH_LONG).show();
                } else if (u.available) {
                    showDialog(a, u);
                } else {
                    new AlertDialog.Builder(a)
                            .setTitle("Up to date")
                            .setMessage("You're on the latest version (v" + BuildConfig.VERSION_NAME + ").")
                            .setPositiveButton("Close", null)
                            .show();
                }
            });
        }).start();
    }

    private static Update check() {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
            c.setRequestProperty("Accept", "application/vnd.github+json");
            c.setRequestProperty("User-Agent", "TwoDollarChecker");
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            if (c.getResponseCode() != 200) return null;
            String body = readAll(c.getInputStream());
            c.disconnect();

            JSONObject j = new JSONObject(body);
            Update u = new Update();
            u.latestVersion = j.optString("tag_name");
            u.notes = j.optString("body");
            u.apkUrl = "";
            JSONArray assets = j.optJSONArray("assets");
            if (assets != null && assets.length() > 0) {
                u.apkUrl = assets.getJSONObject(0).optString("browser_download_url");
            }
            u.available = newer(u.latestVersion, BuildConfig.VERSION_NAME);
            return u;
        } catch (Exception e) {
            return null;
        }
    }

    private static void showDialog(Activity a, Update u) {
        String msg = "You're on v" + BuildConfig.VERSION_NAME + ".\n\nWhat's new in "
                + u.latestVersion + ":\n\n" + u.notes;
        new AlertDialog.Builder(a)
                .setTitle("Update available — " + u.latestVersion)
                .setMessage(msg)
                .setPositiveButton("Download & install", (d, w) -> downloadAndInstall(a, u))
                .setNegativeButton("Later", null)
                .show();
    }

    private static void downloadAndInstall(Activity a, Update u) {
        if (u.apkUrl == null || u.apkUrl.isEmpty()) {
            Toast.makeText(a, "No APK asset on this release — open the release page",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(a, "Downloading update…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                File dir = new File(a.getExternalFilesDir(null), "updates");
                dir.mkdirs();
                File apk = new File(dir, "update.apk");

                HttpURLConnection c = (HttpURLConnection) new URL(u.apkUrl).openConnection();
                c.setConnectTimeout(30000);
                c.setReadTimeout(120000);
                InputStream in = c.getInputStream();
                FileOutputStream out = new FileOutputStream(apk);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                out.close();
                in.close();
                c.disconnect();

                a.runOnUiThread(() -> install(a, apk));
            } catch (Exception e) {
                a.runOnUiThread(() -> Toast.makeText(a,
                        "Download failed — check your connection", Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private static void install(Activity a, File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(a, a.getPackageName() + ".fileprovider", apk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(a, "Can't open installer — allow 'install unknown apps' for this app",
                    Toast.LENGTH_LONG).show();
        }
    }

    private static boolean newer(String latest, String current) {
        int[] l = parse(latest);
        int[] c = parse(current);
        int len = Math.max(l.length, c.length);
        for (int i = 0; i < len; i++) {
            int lv = i < l.length ? l[i] : 0;
            int cv = i < c.length ? c[i] : 0;
            if (lv != cv) return lv > cv;
        }
        return false;
    }

    private static int[] parse(String v) {
        String s = v == null ? "" : v.replaceFirst("^[vV]", "");
        String[] parts = s.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
            } catch (Exception e) {
                out[i] = 0;
            }
        }
        return out;
    }

    private static String readAll(InputStream is) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }
}
