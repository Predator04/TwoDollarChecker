package com.twodollar.checker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class BillDbHelper extends SQLiteOpenHelper {

    private static final String DB = "bills.db";
    private static final int VER = 4;

    public BillDbHelper(Context ctx) {
        super(ctx, DB, null, VER);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE bills (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "series TEXT, serial TEXT, star INTEGER, grade INTEGER, error TEXT, district TEXT, " +
                "value TEXT, score INTEGER, reason TEXT, photo TEXT, added INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) {
        if (o < 4) {
            db.execSQL("ALTER TABLE bills ADD COLUMN photo TEXT DEFAULT ''");
        }
    }

    public void add(String series, String serial, boolean star, int grade, String error,
                    String district, String value, int score, String reason, String photo) {
        ContentValues cv = new ContentValues();
        cv.put("series", series);
        cv.put("serial", serial);
        cv.put("star", star ? 1 : 0);
        cv.put("grade", grade);
        cv.put("error", error == null ? "" : error);
        cv.put("district", district == null ? "" : district);
        cv.put("value", value);
        cv.put("score", score);
        cv.put("reason", reason);
        cv.put("photo", photo == null ? "" : photo);
        cv.put("added", System.currentTimeMillis());
        getWritableDatabase().insert("bills", null, cv);
    }

    public static class Row {
        public final long id;
        public final String series, serial, error, district, value, reason, photo;
        public final boolean star;
        public final int grade, score;

        Row(long id, String series, String serial, boolean star, int grade, String error,
            String district, String value, int score, String reason, String photo) {
            this.id = id;
            this.series = series;
            this.serial = serial;
            this.star = star;
            this.grade = grade;
            this.error = error;
            this.district = district;
            this.value = value;
            this.score = score;
            this.reason = reason;
            this.photo = photo;
        }
    }

    public List<Row> all() {
        List<Row> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("bills", null, null, null, null, null, "score DESC");
        while (c.moveToNext()) {
            out.add(new Row(
                    c.getLong(c.getColumnIndexOrThrow("_id")),
                    c.getString(c.getColumnIndexOrThrow("series")),
                    c.getString(c.getColumnIndexOrThrow("serial")),
                    c.getInt(c.getColumnIndexOrThrow("star")) == 1,
                    c.getInt(c.getColumnIndexOrThrow("grade")),
                    c.getString(c.getColumnIndexOrThrow("error")),
                    c.getString(c.getColumnIndexOrThrow("district")),
                    c.getString(c.getColumnIndexOrThrow("value")),
                    c.getInt(c.getColumnIndexOrThrow("score")),
                    c.getString(c.getColumnIndexOrThrow("reason")),
                    c.getString(c.getColumnIndexOrThrow("photo"))));
        }
        c.close();
        return out;
    }

    public void delete(long id) {
        getWritableDatabase().delete("bills", "_id=?", new String[]{String.valueOf(id)});
    }
}
