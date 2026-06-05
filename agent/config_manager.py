import json
import os
import platform
import uuid

def get_config_path():
    """
    실행 중인 OS를 감지하여 사용자 눈에 보이지 않는  시스템 표준 숨김 폴더 경로에 config.json 주소를 생성
    """
    current_os = platform.system()
    
    if current_os == "Windows":
        # Windows 숨김 폴더 표준: C:\Users\유저명\AppData\Roaming
        base_dir = os.environ.get("APPDATA", os.path.expanduser("~"))
    else:
        # Linux / macOS 숨김 폴더 표준: 홈 디렉터리 (~)
        base_dir = os.path.expanduser("~")
        
    # 각 OS 규격에 맞게 숨김 폴더(.capstone_monitoring) 개설
    config_dir = os.path.join(base_dir, ".capstone_monitoring")
    os.makedirs(config_dir, exist_ok=True)
    
    return os.path.join(config_dir, "config.json")

def load_config():
    """🔒 지정된 안전 구역에서 설정을 읽어오며, 파일이 없으면 상용 규격 기본값을 세팅합니다."""
    config_file = get_config_path()
    
    # 파일이 없으면 기본값으로 생성
    if not os.path.exists(config_file):
        default_config = {
            "agent_id": str(uuid.uuid4())[:8],      # 고유 ID 생성 (앞 8자리)
            "org_code": "미설정",                    # ui_agent.py 연동을 위한 조직코드 칸 추가
            "nickname": "My-PC",
            "server_ip": "141.164.50.161",
            "interval": 2,
            "auto_start": False,                    # 윈도우 시작프로그램 플래그 초기화
            "minimize_to_tray": True                # 트레이 최소화 기본 활성화
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
    """ 사용자가 UI에서 바꾼 설정을 시스템 숨김 서랍 내부에 영구 저장 """
    config_file = get_config_path()
    try:
        with open(config_file, "w", encoding="utf-8") as f:
            json.dump(config, f, ensure_ascii=False, indent=4)
    except Exception as e:
        print(f"설정 저장 실패: {e}")