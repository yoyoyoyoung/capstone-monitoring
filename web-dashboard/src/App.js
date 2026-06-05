import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';

function App() {
  // ─── [세션 및 로그인 상태 관리 상태] ───
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [username, setUsername] = useState("");
  const [userOrgCode, setUserOrgCode] = useState(""); // 🔒 로그인한 유저의 조직 코드 (데이터 격리용)
  const [userRole, setUserRole] = useState("");       // USER 또는 ADMIN 구분

  // ─── [로그인 폼 상태 입력값] ───
  const [loginId, setLoginId] = useState("");
  const [loginPw, setLoginPw] = useState("");
  const [loginError, setLoginError] = useState("");

  // 📝 회원가입 폼 상태 입력값 (운영자/사용자 권한 드롭다운 레이아웃은 기획 제외 반영)
  const [isSignUpMode, setIsSignUpMode] = useState(false);
  const [joinId, setJoinId] = useState("");
  const [joinPw, setJoinPw] = useState("");
  const [joinOrg, setJoinOrg] = useState("");
  const [joinError, setJoinError] = useState("");

  // ─── [기존 대시보드 상태 데이터] ───
  const [agents, setAgents] = useState([]);
  const [selectedAgent, setSelectedAgent] = useState("");
  const [data, setData] = useState([]);
  const [tokenInfo, setTokenInfo] = useState(null);
  const [showModal, setShowModal] = useState(false);

  // 🔑 비밀번호 변경 모달 폼 상태 입력값
  const [showPwModal, setShowPwModal] = useState(false);
  const [currentPw, setCurrentPw] = useState("");
  const [newPw, setNewPw] = useState("");
  const [pwError, setPwError] = useState("");

  // ⚙️ 가변형 임계치 설정 제어 상태 변수 (초기값 90%)
  const [cpuThreshold, setCpuThreshold] = useState(90);
  const [memThreshold, setMemThreshold] = useState(90);

  // 🚨 [오류 해결] 과거 장애 이력 상태 변수 서랍 정식 개설
  const [incidents, setIncidents] = useState([]);

  // !! Alert 강제 무시 테스트 용도 !!
  const [isAlertDismissed, setIsAlertDismissed] = useState(false);

  // 🌐 네트워크 속도 가변 단위 변환기
  const formatNetworkSpeed = (kbValue) => {
    if (kbValue === undefined || kbValue === null || isNaN(kbValue)) return '0 KB/s';
    if (kbValue >= 1024 * 1024) {
      return `${(kbValue / (1024 * 1024)).toFixed(1)} GB/s`;
    }
    if (kbValue >= 1024) {
      return `${(kbValue / 1024).toFixed(1)} MB/s`;
    }
    return `${kbValue.toFixed(1)} KB/s`;
  };

  // ⚙️ [API] 조절된 임계치를 백엔드로 동적 전송하는 함수
  const handleUpdateThreshold = () => {
    if (!selectedAgent) return;
    axios.post('http://141.164.50.161:8080/api/auth/threshold', {
      agentId: selectedAgent,
      cpuThreshold: cpuThreshold,
      memThreshold: memThreshold
    })
    .then(response => {
      if (response.data.success) {
        alert(response.data.message);
      }
    })
    .catch(error => console.error("임계치 동적 반영 실패:", error));
  };

  // 🔐 [API] 로그인 처리 함수
  const handleLogin = (e) => {
    e.preventDefault();
    setLoginError("");

    if (!loginId || !loginPw) {
      setLoginError("아이디와 비밀번호를 모두 입력해주세요.");
      return;
    }

    axios.post('http://141.164.50.161:8080/api/auth/login', {
      username: loginId,
      password: loginPw
    })
    .then(response => {
      if (response.data.success) {
        localStorage.setItem("userToken", response.data.token);
        localStorage.setItem("username", response.data.username);
        localStorage.setItem("orgCode", response.data.orgCode);
        localStorage.setItem("userRole", response.data.role);

        setIsLoggedIn(true);
        setUsername(response.data.username);
        setUserOrgCode(response.data.orgCode);
        setUserRole(response.data.role);
      }
    })
    .catch(error => {
      if (error.response && error.response.data) {
        setLoginError(error.response.data.message);
      } else {
        setLoginError("서버와의 통신이 원활하지 않습니다.");
      }
    });
  };

  // 🔑 [API] 비밀번호 변경 처리 함수
  const handleChangePassword = (e) => {
    e.preventDefault();
    setPwError("");
    if (!currentPw || !newPw) {
      setPwError("모든 항목을 입력해주세요.");
      return;
    }
    axios.post('http://141.164.50.161:8080/api/auth/change-password', {
      username: username, currentPassword: currentPw, newPassword: newPw
    })
    .then(response => {
      if (response.data.success) {
        alert(response.data.message);
        setCurrentPw(""); setNewPw(""); setShowPwModal(false);
      }
    })
    .catch(error => {
      setPwError(error.response?.data?.message || "비밀번호 변경에 실패했습니다.");
    });
  };

  // 📝 [API] 회원가입 처리 함수
  const handleSignup = (e) => {
    e.preventDefault();
    setJoinError("");

    if (!joinId || !joinPw || !joinOrg) {
      setJoinError("모든 항목을 입력해주세요.");
      return;
    }

    axios.post('http://141.164.50.161:8080/api/auth/signup', {
      username: joinId,
      password: joinPw,
      orgCode: joinOrg
    })
    .then(response => {
      alert("회원가입이 완료되었습니다! 로그인해 주세요.");
      setJoinId(""); setJoinPw(""); setJoinOrg("");
      setIsSignUpMode(false);
    })
    .catch(error => {
      if (error.response && error.response.data) {
        setJoinError(error.response.data);
      } else {
        setJoinError("서버와의 통신이 원활하지 않습니다.");
      }
    });
  };

  // 🚪 [API] 로그아웃 처리 함수 (세션 즉시 파기)
  const handleLogout = () => {
    localStorage.clear(); 
    setIsLoggedIn(false);
    setUsername(""); setUserOrgCode(""); setUserRole(""); setSelectedAgent(""); setData([]);
    setLoginId(""); setLoginPw("");
  };

  // 1. 에이전트 목록 가져오기 (UUID 기준 중복 정화 및 이름표 스왑 알고리즘)
  const fetchAgents = () => {
    if (!isLoggedIn) return;
    axios.get('http://141.164.50.161:8080/api/agents')
      .then(response => {
        // [기존 로직] 멀티테넌시 조직 기본 필터링
        const filtered = response.data.filter(agent => {
          if (userRole === "ADMIN") return true; 
          return agent.toUpperCase().includes(userOrgCode.toUpperCase()); 
        });

        // 🛡️ [고도화 기믹] 닉네임이 바뀌어도 괄호 안의 고유 UUID를 기준으로 자동 그룹화
        const uuidMap = {};
        filtered.forEach(agent => {
          // "닉네임(721c1dd2)" 구조에서 괄호 안의 고유 ID인 UUID만 파싱합니다.
          const match = agent.match(/\(([^)]+)\)/);
          const uuid = match ? match[1] : agent;
          
          // 장부에 UUID를 키로 저장 (동일한 UUID라면 가장 마지막에 나온 최신 이름표가 이전 이름표를 덮어씀)
          uuidMap[uuid] = agent;
        });

        // 중복 글자가 완벽하게 세척된 단일 에이전트 배열 추출
        const uniqueAgents = Object.values(uuidMap);

        setAgents(uniqueAgents);
        
        // 🎯 [세션 고정 및 프리셀렉트 통합 락] 
        setSelectedAgent(prev => {
          // ⚡ [최우선 순위 순정 패치] 에이전트 [웹 대시보드 열기]를 통해 주소창에 파라미터가 유입된 경우!
          const queryParams = new URLSearchParams(window.location.search);
          const urlTargetUuid = queryParams.get('agentId') || queryParams.get('uuid');
          
          if (urlTargetUuid) {
            // 내 조직 리스트 중 에이전트 주소창 UUID를 포함하는 완벽한 매칭 노드 검색
            const matchedUrlAgent = uniqueAgents.find(a => a.toLowerCase().includes(urlTargetUuid.toLowerCase()));
            if (matchedUrlAgent) return matchedUrlAgent;
          }

          // [2순위] 기존에 보던 컴퓨터 세션 추적 (이름 변경 대응)
          if (prev) {
            const prevMatch = prev.match(/\(([^)]+)\)/);
            const prevUuid = prevMatch ? prevMatch[1] : prev;
            
            const updatedNameAgent = uniqueAgents.find(a => {
              const m = a.match(/\(([^)]+)\)/);
              return m && m[1] === prevUuid;
            });
            
            if (updatedNameAgent) return updatedNameAgent; 
          }
          
          // [3순위] 최초 진입 및 찌꺼기가 없을 시 0번째 컴퓨터 자동 프리셀렉트
          if (uniqueAgents.length > 0) return uniqueAgents[0];
          
          return "";
        });
      })
      .catch(error => console.error("에이전트 목록 로드 실패:", error));
  };

  // 1-2. 과거 장애 이력 가져오기 (레포지토리 격리 쿼리 동기화)
  const fetchIncidents = () => {
    if (!isLoggedIn) return;
    axios.get(`http://141.164.50.161:8080/api/auth/incidents?orgCode=${userOrgCode}&role=${userRole}`)
      .then(response => {
        setIncidents(response.data);
      })
      .catch(error => console.error("장애 이력 로드 실패:", error));
  };

  // 2. 특정 에이전트 데이터 가져오기
  const fetchMetrics = () => {
    if (!isLoggedIn || !selectedAgent) return;

    axios.get(`http://141.164.50.161:8080/api/metrics/${selectedAgent}`)
      .then(response => {
        const processedData = response.data.slice(0, 20).reverse().map(item => {
          let displayTime = "00:00:00";
          if (item.timestamp) {
            const timePart = item.timestamp.includes('T') ? item.timestamp.split('T')[1] : item.timestamp.split(' ')[1];
            displayTime = timePart ? timePart.split('.')[0] : item.timestamp;
          }
          return { ...item, displayTime };
        });
        setData(processedData);
      })
      .catch(error => console.error("데이터 로드 실패:", error));
  };

  // 3. 텔레그램 연동 토큰 발급
  const handleRequestTelegram = () => {
    if (!selectedAgent) return;
    axios.post('http://141.164.50.161:8080/api/telegram/token', { agentId: selectedAgent })
      .then(response => {
        setTokenInfo(response.data);
        setShowModal(true);
      })
      .catch(error => console.error("텔레그램 토큰 발급 실패:", error));
  };

  // 📊 실시간 수치 연산을 처리하는 변수 (useEffect보다 위에 선언하여 ReferenceError 완벽 방어)
  const latest = data[data.length - 1] || {
    cpuUsage: 0, memoryUsage: 0, diskUsage: 0,
    netDownloadSpeed: 0, netUploadSpeed: 0, networkLatency: 0,
    isJavaAlive: false, isMysqlAlive: false
  };

  // 🔄 [최초 실행] 브라우저에 저장된 로그인 세션 자동 스캔
  useEffect(() => {
    const savedToken = localStorage.getItem("userToken");
    const savedUser = localStorage.getItem("username");
    const savedOrg = localStorage.getItem("orgCode");
    const savedRole = localStorage.getItem("userRole");

    if (savedToken && savedUser && savedOrg && savedRole) {
      setIsLoggedIn(true);
      setUsername(savedUser);
      setUserOrgCode(savedOrg);
      setUserRole(savedRole);
    }
  }, []);

  useEffect(() => {
    fetchAgents();
    fetchIncidents(); 
    
    const agentInterval = setInterval(() => {
      fetchAgents();
      fetchIncidents(); 
    }, 5000);
    
    return () => clearInterval(agentInterval);
  }, [isLoggedIn, userRole, userOrgCode]);

  useEffect(() => {
    fetchMetrics();
    const metricsInterval = setInterval(fetchMetrics, 2000);
    return () => clearInterval(metricsInterval);
  }, [selectedAgent, isLoggedIn]);

  // !! Alert 강제 무시 테스트 용도 !!
  useEffect(() => {
    const isCurrentlyCritical = (latest.cpuUsage >= cpuThreshold || latest.memoryUsage >= memThreshold);
    if (!isCurrentlyCritical) {
      setIsAlertDismissed(false);
    }
  }, [latest.cpuUsage, latest.memoryUsage, cpuThreshold, memThreshold, selectedAgent]);

  // ─── 🚪 화면 1: 로그인하지 않은 상태일 때 ───
  if (!isLoggedIn) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh', backgroundColor: '#13131a', fontFamily: 'sans-serif', color: '#fff' }}>
        <div style={{ backgroundColor: '#1c1c24', border: '1px solid #2d2d3d', padding: '40px', borderRadius: '16px', width: '360px', boxShadow: '0 20px 40px rgba(0,0,0,0.4)' }}>
          
          {isSignUpMode ? (
            /* 📝 회원가입 폼 레이아웃 구역 */
            <form onSubmit={handleSignup} style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
              <div style={{ textAlign: 'center', marginBottom: '20px' }}>
                <h2 style={{ margin: 0, fontSize: '24px', color: '#ffffff' }}>📝 관제 계정 생성</h2>
                <p style={{ margin: '5px 0 0 0', color: '#71717a', fontSize: '13px' }}>새로운 인프라 사용자 계정을 등록합니다.</p>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '12px', color: '#a1a1aa', marginBottom: '5px', fontWeight: 'bold' }}>CREATE ID</label>
                <input 
                  type="text" 
                  value={joinId} 
                  onChange={(e) => setJoinId(e.target.value)}
                  placeholder="새로운 아이디를 입력하세요"
                  style={{ width: '100%', boxSizing: 'border-box', backgroundColor: '#13131a', border: '1px solid #2d2d3d', borderRadius: '8px', padding: '12px', color: '#fff', fontSize: '14px', outline: 'none' }}
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '12px', color: '#a1a1aa', marginBottom: '5px', fontWeight: 'bold' }}>PASSWORD</label>
                <input 
                  type="password" 
                  value={joinPw} 
                  onChange={(e) => setJoinPw(e.target.value)}
                  placeholder="비밀번호를 입력하세요"
                  style={{ width: '100%', boxSizing: 'border-box', backgroundColor: '#13131a', border: '1px solid #2d2d3d', borderRadius: '8px', padding: '12px', color: '#fff', fontSize: '14px', outline: 'none' }}
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '12px', color: '#a1a1aa', marginBottom: '5px', fontWeight: 'bold' }}>ORGANIZATION CODE</label>
                <input 
                  type="text" 
                  value={joinOrg} 
                  onChange={(e) => setJoinOrg(e.target.value)}
                  placeholder="조직 코드를 입력하세요 (예: COMPANY_A)"
                  style={{ width: '100%', boxSizing: 'border-box', backgroundColor: '#13131a', border: '1px solid #2d2d3d', borderRadius: '8px', padding: '12px', color: '#fff', fontSize: '14px', outline: 'none' }}
                />
              </div>

              {joinError && <p style={{ color: '#ff6b6b', fontSize: '13px', margin: '0', fontWeight: '500' }}>❌ {joinError}</p>}

              <button 
                type="submit"
                style={{ backgroundColor: '#4ecdc4', color: '#13131a', border: 'none', padding: '14px', borderRadius: '8px', fontSize: '15px', fontWeight: 'bold', cursor: 'pointer', marginTop: '10px', transition: '0.2s' }}
              >
                가입 완료하기
              </button>
              
              <p 
                onClick={() => { setIsSignUpMode(false); setJoinError(""); }} 
                style={{ textAlign: 'center', color: '#a1a1aa', fontSize: '13px', cursor: 'pointer', marginTop: '10px', textDecoration: 'underline' }}
              >
                기존 계정으로 로그인하기
              </p>
            </form>
          ) : (
            /* 🔐 로그인 폼 레이아웃 구역 */
            <>
              <div style={{ textAlign: 'center', marginBottom: '30px' }}>
                <h2 style={{ margin: 0, fontSize: '24px', color: '#ffffff' }}>🔐 인프라 통합 관제탑</h2>
                <p style={{ margin: '5px 0 0 0', color: '#71717a', fontSize: '13px' }}>보안 세션 네트워크 로그인이 필요합니다.</p>
              </div>
              
              <form onSubmit={handleLogin} style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '12px', color: '#a1a1aa', marginBottom: '5px', fontWeight: 'bold' }}>ACCESS ID</label>
                  <input 
                    type="text" 
                    value={loginId} 
                    onChange={(e) => setLoginId(e.target.value)}
                    placeholder="아이디를 입력하세요"
                    style={{ width: '100%', boxSizing: 'border-box', backgroundColor: '#13131a', border: '1px solid #2d2d3d', borderRadius: '8px', padding: '12px', color: '#fff', fontSize: '14px', outline: 'none' }}
                  />
                </div>
                
                <div>
                  <label style={{ display: 'block', fontSize: '12px', color: '#a1a1aa', marginBottom: '5px', fontWeight: 'bold' }}>SECURE PASSWORD</label>
                  <input 
                    type="password" 
                    value={loginPw} 
                    onChange={(e) => setLoginPw(e.target.value)}
                    placeholder="비밀번호를 입력하세요"
                    style={{ width: '100%', boxSizing: 'border-box', backgroundColor: '#13131a', border: '1px solid #2d2d3d', borderRadius: '8px', padding: '12px', color: '#fff', fontSize: '14px', outline: 'none' }}
                  />
                </div>

                {loginError && <p style={{ color: '#ff6b6b', fontSize: '13px', margin: '0', fontWeight: '500' }}>❌ {loginError}</p>}

                <button 
                  type="submit"
                  style={{ backgroundColor: '#4361ee', color: '#fff', border: 'none', padding: '14px', borderRadius: '8px', fontSize: '15px', fontWeight: 'bold', cursor: 'pointer', marginTop: '10px', transition: '0.2s' }}
                  onMouseOver={(e) => e.target.style.backgroundColor = '#3f37c9'}
                  onMouseOut={(e) => e.target.style.backgroundColor = '#4361ee'}
                >
                  관제 세션 연결하기
                </button>

                <p 
                  onClick={() => { setIsSignUpMode(true); setLoginError(""); }} 
                  style={{ textAlign: 'center', color: '#4cc9f0', fontSize: '13px', cursor: 'pointer', marginTop: '10px', textDecoration: 'underline' }}
                >
                  아직 계정이 없으신가요? 회원가입
                </p>
              </form>
            </>
          )}

        </div>
      </div>
    );
  }

  // ─── 📊 화면 2: 로그인 성공 시 출력되는 메인 관제 대시보드 ───
  return (
    <div style={{ padding: '30px', backgroundColor: '#13131a', minHeight: '100vh', fontFamily: '-apple-system, BlinkMacSystemFont, sans-serif', color: '#ffffff' }}>
      
      {/* HEADER SECTION */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '30px', borderBottom: '1px solid #222230', paddingBottom: '20px' }}>
        <div>
          <h1 style={{ margin: 0, fontSize: '28px', fontWeight: 'bold', color: '#ffffff' }}>🖥️ 대형 관제 서버 통합 대시보드</h1>
          <div style={{ display: 'flex', gap: '15px', alignItems: 'center', marginTop: '5px' }}>
            <span style={{ color: '#4cc9f0', fontSize: '14px', fontWeight: 'bold' }}>👤 {username} 계정 세션 가동 중</span>
            <span style={{ color: '#71717a', fontSize: '14px' }}>|</span>
            <span style={{ backgroundColor: '#2d2d3d', padding: '2px 8px', borderRadius: '4px', fontSize: '12px', color: '#a1a1aa', fontWeight: 'bold' }}>조직: {userOrgCode}</span>
            <span style={{ backgroundColor: '#4361ee', padding: '2px 8px', borderRadius: '4px', fontSize: '12px', color: '#fff', fontWeight: 'bold' }}>등급: {userRole}</span>
          </div>
        </div>
        
        {/* 제어 컨트롤러 존 */}
        <div style={{ display: 'flex', gap: '15px', alignItems: 'center' }}>
          <div style={{ backgroundColor: '#1c1c24', padding: '10px 15px', borderRadius: '8px', border: '1px solid #2d2d3d' }}>
            <label style={{ marginRight: '10px', fontWeight: '600', color: '#a1a1aa', fontSize: '14px' }}>감시 대상:</label>
            <select 
              value={selectedAgent} 
              onChange={(e) => setSelectedAgent(e.target.value)}
              style={{ backgroundColor: '#13131a', color: '#fff', padding: '6px 12px', borderRadius: '6px', border: '1px solid #4361ee', fontSize: '14px', fontWeight: 'bold', cursor: 'pointer', outline: 'none' }}
            >
              {agents.length === 0 && <option value="">서버 감지 중...</option>}
              {agents.map(agent => {
                const displayLabel = userRole === "ADMIN" ? agent : agent.replace(new RegExp(`^${userOrgCode}_`, "i"), "");
                return (
                <option key={agent} value={agent} style={{ background: '#1c1c24' }}>{displayLabel}</option>
              );
              })}
            </select>
          </div>

          <button 
            onClick={handleRequestTelegram}
            disabled={!selectedAgent}
            style={{ backgroundColor: '#4361ee', color: '#fff', border: 'none', padding: '12px 20px', borderRadius: '8px', fontSize: '14px', fontWeight: 'bold', cursor: selectedAgent ? 'pointer' : 'default', opacity: selectedAgent ? 1 : 0.5 }}
          >
            🔔 텔레그램 알림 활성화
          </button>

          <button 
            onClick={handleLogout}
            style={{ backgroundColor: '#ff6b6b', color: '#fff', border: 'none', padding: '12px 15px', borderRadius: '8px', fontSize: '14px', fontWeight: 'bold', cursor: 'pointer' }}
          >
            🚪 로그아웃
          </button>

          <button 
            onClick={() => setShowPwModal(true)}
            style={{ backgroundColor: '#6c757d', color: '#fff', border: 'none', padding: '12px 15px', borderRadius: '8px', fontSize: '14px', fontWeight: 'bold', cursor: 'pointer' }}
          >
            🔑 비밀번호 변경
          </button>
        </div>
      </div>

      {/* LIVE STATE TRAFFIC LIGHTS */}
      <div style={{ display: 'flex', gap: '20px', marginBottom: '25px' }}>
        <div style={{ display: 'flex', alignItems: 'center', backgroundColor: '#1c1c24', padding: '12px 20px', borderRadius: '10px', border: '1px solid #2d2d3d', flex: 1 }}>
          <span style={{ fontSize: '14px', color: '#a1a1aa', marginRight: 'auto' }}>OS 자바 어플리케이션 가동 상태</span>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontWeight: 'bold', fontSize: '15px', color: latest.isJavaAlive ? '#4ecdc4' : '#ff6b6b' }}>
            {latest.isJavaAlive ? '🟢 가동중 (Active)' : '🔴 중지됨 (Dead)'}
          </span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', backgroundColor: '#1c1c24', padding: '12px 20px', borderRadius: '10px', border: '1px solid #2d2d3d', flex: 1 }}>
          <span style={{ fontSize: '14px', color: '#a1a1aa', marginRight: 'auto' }}>OS 내장 MySQL 데이터베이스 상태</span>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontWeight: 'bold', fontSize: '15px', color: latest.isMysqlAlive ? '#4ecdc4' : '#ff6b6b' }}>
            {latest.isMysqlAlive ? '🟢 가동중 (Online)' : '🔴 중지됨 (Offline)'}
          </span>
        </div>
      </div>

      {/* MAIN GRID LAYOUT */}
      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '25px' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '25px' }}>
          
          {/* 차트 1: 연산 지표 */}
          <div style={{ backgroundColor: '#1c1c24', border: '1px solid #2d2d3d', padding: '20px', borderRadius: '12px' }}>
            <h3 style={{ margin: '0 0 15px 0', fontSize: '16px', color: '#e4e4e7' }}>📊 연산 및 가상 메모리 실시간 지표</h3>
            <div style={{ width: '100%', height: 260 }}>
              <ResponsiveContainer>
                <LineChart data={data} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#2d2d3d" vertical={false} />
                  <XAxis dataKey="displayTime" stroke="#71717a" style={{ fontSize: '12px' }} />
                  <YAxis domain={[0, 100]} stroke="#71717a" style={{ fontSize: '12px' }} />
                  <Tooltip contentStyle={{ backgroundColor: '#1c1c24', borderColor: '#2d2d3d', color: '#fff' }} />
                  <Legend verticalAlign="top" height={36} />
                  <Line type="monotone" dataKey="cpuUsage" stroke="#ff4d4d" strokeWidth={2.5} dot={false} name="CPU (%)" isAnimationActive={false} />
                  <Line type="monotone" dataKey="memoryUsage" stroke="#3399ff" strokeWidth={2.5} dot={false} name="RAM (%)" isAnimationActive={false} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </div>

          {/* 차트 2: 네트워크 트래픽 */}
          <div style={{ backgroundColor: '#1c1c24', border: '1px solid #2d2d3d', padding: '20px', borderRadius: '12px' }}>
            <h3 style={{ margin: '0 0 15px 0', fontSize: '16px', color: '#e4e4e7' }}>🌐 네트워크 입출력 대역폭 트래픽 (I/O)</h3>
            <div style={{ width: '100%', height: 200 }}>
              <ResponsiveContainer>
                <LineChart data={data} margin={{ top: 10, right: 10, left: 10, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#2d2d3d" vertical={false} />
                  <XAxis dataKey="displayTime" stroke="#71717a" style={{ fontSize: '12px' }} />
                  
                  <YAxis 
                    stroke="#71717a" 
                    style={{ fontSize: '12px' }} 
                    domain={[0, 'auto']} 
                    tickFormatter={formatNetworkSpeed} 
                    width={75} 
                  />
                  <Tooltip 
                    contentStyle={{ backgroundColor: '#1c1c24', borderColor: '#2d2d3d', color: '#fff' }} 
                    formatter={(value) => [formatNetworkSpeed(value), "현재 속도"]}
                  />
                  
                  <Legend verticalAlign="top" height={36} />
                  <Line type="monotone" dataKey="netDownloadSpeed" stroke="#4cc9f0" strokeWidth={2} dot={false} name="다운로드" isAnimationActive={false} />
                  <Line type="monotone" dataKey="netUploadSpeed" stroke="#f72585" strokeWidth={2} dot={false} name="업로드" isAnimationActive={false} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </div>
        </div>

        {/* SIDE PANELS */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '25px' }}>
          <div style={{ backgroundColor: '#1c1c24', border: '1px solid #2d2d3d', padding: '25px', borderRadius: '12px' }}>
            <div style={{ fontSize: '14px', color: '#a1a1aa', marginBottom: '8px' }}>💾 스토리지 디스크 점유율</div>
            <div style={{ fontSize: '28px', fontWeight: 'bold', color: '#f77f00', marginBottom: '15px' }}>{latest.diskUsage?.toFixed(1)} %</div>
            <div style={{ width: '100%', height: '8px', backgroundColor: '#2d2d3d', borderRadius: '4px', overflow: 'hidden' }}>
              <div style={{ width: `${latest.diskUsage || 0}%`, height: '100%', backgroundColor: '#f77f00', transition: 'width 0.5s ease' }}></div>
            </div>
          </div>

          <div style={{ backgroundColor: '#1c1c24', border: '1px solid #2d2d3d', padding: '25px', borderRadius: '12px' }}>
            <div style={{ fontSize: '14px', color: '#a1a1aa', marginBottom: '8px' }}>⚡ 원격 네트워크 응답 지연 (RTT)</div>
            <div style={{ fontSize: '28px', fontWeight: 'bold', color: latest.networkLatency < 50 ? '#4ecdc4' : '#ffb703' }}>
              {latest.networkLatency?.toFixed(1)} <span style={{ fontSize: '16px', fontWeight: 'normal', color: '#71717a' }}>ms</span>
            </div>
          </div>

          {/* ⚙️ 실시간 가변형 임계치 컨트롤 조절기 패널 (범위 10% ~ 95% 🛠️) */}
          <div style={{ backgroundColor: '#1c1c24', border: '1px solid #2d2d3d', padding: '25px', borderRadius: '12px' }}>
            <div style={{ fontSize: '14px', color: '#a1a1aa', marginBottom: '15px', fontWeight: 'bold' }}>⚙️ 실시간 장애 알림 임계치 제어</div>
            
            <div style={{ marginBottom: '15px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px', color: '#fff', marginBottom: '5px' }}>
                <span>CPU 경보 임계치</span>
                <span style={{ color: '#ff4d4d', fontWeight: 'bold' }}>{cpuThreshold} %</span>
              </div>
              {/* 🛠️ 테스팅 강화를 위해 min 범위를 50에서 10으로 하향 조정 */}
              <input 
                type="range" min="10" max="95" value={cpuThreshold} 
                onChange={(e) => setCpuThreshold(Number(e.target.value))} 
                style={{ width: '100%', accentColor: '#ff4d4d', cursor: 'pointer' }} 
              />
            </div>

            <div style={{ marginBottom: '20px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px', color: '#fff', marginBottom: '5px' }}>
                <span>RAM 경보 임계치</span>
                <span style={{ color: '#3399ff', fontWeight: 'bold' }}>{memThreshold} %</span>
              </div>
              {/* 🛠️ 테스팅 강화를 위해 min 범위를 50에서 10으로 하향 조정 */}
              <input 
                type="range" min="10" max="95" value={memThreshold} 
                onChange={(e) => setMemThreshold(Number(e.target.value))} 
                style={{ width: '100%', accentColor: '#3399ff', cursor: 'pointer' }} 
              />
            </div>

            <button 
              onClick={handleUpdateThreshold}
              disabled={!selectedAgent}
              style={{ width: '100%', backgroundColor: '#4361ee', color: '#fff', border: 'none', padding: '12px', borderRadius: '8px', fontSize: '13px', fontWeight: 'bold', cursor: selectedAgent ? 'pointer' : 'default', opacity: selectedAgent ? 1 : 0.5, transition: '0.2s' }}
            >
              🚀 가변 임계치 실시간 동기화
            </button>
          </div>

          <div style={{ backgroundColor: '#13131a', border: '1px solid #222230', padding: '20px', borderRadius: '12px', flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
            <div style={{ fontSize: '13px', color: '#71717a', borderBottom: '1px solid #222230', paddingBottom: '8px', marginBottom: '8px' }}>접속 노드 ID</div>
            <div style={{ fontSize: '14px', fontFamily: 'monospace', color: '#a1a1aa', marginBottom: '15px' }}>{selectedAgent ? (userRole === "ADMIN" ? selectedAgent : selectedAgent.replace(new RegExp(`^${userOrgCode}_`, "i"), "")) : 'None'}</div>
            <div style={{ fontSize: '13px', color: '#71717a', borderBottom: '1px solid #222230', paddingBottom: '8px', marginBottom: '8px' }}>원격 백엔드 연동 엔드포인트</div>
            <div style={{ fontSize: '14px', fontFamily: 'monospace', color: '#4361ee' }}>gRPC://141.164.50.161:9090</div>
          </div>
        </div>
      </div>

      {/* 🚨 과거 인프라 장애 이력 추적 테이블 판넬 */}
      <div style={{ backgroundColor: '#1c1c24', border: '1px solid #2d2d3d', padding: '25px', borderRadius: '12px', marginTop: '25px' }}>
        <h3 style={{ margin: '0 0 15px 0', fontSize: '16px', color: '#ff6b6b', fontWeight: 'bold' }}>📋 시스템 다운타임 및 장애 인시던트 이력 (최신 10건)</h3>
        
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '14px' }}>
            <thead>
              <tr style={{ borderBottom: '2px solid #2d2d3d', color: '#a1a1aa' }}>
                <th style={{ padding: '12px 8px' }}>발생 시각</th>
                <th style={{ padding: '12px 8px' }}>장애 서버 ID (조직 식별자)</th>
                <th style={{ padding: '12px 8px' }}>상태 코드</th>
                <th style={{ padding: '12px 8px' }}>조치 현황</th>
              </tr>
            </thead>
            <tbody>
              {incidents.length === 0 ? (
                <tr style={{ borderBottom: '1px solid #2d2d3d', color: '#71717a' }}>
                  <td colSpan="4" style={{ padding: '20px 8px', textAlign: 'center' }}>🟢 탐지된 과거 인프라 셧다운 이력이 없습니다. 시스템 안정 최상.</td>
                </tr>
              ) : (
                incidents.map((incident, idx) => {
                  const cleanId = userRole === "ADMIN" ? incident.agentId : incident.agentId?.replace(new RegExp(`^${userOrgCode}_`, "i"), "");
                  
                  if (userRole !== "ADMIN" && !incident.agentId?.toUpperCase().includes(userOrgCode.toUpperCase())) {
                    return null;
                  }

                  return (
                    <tr key={idx} style={{ borderBottom: '1px solid #2d2d3d', color: '#e4e4e7' }}>
                      <td style={{ padding: '12px 8px', fontFamily: 'monospace' }}>
                        {incident.timestamp?.replace('T', ' ').split('.')[0]}
                      </td>
                      <td style={{ padding: '12px 8px', fontWeight: '500', color: '#4cc9f0' }}>{cleanId}</td>
                      <td style={{ padding: '12px 8px' }}>
                        <span style={{ backgroundColor: 'rgba(255,107,107,0.15)', color: '#ff6b6b', padding: '3px 8px', borderRadius: '4px', fontSize: '12px', fontWeight: 'bold' }}>CRITICAL_DOWN</span>
                      </td>
                      <td style={{ padding: '12px 8px', color: '#a1a1aa' }}>⚠️ 복구 대기 및 관리자 검토 중</td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* TELEGRAM MODAL POPUP */}
      {showModal && tokenInfo && (
        <div style={{ position: 'fixed', top: 0, left: 0, width: '100%', height: '100%', backgroundColor: 'rgba(0,0,0,0.7)', display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: 1000 }}>
          <div style={{ backgroundColor: '#1c1c24', border: '1px solid #2d2d3d', padding: '30px', borderRadius: '16px', width: '450px', textAlign: 'center' }}>
            <h2 style={{ margin: '0 0 10px 0', fontSize: '20px', color: '#ffffff' }}>🔒 텔레그램 보안 알림 연동 인증</h2>
            <div style={{ backgroundColor: '#13131a', border: '1px solid #4361ee', padding: '20px', borderRadius: '12px', marginBottom: '20px', marginTop: '20px' }}>
              <div style={{ fontSize: '36px', fontWeight: 'bold', color: '#4cc9f0', letterSpacing: '4px' }}>{tokenInfo.token}</div>
            </div>
            <a href={tokenInfo.deepLinkUrl} target="_blank" rel="noopener noreferrer" style={{ display: 'block', backgroundColor: '#0088cc', color: '#fff', textDecoration: 'none', padding: '12px', borderRadius: '8px', fontWeight: 'bold', marginBottom: '12px' }} onClick={() => setShowModal(false)}>
              ✈️ 텔레그램 앱 열어 인증하기
            </a>
            <button onClick={() => setShowModal(false)} style={{ backgroundColor: 'transparent', color: '#71717a', border: 'none', cursor: 'pointer' }}>창 닫기</button>
          </div>
        </div>
      )}

      {/* [실시간 관제 긴급 팝업 오버레이] */}
      {(latest.cpuUsage >= cpuThreshold || latest.memoryUsage >= memThreshold) && selectedAgent && !isAlertDismissed && (
        <div style={{
          position: 'fixed', top: 0, left: 0, width: '100%', height: '100%',
          backgroundColor: 'rgba(255, 0, 0, 0.15)', backdropFilter: 'blur(4px)',
          display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: 9999,
          border: '10px solid #ff4d4d', boxSizing: 'border-box',
          animation: 'pulse 1.5s infinite alternate'
        }}>
          <style>{`
            @keyframes pulse {
              0% { rgba(255, 0, 0, 0.15); border-color: #ff4d4d; }
              100% { rgba(255, 0, 0, 0.3); border-color: #ff1a1a; box-shadow: inset 0 0 100px rgba(255,0,0,0.5); }
            }
          `}</style>

          <div style={{
            backgroundColor: '#1c1c24', border: '2px solid #ff4d4d', padding: '40px', borderRadius: '24px',
            width: '500px', textAlign: 'center', boxShadow: '0 25px 50px rgba(0,0,0,0.6)'
          }}>
            <span style={{ fontSize: '64px', display: 'block', marginBottom: '10px' }}>🚨</span>
            <h2 style={{ margin: '0 0 10px 0', fontSize: '28px', color: '#ff4d4d', fontWeight: '900' }}>CRITICAL INFRA ALERT</h2>
            <p style={{ color: '#a1a1aa', fontSize: '15px', margin: '0 0 25px 0' }}>인프라 노드에서 위험 수준의 이상 징후가 감지되었습니다.</p>
            
            <div style={{ backgroundColor: '#13131a', border: '1px solid #2d2d3d', padding: '20px', borderRadius: '12px', textAlign: 'left', display: 'flex', flexDirection: 'column', gap: '10px', fontSize: '15px' }}>
              <div style={{ color: '#fff' }}>• 대상 서버: <span style={{ color: '#4cc9f0', fontFamily: 'monospace' }}>{userRole === "ADMIN" ? selectedAgent : selectedAgent.replace(new RegExp(`^${userOrgCode}_`, "i"), "")}</span></div>
              
              {latest.cpuUsage >= cpuThreshold && <div style={{ color: '#ff4d4d', fontWeight: 'bold' }}>• 연산 과부하: CPU 점유율 {latest.cpuUsage.toFixed(1)}% (설정 기준: {cpuThreshold}%)</div>}
              {latest.memoryUsage >= memThreshold && <div style={{ color: '#ff4d4d', fontWeight: 'bold' }}>• 메모리 고갈: RAM 점유율 {latest.memoryUsage.toFixed(1)}% (설정 기준: {memThreshold}%)</div>}
            </div>

            <p style={{ color: '#71717a', fontSize: '13px', marginTop: '20px', marginBottom: 0 }}>* 해당 인프라 시스템이 복구되거나 정상 범위 진입 시 자동으로 해제됩니다.</p>
            
            {/* !! Alert 강제 무시 테스트 용도 !! */}
            <button 
              onClick={() => setIsAlertDismissed(true)}
              style={{
                backgroundColor: '#ff6b6b',
                color: '#fff',
                border: 'none',
                padding: '12px',
                borderRadius: '8px',
                fontSize: '14px',
                fontWeight: 'bold',
                cursor: 'pointer',
                marginTop: '20px',
                width: '100%',
                transition: 'background-color 0.2s'
              }}
              onMouseOver={(e) => e.target.style.backgroundColor = '#e05353'}
              onMouseOut={(e) => e.target.style.backgroundColor = '#ff6b6b'}
            >
              ❌ 경고 임시 숨기기 (장애 인지)
            </button>
          </div>
        </div>
      )}

      {/* PASSWORD CHANGE MODAL POPUP */}
      {showPwModal && (
        <div style={{ position: 'fixed', top: 0, left: 0, width: '100%', height: '100%', backgroundColor: 'rgba(0,0,0,0.7)', display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: 1000 }}>
          <div style={{ backgroundColor: '#1c1c24', border: '1px solid #2d2d3d', padding: '30px', borderRadius: '16px', width: '360px', textAlign: 'center' }}>
            <h2 style={{ margin: '0 0 20px 0', fontSize: '20px', color: '#ffffff' }}>🔑 비밀번호 변경 보안 세션</h2>
            <form onSubmit={handleChangePassword} style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
              <input type="password" value={currentPw} onChange={(e) => setCurrentPw(e.target.value)} placeholder="현재 비밀번호" style={{ width: '100%', boxSizing: 'border-box', backgroundColor: '#13131a', border: '1px solid #2d2d3d', borderRadius: '8px', padding: '12px', color: '#fff' }} />
              <input type="password" value={newPw} onChange={(e) => setNewPw(e.target.value)} placeholder="새 비밀번호 (8자 이상, 대문자/특수문자 포함)" style={{ width: '100%', boxSizing: 'border-box', backgroundColor: '#13131a', border: '1px solid #2d2d3d', borderRadius: '8px', padding: '12px', color: '#fff' }} />
              {pwError && <p style={{ color: '#ff6b6b', margin: 0, fontSize: '13px', textAlign: 'left' }}>❌ {pwError}</p>}
              <button type="submit" style={{ backgroundColor: '#4361ee', color: '#fff', border: 'none', padding: '14px', borderRadius: '8px', fontWeight: 'bold', cursor: 'pointer' }}>변경 적용하기</button>
              <button type="button" onClick={() => { setShowPwModal(false); setPwError(""); }} style={{ backgroundColor: 'transparent', color: '#71717a', border: 'none', cursor: 'pointer', marginTop: '5px' }}>취소하고 나가기</button>
            </form>
          </div>
        </div>
      )}

    </div>
  );
}

export default App;