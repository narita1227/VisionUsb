# VisionUsb

USB camera baseline validation project.

## Structure

- `app/`: Android client (WebSocket binary frame display + metrics)
- `python-server/`: Python server (USB input + lightweight processing + WebSocket streaming)

Related standalone projects (sibling directories):
- `../VisionCameraX`: CameraX <-> Python round-trip
- `../ImuSensorBridge`: IMU <-> Python round-trip

## Android Build

```powershell
.\gradlew :app:assembleDebug
```

## Python Run

```powershell
cd python-server
python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt
python app.py
```

## Endpoints

- WS video: `ws://<PC_LAN_IP>:8766/ws/video` (text metadata + binary JPEG frames)
