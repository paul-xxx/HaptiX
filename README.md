# HaptiX

**HaptiX** is a powerful Android utility designed to convert standard audio files into OGG Opus files with embedded haptic feedback data. It uses the "audio-coupled haptics" API to drive your device's vibration motor based on the audio content, specifically targeting low-frequency signals.

## Features

- **Multi-format Support**: Convert MP3, FLAC, WAV, OGG, AAC, M4A, and more.
- **Advanced Processing**: Uses FFmpeg to extract channels and generate a dedicated sub-channel for haptic feedback.
- **Discrete Channels**: Implements Opus `mapping_family 255` to ensure 3 clean, independent channels (Left, Right, and Haptic).
- **Embedded Metadata**: Automatically adds `ANDROID_HAPTIC=1` flag and preserves original tags (Artist, Album, etc.).
- **Built-in Player**: Test your converted files immediately with full haptic support and standard playback controls (Shuffle, Repeat, Next/Prev).
- **File Management**: Share, rename, delete, or view detailed info for your converted files.
- **Multilingual**: Supports English, Russian, Ukrainian, Kazakh, Portuguese (Brazil), Spanish, and Belarusian.

## How It Works

HaptiX performs a "hardcore" assembly of your audio:
1. **Extraction**: Splits the original audio into Left and Right channels.
2. **Sub-channel Generation**: Mixes L+R and applies filters (`highpass=60Hz`, `lowpass=250Hz`, `alimiter`) to create a haptic-ready signal.
3. **Opus Encoding**: Merges the three channels into a single OGG file using a specific channel mapping that Android's system player recognizes as haptic-coupled audio.

## Installation

1. Download the latest `app-debug.apk`.
2. Install it on your Android device (requires Android 15+ for full feature support).
3. Grant necessary permissions (Media access and Vibration).

## Usage

1. Launch HaptiX.
2. Tap the **Prepare** button at the top.
3. Select any audio file from your device.
4. Wait for the conversion to finish.
5. Tap the file in the list to play it with haptics.
6. **Long-press** any file for more options (Share, Rename, etc.).

## Disclaimer

> **IMPORTANT**: The author of this application is not responsible for any damage to your device's vibration motor. Continuous use of high-intensity haptics for prolonged periods is not recommended.
>
> **Tip**: You can adjust the vibration intensity in your phone's system settings under "Sound & Vibration".

## License

This project uses FFmpeg Kit which is licensed under LGPL/GPL depending on the build.
