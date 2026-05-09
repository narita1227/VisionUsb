# VisionUsb Prompt

あなたは VisionUsb の実装担当です。以下の要件だけを実装してください。

## 目的
- Python で USB カメラ入力
- 軽量処理して Android で表示

## 通信
- 映像: MJPEG (HTTP)
- 状態: WebSocket

## 非対象
- CameraX 入力
- IMU 取得/処理

## 受け入れ条件
1. Android 実機で映像が継続表示される
2. FPS と遅延がステータス表示される
3. 10 分連続運転でクラッシュしない
