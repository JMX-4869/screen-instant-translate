"""翻译层：硅基流动(OpenAI 兼容) 为主，有道公开接口兜底。"""
from __future__ import annotations

import hashlib
import json
import os
import re
import time
from typing import Sequence

import httpx

HERE = os.path.dirname(os.path.abspath(__file__))
CONF_PATH = os.path.join(HERE, "config.json")

_cache: dict[str, str] = {}

SYS = (
    "你是漫画/网漫翻译。把每行原文翻译成简体中文。"
    "规则：逐行对应，不合并、不拆分、不解释、不加序号说明；"
    "口语化、保留语气与粗俗度；拟声词译成中文拟声；"
    "人名地名保留原文写法；无法识别的内容输出空行。"
    "输出格式：每行以 §N§ 开头（N 为行号），后面紧跟译文。"
)


def load_conf() -> dict:
    try:
        with open(CONF_PATH, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return {}


def save_conf(c: dict) -> None:
    with open(CONF_PATH, "w", encoding="utf-8") as f:
        json.dump(c, f, ensure_ascii=False, indent=2)


def _key() -> tuple[str, str, str]:
    c = load_conf()
    return (
        c.get("api_key", ""),
        c.get("base_url", "https://api.siliconflow.cn/v1"),
        c.get("model", "Qwen/Qwen3.5-9B"),
    )


def _parse(out: str, n: int) -> list[str]:
    res = [""] * n
    for line in out.splitlines():
        m = re.match(r"\s*§\s*(\d+)\s*§\s*(.*)$", line)
        if m:
            i = int(m.group(1)) - 1
            if 0 <= i < n:
                res[i] = m.group(2).strip()
    return res


async def _siliconflow(lines: Sequence[str]) -> list[str]:
    key, base, model = _key()
    payload = "\n".join(f"§{i}§ {t}" for i, t in enumerate(lines, 1))
    body = {
        "model": model,
        "messages": [
            {"role": "system", "content": SYS},
            {"role": "user", "content": payload},
        ],
        "temperature": 0.2,
        "max_tokens": 2048,
        "stream": False,
    }
    async with httpx.AsyncClient(timeout=30) as cli:
        r = await cli.post(
            base.rstrip("/") + "/chat/completions",
            headers={"Authorization": f"Bearer {key}"},
            json=body,
        )
        r.raise_for_status()
        data = r.json()
    out = data["choices"][0]["message"]["content"]
    got = _parse(out, len(lines))
    if sum(1 for g in got if g) < max(1, len(lines) // 2):
        # 模型没按格式来，整段塞进第一行不如报错
        raise ValueError("bad format")
    return got


async def _youdao(text: str, lang: str) -> str:
    typ = {"japan": "JA2ZH_CN", "ch": "ZH_CN2ZH_CN", "korean": "KR2ZH_CN"}.get(lang, "AUTO")
    async with httpx.AsyncClient(timeout=12) as cli:
        r = await cli.post(
            "https://fanyi.youdao.com/translate",
            data={"doctype": "json", "type": typ, "i": text},
            headers={"User-Agent": "Mozilla/5.0", "Referer": "https://fanyi.youdao.com/"},
        )
        r.raise_for_status()
        d = r.json()
    return "".join(x.get("tgt", "") for x in d["translateResult"][0]) if d.get("translateResult") else ""


async def translate(lines: Sequence[str], lang: str = "japan", cache_key: str = "") -> list[str]:
    lines = [l.strip() for l in lines]
    key = cache_key or hashlib.sha1(("|".join(lines) + lang).encode()).hexdigest()
    hit = _cache.get(key)
    if hit is not None:
        return hit.split("\u0001") if hit else list(lines)

    if load_conf().get("mock"):
        return ["【测试译文】" + ("这是一段较长中文用来验证换行与字号收缩" if i % 2 == 0 else "短译文") for i, _ in enumerate(lines)]

    key_cfg, _, _ = _key()
    out: list[str] = []
    if key_cfg:
        try:
            out = await _siliconflow(lines)
        except Exception:
            out = []
    if not out or sum(1 for x in out if x) < max(1, len(lines) // 2):
        try:
            out = [await _youdao(t, lang) for t in lines]
        except Exception:
            out = list(lines)

    _cache[key] = "\u0001".join(out)
    return out
