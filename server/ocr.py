"""OCR：RapidOCR(onnxruntime) 多语种识别，det 共用、rec 按行挑最优。"""
from __future__ import annotations

import os
import time

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
M = os.path.join(HERE, "models")

_threads = 4
_common = dict(max_side_len=960, intra_op_num_threads=_threads)

_cache: dict[str, object] = {}


def _engines():
    if _cache:
        return _cache
    from rapidocr_onnxruntime import RapidOCR

    _cache["det"] = RapidOCR(**_common)
    _cache["japan"] = RapidOCR(rec_model_path=os.path.join(M, "japan_rec.onnx"),
                               rec_keys_path=os.path.join(M, "japan_dict.txt"), **_common)
    _cache["ch"] = _cache["det"]
    if os.path.exists(os.path.join(M, "korean_rec.onnx")):
        _cache["korean"] = RapidOCR(rec_model_path=os.path.join(M, "korean_rec.onnx"),
                                    rec_keys_path=os.path.join(M, "korean_dict.txt"), **_common)
    return _cache


def _crop(img: np.ndarray, box: np.ndarray) -> tuple[np.ndarray, bool]:
    x0, y0 = box[:, 0].min(), box[:, 1].min()
    x1, y1 = box[:, 0].max(), box[:, 1].max()
    x0, y0 = max(0, int(x0) - 2), max(0, int(y0) - 2)
    x1, y1 = min(img.shape[1], int(x1) + 2), min(img.shape[0], int(y1) + 2)
    if x1 <= x0 or y1 <= y0:
        return np.zeros((1, 1, 3), dtype=np.uint8), False
    c = img[y0:y1, x0:x1]
    vertical = (y1 - y0) > (x1 - x0) * 2.0
    if vertical:
        c = np.rot90(c, k=-1)  # 竖排转横排再识别
    return np.ascontiguousarray(c), vertical


def ocr(img: Image.Image, mode: str = "auto") -> dict:
    t0 = time.time()
    if img.mode != "RGB":
        img = img.convert("RGB")
    arr = np.asarray(img)[:, :, ::-1].copy()  # RGB->BGR
    eng = _engines()

    boxes, _ = eng["det"].text_det(arr)
    if boxes is None or len(boxes) == 0:
        return {"lines": [], "lang": "unknown", "ms": int((time.time() - t0) * 1000)}
    boxes = eng["det"].sorted_boxes(boxes)

    langs = ["japan", "ch"] if mode == "auto" else [mode]

    crops: list[np.ndarray] = []
    verticals: list[bool] = []
    for b in boxes:
        c, v = _crop(arr, b)
        crops.append(c)
        verticals.append(v)

    from concurrent.futures import ThreadPoolExecutor

    def rec(lg_):
        try:
            out, _ = eng[lg_].text_rec(crops)
            return lg_, out or []
        except Exception:
            return lg_, []

    picks: list[tuple[str, float, str] | None] = [None] * len(crops)

    def merge(results):
        for lg_, out in results:
            for i, item in enumerate(out):
                if i >= len(crops):
                    break
                try:
                    txt, sc = item
                except Exception:
                    continue
                txt = (txt or "").strip()
                if not txt:
                    continue
                cur = picks[i]
                if cur is None or sc > cur[1]:
                    picks[i] = (txt, float(sc), lg_)

    with ThreadPoolExecutor(max_workers=max(1, len(langs))) as ex:
        merge(list(ex.map(rec, langs)))

    # 韩文按需补跑：置信度低或出现谚文
    if mode == "auto" and "korean" in eng:
        best = max((p[1] for p in picks if p), default=0.0)
        has_hangul = any("\uac00" <= ch <= "\ud7a3" for p in picks if p for ch in p[0])
        if best < 0.75 or has_hangul:
            merge([rec("korean")])

    lines: list[dict] = []
    for i, b in enumerate(boxes):
        pick = picks[i]
        if not pick:
            continue
        txt, sc, lg = pick
        x0, y0 = float(b[:, 0].min()), float(b[:, 1].min())
        x1, y1 = float(b[:, 0].max()), float(b[:, 1].max())
        lines.append({"text": txt, "score": sc, "lang": lg, "vertical": verticals[i],
                      "box": [x0, y0, x1, y1]})

    from collections import Counter

    top = Counter(l["lang"] for l in lines).most_common(1)
    return {"lines": lines, "lang": top[0][0] if top else "unknown",
            "ms": int((time.time() - t0) * 1000)}
