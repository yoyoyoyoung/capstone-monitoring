import json
import os
import platform
import uuid

def get_config_path():
    current_os = platform.system()
    
    if current_os == "Windows":
        base_dir = os.environ.get("APPDATA", os.path.expanduser("~"))
    else:
        base_dir = os.path.expanduser("~")

    config_dir = os.path.join(base_dir, ".capstone_monitoring")
    os.makedirs(config_dir, exist_ok=True)
    
    return os.path.join(config_dir, "config.json")

def load_config():
    config_file = get_config_path()
    
    if not os.path.exists(config_file):
        default_config = {
            "agent_id": str(uuid.uuid4())[:8],
            "org_code": "미설정",
            "nickname": "My-PC",
            "server_ip": "141.164.50.161",
            "interval": 2,
            "auto_start": False,
            "minimize_to_tray": True
        }
        save_config(default_config)
        return default_config
    
    try:
        with open(config_file, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception as e:
        print(f"설정 로드 중 예외 발생, 빈 장부 리턴: {e}")
        return {}

def save_config(config):
    config_file = get_config_path()
    try:
        with open(config_file, "w", encoding="utf-8") as f:
            json.dump(config, f, ensure_ascii=False, indent=4)
    except Exception as e:
        print(f"설정 저장 실패: {e}")