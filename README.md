# 📱 IG-Node Android 5G Residential Node

This project turns any Android phone into a high-speed, unbannable **Cellular Residential Relay Node** for IG-Save.

---

## ⚡ 3 Ways to Run on Android:

### Method 1: Instant Mobile Web Runner (Zero App Install)
1. Ensure your phone is connected to the same Wi-Fi as your PC (or use your Cloudflare domain/tunnel).
2. Open in Chrome on Android:
   ```text
   http://192.168.190.25/ig-save/node
   ```
   *(Or scan the QR code in the Secret Admin Panel at `/02062002admin`)*
3. Tap **[ 🟢 START NODE ]**.
4. The phone acquires Screen WakeLock and immediately starts processing resolution jobs!

---

### Method 2: Termux Background Daemon (24/7 Screen-Off Mode)
1. Install [Termux from F-Droid](https://f-droid.org/packages/com.termux/).
2. Run this single command:
   ```bash
   pkg update -y && pkg install nodejs curl -y && curl -s http://192.168.190.25/ig-save/relay/android_node.js -o android_node.js && node android_node.js
   ```
3. Type `termux-wake-lock` to keep it running 24/7 with the screen turned off.

---

### Method 3: Compile Native APK (`ig-node.apk`)
1. Open this `android-node` folder in **Android Studio**.
2. Click **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**.
3. Install the generated `.apk` on your phone.
4. Tap **[ 🟢 START 5G NODE ]**. It runs as an always-on Android Foreground Service with boot-auto-restart!
