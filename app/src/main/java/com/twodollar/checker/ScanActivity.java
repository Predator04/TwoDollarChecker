package com.twodollar.checker;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Base64;
import android.util.Size;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScanActivity extends ComponentActivity {

    private static final String OPENAI_KEY = BuildConfig.OPENAI_KEY;

    private static final String[] GRADES = {
            "Circulated", "Fine", "Very Fine", "Extremely Fine",
            "About Uncirculated", "Uncirculated", "Gem Uncirculated"
    };

    private static final int AMBER = Color.rgb(0xFF, 0xD5, 0x4F);
    private static final int GREEN = Color.rgb(0x00, 0xE6, 0x76);

    private PreviewView previewView;
    private TextView guidanceText, lastSavedText, countText;
    private TextRecognizer recognizer;
    private ScannerOverlay overlay;
    private BillDbHelper db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private volatile boolean capturing = false;
    private String lastSerial = null;
    private int savedCount = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_scan);
        previewView = findViewById(R.id.preview);
        overlay = findViewById(R.id.overlay);
        guidanceText = findViewById(R.id.guidance);
        lastSavedText = findViewById(R.id.last_saved);
        countText = findViewById(R.id.count);
        db = new BillDbHelper(this);
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        findViewById(R.id.done_button).setOnClickListener(v -> finish());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            guidanceText.setText("Camera permission is required to scan");
            finish();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                int rot = previewView.getDisplay() != null
                        ? previewView.getDisplay().getRotation() : 0;

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1280, 960))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(executor, this::analyzeFrame);

                preview.setTargetRotation(rot);
                analysis.setTargetRotation(rot);

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
            } catch (Exception e) {
                guidanceText.setText("Camera failed: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeFrame(ImageProxy image) {
        if (capturing) {
            image.close();
            return;
        }
        // Copy the frame to a Bitmap NOW and close the ImageProxy immediately —
        // with STRATEGY_KEEP_ONLY_LATEST the camera closes frames out from under
        // async work, so holding the ImageProxy across the OCR callback crashes.
        Bitmap bmp;
        try {
            bmp = image.toBitmap();
        } catch (Exception e) {
            image.close();
            return;
        }
        image.close();
        if (bmp == null) return;

        InputImage in = InputImage.fromBitmap(bmp, 0);
        recognizer.process(in)
                .addOnSuccessListener(result -> onRecognized(bmp, result))
                .addOnFailureListener(e -> {});
    }

    private void onRecognized(Bitmap bmp, Text result) {
        String text = result.getText();
        String serial = extractSerial(text);
        String seriesText = extractSeriesText(text);

        if (serial == null) {
            runOnUiThread(() -> setGuidance("🔍 Align the serial number in the box", AMBER));
            return;
        }
        if (serial.equals(lastSerial)) {
            runOnUiThread(() -> setGuidance("✓ " + serial + " saved — next bill", GREEN));
            return;
        }
        Rect box = findSerialBox(result, serial);
        String dir = guidanceFor(box, bmp.getWidth(), bmp.getHeight());
        if (dir != null) {
            runOnUiThread(() -> setGuidance(dir, AMBER));
            return;
        }

        capturing = true;
        runOnUiThread(() -> setGuidance("✅ Reading " + serial + "...", GREEN));
        captureAndSave(bmp, serial, seriesText);
    }

    private void captureAndSave(Bitmap bmp, String serial, String seriesText) {
        new Thread(() -> {
            try {
                boolean star = false;
                int grade = 0;
                String series = resolveSeries(seriesText);
                String photo = Prefs.savePhotos(this) ? savePhoto(bmp) : "";
                if (Prefs.aiVision(this)) {
                    try {
                        String b64 = bitmapToBase64(bmp);
                        String resp = callVision(b64);
                        star = resp.toUpperCase().contains("STAR:YES");
                        grade = gradeIndex(extractGrade(resp));
                    } catch (Exception ignored) {
                        // vision failed — keep OCR-only data
                    }
                }
                BillValuator.Result r = BillValuator.valuate(
                        series, serial, star, grade, "None", "Unknown", false);
                db.add(series, serial, star, grade, "None", "Unknown",
                        r.valueRange, r.score, r.reason, photo);
                lastSerial = serial;
                savedCount++;
                final boolean worth = r.worthMore;
                final String line = series + " · " + serial + (star ? " ★" : "") + " — " + r.valueRange;
                runOnUiThread(() -> {
                    lastSavedText.setText(line);
                    countText.setText(savedCount + " saved");
                    setGuidance((worth ? "💰 " : "✓ ") + r.valueRange + " — next bill", GREEN);
                    vibrate();
                });
            } catch (Exception e) {
                runOnUiThread(() -> setGuidance("⚠ Save failed — try again", AMBER));
            } finally {
                try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
                capturing = false;
            }
        }).start();
    }

    // ---------- OCR helpers ----------

    private String savePhoto(Bitmap bmp) {
        try {
            File dir = new File(getFilesDir(), "bills");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, System.currentTimeMillis() + ".jpg");
            FileOutputStream fos = new FileOutputStream(f);
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, fos);
            fos.close();
            return f.getAbsolutePath();
        } catch (Exception e) {
            return "";
        }
    }

    private String extractSerial(String text) {
        Matcher m = Pattern.compile("[A-Za-z]\\s*\\d{8}\\s*[A-Za-z]").matcher(text);
        if (m.find()) return m.group().replaceAll("\\s", "").toUpperCase();
        Matcher m2 = Pattern.compile("\\d{8}").matcher(text);
        if (m2.find()) return m2.group();
        return null;
    }

    private String extractSeriesText(String text) {
        Matcher m = Pattern.compile("(18[0-9]{2}|19[0-9]{2}|20[0-9]{2})\\s*([A-Z])?").matcher(text);
        if (m.find()) {
            String letter = m.group(2) != null ? m.group(2) : "";
            return m.group(1) + letter;
        }
        return null;
    }

    private Rect findSerialBox(Text result, String serial) {
        if (serial == null) return null;
        String digits = serial.replaceAll("[^0-9]", "");
        for (Text.TextBlock block : result.getTextBlocks()) {
            String t = block.getText() == null ? "" : block.getText().replaceAll("\\s", "");
            if (t.contains(digits)) return block.getBoundingBox();
        }
        return null;
    }

    private String resolveSeries(String seriesText) {
        if (seriesText == null) return "1976 (Green Seal)";
        String digits = seriesText.replaceAll("[^0-9]", "");
        if (digits.length() < 4) return "1976 (Green Seal)";
        String year = digits.substring(0, 4);
        String letter = seriesText.replaceAll("[^A-Za-z]", "");
        int y;
        try {
            y = Integer.parseInt(year);
        } catch (Exception e) {
            return "1976 (Green Seal)";
        }
        if (y == 1890) return "1890 (Treasury Note)";
        if (y == 1891) return "1891 (Treasury Note)";
        if (y == 1896) return "1896 Educational (Silver Cert)";
        if (y == 1899) return "1899 (Silver Cert)";
        if (y == 1918) return "1918 (Large Size)";
        String seal = y <= 1963 ? "Red Seal" : "Green Seal";
        return year + (letter.isEmpty() ? "" : letter) + " (" + seal + ")";
    }

    private String guidanceFor(Rect box, int fw, int fh) {
        if (box == null) return "🔍 Align the serial number in the box";
        Rect t = new Rect((int) (fw * 0.15), (int) (fh * 0.30),
                (int) (fw * 0.85), (int) (fh * 0.55));
        if (box.width() < t.width() * 0.30) return "⬆ Move closer";
        if (box.width() > t.width() * 1.20) return "⬇ Move back (too close)";
        if (box.centerX() < t.centerX() - t.width() * 0.18) return "➡ Move right";
        if (box.centerX() > t.centerX() + t.width() * 0.18) return "⬅ Move left";
        if (box.centerY() < t.centerY() - t.height() * 0.22) return "⬇ Move down";
        if (box.centerY() > t.centerY() + t.height() * 0.22) return "⬆ Move up";
        return null;
    }

    private int gradeIndex(String grade) {
        if (grade == null) return 0;
        for (int i = 0; i < GRADES.length; i++) {
            if (GRADES[i].equalsIgnoreCase(grade)) return i;
        }
        for (int i = 0; i < GRADES.length; i++) {
            if (GRADES[i].toLowerCase().contains(grade.toLowerCase())
                    || grade.toLowerCase().contains(GRADES[i].toLowerCase())) return i;
        }
        return 0;
    }

    // ---------- AI vision ----------

    private String bitmapToBase64(Bitmap bmp) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
    }

    private String callVision(String b64) throws Exception {
        String prompt = "This is a photo of a US $2 bill. Answer exactly two lines and nothing else:\n"
                + "STAR:yes or STAR:no (is there a star symbol at the end of the serial number?)\n"
                + "GRADE:<Poor|Fine|Very Fine|Extremely Fine|About Uncirculated|Uncirculated|Gem Uncirculated> (overall condition)";
        JSONArray parts = new JSONArray();
        parts.put(new JSONObject().put("type", "text").put("text", prompt));
        parts.put(new JSONObject().put("type", "image_url")
                .put("image_url", new JSONObject().put("url", "data:image/jpeg;base64," + b64)));
        JSONArray msgs = new JSONArray();
        msgs.put(new JSONObject().put("role", "user").put("content", parts));
        JSONObject body = new JSONObject();
        body.put("model", "gpt-4o");
        body.put("messages", msgs);
        body.put("max_tokens", 50);

        URL url = new URL("https://api.openai.com/v1/chat/completions");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        String key = Prefs.apiKey(this);
        if (key.isEmpty()) key = OPENAI_KEY;
        c.setRequestProperty("Authorization", "Bearer " + key);
        c.setDoOutput(true);
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        c.getOutputStream().write(body.toString().getBytes("UTF-8"));
        c.getOutputStream().close();

        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String resp = readStream(is);
        c.disconnect();
        if (code >= 400) {
            throw new Exception("API " + code + ": "
                    + resp.substring(0, Math.min(120, resp.length())));
        }
        JSONObject j = new JSONObject(resp);
        return j.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
    }

    private String readStream(InputStream is) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }

    private String extractGrade(String resp) {
        Matcher m = Pattern.compile("GRADE:\\s*([\\w ]+)", Pattern.CASE_INSENSITIVE).matcher(resp);
        if (m.find()) return m.group(1).trim();
        return null;
    }

    private void setGuidance(String text, int color) {
        guidanceText.setText(text);
        guidanceText.setTextColor(color);
        if (overlay != null) overlay.setBracketColor(color);
    }

    private void vibrate() {
        if (!Prefs.vibrate(this)) return;
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            v.vibrate(180);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        if (recognizer != null) recognizer.close();
    }
}
