package com.twodollar.checker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;


import java.util.List;
import java.net.URLEncoder;

public class MainActivity extends Activity {

    private static final String[] SERIES = {
            "1862 (Red Seal)", "1869 (Red Seal)", "1874-1880 (Red Seal)",
            "1880 (Blue Seal)", "1880 (Brown Seal)",
            "1890 (Treasury Note)", "1891 (Treasury Note)",
            "1896 Educational (Silver Cert)", "1899 (Silver Cert)",
            "1917 (Red Seal)", "1918 (Large Size)",
            "1928 (Red Seal)", "1928A (Red Seal)", "1928B (Red Seal)",
            "1928C (Red Seal)", "1928D (Red Seal)", "1928E (Red Seal)",
            "1928F (Red Seal)", "1928G (Red Seal)",
            "1953 (Red Seal)", "1953A (Red Seal)", "1953C (Red Seal)",
            "1963 (Red Seal)", "1976 (Green Seal)", "1995 (Green Seal)",
            "2003 (Green Seal)", "2003A (Green Seal)", "2009 (Green Seal)",
            "2013 (Green Seal)", "2017A (Green Seal)"
    };

    private static final String[] DISTRICTS = {
            "Unknown", "A - Boston", "B - New York", "C - Philadelphia",
            "D - Cleveland", "E - Richmond", "F - Atlanta", "G - Chicago",
            "H - St. Louis", "I - Minneapolis", "J - Kansas City",
            "K - Dallas", "L - San Francisco"
    };

    private static final String[] ERRORS = {
            "None", "Missing 2nd print", "Inverted overprint", "Missing 1st print (back)",
            "Missing 3rd print", "Mismatched serial numbers", "Overprint on back",
            "Missing seal", "Misaligned overprint (major shift)", "Foldover",
            "Butterfly fold", "Obstructed printing", "Offset ink transfer",
            "Insufficient inking", "Miscut", "Ink smear", "Gutter fold", "Minor shift",
            "1928C mule (micro/macro back)", "1928D mule (macro/micro back)", "1928D star mule"
    };

    private static final String[] GRADES = {
            "Circulated", "Fine", "Very Fine", "Extremely Fine",
            "About Uncirculated", "Uncirculated", "Gem Uncirculated"
    };



    private Spinner seriesSpinner, errorSpinner, districtSpinner, gradeSpinner;
    private EditText serialEdit;
    private CheckBox starCheck, firstDayCheck;
    private TextView resultText, reasonText, scoreText, collectionCount;
    private LinearLayout collectionList, resultArea;
    private BillDbHelper db;

    private BillValuator.Result lastResult;
    private String lastSeries, lastSerial, lastError, lastDistrict;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        db = new BillDbHelper(this);

        seriesSpinner = findViewById(R.id.series_spinner);
        errorSpinner = findViewById(R.id.error_spinner);
        districtSpinner = findViewById(R.id.district_spinner);
        gradeSpinner = findViewById(R.id.grade_spinner);
        serialEdit = findViewById(R.id.serial_edit);
        starCheck = findViewById(R.id.star_check);
        firstDayCheck = findViewById(R.id.first_day_check);
        resultText = findViewById(R.id.result_text);
        reasonText = findViewById(R.id.reason_text);
        scoreText = findViewById(R.id.score_text);
        resultArea = findViewById(R.id.result_area);
        collectionCount = findViewById(R.id.collection_count);
        collectionList = findViewById(R.id.collection_list);
        Button checkBtn = findViewById(R.id.check_button);
        Button saveBtn = findViewById(R.id.save_button);
        Button guideBtn = findViewById(R.id.guide_button);
        Button scanBtn = findViewById(R.id.scan_button);
        Button sellBtn = findViewById(R.id.sell_button);
        Button gradeGuideBtn = findViewById(R.id.grade_guide_button);
        Button settingsBtn = findViewById(R.id.settings_button);

        seriesSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, SERIES));
        ((ArrayAdapter) seriesSpinner.getAdapter()).setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);

        errorSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, ERRORS));
        ((ArrayAdapter) errorSpinner.getAdapter()).setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);

        districtSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, DISTRICTS));
        ((ArrayAdapter) districtSpinner.getAdapter()).setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);

        gradeSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, GRADES));
        ((ArrayAdapter) gradeSpinner.getAdapter()).setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);

        checkBtn.setOnClickListener(v -> onCheck());
        saveBtn.setOnClickListener(v -> onSave());
        guideBtn.setOnClickListener(v -> showGuide());
        scanBtn.setOnClickListener(v -> startActivity(new Intent(this, ScanActivity.class)));
        sellBtn.setOnClickListener(v -> showSellGuide());
        gradeGuideBtn.setOnClickListener(v -> showGradingGuide());
        settingsBtn.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        renderCollection();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderCollection();
    }

    private void onCheck() {
        String series = (String) seriesSpinner.getSelectedItem();
        String serial = serialEdit.getText().toString().trim();
        if (serial.isEmpty()) {
            Toast.makeText(this, "Enter the serial number", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean star = starCheck.isChecked();
        String error = (String) errorSpinner.getSelectedItem();
        String district = (String) districtSpinner.getSelectedItem();
        int grade = gradeSpinner.getSelectedItemPosition();
        boolean firstDay = firstDayCheck.isChecked();

        BillValuator.Result r = BillValuator.valuate(series, serial, star, grade, error, district, firstDay);
        lastResult = r;
        lastSeries = series;
        lastSerial = serial;
        lastError = error;
        lastDistrict = district;

        resultText.setText(r.valueRange);
        reasonText.setText(r.reason);
        scoreText.setText("Collector score: " + r.score + "/100");

        if (r.worthMore) {
            // 🎉 celebration — light it up
            resultArea.setBackgroundColor(Color.rgb(0xE8, 0xF5, 0xE9));
            resultText.setTextColor(Color.rgb(0x1B, 0x8A, 0x3A));
            resultText.setTextSize(30);
            reasonText.setTextColor(Color.DKGRAY);
            resultText.setScaleX(1f);
            resultText.setScaleY(1f);
            resultText.animate().scaleX(1.25f).scaleY(1.25f).setDuration(160)
                    .withEndAction(() -> resultText.animate().scaleX(1f).scaleY(1f).setDuration(160).start())
                    .start();
            vibrate();
            Toast.makeText(this, "\uD83D\uDCB0 Worth more than $2!", Toast.LENGTH_SHORT).show();
        } else {
            resultArea.setBackgroundColor(Color.TRANSPARENT);
            resultText.setTextColor(Color.GRAY);
            resultText.setTextSize(24);
            reasonText.setTextColor(Color.GRAY);
        }
    }

    private void vibrate() {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            v.vibrate(200);
        }
    }

    private void onSave() {
        if (lastResult == null) {
            Toast.makeText(this, "Check a bill first", Toast.LENGTH_SHORT).show();
            return;
        }
        db.add(lastSeries, lastSerial, starCheck.isChecked(),
                gradeSpinner.getSelectedItemPosition(), lastError, lastDistrict,
                lastResult.valueRange, lastResult.score, lastResult.reason, "");
        Toast.makeText(this, "Saved to collection", Toast.LENGTH_SHORT).show();
        serialEdit.setText("");
        lastResult = null;
        resultText.setText("");
        reasonText.setText("");
        scoreText.setText("");
        resultArea.setBackgroundColor(Color.TRANSPARENT);
        renderCollection();
    }

    private void showGuide() {
        String msg =
                "WHAT MAKES A $2 BILL VALUABLE\n\n" +
                "• Red seal (1928 / 1953 / 1963) — always worth more than $2, up to $300+ uncirculated\n\n" +
                "• Star note ★ — replacement note, worth a premium (up to $150 for 1953, $500+ for 1928)\n\n" +
                "• 1928B star — the king: $1,000 – $10,000+\n\n" +
                "• Pre-1928 series (1862–1917) — $100 to $3,000+\n\n" +
                "• Fancy serial numbers:\n" +
                "   solid (77777777) $1,000–4,000\n" +
                "   ladder (12345678) $900–1,900\n" +
                "   low # (00000001–99) $100–1,000\n" +
                "   radar / repeater / binary $20–75\n\n" +
                "• Printing errors — missing/inverted/misaligned print, mismatched serials, ink smears: $35–550+\n\n" +
                "• FRB district matters on star notes (1976 KC/Minneapolis, 2003A Atlanta, 2017A New York are scarce)\n\n" +
                "• Condition: uncirculated (crisp) is worth several times a folded bill\n\n" +
                "Most modern $2 bills are worth exactly $2. Values are rough guides — check sold listings for a real price.";
        new AlertDialog.Builder(this)
                .setTitle("Value guide")
                .setMessage(msg)
                .setPositiveButton("Close", null)
                .show();
    }

    private void showSellGuide() {
        String msg =
                "HOW TO SELL A VALUABLE $2 BILL\n\n" +
                "1. Don't spend it, clean it, iron it, or laminate it — any of those destroys the value\n\n" +
                "2. Grade it first (use the grading guide) — condition sets the price\n\n" +
                "3. Check real prices on eBay SOLD listings (not asking prices) and Heritage Auctions archives\n\n" +
                "4. Sell where it fits:\n" +
                "   • eBay — best for $10–$500 (photograph well, describe honestly)\n" +
                "   • Heritage Auctions — for $500+ rarities\n" +
                "   • Local coin shop — fastest, but pays ~50–70% of retail\n\n" +
                "5. Never sell to 'we buy currency' ads or pawn shops — they pay pennies on the dollar";
        new AlertDialog.Builder(this)
                .setTitle("How to sell")
                .setMessage(msg)
                .setPositiveButton("Open eBay sold listings", (d, w) -> openEbay())
                .setNegativeButton("Close", null)
                .show();
    }

    private void openEbay() {
        String q = "$2 bill";
        if (lastSeries != null) {
            q = lastSeries.split(" \\(")[0] + " $2 bill"
                    + (starCheck.isChecked() ? " star note" : "");
        }
        try {
            String url = "https://www.ebay.com/sch/i.html?_nkw="
                    + URLEncoder.encode(q, "UTF-8") + "&LH_Sold=1";
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {}
    }

    private void showGradingGuide() {
        String msg =
                "CONDITION / GRADING GUIDE\n\n" +
                "• Circulated — folds, soft corners, some soiling\n\n" +
                "• Fine — moderate wear, design fully clear, several folds\n\n" +
                "• Very Fine — light wear, a few folds, still crisp\n\n" +
                "• Extremely Fine — minimal wear, one light fold, sharp corners\n\n" +
                "• About Uncirculated — no folds, only traces of handling\n\n" +
                "• Uncirculated — crisp, no wear, original paper sheen\n\n" +
                "• Gem Uncirculated — flawless, perfect centering, full luster\n\n" +
                "Each step up roughly doubles the value. A crisp bill is worth several times a folded one.";
        new AlertDialog.Builder(this)
                .setTitle("Grading guide")
                .setMessage(msg)
                .setPositiveButton("Close", null)
                .show();
    }

    private void renderCollection() {
        List<BillDbHelper.Row> rows = db.all();
        collectionList.removeAllViews();
        collectionCount.setText(rows.isEmpty() ? "No bills saved yet"
                : rows.size() + " bill" + (rows.size() == 1 ? "" : "s") + " saved — best finds first");

        for (final BillDbHelper.Row row : rows) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(0, dp(8), 0, dp(8));

            if (row.photo != null && !row.photo.isEmpty()) {
                Bitmap thumb = loadThumbnail(row.photo, 200);
                if (thumb != null) {
                    ImageView iv = new ImageView(this);
                    iv.setImageBitmap(thumb);
                    iv.setAdjustViewBounds(true);
                    iv.setMaxWidth(dp(200));
                    iv.setPadding(0, 0, 0, dp(6));
                    iv.setOnClickListener(v -> showPhoto(row.photo));
                    item.addView(iv);
                }
            }

            TextView top = new TextView(this);
            top.setText(row.series + "  ·  serial " + row.serial
                    + (row.star ? "  ★" : "")
                    + (row.error != null && !row.error.isEmpty() ? "  ·  " + row.error : "")
                    + (row.district != null && !row.district.isEmpty() && !row.district.equals("Unknown")
                        ? "  ·  " + row.district : ""));
            top.setTextSize(14);
            top.setTextColor(Color.BLACK);

            TextView val = new TextView(this);
            val.setText(row.value + "   —   " + row.reason + "   ·   score " + row.score);
            val.setTextSize(13);
            boolean worth = row.value != null && !row.value.equals("$2")
                    && !row.value.startsWith("$2 ");
            val.setTextColor(worth ? Color.rgb(0x1B, 0x8A, 0x3A) : Color.GRAY);

            item.addView(top);
            item.addView(val);

            item.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("Remove bill?")
                        .setMessage(row.series + " · " + row.serial)
                        .setPositiveButton("Remove", (d, w) -> {
                            db.delete(row.id);
                            renderCollection();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return true;
            });

            collectionList.addView(item);
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private Bitmap loadThumbnail(String path, int maxW) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            int sample = 1;
            while (o.outWidth / sample > maxW * 2) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            return BitmapFactory.decodeFile(path, o2);
        } catch (Exception e) {
            return null;
        }
    }

    private void showPhoto(String path) {
        try {
            Bitmap bmp = BitmapFactory.decodeFile(path);
            if (bmp == null) return;
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(bmp);
            iv.setAdjustViewBounds(true);
            new AlertDialog.Builder(this)
                    .setTitle("Bill photo")
                    .setView(iv)
                    .setPositiveButton("Close", null)
                    .show();
        } catch (Exception ignored) {}
    }
}
