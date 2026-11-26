"""屏幕/漫画 即时翻译服务：OCR(本地) + 翻译(云端) + 位置回传。"""
from __future__ import annotations

import asyncio
import base64
import hashlib
import io
import os
import time

import httpx
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel
from PIL import Image

import mt
import ocr as ocrmod

HERE = os.path.dirname(os.path.abspath(__file__))
CACHE_DIR = os.path.join(HERE, ".cache")
os.makedirs(CACHE_DIR, exist_ok=True)

app = FastAPI()
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"


class Item(BaseModel):
    id: str
    url: str | None = None
    referer: str | None = None
    data: str | None = None  # data:image/...;base64,
    lang: str = "auto"


class Payload(BaseModel):
    items: list[Item]


def _cache_path(h: str) -> str:
    return os.path.join(CACHE_DIR, h + ".json")


def _load_img(raw: bytes) -> Image.Image:
    return Image.open(io.BytesIO(raw))


async def _fetch(url: str, referer: str | None) -> bytes:
    headers = {"User-Agent": UA}
    if referer:
        headers["Referer"] = referer
    async with httpx.AsyncClient(timeout=20, follow_redirects=True, headers=headers) as cli:
        r = await cli.get(url)
        r.raise_for_status()
        return r.content


async def _one(item: Item) -> tuple[str, dict]:
    t0 = time.time()
    if item.data:
        raw = base64.b64decode(item.data.split(",")[-1])
        ck = "b64_" + hashlib.sha1(raw).hexdigest()
    else:
        if not item.url:
            return item.id, {"error": "no url"}
        raw = await _fetch(item.url, item.referer)
        ck = hashlib.sha1(raw).hexdigest()

    cp = _cache_path(ck)
    if os.path.exists(cp):
        import json

        with open(cp, "r", encoding="utf-8") as f:
            data = json.load(f)
        data["cached"] = True
        data["ms"] = int((time.time() - t0) * 1000)
        return item.id, data

    img = _load_img(raw)
    W, H = img.size
    res = await asyncio.to_thread(ocrmod.ocr, img, item.lang)
    lines = res["lines"]
    conf = mt.load_conf()
    need_key = not conf.get("api_key") and not conf.get("mock")
    if not lines:
        out = {"w": W, "h": H, "lines": [], "lang": res["lang"], "ms": int((time.time() - t0) * 1000)}
    else:
        texts = [l["text"] for l in lines]
        trans = ["" for _ in texts] if need_key else await mt.translate(texts, res["lang"])
        norm = []
        for l, t in zip(lines, trans):
            x0, y0, x1, y1 = l["box"]
            norm.append(
                {
                    "x": round(x0 / W, 5),
                    "y": round(y0 / H, 5),
                    "w": round((x1 - x0) / W, 5),
                    "h": round((y1 - y0) / H, 5),
                    "text": l["text"],
                    "tr": t,
                    "score": round(l["score"], 3),
                }
            )
        out = {"w": W, "h": H, "lines": norm, "lang": res["lang"], "ms": int((time.time() - t0) * 1000)}
        if need_key:
            out["need_key"] = True
        import json

        if not need_key:
            os.makedirs(CACHE_DIR, exist_ok=True)
            with open(cp, "w", encoding="utf-8") as f:
                json.dump(out, f, ensure_ascii=False)
    out["ocr_ms"] = res.get("ms")
    return item.id, out


@app.get("/api/health")
async def health():
    return {"ok": True, "key_set": bool(mt.load_conf().get("api_key")), "model": mt.load_conf().get("model")}


@app.get("/api/config")
async def get_config():
    c = mt.load_conf()
    k = c.get("api_key", "")
    return {"api_key": (k[:6] + "***" + k[-4:]) if len(k) > 12 else "", "model": c.get("model", ""), "base_url": c.get("base_url", "")}


@app.post("/api/config")
async def set_config(c: dict):
    cur = mt.load_conf()
    cur.update({k: v for k, v in c.items() if v is not None})
    mt.save_conf(cur)
    mt._cache.clear()
    return {"ok": True}


@app.post("/api/translate")
async def translate(p: Payload):
    sem = asyncio.Semaphore(4)

    async def run(it: Item):
        async with sem:
            try:
                return await _one(it)
            except Exception as e:  # noqa: BLE001
                return it.id, {"error": str(e)[:200]}

    pairs = await asyncio.gather(*(run(i) for i in p.items))
    return {"results": {k: v for k, v in pairs}}


app.mount("/", StaticFiles(directory=os.path.join(HERE, "web"), html=True), name="web")
