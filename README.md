# सितलानी रोटी छा थ्यो? — Sitlani's Roti Chha Thhayu (Android APK)

A real Android app (WebView shell + native voice, camera and share). GitHub builds the **APK** for you — no Android Studio needed.

## Get the APK (English)
1. github.com → **New repository** (public or private) → create.
2. **Add file → Upload files** → drag **everything inside this folder** (including the `.github` folder) → **Commit changes**.
   - If `.github` was skipped: **Add file → Create new file**, type the name `.github/workflows/build-apk.yml`, paste the text of `BUILD-APK-WORKFLOW.txt`, commit.
3. Open the **Actions** tab → "Build Android APK" runs by itself (about 5 minutes). Wait for the green tick. (Red? tap it, copy the error text and send it to me.)
4. Open **Releases** (right side of the repo page) → download **Sitlani-Roti-Chha-Thhayu.apk** on your phone → open it → allow "Install unknown apps" → Install.

## APK पाओ (हिंदी)
1. github.com पर नई repository बनाइए।
2. **Add file → Upload files** में इस फोल्डर की **सारी फाइलें** (`.github` फोल्डर समेत) डालकर **Commit** कीजिए। (`.github` न जाए तो `BUILD-APK-WORKFLOW.txt` का लिखा हुआ `.github/workflows/build-apk.yml` नाम से नई फाइल बनाइए।)
3. **Actions** टैब में लगभग 5 मिनट रुकिए, हरा निशान आने तक।
4. **Releases** में जाकर **Sitlani-Roti-Chha-Thhayu.apk** फोन में डाउनलोड कीजिए → खोलिए → "Unknown apps install" की अनुमति दीजिए → Install।

## After install
- Open the app → **⚙️ Me → Gemini API key** → paste your key (free: aistudio.google.com/apikey) → Save. Key stays only on the phone.
- 🎙️ Amma uses the phone's own speech engine (Hindi / English; Sindhi if your phone supports it, else Hindi).
- 📷 Fridge scan (Home): take a photo / ≤10-second video or choose from gallery.
- New dishes & jokes later: edit `content.json` in this repo → in the app **Me → Content link** paste the file's *Raw* link → press 🔄. (Gemini also adds fresh content with 🔄.)
- New app version later? Upload the new files → a new APK appears in Releases; installing it over the old one keeps all data (same signing key — keep `app/release.keystore`).

## Notes
- Package: `com.sitlani.rotichhathhayu` · min Android 7.0 · signed with the bundled personal key (password `sitlani123`) — fine for family use, not for Play Store.
- Web app source = `app/src/main/assets/index.html`.
