import sys
import os
import time
import psutil
import grpc
import subprocess
import winreg
import webbrowser

from PyQt5.QtWidgets import (QApplication, QMainWindow, QSystemTrayIcon, 
                             QMenu, QAction, QMessageBox, QVBoxLayout, 
                             QWidget, QLabel, QPushButton, QInputDialog, QLineEdit, QCheckBox, QDialog)
from PyQt5.QtNetwork import QLocalServer, QLocalSocket
from PyQt5.QtGui import QIcon, QFont
from PyQt5.QtCore import QCoreApplication, QThread, pyqtSignal, Qt, QSharedMemory

import monitoring_pb2
import monitoring_pb2_grpc
from config_manager import load_config, save_config

MODERN_STYLE = """
    QMainWindow, QDialog {
        background-color: #1e1e24;
    }
    QLabel {
        color: #ffffff;
        font-size: 13px;
        font-family: 'Segoe UI', Malgun Gothic;
    }
    QLabel#status_lbl {
        color: #4cc9f0;
        font-weight: bold;
        font-size: 13px;
        background-color: #2b2b36;
        border-radius: 6px;
        padding: 12px;
        line-height: 1.5;
    }
    QLineEdit {
        background-color: #2b2b36;
        border: 1px solid #4361ee;
        border-radius: 4px;
        color: #ffffff;
        padding: 5px;
    }
    QCheckBox {
        color: #ffffff;
        font-size: 12px;
    }
    QCheckBox::indicator {
        width: 14px;
        height: 14px;
        border: 2px solid #4361ee;
        border-radius: 4px;
        background: #1e1e24;
    }
    QCheckBox::indicator:checked {
        background: #4cc9f0;
        border-color: #4cc9f0;
    }
    QPushButton {
        background-color: #4361ee;
        color: white;
        border-radius: 6px;
        padding: 8px;
        font-weight: bold;
    }
    QPushButton:hover {
        background-color: #3f37c9;
    }
"""

class GrpcSenderThread(QThread):
    status_signal = pyqtSignal(str)

    def __init__(self):
        super().__init__()
        self.running = True
        self.is_sending = True

    def get_network_latency(self, ip):
        """윈도우 ping 명령어를 통해 네트워크 지연시간(ms)을 측정하는 함수"""
        try:
            cmd = ["ping", "-n", "1", "-w", "1000", ip]
            res = subprocess.run(cmd, capture_output=True, text=True, creationflags=0x08000000)
            if res.returncode == 0:
                for line in res.stdout.splitlines():
                    if "시간" in line or "time" in line:
                        for part in line.split():
                            if "ms" in part:
                                val = part.replace("시간=", "").replace("time=", "").replace("시간<", "").replace("time<", "").replace("ms", "").strip()
                                return float(val) if val else 0.0
            return 0.0
        except:
            return 0.0

    def sendGoodbyeSignal(self):
        try:
            config = load_config()
            server_address = f"{config['server_ip']}:9090"
            # 🔒 [UI 분리 반영] config.json의 org_code와 nickname을 조합하여 백엔드가 인식할 이름표 자동 빌드
            combined_agent_id = f"{config.get('org_code', '')}_{config['nickname']}({config['agent_id']})"
            
            channel = grpc.insecure_channel(server_address)
            stub = monitoring_pb2_grpc.MonitoringServiceStub(channel)
            
            goodbye_request = monitoring_pb2.MetricRequest(
                agent_id=combined_agent_id,
                cpu_usage=-99.0,
                memory_usage=0.0, disk_usage=0.0, net_download_speed=0.0, net_upload_speed=0.0, network_latency=0.0,
                is_java_alive=False, is_mysql_alive=False
            )
            stub.SendMetrics(goodbye_request, timeout=1)
            print("[스레드] 자바 서버에 관제 일시 중지 안전 신고 완료.")
        except Exception as e:
            print(f"[스레드] 중지 신호 발송 실패: {e}")

    def run(self):
        was_sending = True  # 🔄 직전 루프 때 데이터를 보내고 있었는지 기억하는 상태 장부
        
        # 1. 네트워크 속도 측정을 위한 이전 값 패치
        psutil.cpu_percent(interval=None)
        net_before = psutil.net_io_counters()
        time_before = time.time()
        
        while self.running:
            if not self.is_sending:
                # 🛑 [버그 박멸 구간] 전송 중지 상태로 바뀌는 '그 첫 번째 순간'에 딱 한 번만 인사를 보냅니다.
                if was_sending:
                    self.sendGoodbyeSignal()
                    was_sending = False  # 연속으로 인사장 스팸 안 날리게 플래그 잠금
                
                self.status_signal.emit("전송 일시 중지됨")
                time.sleep(1)
                
                # 중지 상태에서 재개될 때 네트워크 속도가 튀지 않도록 기준점 초기화
                net_before = psutil.net_io_counters()
                time_before = time.time()
                continue

            # 🟢 전송 중일 때는 직전 상태 플래그를 계속 True로 켜둡니다.
            was_sending = True
            config = load_config()
            server_address = f"{config['server_ip']}:9090"
            # 🔒 [UI 분리 반영] 루프 내 전송 세션 데이터 패킹 시에도 org_code를 접두사로 결합하여 매핑 전송
            combined_agent_id = f"{config.get('org_code', '')}_{config['nickname']}({config['agent_id']})"

            try:
                channel = grpc.insecure_channel(server_address)
                stub = monitoring_pb2_grpc.MonitoringServiceStub(channel)

                # 2. CPU 측정을 겸한 1초 대기 (네트워크 속도의 시간 분모 역할)
                cpu = psutil.cpu_percent(interval=None)
                mem = psutil.virtual_memory().percent
                
                # 3. 디스크 사용량 측정 (현재 에이전트가 실행 중인 드라이브 기준)
                disk = psutil.disk_usage('/').percent

                # 4. 네트워크 속도 계산 (KB/s 단위 변환)
                net_after = psutil.net_io_counters()
                time_after = time.time()
                time_delta = time_after - time_before
                
                if time_delta > 0:
                    download_speed = (net_after.bytes_recv - net_before.bytes_recv) / 1024 / time_delta
                    upload_speed = (net_after.bytes_sent - net_before.bytes_sent) / 1024 / time_delta
                else:
                    download_speed = 0.0
                    upload_speed = 0.0
                
                # 다음 루프 측정을 위해 이전 값 최신화
                net_before = net_after
                time_before = time_after

                # 5. 네트워크 지연시간(Ping) 및 프로세스 상태 측정
                latency = self.get_network_latency(config['server_ip'])
                
                java_alive = False
                mysql_alive = False
                for proc in psutil.process_iter(['name']):
                    try:
                        pname = proc.info['name'].lower()
                        if "java" in pname:
                            java_alive = True
                        if "mysql" in pname or "mysqld" in pname:
                            mysql_alive = True
                    except (psutil.NoSuchProcess, psutil.AccessDenied, psutil.ZombieProcess):
                        pass

                # 6대 지표 데이터 패킹 (정의한 .proto 규격과 1:1 매칭)
                request = monitoring_pb2.MetricRequest(
                    agent_id=combined_agent_id,
                    cpu_usage=cpu,
                    memory_usage=mem,
                    disk_usage=disk,
                    net_download_speed=download_speed,
                    net_upload_speed=upload_speed,
                    network_latency=latency,
                    is_java_alive=java_alive,
                    is_mysql_alive=mysql_alive
                )
                
                response = stub.SendMetrics(request, timeout=2) 

                if response.success:
                    # 🛠️ [실시간 연동 가이드 추가] 자바 서버의 응답 메시지에 따라 텔레그램 헤더 텍스트 조합
                    if response.message == "WARN_NOT_LINKED":
                        telegram_status = "⚠️ 텔레그램 알림 미연동 (알림 작동 불가)\n"
                    elif response.message == "SUCCESS_LINKED":
                        telegram_status = "🔒 텔레그램 보안 알림 연동 완료\n"
                    else:
                        telegram_status = ""

                    status_text = (
                        f"데이터 정상 전송 중\n"
                        f"{telegram_status}"  # 상태 라벨 상단에 연동 여부를 직관적으로 표기
                        f"• CPU: {cpu}% | RAM: {mem}% | 디스크: {disk}%\n"
                        f"• 다운로드: {download_speed:.1f} KB/s | ⬆️ 업로드: {upload_speed:.1f} KB/s\n"
                        f"• 지연시간: {latency} ms\n"
                        f"• 자바 서버: {'🟢 가동중' if java_alive else '🔴 중지됨'} | DB(MySQL): {'🟢 가동중' if mysql_alive else '🔴 중지됨'}"
                    )
                    self.status_signal.emit(status_text)
                else:
                    self.status_signal.emit("서버 응답 오류 발생")

            except grpc.RpcError:
                self.status_signal.emit(f"연결 실패 ({config['server_ip']})")
            except Exception as e:
                self.status_signal.emit(f"오류: {str(e)}")

            time.sleep(config['interval'])

    def stop(self):
        self.running = False
        self.wait()

class MonitoringAgentUI(QMainWindow):
    def __init__(self):
        super().__init__()
        self.config = load_config()
        self.initUI()
        self.createTrayIcon()

        self.sender_thread = GrpcSenderThread()
        self.sender_thread.status_signal.connect(self.updateStatusLabel)
        self.sender_thread.start()

    def initUI(self):
        # 상단 창 제목 설정
        self.setWindowTitle('Server Monitoring')
        
        if os.path.exists('icon.ico'):
            self.setWindowIcon(QIcon('icon.ico'))
            
        self.setFixedSize(420, 480)
        self.setStyleSheet(MODERN_STYLE)

        layout = QVBoxLayout()
        layout.setContentsMargins(20, 20, 20, 20)
        layout.setSpacing(8)

        self.id_label = QLabel(f"🆔 고유 UUID:  {self.config['agent_id']}")
        # 🏢 [누락 버그 수리] 화면 상단에 현재 에이전트의 조직 코드가 무엇인지 명확하게 시각화 라벨 추가
        self.org_label = QLabel(f"🏢 소속 조직 코드:  {self.config.get('org_code', '미설정')}")
        self.nick_label = QLabel(f"👤 표시 닉네임:  {self.config['nickname']}")
        self.ip_label = QLabel(f"🌐 서버 주소:  {self.config['server_ip']}")
        
        # 윈도우 시작 시 자동 실행 체크박스 생성 및 초기값 세팅
        self.auto_start_cb = QCheckBox(" 윈도우 시작 시 자동 실행 (백그라운드)")
        self.auto_start_cb.setChecked(self.config.get('auto_start', False))
        self.auto_start_cb.toggled.connect(self.toggleAutoStart)

        self.tray_min_cb = QCheckBox(" 닫기(X) 누를 때 시스템 트레이로 최소화")
        self.tray_min_cb.setChecked(self.config.get('minimize_to_tray', True))
        self.tray_min_cb.toggled.connect(self.toggleTrayMinimize)

        self.status_label = QLabel("상태: 데이터 수집 준비 중...")
        self.status_label.setObjectName("status_lbl")
        self.status_label.setWordWrap(True) 

        self.toggle_btn = QPushButton("전송 중지")
        self.toggle_btn.clicked.connect(self.toggleSending)
        self.toggle_btn.setCursor(Qt.PointingHandCursor)

        self.edit_btn = QPushButton("⚙️ 에이전트 설정 수정")
        self.edit_btn.clicked.connect(self.showSettingsDialog)
        self.edit_btn.setCursor(Qt.PointingHandCursor)
        
        self.web_btn = QPushButton("🌐 웹 관제 대시보드 열기")
        self.web_btn.clicked.connect(self.openWebDashboard)
        self.web_btn.setCursor(Qt.PointingHandCursor)
        self.web_btn.setStyleSheet("background-color: #2b2b36; color: #4cc9f0; border: 1px solid #4cc9f0;")

        layout.addWidget(self.id_label)
        layout.addWidget(self.org_label) # 🏢 조직 라벨 배치 추가
        layout.addWidget(self.nick_label)
        layout.addWidget(self.ip_label)
        layout.addWidget(self.auto_start_cb) 
        layout.addWidget(self.tray_min_cb)
        layout.addWidget(self.status_label)
        layout.addWidget(self.toggle_btn)
        layout.addWidget(self.edit_btn)
        layout.addWidget(self.web_btn)

        container = QWidget()
        container.setLayout(layout)
        self.setCentralWidget(container)

    def updateStatusLabel(self, text):
        self.status_label.setText(text)

    def sendGoodbyeSignal(self):
        """자바 서버에 정상 종료/중지 상태임을 자발적으로 신고하는 공용 함수"""
        try:
            config = load_config()
            server_address = f"{config['server_ip']}:9090"
            combined_agent_id = f"{config.get('org_code', '')}_{config['nickname']}({config['agent_id']})"
            
            channel = grpc.insecure_channel(server_address)
            stub = monitoring_pb2_grpc.MonitoringServiceStub(channel)
            
            goodbye_request = monitoring_pb2.MetricRequest(
                agent_id=combined_agent_id,
                cpu_usage=-99.0,
                memory_usage=0.0, disk_usage=0.0, net_download_speed=0.0, net_upload_speed=0.0, network_latency=0.0,
                is_java_alive=False, is_mysql_alive=False
            )
            stub.SendMetrics(goodbye_request, timeout=1)
            print("서버에 정상 상태 변경 신고 완료.")
        except Exception as e:
            print(f"서버 통신 실패로 정상 신고 건너뜀: {e}")
            
    def toggleSending(self):
        if self.sender_thread.is_sending:
            self.sender_thread.is_sending = False
            self.toggle_btn.setText("전송 시작")
            self.toggle_btn.setStyleSheet("background-color: #4cc9f0; color: #1e1e24;")
            self.status_label.setText("전송 일시 중지됨")
        else:
            self.sender_thread.is_sending = True
            self.toggle_btn.setText("전송 중지")
            self.toggle_btn.setStyleSheet("")

    # レ지스트리에 등록/해제
    def toggleAutoStart(self, checked):
        key_path = r"Software\Microsoft\Windows\CurrentVersion\Run"
        app_name = "CapstoneMonitoringAgent"
        
        python_exe = sys.executable
        script_path = os.path.abspath(sys.argv[0])
        cmd = f'"{python_exe}" "{script_path}"'
        
        try:
            key = winreg.OpenKey(winreg.HKEY_CURRENT_USER, key_path, 0, winreg.KEY_SET_VALUE)
            if checked:
                winreg.SetValueEx(key, app_name, 0, winreg.REG_SZ, cmd)
            else:
                try:
                    winreg.DeleteValue(key, app_name)
                except FileNotFoundError:
                    pass
            winreg.CloseKey(key)
            self.config['auto_start'] = checked
            save_config(self.config)
        except Exception as e:
            QMessageBox.warning(self, "설정 오류", f"윈도우 시작 프로그램 등록에 실패했습니다:\n{str(e)}")

    def toggleTrayMinimize(self, checked):
        self.config['minimize_to_tray'] = checked
        save_config(self.config)

    def showSettingsDialog(self):
        # 🏢 [3단계 입력 리팩토링 락] 조직 코드를 팝업창에서 직접 수정받아 머금을 수 있도록 1단계 입력 모달 가설 추가
        dialog_org = QInputDialog(self)
        dialog_org.setWindowTitle("에이전트 설정 수정")
        # 테스트를 위한 조직 코드 입력란. 상용화 때는 삭제 요망
        dialog_org.setLabelText("조직 코드를 입력하세요 (테스트용) (예: COMPANY_A):")
        dialog_org.setTextValue(self.config.get('org_code', ''))
        dialog_org.setStyleSheet(MODERN_STYLE)
        
        if dialog_org.exec_() == QDialog.Accepted:
            new_org = dialog_org.textValue().strip().upper() # 대소문자 무시 필터링 안정성을 위해 무조건 대문자 강제 정화
            
            dialog = QInputDialog(self)
            dialog.setWindowTitle("에이전트 설정 수정")
            dialog.setLabelText("새 닉네임을 입력하세요:")
            dialog.setTextValue(self.config['nickname'])
            dialog.setStyleSheet(MODERN_STYLE)
            
            if dialog.exec_() == QDialog.Accepted:
                new_nick = dialog.textValue().strip()
                
                dialog2 = QInputDialog(self)
                dialog2.setWindowTitle("에이전트 설정 수정")
                dialog2.setLabelText("서버 IP를 입력하세요:")
                dialog2.setTextValue(self.config['server_ip'])
                dialog2.setStyleSheet(MODERN_STYLE)
                
                if dialog2.exec_() == QDialog.Accepted:
                    new_ip = dialog2.textValue().strip()
                    
                    # 💾 입력받은 3가지 데이터를 세션 장부 및 내부 config.json 파일에 자동 세이브 마감
                    self.config['org_code'] = new_org
                    self.config['nickname'] = new_nick
                    self.config['server_ip'] = new_ip
                    save_config(self.config)
                    
                    # UI 화면 텍스트 즉시 동적 갱신 리렌더링
                    self.org_label.setText(f"🏢 소속 조직 코드:  {self.config['org_code']}")
                    self.nick_label.setText(f"👤 표시 닉네임:  {self.config['nickname']}")
                    self.ip_label.setText(f"🌐 서버 주소:  {self.config['server_ip']}")

    def createTrayIcon(self):
        self.tray_icon = QSystemTrayIcon(self)
        
        # 트레이 아이콘 설정
        if os.path.exists('icon.ico'):
            self.tray_icon.setIcon(QIcon('icon.ico'))
        else:
            self.tray_icon.setIcon(self.style().standardIcon(self.style().SP_ComputerIcon))
            
        tray_menu = QMenu()
        tray_menu.setStyleSheet("background-color: #2b2b36; color: white;")
        show_action = QAction("열기", self)
        quit_action = QAction("종료", self)
        show_action.triggered.connect(self.showNormal)
        quit_action.triggered.connect(self.quitApplication)
        tray_menu.addAction(show_action)
        tray_menu.addSeparator()
        tray_menu.addAction(quit_action)
        self.tray_icon.setContextMenu(tray_menu)
        self.tray_icon.show()
        self.tray_icon.activated.connect(self.onTrayIconActivated)

    def onTrayIconActivated(self, reason):
        if reason == QSystemTrayIcon.DoubleClick: self.showNormal()

    def closeEvent(self, event):
        if self.config.get('minimize_to_tray', True) and self.tray_icon.isVisible():
            self.tray_icon.showMessage("Server Monitoring", "백그라운드에서 실행 중입니다.", QSystemTrayIcon.Information, 1500)
            self.tray_icon.show()
            self.hide()
            event.ignore()
        else:
            self.quitApplication()
            event.accept()

    def quitApplication(self):
        try:
            self.sender_thread.sendGoodbyeSignal()
        except:
            pass
        self.sender_thread.stop()
        QCoreApplication.instance().quit()
        
    def openWebDashboard(self):
        webbrowser.open("http://141.164.50.161:8080")


if __name__ == '__main__':
    app = QApplication(sys.argv)
    app.setQuitOnLastWindowClosed(False)
    app_key = "MonitoringAgentUI_SingleInstance_UniqueKey_YJ"
    
    socket = QLocalSocket()
    socket.connectToServer(app_key)
    
    if socket.waitForConnected(500):
        socket.write(b"ACTIVATE_EXISTING_WINDOW")
        socket.waitForBytesWritten(500)
        sys.exit(0)
        
    QLocalServer.removeServer(app_key)
    local_server = QLocalServer()
    local_server.listen(app_key)
    
    ex = MonitoringAgentUI()
    ex.show()
    
    def handle_new_connection():
        client_socket = local_server.nextPendingConnection()
        if client_socket.waitForReadyRead(500):
            msg = client_socket.readAll().data().decode()
            if msg == "ACTIVATE_EXISTING_WINDOW":
                ex.show()
                ex.showNormal()
                ex.raise_()
                ex.activateWindow()

    local_server.newConnection.connect(handle_new_connection)
    
    sys.exit(app.exec_())
    