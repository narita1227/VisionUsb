import asyncio
import json
import logging
import time
from dataclasses import dataclass

import cv2
import websockets
from websockets.exceptions import ConnectionClosed

HOST = "0.0.0.0"
WS_PORT = 8766
CAMERA_INDEX = 1
OUTPUT_WIDTH = 640
OUTPUT_HEIGHT = 480
JPEG_QUALITY = 70
FRAME_INTERVAL_SEC = 0.03

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
logger = logging.getLogger("visionusb_server")


@dataclass
class StreamMetrics:
    fps: float = 0.0
    latency_ms: float = 0.0
    last_frame_ts_ms: int = 0
    frame_seq: int = 0


class StreamState:
    def __init__(self):
        self.latest_jpeg: bytes | None = None
        self.lock = asyncio.Lock()
        self.metrics = StreamMetrics()


def now_ms() -> int:
    return int(time.time() * 1000)


def build_ws_message(msg_type: str, source: str, seq: int, payload: dict) -> str:
    envelope = {
        "type": msg_type,
        "source": source,
        "timestamp_ms": now_ms(),
        "seq": seq,
        "payload": payload,
    }
    return json.dumps(envelope, ensure_ascii=False)


def process_frame(frame):
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    out = cv2.cvtColor(gray, cv2.COLOR_GRAY2BGR)
    cv2.putText(
        out,
        time.strftime("%H:%M:%S"),
        (16, 36),
        cv2.FONT_HERSHEY_SIMPLEX,
        1.0,
        (255, 255, 255),
        2,
    )
    return out


async def capture_loop(state: StreamState):
    cap = cv2.VideoCapture(CAMERA_INDEX)
    cap.set(cv2.CAP_PROP_FRAME_WIDTH, OUTPUT_WIDTH)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, OUTPUT_HEIGHT)

    if not cap.isOpened():
        raise RuntimeError(f"camera open failed index={CAMERA_INDEX}")

    logger.info("camera opened index=%d", CAMERA_INDEX)

    frame_count = 0
    last_fps_at = time.time()

    try:
        while True:
            t0 = time.time()
            ok, frame = cap.read()
            if not ok:
                logger.warning("camera read failed")
                await asyncio.sleep(0.05)
                continue

            processed = process_frame(frame)
            encode_ok, buf = cv2.imencode(
                ".jpg", processed, [int(cv2.IMWRITE_JPEG_QUALITY), JPEG_QUALITY]
            )
            if not encode_ok:
                logger.warning("cv2.imencode returned false")
                await asyncio.sleep(FRAME_INTERVAL_SEC)
                continue

            frame_count += 1
            now = time.time()
            if now - last_fps_at >= 1.0:
                state.metrics.fps = frame_count / (now - last_fps_at)
                frame_count = 0
                last_fps_at = now

            state.metrics.latency_ms = (now - t0) * 1000.0
            state.metrics.last_frame_ts_ms = int(now * 1000)
            state.metrics.frame_seq += 1

            async with state.lock:
                state.latest_jpeg = buf.tobytes()

            await asyncio.sleep(FRAME_INTERVAL_SEC)
    finally:
        cap.release()
        logger.info("camera released")


async def ws_handler(websocket, state: StreamState):
    client = getattr(websocket, "remote_address", None)
    logger.info("ws client connected: %s", client)
    last_sent_seq = -1

    try:
        while True:
            async with state.lock:
                jpeg = state.latest_jpeg
                metrics = StreamMetrics(
                    fps=state.metrics.fps,
                    latency_ms=state.metrics.latency_ms,
                    last_frame_ts_ms=state.metrics.last_frame_ts_ms,
                    frame_seq=state.metrics.frame_seq,
                )

            if jpeg is not None and metrics.frame_seq != last_sent_seq:
                payload = {
                    "fps": metrics.fps,
                    "latency_ms": metrics.latency_ms,
                    "last_frame_ts_ms": metrics.last_frame_ts_ms,
                    "frame_seq": metrics.frame_seq,
                }
                await websocket.send(
                    build_ws_message(
                        msg_type="frame_meta",
                        source="python-server",
                        seq=metrics.frame_seq,
                        payload=payload,
                    )
                )
                await websocket.send(jpeg)
                last_sent_seq = metrics.frame_seq

            await asyncio.sleep(0.01)
    except ConnectionClosed:
        logger.info("ws client disconnected: %s", client)
        return
    except Exception:
        logger.exception("ws_handler failed for client=%s", client)
        return


async def start_ws_server(state: StreamState):
    return await websockets.serve(
        lambda websocket: ws_handler(websocket, state),
        HOST,
        WS_PORT,
    )


async def main():
    state = StreamState()
    await start_ws_server(state)
    logger.info("WS video stream: ws://%s:%d/ws/video", HOST, WS_PORT)
    await capture_loop(state)


if __name__ == "__main__":
    asyncio.run(main())
