# vision-usb-python

USB camera input -> lightweight Python processing -> MJPEG/WS output server.

## Setup

```powershell
python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt
```

## Run

```powershell
python app.py
```

## Endpoints

- MJPEG: http://0.0.0.0:8081/mjpeg
- WS status: ws://0.0.0.0:8766/ws/status

Use your PC LAN IP from Android real devices.
