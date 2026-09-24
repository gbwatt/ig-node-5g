# 📱 IG-Node 5G Native Android APK: Build & Scale Guide

This native Android application (`com.igsave.node`) turns any Android phone (Jio 5G, Airtel 4G, etc.) into an unbannable, always-on **Residential Fleet Swarm Relay** for IG-Save.

---

## 🏗️ Architecture & Features

* **Foreground Service with Notification:** Runs 24/7 in the background without Android OS killing it.
* **BootReceiver:** Automatically restarts the relay service when the phone is turned on or rebooted.
* **WakeLock & Battery Exemption:** Bypasses Xiaomi MIUI, Samsung OneUI, and OnePlus aggressive task-killers.
* **4-Door Stealth Failover:** Automatically switches between `api.ig-save.com`, `api.thumbnailify.com`, `api.needsuite.com`, and `api.mbios.net`.
* **OkHttp 4.12 & Kotlin Coroutines:** Ultra-lightweight asynchronous HTTP polling; only resolves ~5KB JSON metadata (never proxies heavy 50MB video downloads through mobile bandwidth).

---

## 🛠️ Option A: Build in Android Studio (Local PC)

1. Download and install **[Android Studio](https://developer.android.com/studio)** on your computer.
2. Open Android Studio &rarr; Click **Open**.
3. Select the folder:
   ```text
   C:\xampp\htdocs\ig-save\android-node
   ```
4. Wait 1 minute for Gradle to sync dependencies.
5. In the top menu, click **Build &rarr; Build Bundle(s) / APK(s) &rarr; Build APK(s)**.
6. Android Studio will show a popup: **"APK(s) generated successfully"**. Click **locate** to get `app-debug.apk`.
7. Transfer this `.apk` to your phone via WhatsApp, USB cable, or Google Drive, and install it!

---

## ☁️ Option B: Auto-Build in GitHub Actions (0 Install Required)

If you don't want to install 3GB of Android Studio on your PC:
1. Push this repository to **GitHub**.
2. Go to the **Actions** tab in your GitHub repository.
3. You will see the workflow: **"Build Android 5G IG-Node APK"**.
4. Click **Run workflow**.
5. In ~2 minutes, GitHub's cloud servers will compile the APK and give you a downloadable zip containing:
   ```text
   ig-node-5g-debug.apk
   ```
6. Download it directly on your Android phone and install!

---

## 📱 How to Run on Your Phone:

1. Open **IG-Node 5G** on your Android phone.
2. If prompted, tap **"Allow"** for:
   - Notifications (required for 24/7 background service).
   - Ignore Battery Optimizations (prevents OS from killing it in pocket).
3. If testing locally on your Wi-Fi, tap **⚙️ Configure Gateway URL** and ensure it points to:
   ```text
   http://192.168.190.25/ig-save
   ```
   *(In production, it automatically uses `https://api.ig-save.com`)*
4. Tap **[ 🟢 START 5G NODE ]**.
5. Check your Secret Admin Panel at `/02062002admin`:
   Your phone will appear with a 📱 **Android Phone** badge, carrier name (`Jio 5G`), and live latency!
