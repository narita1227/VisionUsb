# VisionUsb

USB camera baseline validation project.

## Structure

- `app/`: Android client (MJPEG display + WS metrics)
- `python-server/`: Python server (USB input + lightweight processing)

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

- MJPEG: `http://<PC_LAN_IP>:8081/mjpeg`
- WS status: `ws://<PC_LAN_IP>:8766/ws/status`
