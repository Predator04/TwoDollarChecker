package com.twodollar.checker;

import android.app.Activity;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.EditText;

public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);

        CheckBox aiBox = findViewById(R.id.ai_check);
        CheckBox vibBox = findViewById(R.id.vibrate_check);
        CheckBox photoBox = findViewById(R.id.photo_check);
        EditText keyEdit = findViewById(R.id.key_edit);

        aiBox.setChecked(Prefs.aiVision(this));
        vibBox.setChecked(Prefs.vibrate(this));
        photoBox.setChecked(Prefs.savePhotos(this));
        keyEdit.setText(Prefs.apiKey(this));

        findViewById(R.id.save_button).setOnClickListener(v -> {
            Prefs.get(this).edit()
                    .putBoolean("ai_vision", aiBox.isChecked())
                    .putBoolean("vibrate", vibBox.isChecked())
                    .putBoolean("save_photos", photoBox.isChecked())
                    .putString("api_key", keyEdit.getText().toString().trim())
                    .apply();
            finish();
        });

        findViewById(R.id.back_button).setOnClickListener(v -> finish());
    }
}
