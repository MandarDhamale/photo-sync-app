# PhotoSync

PhotoSync is a privacy-first application that securely backs up photos from your Android phone directly to your personal computer (Windows or Linux) over your local home Wi-Fi network. 

**Zero Cloud:** Photos are transferred directly over your local network. No third-party servers, no monthly storage fees, and complete privacy.
**Easy Pairing:** Uses a secure QR code to pair your phone and your computer instantly.
**Standalone Backend:** The server comes bundled with a Java runtime and an embedded SQLite database. No setup required!

## How to Use 

### Prerequisites
Make sure your Android phone and your computer are connected to the **same Wi-Fi network**.

### Part 1: Getting your Computer Ready
1. Go to the **Releases** page of this repository and download the `PhotoSyncServer` package for your operating system (Windows or Linux).
2. Unzip the downloaded folder on your computer.
3. Open the folder, go into the `bin` directory, and double-click the `PhotoSyncServer` application to start the receiver.
4. Open your web browser on your computer and go to `http://localhost:8080`. You will be greeted by the PhotoSync logo and a secure pairing QR code.

### Part 2: Getting your Phone Ready
1. Download and install the `android_app.apk` from the Releases page on your Android phone.
2. When you open the app, tap the **Settings (gear) icon** in the top right corner.
3. Your camera will open. Point it at the QR code displayed on your computer screen. Once paired, your phone will remember your computer securely!

### Part 3: Syncing your Photos
1. Tap the **Sync Now** (or plus) button. 
2. The app will ask for permission to access your photos. (On Android 14+, you can safely choose "Select photos" to back up specific memories, or "Allow all" for a full backup).
3. A progress wheel will appear while your photos zip across your Wi-Fi directly into your computer. 
4. The counter at the top of the app will update to show exactly how many photos were successfully synced!

**Where do my photos go?**
On your computer, look inside the unzipped `PhotoSyncServer` folder. You will see an `uploads` folder. All your photos are safely backed up inside!

## For Developers

### Tech Stack
- **Android App:** Kotlin, Material Design 3, Coroutines, Retrofit, CameraX (for QR scanning).
- **Desktop Backend:** Spring Boot, Java 21, SQLite (Embedded), ZXing (QR generation).

### Automated CI/CD Builds
This repository uses GitHub Actions to automatically build the project.
Every time code is pushed to the `main` branch, the workflow will use `jpackage` to compile standalone, dependency-free executables for both **Linux** and **Windows**. 
You can download these automatically generated `.zip` bundles from the "Actions" tab.

### Local Development Setup

**Backend:**
```bash
cd backend
mvn clean package -DskipTests
# To package the desktop app locally (requires Java 21)
./package.sh
```

**Android:**
Open the `app/` directory in Android Studio. Ensure you have the Android SDK installed and sync your Gradle project to build the APK.

## Security
The app works securely over your local network. When the backend starts, it generates a random UUID authentication token. This token is embedded in the QR code. Your Android app uses this token to authenticate all upload requests, ensuring that no unauthorized devices on your network can upload files to your computer. (Note: Traffic is sent over standard HTTP, so it is designed for trusted private home networks).
