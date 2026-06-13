# 더는 사용하지 않음

import grpc
import psutil
import time

import monitoring_pb2
import monitoring_pb2_grpc

import socket

def run_agent():
    host_name = socket.gethostname()
    
    channel = grpc.insecure_channel('141.164.50.161:9090')
    stub = monitoring_pb2_grpc.MonitoringServiceStub(channel)
    
    print(f"에이전트({host_name}) 가동. 실시간 데이터 전송.. (Ctrl+C로 종료)")

    try:
        while True:
            cpu_usage = psutil.cpu_percent(interval=1)
            memory_usage = psutil.virtual_memory().percent
            
            request = monitoring_pb2.MetricRequest(
                agent_id=host_name,
                cpu_usage=cpu_usage,
                memory_usage=memory_usage
            )
            
            response = stub.SendMetrics(request)
            
            if response.success:
                print(f"[보냄] CPU: {cpu_usage}%, RAM: {memory_usage}% | 서버 응답: {response.message}")
            
            time.sleep(2)
            
    except KeyboardInterrupt:
        print("\n에이전트 종료.")

if __name__ == "__main__":
    run_agent()