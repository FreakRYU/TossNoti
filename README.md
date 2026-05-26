<div align="center">

<img src="app/src/main/res/drawable/ic_launcher_foreground.png" width="140" alt="TossNoti icon" />

# TossNoti

### 다른 기기의 알림을 내 폰으로 토스해주는 원격 알림 릴레이 앱

_Cross-device notification relay over an end-to-end encrypted channel._

</div>

---

## 한 줄 소개

**태블릿에서 받은 인스타·카톡·텔레그램 알림을 PIN으로 페어링된 폰에 실시간으로 전달**합니다. 내용은 종단간 암호화되어 어떤 서버도 못 읽고, 추가 인프라 셋업 없이 무료로 동작합니다.

## 왜 만들었나

태블릿을 작업용으로 쓰는데 알림을 자꾸 놓치게 되어서 만들었습니다. KakaoTalk 같은 일부 메신저는 멀티 디바이스 동기화가 제한적이고, 폰을 다른 방에 두면 인스타 DM·팔로우·댓글 알림을 못 보는 일이 잦았어요.

기존 솔루션들의 한계:
- **KDE Connect** — 같은 와이파이 필요, 설정 복잡
- **Pushbullet** — 유료, 미러링 기능 제한적
- **Tasker 등 자동화 앱** — 셋업 복잡, UI 안 예쁨
- **FCM 직접 구현** — Firebase 프로젝트 셋업 필요

→ **PIN 6자리 입력 두 번이면 셋업 끝나는 앱**을 만들고 싶었음.

## 주요 기능

- 🔒 **PIN 기반 페어링** — 6자리 PIN 한 번 입력으로 두 기기 연동
- 🔐 **종단간 암호화** — AES-256-GCM + PBKDF2(100k 라운드) 키 파생
- 📱 **앱 선택기** — 송신 기기에 설치된 모든 앱 중 가로챌 대상 선택 (검색 가능)
- 🏷 **실제 앱 이름 표시** — 인스타 알림은 `[Instagram]`, 카톡은 `[KakaoTalk]` 형식으로 자동 라벨링
- 🔋 **스마트 절전** — 수신 폰 화면 OFF 시 자동 연결 종료, ON 후 10초 디바운스 재연결 (배터리 5-10%/일 → 1-3%/일)
- 📦 **중복 제거** — 같은 앱이 알림을 업데이트할 때 (예: "메시지 1개→2개") 중복 전송 방지
- 🔁 **자동 재연결** — 네트워크 끊겨도 지수 백오프로 재연결, `?since=` 파라미터로 누락 메시지 복구
- 🗂 **활동 로그** — 송수신 내역 시각화 + 삭제 / 일괄 비우기
- 💎 **Coinbase 스타일 라이트 UI** — Compose Material 3

## 작동 화면

```
[송신 기기 - 태블릿]              [수신 기기 - 폰]
┌──────────────────────┐         ┌──────────────────────┐
│ TossNoti     ● Live  │         │ TossNoti      ● Live │
│                       │         │                       │
│ ┌─Sender─┐ ┌─Recv───┐│         │ ┌─Sender─┐ ┌─Recv───┐│
│ │ 378240 │ │  Off   ││         │ │   —    │ │  Live  ││
│ │ 3 apps │ │ Phone..│           │ │ 0 apps │ │ Phone..│
│ └────────┘ └────────┘│         │ └────────┘ └────────┘│
│                       │         │                       │
│ Manage                │         │ Pair this device      │
│ 🔒 Pairing PIN        │         │ Enter sender's PIN    │
│    378240             │         │   378240              │
│ 🔔 Target apps        │         │ [Start receiving]     │
│    3 selected         │         │                       │
│ [📷 Instagram] [💬 KakaoTalk]  │ Activity              │
│                       │         │ 📷 Instagram   2m ago│
│ ✓ Notification access │         │    "John이 ..."       │
└──────────────────────┘         └──────────────────────┘
```

## 작동 방식

```
[송신 태블릿]                                                [수신 폰]
                                                            
NotificationListenerService                              Foreground Service
   ↓ 알림 가로채기                                            ↑ HTTPS 영구 스트림
PackageManager 조회                                          │ (화면 ON일 때만)
   ↓ 앱 이름 / 아이콘                                          │
RelaySender                                                  ↑
   ↓ AES-256-GCM 암호화                                       │
   ↓ HTTPS POST                                              │
       ↓                                                     │
       └─────→ ntfy.sh (퍼블릭 메시지 릴레이) ─────────────→
                       ↑                                     │
                       │ 익명, 계정 없음, 무료                │
                       └─ 토픽: atoss_<PIN 해시 24자> ────────┘
```

각 PIN으로부터 두 가지가 동시에 파생됩니다:
- **토픽 이름** (SHA-256 해시) — 어디로 보낼지
- **AES-256 키** (PBKDF2 100k 라운드) — 어떻게 암호화할지

같은 PIN을 양쪽에 입력해야 같은 토픽을 듣고 같은 키로 복호화 가능. PIN 모르는 사람은 토픽도 못 찾고, 운 좋게 찾아도 못 읽음.

## 빌드 및 실행

### 필요 사항
- **Android Studio Hedgehog** (2023.1.1) 이상 또는 호환 CLI 환경
- **JDK 21** (Eclipse Adoptium / OpenJDK)
- **Android SDK Platform 36** (Android 16)
- **Android Build Tools 36.0.0**
- 양쪽 기기 모두 Android 7.0 (API 24) 이상

### 빌드
```bash
git clone https://github.com/<your-username>/TossNoti.git
cd TossNoti
./gradlew :app:assembleDebug
```

APK 위치: `app/build/outputs/apk/debug/app-debug.apk`

### 기기에 설치 (USB 디버깅 켠 상태)
```bash
./gradlew :app:installDebug
```

또는 APK 파일을 기기로 옮겨서 직접 탭.

> **참고**: 디버그 키스토어(`debug.keystore`)는 `.gitignore`에 포함되어 있어 저장소에 없습니다. 첫 빌드 시 직접 생성하거나, 기존 키스토어를 프로젝트 루트에 두세요:
> ```bash
> keytool -genkeypair -v -keystore debug.keystore -storetype PKCS12 \
>   -storepass android -alias androiddebugkey -keypass android \
>   -dname "CN=Android Debug,O=Android,C=US" \
>   -keyalg RSA -keysize 2048 -validity 10000
> ```

## 사용법

### 1) 두 기기 모두 설치
같은 APK를 송신 기기(태블릿)와 수신 기기(폰) 양쪽에 설치합니다.

### 2) 송신 기기 (태블릿)
1. TossNoti 실행
2. 상단 탭에서 **Sender** 선택
3. **"Generate"** 눌러 6자리 PIN 생성
4. **알림 접근 권한 허용**: 시스템 설정 → 알림 → 특수 액세스 → TossNoti
5. **"앱 선택"** 눌러 가로챌 앱 지정 (인스타·카톡·텔레그램 등)

### 3) 수신 기기 (폰)
1. TossNoti 실행
2. 상단 탭에서 **Receiver** 선택
3. 송신 기기의 6자리 PIN 입력
4. **"Start receiving"** 누름

이제 송신 기기에 알림이 뜨면 수신 기기에서 자동으로 받아 표시합니다.

### 4) Samsung 사용자 — Auto Blocker 해제
설정 → 보안 및 개인정보 보호 → **Auto Blocker** → OFF

(One UI 6 이상부터 기본 ON. 이 토글이 켜져있으면 사이드로드와 ADB 설치가 차단됨)

### 5) 배터리 최적화 해제
설정 → 앱 → TossNoti → 배터리 → **제한 없음**

(특히 Samsung은 백그라운드 앱을 매우 공격적으로 종료시킴)

## 보안 모델

| 위협 | 방어 |
|---|---|
| ntfy 서버가 메시지 훔쳐봄 | AES-256-GCM 종단간 암호화 |
| MITM이 패킷 변조 | GCM 인증 태그로 변조 감지 |
| 누가 토픽 추측 / 무차별 대입 | SHA-256 해시 토픽 + PBKDF2 키 파생 (브루트포스 비용 약 28시간 CPU) |
| ISP / 와이파이 운영자가 내용 봄 | HTTPS + E2E |

**한계**: PIN 6자리(10⁶ 조합)는 진지한 공격자에겐 약함. 친구·가족·본인 기기간 사용엔 충분하지만 매우 민감한 정보엔 부적합. 진짜 안전이 필요하면:
- 더 긴 PIN (10+자리 영숫자)
- 자체 ntfy 서버 호스팅
- FCM으로 전환 (Google이 관리)

자세한 보안 분석은 [코드 주석 + CryptoUtils.kt 참고](app/src/main/java/com/example/security/CryptoUtils.kt).

## 한계 및 알려진 이슈

- ntfy.sh 무료 한도: **하루 250개 메시지**, **메시지당 4KB**. 이 정도는 개인용으로 충분.
- 페이로드는 텍스트만 전송하므로 메시지당 약 200-300B (아이콘 미전송).
- 폰 재부팅 시 수신 서비스 자동 시작 안 됨 (`BootReceiver` 미구현).
- 수신 폰 화면 12시간 이상 OFF면 ntfy 보관 만료로 일부 알림 손실 가능.
- Samsung Knox 관리 기기는 사이드로드 자체가 차단됨 — 우회 불가.

## 기술 스택

- **언어**: Kotlin 2.2.10
- **UI**: Jetpack Compose Material 3, light theme
- **DB**: Room 2.7.0 (with KSP)
- **네트워크**: OkHttp 4.10.0 (raw HTTP)
- **암호화**: javax.crypto (AES-GCM + PBKDF2)
- **메시지 릴레이**: [ntfy.sh](https://ntfy.sh)
- **빌드**: Gradle 9.3.1, AGP 9.1.1
- **최소 SDK**: 24 (Android 7.0), 타겟 SDK: 36 (Android 16)

## 라이선스

**프로젝트 코드**: [`LICENSE`](LICENSE) 파일 참고

**사용된 오픈소스**: [`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md) — 모든 의존성과 라이선스 목록

## Credits

- 메시지 릴레이 인프라: [ntfy.sh](https://ntfy.sh) by Philipp Heckel
- 앱 아이콘: ChatGPT 4o 이미지 생성
- 디자인 영감: Coinbase mobile app
- 영감을 준 비슷한 프로젝트들: KDE Connect, Pushbullet, Tasker

---

<div align="center">
<sub>Made with care for personal use. Not affiliated with any service mentioned above.</sub>
</div>
