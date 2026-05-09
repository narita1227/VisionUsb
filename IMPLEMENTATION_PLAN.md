# Implementation Plan (2026-05-08)

## 1. 背景と方針
本計画は以下 3 本を「別プロジェクト」として実装するための正式計画である。

1. VisionUsb
2. VisionCameraX
3. ImuSensorBridge

配置方針:
- VisionUsb: `c:/Users/narit/AndroidStudioProjects/VisionUsb`
- VisionCameraX: `c:/Users/narit/AndroidStudioProjects/VisionCameraX`
- ImuSensorBridge: `c:/Users/narit/AndroidStudioProjects/ImuSensorBridge`
- VisionUsb 配下に VisionCameraX / ImuSensorBridge を同居させない

前提として、既存ワークスペースは VisionUsb のベース実装であり、VisionCameraX と ImuSensorBridge は未着手である。

## 2. 現状整理 (As-Is)

### 2.1 VisionUsb (現ワークスペース)
- 実装済み: Python で USB カメラを取得し、Android で表示
- 通信: MJPEG (映像) + WS (状態)

### 2.2 VisionCameraX
- 未実装

### 2.3 ImuSensorBridge
- 未実装

## 3. プロジェクト定義 (To-Be)

### 3.1 Project A: VisionUsb
目的:
- PC 側 USB カメラ入力を Android 表示する基準系を維持

スコープ:
- Python: USB カメラ入力、軽量処理、配信
- Android: ストリーム表示、接続状態表示

完了条件:
- 実機で 10 分連続表示
- 再接続時に自動復帰

### 3.2 Project B: VisionCameraX
目的:
- Android CameraX 入力を Python 受信・処理し、Android へ戻して表示

スコープ:
- Android: CameraX でフレーム取得
- 送信: WS を第一候補 (一本化検証)
- Python: 受信・処理・返送
- Android: 返送映像を描画

完了条件:
- 目標 20fps 以上 (720p または同等条件)
- E2E 遅延 200ms 以下 (P95)
- 5 分連続でクラッシュなし

### 3.3 Project C: ImuSensorBridge
目的:
- Android IMU を Python 受信・処理し、Android へ結果を戻して表示

スコープ:
- Android: 加速度/ジャイロ取得
- Python: フィルタ処理 (例: LPF, 移動平均, 姿勢推定の簡易版)
- Android: 処理後データをリアルタイム表示

完了条件:
- センサ更新レート表示
- Python 処理結果の遅延可視化
- 欠損率 1% 未満 (5 分計測)

## 4. 通信方針

### 4.1 段階方針
- Phase 1 (短期): VisionUsb は現行維持 (MJPEG + WS)
- Phase 2 (中期): VisionCameraX / ImuSensorBridge は WS 中心で実装
- Phase 3 (必要時): 映像系の高性能化が必要なら WebRTC 検討

### 4.2 共通メッセージ仕様
全プロジェクトで以下エンベロープを統一する。

```json
{
  "type": "status|video|imu|control",
  "source": "android|python-server",
  "timestamp_ms": 1710000000000,
  "seq": 123,
  "payload": {}
}
```

## 5. 実装マイルストーン

### M1: Plan Freeze
- 本ドキュメント確定
- 命名、受け入れ条件、計測項目を固定

### M2: VisionUsb Stabilize
- 既存系の回帰防止
- 接続/切断/再接続の安定化

### M3: VisionCameraX v0
- CameraX -> Python -> Android 往復を最小構成で成立

### M4: ImuSensorBridge v0
- IMU -> Python -> Android 往復を最小構成で成立

### M5: Performance Gate
- 目標値と実測値を比較し、WS 継続 or WebRTC 移行判断

## 6. タスク分解

### 6.1 共通タスク
1. 共通メッセージ仕様をソースコード定数として定義
2. 計測項目 (fps, latency, drop) を統一
3. ログ形式を統一

### 6.2 VisionCameraX タスク
1. Android CameraX キャプチャ実装
2. フレーム送信 (WS)
3. Python 側受信と処理パイプライン
4. Android 側描画
5. バックプレッシャ処理 (最新優先)

### 6.3 ImuSensorBridge タスク
1. Android センサ収集のレート制御
2. Python 側 IMU 処理
3. Android 側可視化 UI
4. 時刻同期と遅延計測

## 7. リスクと対策

1. 帯域不足
- 対策: 解像度/FPS/JPEG品質を動的制御

2. 遅延増大
- 対策: キュー上限設定、古いフレーム破棄

3. 接続不安定
- 対策: 指数バックオフ再接続、heartbeat

4. 実装拡散
- 対策: 3 プロジェクトを分離し、共通仕様のみ共有

## 8. 成果物一覧

1. VisionUsb: 安定版
2. VisionCameraX: Android CameraX 往復表示版
3. ImuSensorBridge: IMU 往復可視化版
4. 各プロジェクト README
5. 計測レポート (遅延/FPS/欠損率)

## 9. 実施ルール

1. 変更はプロジェクト単位で行う
2. 受け入れ条件を満たさない機能は完了扱いにしない
3. 計測値なしで性能評価を確定しない

## 10. 次アクション

1. VisionCameraX 用の新規プロジェクトフォルダを作成
2. ImuSensorBridge 用の新規プロジェクトフォルダを作成
3. 共通メッセージ仕様を 3 プロジェクトへ反映