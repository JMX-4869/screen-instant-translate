"""启动服务：本机 127.0.0.1:8017，局域网同网段手机可访问。"""
import socket

import uvicorn


def lan_ip() -> str:
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("223.5.5.5", 80))
        return s.getsockname()[0]
    except Exception:
        return "127.0.0.1"
    finally:
        s.close()


if __name__ == "__main__":
    ip = lan_ip()
    print(f"本机: http://127.0.0.1:8017")
    print(f"手机: http://{ip}:8017   (同一 Wi-Fi)")
    uvicorn.run("server:app", host="0.0.0.0", port=8017, log_level="warning")
