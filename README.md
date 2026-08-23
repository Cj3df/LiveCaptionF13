# LiveCaptions for Samsung Galaxy F13 📱
### High-Accuracy Real-Time Speech-to-Text & Floating Subtitle Overlay

Designed and optimized specifically for the **Samsung Galaxy F13** (running One UI Core / Android 12, 13, 14 with Exynos 850).

---

## 🌟 Key Features

1. **Direct Internal Media Audio Capture (Android 10+)**
   - Captures digital sound directly from YouTube, Instagram Reels, Netflix, Chrome, Twitter/X, Podcasts, and Games without ambient room noise.
2. **Microphone Fallback Mode**
   - Allows captioning in-person conversations, ambient lectures, or speakerphone calls.
3. **High-Accuracy Cloud Speech Engine (Deepgram Nova-2)**
   - Sub-300ms ultra-low streaming latency.
   - Automatic punctuation, capitalization, number formatting, and smart formatting.
   - Supports English, Hindi, and 30+ languages.
4. **Draggable & Customizable Floating Subtitles**
   - System overlay window (`SYSTEM_ALERT_WINDOW`) that floats over any active app.
   - Drag to reposition anywhere on the screen.
   - Adjustable font sizes (Small / Medium / Large).
   - Adjustable background transparency / opacity (30% to 100%).
   - Live streaming text: Finalized text in bold white, interim speech in italicized light gray.
5. **Samsung One UI Background Optimization**
   - Runs as a Foreground Service with notification controls (`Start`, `Pause`, `Stop`) so Samsung's background app killer does not terminate the captions mid-video.

---

## 🛠️ Project Structure

```
LiveCaptionF13/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/livecaption/f13/
│   │   │   ├── MainActivity.kt               # Main configuration & controls UI
│   │   │   ├── service/
│   │   │   │   ├── CaptionForegroundService.kt # Foreground service & notification
│   │   │   │   ├── AudioCaptureManager.kt      # Internal audio / mic capture stream
│   │   │   │   └── FloatingCaptionOverlay.kt   # Draggable floating window overlay
│   │   │   ├── stt/
│   │   │   │   ├── DeepgramLiveSttEngine.kt   # Real-time WebSocket streaming client
│   │   │   │   └── SpeechRecognitionCallback.kt
│   │   │   ├── model/
│   │   │   │   ├── AppSettings.kt             # Audio source & font models
│   │   │   │   └── CaptionState.kt
│   │   │   └── utils/
│   │   │       ├── PreferenceHelper.kt        # SharedPreferences storage
│   │   │       └── PermissionHelper.kt        # Permission verification
│   │   └── res/                               # Layouts, drawables, themes, colors
│   └── build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 🚀 How to Build and Run

### 1. Open in Android Studio
1. Open **Android Studio** (Hedgehog / Iguana / Jellyfish or newer).
2. Select **Open** and select the folder `LiveCaptionF13`.
3. Allow Gradle to sync dependencies automatically.

### 2. Get a Free Deepgram API Key (No Credit Card Required)
1. Go to [https://deepgram.com](https://deepgram.com) and create a free account.
2. You will instantly receive **$200 in free credits** (enough for hundreds of hours of live captioning).
3. Go to the dashboard, generate an **API Key**, and copy it.

### 3. Run on Samsung Galaxy F13
1. Enable **Developer Options** and **USB Debugging** on your Samsung Galaxy F13:
   - Go to `Settings` -> `About phone` -> `Software information`.
   - Tap `Build number` 7 times.
   - Go back to `Settings` -> `Developer options` -> Enable `USB debugging`.
2. Connect your phone via USB cable and select it in Android Studio.
3. Click the **Run ▶** button (or press `Shift + F10`).

---

## 📱 How to Use on the Phone

1. Open **Live Captions**.
2. Paste your **Deepgram API Key** into the settings field and tap **Save Key**.
3. Select **Audio Source**:
   - `Internal Audio` for YouTube, Reels, movies, and games.
   - `Microphone` for live room voices or calls.
4. Tap **Start Captions**.
5. Grant the permissions requested:
   - *Display over other apps* (allows floating overlay).
   - *Microphone / Audio Capture*.
   - *Start recording / Cast permission* (Android system prompt for internal audio capture).
6. Switch to YouTube, Instagram, or any video app — your live captions will appear in real time!
