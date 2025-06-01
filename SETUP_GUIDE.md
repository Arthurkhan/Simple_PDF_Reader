# Setup and Build Guide

## Prerequisites

1. **Android Studio** (Latest version recommended)
2. **Android SDK** with API 28+ installed
3. **Android tablet** or emulator for testing

## Building the App

### 1. Clone the Repository
```bash
git clone https://github.com/Arthurkhan/Simple_PDF_Reader.git
cd Simple_PDF_Reader
```

### 2. Open in Android Studio
- Launch Android Studio
- Select "Open an existing project"
- Navigate to the cloned repository folder
- Wait for Gradle sync to complete

### 3. Build the APK

#### Debug APK (for testing):
- Click `Build > Build Bundle(s) / APK(s) > Build APK(s)`
- APK will be in `app/build/outputs/apk/debug/`

#### Release APK (for distribution):
1. Generate a signing key:
   - `Build > Generate Signed Bundle / APK`
   - Select `APK`
   - Create new keystore or use existing
   - Fill in the certificate information

2. Build signed APK:
   - Choose release build type
   - Select V2 signature
   - Build

## Installation

### On Device:
1. Enable "Install from Unknown Sources" in device settings
2. Transfer APK to device
3. Open APK file to install

### Via ADB:
```bash
adb install app-release.apk
```

## Usage Instructions

### Basic Operation:
1. **Open PDF**: Tap the + button and select "Open File"
2. **Navigate Pages**: Use arrow buttons at bottom
3. **Add to Favorites**: Tap the star icon when viewing a PDF
4. **Access Favorites**: Tap + then star icon
5. **Lock Mode**: Tap + then lock icon

### Lock Mode:
- Enables kiosk-like mode
- Prevents accidental exits
- **To exit**: Double-tap the home button quickly

## Permissions Setup

The app requires storage permission to access PDFs:
- On first file selection, grant storage permission
- The app uses scoped storage for Android 10+

## Troubleshooting

### PDF Won't Load:
- Ensure the PDF is not corrupted
- Check file permissions
- Try a different PDF

### Lock Mode Issues:
- Some devices may restrict lock task mode
- Ensure the app has proper permissions
- Try disabling battery optimization for the app

### Performance:
- Large PDFs may load slowly
- The app renders at 2x resolution for clarity
- Consider reducing PDF size if needed

## Customization

### Change App Name:
Edit `app/src/main/res/values/strings.xml`:
```xml
<string name="app_name">Your App Name</string>
```

### Change Colors:
Edit `app/src/main/res/values/colors.xml`

### Modify Lock Behavior:
Edit `LockScreenManager.kt` for different lock mechanisms

## Notes for Tablets

- Optimized for landscape orientation
- Tested on Android 9+ tablets
- Best experience on 10"+ screens
- Supports both touch and keyboard navigation

## Security Considerations

- PDFs are accessed via Android's Storage Access Framework
- No PDFs are stored within the app
- Favorites only store URI references, not file content
- Lock mode provides basic kiosk functionality

## Support

For issues or questions:
1. Check the update log in `/update_log/`
2. Review the README.md
3. Create an issue on GitHub
