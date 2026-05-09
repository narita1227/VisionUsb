# vision-usb app

Android client for the USB baseline pipeline.

## What it does

- Shows processed MJPEG stream from `python-server`
- Shows simple WS metrics (fps and latency)

## Build

```powershell
.\gradlew :app:assembleDebug
```

## Optional local.properties keys

```properties
visionusb.server.realDeviceHost=192.168.x.x
visionusb.server.emulatorHost=10.0.2.2
visionusb.server.httpPort=8081
visionusb.server.wsPort=8766
visionusb.server.host.override=
```
