"""下载 OCR 模型（日文/韩文识别 + 词表）。

用法：python download.py
默认从 ModelScope 拉 RapidAI/RapidOCR 仓库里的 PP-OCRv4 mobile 模型。
"""
import os
import sys
import urllib.request

BASE = "https://www.modelscope.cn/models/RapidAI/RapidOCR/resolve/master"
FILES = {
    "onnx/PP-OCRv4/rec/japan_PP-OCRv4_rec_mobile.onnx": "japan_rec.onnx",
    "paddle/PP-OCRv4/rec/japan_PP-OCRv4_rec_mobile/japan_dict.txt": "japan_dict.txt",
    "onnx/PP-OCRv4/rec/korean_PP-OCRv4_rec_mobile.onnx": "korean_rec.onnx",
    "paddle/PP-OCRv4/rec/korean_PP-OCRv4_rec_mobile/korean_dict.txt": "korean_dict.txt",
}

HERE = os.path.dirname(os.path.abspath(__file__))


def main() -> int:
    for src, dst in FILES.items():
        out = os.path.join(HERE, dst)
        if os.path.exists(out) and os.path.getsize(out) > 1024:
            print(f"skip {dst} ({os.path.getsize(out)} bytes)")
            continue
        url = f"{BASE}/{src}"
        print(f"get {dst} ...", end="", flush=True)
        try:
            with urllib.request.urlopen(url, timeout=120) as r, open(out, "wb") as f:
                f.write(r.read())
            print(f" {os.path.getsize(out)} bytes")
        except Exception as e:  # noqa: BLE001
            print(f" FAIL {e}")
            return 1
    print("done. 中文/英文模型由 rapidocr-onnxruntime 包自带，无需下载。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
