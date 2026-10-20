package com.twodollar.checker;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {

    private static final String NAME = "settings";

    public static SharedPreferences get(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static boolean aiVision(Context c) {
        return get(c).getBoolean("ai_vision", true);
    }

    public static boolean vibrate(Context c) {
        return get(c).getBoolean("vibrate", true);
    }

    public static boolean savePhotos(Context c) {
        return get(c).getBoolean("save_photos", true);
    }

    public static String apiKey(Context c) {
        return get(c).getString("api_key", "");
    }
}
