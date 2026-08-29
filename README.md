# TheCut 🎬

<div align="center">
  <h2>The Ultimate Mobile Video Editor</h2>
  <p><strong>Fast. Privacy-First. Limitless Creativity.</strong></p>

  <a href="LICENSE">
    <img src="https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge" height="35" alt="License" />
  </a>
  <a href="https://github.com/AntonioPennino/TheCut">
    <img src="https://img.shields.io/badge/GitHub-TheCut-6366f1?style=for-the-badge&logo=github&logoColor=white" height="35" alt="GitHub Repository" />
  </a>
  <br/>
  <br/>
</div>

**TheCut** is an open-source, high-performance mobile video editor for Android. Built for seamless performance and privacy, it empowers creators to edit, compose, and export watermark-free videos entirely on-device with hardware-accelerated rendering.

---

## 🚀 Key Features

- **⚡ Hardware Acceleration** - Ultra-fast and reliable exports using GPU-accelerated `h264_mediacodec` (with automatic fallback to CPU software encoding).
- **🔒 100% Private & Local** - No watermarks, no cloud uploads, no ads, no telemetry.
- **✂️ Precision Timeline & Trim** - Real-time timeline controls, snapping, multi-segment merging, split, and clip rearrangement.
- **🎨 Multi-Layer Overlays** - Text, stickers, images, GIFs, and picture-in-picture video overlays with continuous looping.
- **🎭 Masking & Chroma Key** - Apply creative masks and clean green screen background removal.
- **📍 Keyframes & Animation** - Animate overlays, opacity, and positioning smoothly over time.
- **💬 Interactive Subtitles** - Import custom `.srt` subtitle files with touch-drag positioning and font resizing on preview.
- **🎵 Multi-Track Audio Suite** - Import custom music/audio, record live voiceovers, apply audio ducking and fades, amplify up to 200%, or export audio-only mixes to MP3.
- **📸 Snapshots & Freeze Frames** - Instant high-res frame capture and freeze frame timeline insertion.
- **📐 Aspect Ratios & Canvas** - Presets for 9:16 (TikTok/Reels/Shorts), 16:9 (YouTube), 1:1, custom cropping, and blurred background canvas.
- **✨ Transitions & Filters** - Real-time transition previews, color filters, brightness, contrast, and saturation adjustments.
- **⏱️ Speed & Reversal** - Custom speed adjustments and instant reverse video proxy generation.
- **✏️ Freehand Drawing & Fonts** - Draw on clips with custom brush controls and import custom `.ttf`/`.otf` typography.
- **💾 Project Files** - Non-destructive project state saving and loading.

---

## 🛠️ Building & Developing

### Prerequisites
* **Android Studio** (Hedgehog / Iguana / Ladybug or newer)
* **JDK 17+**
* **Android SDK 34**

### Build Debug APK
```bash
./gradlew assembleDebug
```

---

## 📜 Credits & License

* **Author / Maintainer**: [Antonio Pennino](https://github.com/AntonioPennino)
* **Original Foundation**: Forked from [LibreCuts](https://github.com/tharunbirla/LibreCuts) by [Tharun Birla](https://github.com/tharunbirla).
* **License**: Released under the [MIT License](LICENSE).
