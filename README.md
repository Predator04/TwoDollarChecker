# Two Dollar Checker

Android app that tells you if a $2 bill is worth more than face value.

## Features

- **Manual check** — series (1862–2017A, full red/green/blue/brown seal coverage), serial, star note, grade, printing error, and FRB district → estimated value + a 0–100 collector score.
- **Continuous camera scan** — live camera with a positioning reticle ("move closer / left / right"), auto-reads the serial + series by OCR, then uses GPT-4o vision to detect the ★ star and grade.
- **Photo log** — saves a photo, price, and the "what it found" reason (solid serial, radar, star note, printing error…) for every bill, sorted best-first.
- **Guides** — value guide, grading guide, and a "how to sell" guide with an eBay sold-listings deep link.

Values are verified rough retail/auction guides (US Currency Auctions, PGS Gold & Coin, MyCurrencyCollection), **not appraisals**.

## Build

1. Put your OpenAI key in a gitignored `apikey.properties` file (this file is never committed):

   ```
   OPENAI_KEY=sk-proj-...
   ```

2. Build the debug APK:

   ```
   ./gradlew assembleDebug
   ```

   Output: `app/build/outputs/apk/debug/app-debug.apk`

If you skip `apikey.properties`, the app still works — the AI read (star + grade) just falls back to manual entry. You can also paste a key in the app's Settings screen.

## Notes

- AI read requires internet (GPT-4o) and costs a fraction of a cent per bill; toggle it off in Settings for fast offline OCR-only scanning.
- No $2 "web notes" exist (those are $1-only); live market lookup is skipped because eBay's API was discontinued and Heritage has no public API — the sell guide deep-links instead.
