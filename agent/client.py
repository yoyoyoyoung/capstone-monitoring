# 더는 사용하지 않음
import grpc
import psutil
import time

import monitoring_pb2
import monitoring_pb2_grpc

import socket

def run_agent():
    # 실행 중인 컴퓨터의 호스트 이름 가져오기
    host_name = socket.gethostname()
    
    # 서버 주소(localhost:9090) 연결
    channel = grpc.insecure_channel('141.164.50.161:9090')
    stub = monitoring_pb2_grpc.MonitoringServiceStub(channel)
    
    print(f"에이전트({host_name}) 가동. 실시간 데이터 전송.. (Ctrl+C로 종료)")

    try:
        while True:
            # psutil로 실제 시스템 정보 읽기
            cpu_usage = psutil.cpu_percent(interval=1)
            memory_usage = psutil.virtual_memory().percent
            
            # gRPC 메시지 규격(MetricRequest)에 담기
            request = monitoring_pb2.MetricRequest(
                agent_id=host_name, # 컴퓨터의 실제 이름 전송
                cpu_usage=cpu_usage,
                memory_usage=memory_usage
            )
            
            # 서버로 전송 및 응답 받기
            response = stub.SendMetrics(request)
            
            if response.success:
                print(f"[보냄] CPU: {cpu_usage}%, RAM: {memory_usage}% | 서버 응답: {response.message}")
            
            # 2초마다 반복 (원하는 주기로 변경 가능)
            time.sleep(2)
            
    except KeyboardInterrupt:
        print("\n에이전트 종료.")

if __name__ == "__main__":
    run_agent()