package com.twodollar.checker;

/**
 * $2 bill valuation engine (v3 — values verified against US Currency Auctions,
 * PGS Gold & Coin, and MyCurrencyCollection price guides, Oct 2026).
 * Numeric internally so grade + rarity score can be computed; ranges are
 * rough retail/auction guides, not appraisals.
 */
public class BillValuator {

    public static class Result {
        public final String valueRange;   // formatted "$X" or "$X - $Y"
        public final String reason;
        public final boolean worthMore;
        public final int score;           // 0-100 collector-interest index

        public Result(String valueRange, String reason, boolean worthMore, int score) {
            this.valueRange = valueRange;
            this.reason = reason;
            this.worthMore = worthMore;
            this.score = score;
        }
    }

    // grade index -> value multiplier on the circulated base
    // 0=Circulated,1=Fine,2=VF,3=XF,4=AU,5=UNC,6=Gem
    private static final double[] GRADE_MULT = {1.0, 1.3, 1.6, 2.0, 3.0, 4.0, 6.0};
    private static final int[] GRADE_BONUS = {0, 2, 4, 6, 8, 10, 15};

    public static Result valuate(String series, String serial, boolean star, int grade,
                                 String errorType, String district, boolean firstDay) {
        int g = Math.max(0, Math.min(6, grade));
        double mult = GRADE_MULT[g];
        int gBonus = GRADE_BONUS[g];
        String digits = extractDigits(serial);

        // ---- 0. printing errors dominate everything ----
        if (errorType != null && !errorType.isEmpty() && !errorType.equals("None")) {
            int[] r = errorValue(errorType);
            if (r != null) {
                return new Result(fmt(r[0] * mult, r[1] * mult),
                        errorType + " (printing error)", true, clamp(40 + gBonus));
            }
        }

        // ---- 1. fancy serial (highest value) ----
        String fancy = fancySerial(digits);
        if (fancy != null) {
            int[] r = fancyValue(digits);
            if (r != null) {
                return new Result(fmt(r[0] * mult, r[1] * mult), fancy, true,
                        clamp(fancyScore(digits) + gBonus));
            }
        }

        // ---- 2. star note ----
        if (star) {
            int[] r = starValue(series, district);
            if (r != null) {
                String reason = (district == null || district.trim().isEmpty() || district.equals("Unknown"))
                        ? "star note \u2605 (" + series + ")"
                        : "star note \u2605 (" + series + ", district " + district.trim().toUpperCase() + ")";
                return new Result(fmt(r[0] * mult, r[1] * mult), reason, true,
                        clamp(starScore(series) + gBonus));
            }
        }

        // ---- 2.5. 1976 first-day postmark ----
        if (firstDay && parseYear(series) == 1976) {
            return new Result(fmt(5 * mult, 100 * mult), "1976 first-day postmarked note",
                    true, clamp(25 + gBonus));
        }

        // ---- 3. base series value ----
        int[] b = baseValue(series);
        boolean worth = b[0] > 2 || b[1] > 2;
        return new Result(fmt(b[0] * mult, b[1] * mult), baseReason(series), worth,
                clamp(baseScore(series) + gBonus));
    }

    // ---------- base series ----------

    private static int[] baseValue(String series) {
        int y = parseYear(series);
        String s = series == null ? "" : series;
        if (y <= 1862) return new int[]{400, 1500};
        if (y <= 1869) return new int[]{250, 900};
        if (y <= 1880) {
            if (s.contains("Brown")) return new int[]{400, 2000};
            if (s.contains("Blue")) return new int[]{250, 1000};
            return new int[]{150, 800};
        }
        if (s.contains("1890") || s.contains("1891")) return new int[]{800, 4000};
        if (s.contains("1896")) return new int[]{1000, 6000};
        if (s.contains("1899")) return new int[]{400, 2500};
        if (s.contains("1918")) return new int[]{300, 1500};
        if (y <= 1917) return new int[]{100, 250};
        if (y <= 1928) {
            if (s.contains("1928B")) return new int[]{25, 50};
            if (s.contains("1928E")) return new int[]{6, 12};
            if (s.contains("1928C") || s.contains("1928D")) return new int[]{15, 75};
            if (s.contains("1928F")) return new int[]{15, 75};
            return new int[]{10, 80};
        }
        if (y <= 1953) return new int[]{5, 30};
        if (y <= 1963) return new int[]{3, 15};
        if (y == 1976) return new int[]{2, 3};
        return new int[]{2, 2};
    }

    private static String baseReason(String series) {
        int y = parseYear(series);
        String s = series == null ? "" : series;
        if (y <= 1862) return "1862 Legal Tender note (red seal)";
        if (y <= 1869) return "1869 'Rainbow' Legal Tender note";
        if (y <= 1880) {
            if (s.contains("Brown")) return "1880 Legal Tender note (brown seal)";
            if (s.contains("Blue")) return "1880 Legal Tender note (blue seal)";
            return "1874\u20131880 Legal Tender note (red seal)";
        }
        if (s.contains("1890")) return "1890 Treasury Note (ornate back)";
        if (s.contains("1891")) return "1891 Treasury Note (ornate back)";
        if (s.contains("1896")) return "1896 'Educational' Silver Certificate";
        if (s.contains("1899")) return "1899 Silver Certificate";
        if (s.contains("1918")) return "1918 large-size note";
        if (y <= 1917) return "1917 Legal Tender note";
        if (y <= 1928) {
            if (s.contains("1928B")) return "1928B red seal note (low print run)";
            if (s.contains("1928E")) return "1928E red seal note (lowest print count)";
            if (s.contains("1928C")) return "1928C red seal note (mule variety)";
            if (s.contains("1928D")) return "1928D red seal note (mule variety)";
            if (s.contains("1928F")) return "1928F red seal note (scarce)";
            if (s.contains("1928A")) return "1928A red seal note";
            if (s.contains("1928G")) return "1928G red seal note";
            return "1928 red seal Legal Tender note";
        }
        if (y <= 1953) return "1953 red seal Legal Tender note";
        if (y <= 1963) return "1963 red seal Legal Tender note";
        if (y == 1976) return "1976 bicentennial green seal (first-day covers $5\u2013$100)";
        return y + " green seal Federal Reserve note";
    }

    private static int baseScore(String series) {
        int y = parseYear(series);
        String s = series == null ? "" : series;
        if (y <= 1862) return 60;
        if (y <= 1869) return 55;
        if (y <= 1880) {
            if (s.contains("Brown")) return 60;
            if (s.contains("Blue")) return 55;
            return 50;
        }
        if (s.contains("1890") || s.contains("1891")) return 70;
        if (s.contains("1896")) return 75;
        if (s.contains("1899")) return 60;
        if (s.contains("1918")) return 55;
        if (y <= 1917) return 45;
        if (y <= 1928) {
            if (s.contains("1928B")) return 55;
            if (s.contains("1928E")) return 40;
            if (s.contains("1928C") || s.contains("1928D")) return 45;
            if (s.contains("1928F")) return 45;
            return 35;
        }
        if (y <= 1953) return 30;
        if (y <= 1963) return 25;
        if (y == 1976) return 10;
        return 5;
    }

    // ---------- star notes ----------

    private static int[] starValue(String series, String district) {
        int y = parseYear(series);
        String s = series == null ? "" : series;
        if (s.contains("1928B")) return new int[]{1000, 10000};
        String d = district == null ? "" : district.trim().toUpperCase();
        if (y == 1976 && (d.equals("J") || d.equals("I"))) return new int[]{15, 90};
        if (s.contains("2003A") && d.equals("F")) return new int[]{20, 100};
        if (s.contains("2017A") && d.equals("B")) return new int[]{100, 250};
        if (y <= 1928) return new int[]{150, 500};
        if (y <= 1953) return new int[]{20, 150};
        if (y <= 1963) return new int[]{10, 50};
        if (y == 1976) return new int[]{4, 15};
        return new int[]{3, 10};
    }

    private static int starScore(String series) {
        String s = series == null ? "" : series;
        if (s.contains("1928B")) return 60;
        int y = parseYear(series);
        if (y <= 1928) return 40;
        if (y <= 1953) return 35;
        if (y <= 1963) return 30;
        if (y == 1976) return 20;
        return 15;
    }

    // ---------- fancy serials ----------

    private static String fancySerial(String d) {
        if (d == null || d.length() != 8 || !d.matches("[0-9]{8}")) return null;
        if (d.chars().distinct().count() == 1) return "solid serial (all " + d.charAt(0) + "s)";
        if (d.equals("12345678") || d.equals("87654321")) return "ladder serial";
        if (isBrokenLadder(d)) return "broken ladder serial";
        if (isSuperRepeater(d)) return "super repeater serial";
        if (hasSevenInRow(d)) return "7-in-a-row serial";
        if (d.matches("[01]{8}")) return "true binary serial (0s and 1s)";
        if (d.matches("[0689]{8}")) return "flipper serial (0/6/8/9 only)";
        if (d.chars().distinct().count() == 2) return "binary serial (two digits)";
        if (d.substring(0, 4).equals(d.substring(4, 8))) return "repeater serial";
        if (new StringBuilder(d).reverse().toString().equals(d)) return "radar (palindrome) serial";
        long n = Long.parseLong(d);
        if (n <= 99) return "low serial #" + n;
        if (n >= 99999900) return "high serial #" + n;
        return null;
    }

    private static int[] fancyValue(String d) {
        long n = Long.parseLong(d);
        if (d.chars().distinct().count() == 1) return new int[]{1000, 4000};
        if (d.equals("12345678") || d.equals("87654321")) return new int[]{900, 1900};
        if (isBrokenLadder(d)) return new int[]{20, 50};
        if (isSuperRepeater(d)) return new int[]{100, 300};
        if (hasSevenInRow(d)) return new int[]{50, 150};
        if (d.matches("[01]{8}")) return new int[]{40, 150};
        if (d.matches("[0689]{8}")) return new int[]{20, 60};
        if (d.chars().distinct().count() == 2) return new int[]{20, 75};
        if (d.substring(0, 4).equals(d.substring(4, 8))) return new int[]{25, 50};
        if (new StringBuilder(d).reverse().toString().equals(d)) return new int[]{20, 60};
        if (n <= 9) return new int[]{500, 1000};
        if (n <= 99) return new int[]{100, 300};
        if (n >= 99999990) return new int[]{500, 500};
        if (n >= 99999900) return new int[]{100, 300};
        return null;
    }

    private static int fancyScore(String d) {
        long n = Long.parseLong(d);
        if (d.chars().distinct().count() == 1) return 50;
        if (d.equals("12345678") || d.equals("87654321")) return 45;
        if (isBrokenLadder(d)) return 10;
        if (isSuperRepeater(d)) return 25;
        if (hasSevenInRow(d)) return 20;
        if (d.matches("[01]{8}")) return 15;
        if (d.matches("[0689]{8}")) return 12;
        if (d.chars().distinct().count() == 2) return 10;
        if (d.substring(0, 4).equals(d.substring(4, 8))) return 15;
        if (new StringBuilder(d).reverse().toString().equals(d)) return 15;
        if (n <= 9) return 45;
        if (n <= 99) return 35;
        if (n >= 99999900) return 25;
        return 0;
    }

    // ---------- errors ----------

    private static int[] errorValue(String type) {
        switch (type) {
            case "Missing 2nd print": return new int[]{400, 550};
            case "Inverted overprint": return new int[]{375, 550};
            case "Missing 1st print (back)": return new int[]{225, 450};
            case "Missing 3rd print": return new int[]{300, 450};
            case "Mismatched serial numbers": return new int[]{275, 400};
            case "Overprint on back": return new int[]{250, 400};
            case "Missing seal": return new int[]{125, 250};
            case "Misaligned overprint (major shift)": return new int[]{125, 300};
            case "Foldover": return new int[]{225, 450};
            case "Butterfly fold": return new int[]{150, 350};
            case "Obstructed printing": return new int[]{20, 125};
            case "Offset ink transfer": return new int[]{75, 150};
            case "Insufficient inking": return new int[]{50, 120};
            case "Miscut": return new int[]{40, 125};
            case "Ink smear": return new int[]{45, 100};
            case "Gutter fold": return new int[]{35, 100};
            case "Minor shift": return new int[]{45, 80};
            case "1928C mule (micro/macro back)": return new int[]{75, 750};
            case "1928D mule (macro/micro back)": return new int[]{15, 75};
            case "1928D star mule": return new int[]{25, 125};
            default: return null;
        }
    }

    // ---------- helpers ----------

    private static boolean isSuperRepeater(String d) {
        return d.charAt(0) == d.charAt(2) && d.charAt(2) == d.charAt(4) && d.charAt(4) == d.charAt(6)
                && d.charAt(1) == d.charAt(3) && d.charAt(3) == d.charAt(5) && d.charAt(5) == d.charAt(7)
                && d.charAt(0) != d.charAt(1);
    }

    private static boolean hasSevenInRow(String d) {
        for (int i = 0; i <= 2; i++) {
            if (d.charAt(i) == d.charAt(i + 1) && d.charAt(i + 1) == d.charAt(i + 2)
                    && d.charAt(i + 2) == d.charAt(i + 3) && d.charAt(i + 3) == d.charAt(i + 4)
                    && d.charAt(i + 4) == d.charAt(i + 5) && d.charAt(i + 5) == d.charAt(i + 6)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBrokenLadder(String d) {
        if (d.chars().distinct().count() != 8) return false;
        char[] arr = d.toCharArray();
        java.util.Arrays.sort(arr);
        int gaps = 0;
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] - arr[i - 1] != 1) gaps++;
        }
        return gaps == 1;
    }

    private static String fmt(double low, double high) {
        long l = Math.round(low);
        long h = Math.round(high);
        if (l == h) return "$" + comma(l);
        return "$" + comma(l) + " - $" + comma(h);
    }

    private static String comma(long n) {
        if (n < 1000) return String.valueOf(n);
        String s = String.valueOf(n);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) sb.append(',');
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    static int parseYear(String series) {
        if (series == null) return 2000;
        String s = series.replaceAll("[^0-9]", "");
        if (s.length() >= 4) {
            try { return Integer.parseInt(s.substring(0, 4)); } catch (Exception ignored) {}
        }
        return 2000;
    }

    static String extractDigits(String serial) {
        if (serial == null) return "";
        return serial.replaceAll("[^0-9]", "");
    }
}
