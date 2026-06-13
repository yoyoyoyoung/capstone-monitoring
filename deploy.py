import subprocess
import sys
import os

SERVER_IP = "141.164.50.161"
SERVER_USER = "root"
VERSION = "0.0.1"
JAR_NAME = f"monitoring-server-{VERSION}-SNAPSHOT.jar"

BASE_DIR = os.getcwd() 
LOCAL_JAR_PATH = os.path.join(BASE_DIR, "server", "monitoring-server", "build", "libs", JAR_NAME)
REMOTE_DIR = "/root"

START_COMMAND = (
    f"nohup java -jar {REMOTE_DIR}/{JAR_NAME} "
    f"--spring.datasource.url='jdbc:mysql://localhost:3306/monitoring_db?serverTimezone=Asia/Seoul' "
    f"--spring.datasource.username='monitor_user' "
    f"--spring.datasource.password='jj14589632' "
    f"--spring.jpa.hibernate.ddl-auto=update "
    f"> {REMOTE_DIR}/server.log 2>&1 &"
)

def run_command(cmd, shell=True):
    result = subprocess.run(cmd, shell=shell, text=True)
    if result.returncode != 0:
        print(f"작업 실패: {cmd}")
        sys.exit(1)

print("[1/4] 로컬에서 스프링 부트 프로젝트 빌드 중")
start_dir = os.getcwd()
os.chdir("./server/monitoring-server")
run_command("gradlew.bat clean bootJar")
os.chdir(start_dir)

print("[2/4] 빌드된 JAR 파일을 Vultr 서버로 전송 중 (SCP)")
if not os.path.exists(LOCAL_JAR_PATH):
    print(f"오류: 빌드된 JAR 파일을 찾을 수 없습니다. 경로 확인 필수: {LOCAL_JAR_PATH}")
    sys.exit(1)
run_command(f"scp {LOCAL_JAR_PATH} {SERVER_USER}@{SERVER_IP}:{REMOTE_DIR}/")

print("[3/4] Vultr 서버에서 기존 구동 중인 자바 서버 종료 중...")
remote_stop_cmd = f'ssh {SERVER_USER}@{SERVER_IP} "sudo fuser -k 8080/tcp; sudo fuser -k 9090/tcp"'
subprocess.run(remote_stop_cmd, shell=True, capture_output=True)

print("[4/4] Vultr 서버에서 새 버전 자바 서버 백그라운드 가동 중...")
remote_start_cmd = f'ssh {SERVER_USER}@{SERVER_IP} "{START_COMMAND}"'
run_command(remote_start_cmd)

print("\n서버 배포가 성공적으로 끝났습니다!")
print(f"실시간 로그 확인 명령어: ssh {SERVER_USER}@{SERVER_IP} 'tail -f {REMOTE_DIR}/server.log'")