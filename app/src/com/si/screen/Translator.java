package com.si.screen;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** 只发文字，走 OpenAI 兼容接口（硅基流动 / 任意兼容服务）。 */
public class Translator {
    private static final String SYS =
            "你是漫画/网漫翻译。把每行原文翻译成简体中文。"
                    + "规则：逐行对应，不合并、不拆分、不解释、不加序号说明；"
                    + "口语化、保留语气与粗俗度；拟声词译成中文拟声；"
                    + "人名地名保留原文写法；无法识别的内容输出空行。"
                    + "输出格式：每行以 §N§ 开头（N 为行号），后面紧跟译文。";

    public static List<String> translate(List<String> lines, String key, String base, String model)
            throws Exception {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            sb.append("§").append(i + 1).append("§ ").append(lines.get(i)).append('\n');
        }
        JSONObject body = new JSONObject();
        body.put("model", model);
        JSONArray msgs = new JSONArray();
        msgs.put(new JSONObject().put("role", "system").put("content", SYS));
        msgs.put(new JSONObject().put("role", "user").put("content", sb.toString()));
        body.put("messages", msgs);
        body.put("temperature", 0.2);
        body.put("max_tokens", 2048);
        body.put("stream", false);

        String resp = post(base.endsWith("/") ? base + "chat/completions" : base + "/chat/completions",
                body.toString(), key, 60);
        JSONObject j = new JSONObject(resp);
        String content = j.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content");
        String[] got = new String[lines.size()];
        for (String line : content.split("\n")) {
            int a = line.indexOf('§', 0);
            if (a < 0) continue;
            int b = line.indexOf('§', a + 1);
            if (b < 0) continue;
            try {
                int idx = Integer.parseInt(line.substring(a + 1, b).trim()) - 1;
                if (idx >= 0 && idx < got.length) {
                    got[idx] = line.substring(b + 1).trim();
                }
            } catch (Exception ignored) {
            }
        }
        for (int i = 0; i < got.length; i++) out.add(got[i] == null ? "" : got[i]);
        return out;
    }

    private static String post(String url, String body, String key, int timeoutSec) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(10000);
        c.setReadTimeout(timeoutSec * 1000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("Authorization", "Bearer " + key);
        byte[] b = body.getBytes("UTF-8");
        c.setFixedLengthStreamingMode(b.length);
        OutputStream os = c.getOutputStream();
        os.write(b);
        os.close();
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while (in != null && (n = in.read(buf)) > 0) bo.write(buf, 0, n);
        if (in != null) in.close();
        String s = new String(bo.toByteArray(), "UTF-8");
        if (code >= 400) throw new Exception("HTTP " + code + " " + s.substring(0, Math.min(120, s.length())));
        return s;
    }
}
