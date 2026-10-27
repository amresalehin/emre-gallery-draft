# Third-Party Notices & Machine Learning Model Provenance

This project contains or downloads pre-trained deep learning models and algorithms for on-device computer vision tasks (face detection, face recognition, and optical character recognition). All models execute entirely on-device with zero network telemetry or biometric data upload.

---

## 1. Ente Photos Face Pipeline (`ente_yolo5face_mobilefacenet`)

### YOLO5Face Detector (`yolov5n-face.onnx`)
- **Origin / Upstream**:
  - Research: *YOLO5Face: Why Should You Take a Closer Look at YOLO for Face Detection?* (Delong Qi et al.)
  - Implementation: `deepcam-cn/yolov5-face` and `ente-io/photos` ML pipeline
- **License**: MIT / GPL-3.0 compatible
- **Architecture**: YOLOv5 Nano backbone with 5-point facial landmark regression head
- **Input Dimensions**: `1 x 3 x 640 x 640` (RGB float32, normalized to `[0.0, 1.0]`)
- **Outputs**:
  - Tensor `[1, 25200, 16]`: 4 box coordinates (cx, cy, w, h), 1 objectness score, 1 face class score, 10 landmark coordinates (5 points x, y)
- **Runtime File**: `filesDir/face-models/ente_yolo5face_mobilefacenet/yolov5n-face.onnx`

### MobileFaceNet Embedder (`mobilefacenet.onnx`)
- **Origin / Upstream**:
  - Research: *MobileFaceNets: Efficient CNNs for Accurate Real-Time Face Verification on Mobile Devices* (Sheng Chen et al.)
  - Implementation: InsightFace / Xiaocong / `ente-io/photos`
- **License**: MIT License
- **Architecture**: MobileFaceNet with global depthwise convolution
- **Input Dimensions**: `1 x 3 x 112 x 112` (RGB float32, normalized via `(pixel - 127.5) / 128.0`)
- **Outputs**:
  - Tensor `[1, 512]`: 512-dimensional facial feature representation, L2 normalized
- **Alignment Template**: 5-point similarity transform using canonical InsightFace coordinates `(38.2946, 51.6963), (73.5318, 51.5014), (56.0252, 71.7366), (41.5493, 92.3655), (70.7299, 92.2041)`
- **Runtime File**: `filesDir/face-models/ente_yolo5face_mobilefacenet/mobilefacenet.onnx`

---

## 2. Buffalo_L Pipeline (`buffalo_l`)

### SCRFD-10GF (`det_10g.onnx`) & ArcFace W600K_R50 (`w600k_r50.onnx`)
- **Origin / Upstream**: InsightFace (DeepInsight)
  - GitHub: https://github.com/deepinsight/insightface
- **License**: Non-commercial / Research use (InsightFace License)
- **Architecture**:
  - Detector: SCRFD-10GF (Sample and Computation Redistribution for Face Detection)
  - Embedder: ResNet-50 ArcFace trained on WebFace600K (512-d feature vector)
- **Input Dimensions**: `640 x 640` (detector), `112 x 112` (recognizer)
- **Runtime File**: `filesDir/face-models/buffalo_l/`

---

## 3. Tesseract OCR Engine

- **Origin / Upstream**: Google / Tesseract OCR
  - GitHub: https://github.com/tesseract-ocr/tessdata_fast
- **License**: Apache License 2.0
- **Model**: `eng.traineddata` (LSTM neural network model for English character recognition)
- **Location**: `app/src/main/assets/tessdata_fast/eng.traineddata`
