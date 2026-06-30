# AI-Based Crowd Prediction & Management System

## Android App Setup

### Step 1 — Open in Android Studio
1. Download and extract `CrowdPrediction.zip`
2. Open Android Studio → **File → Open** → select the `CrowdPrediction` folder
3. Wait for Gradle sync to finish (needs internet first time)

### Step 2 — Set your SDK path
Edit `local.properties`:
```
sdk.dir=C\:\\Users\\YourName\\AppData\\Local\\Android\\Sdk   (Windows)
sdk.dir=/Users/YourName/Library/Android/sdk                   (Mac)
sdk.dir=/home/YourName/Android/Sdk                            (Linux)
```

### Step 3 — Run the app
- Connect phone (USB debugging ON) or start an emulator
- Click **Run ▶**

---

## Python Backend Setup

You can run the backend server in a few ways:
- **Localhost (for Android Emulator)**: Double-click `start_localhost.bat` in the `backend` folder.
- **ngrok Tunnel (fixed public URL)**: Double-click `start_ngrok.bat` in the `backend` folder.
- **localtunnel (fixed public subdomain)**: Double-click `start_public.bat` in the `backend` folder.

Or start it manually:
```bash
cd backend
pip install -r requirements.txt
python app.py
```
Server starts at http://0.0.0.0:5000

### Connecting Android to backend
- **Emulator**: already set to `http://10.0.2.2:5000/` ✓
- **Real phone** (same Wi-Fi): edit `NetworkModule.kt` line:
  ```kotlin
  const val BASE_URL = "http://192.168.X.X:5000/"
  ```
  Replace with your PC's local IP (run `ipconfig` / `ifconfig`)

---

## Project Structure
```
CrowdPrediction/
├── app/src/main/
│   ├── java/com/crowdprediction/
│   │   ├── MainActivity.kt
│   │   ├── CrowdApp.kt
│   │   ├── data/models/Models.kt
│   │   ├── data/repository/ApiService.kt
│   │   ├── data/repository/CrowdRepository.kt
│   │   ├── ui/dashboard/DashboardFragment.kt
│   │   ├── ui/dashboard/ZoneCardAdapter.kt
│   │   ├── ui/alerts/AlertsFragment.kt
│   │   ├── ui/alerts/AlertAdapter.kt
│   │   ├── ui/prediction/PredictionFragment.kt
│   │   ├── viewmodel/MainViewModel.kt
│   │   └── utils/NetworkModule.kt
│   └── res/ (layouts, drawables, values, navigation, menu)
├── backend/
│   ├── app.py       ← Flask + ML server
│   └── requirements.txt
├── build.gradle
└── settings.gradle
```

## Team
- Gautham P Sajith
- Jishnu S Namboothiripad
- Aebel Antosh
