# Third-Party Licenses

TossNoti는 아래 오픈소스 소프트웨어와 외부 서비스 위에서 동작합니다. 모든 프로젝트와 메인테이너들에게 감사드립니다.

마지막 업데이트: 2026-05-27

---

## 외부 서비스 (External Service)

| 서비스 | 라이선스 | 출처 |
|---|---|---|
| **ntfy.sh** (퍼블릭 메시지 릴레이) | Apache License 2.0 | https://ntfy.sh |
| ntfy server software | Apache License 2.0 | https://github.com/binwiederhier/ntfy |

> ntfy.sh의 코드는 본 앱에 번들되지 않으며 HTTP 클라이언트로만 사용됩니다. 자체 호스팅 또는 다른 ntfy 서버로 교체 가능합니다.

---

## 빌드 / 플러그인 (Build Tooling)

모두 Apache License 2.0.

| 도구 | 버전 | 출처 |
|---|---|---|
| Android Gradle Plugin (AGP) | 9.1.1 | https://developer.android.com/build |
| Kotlin Compose Compiler Plugin | 2.2.10 | https://kotlinlang.org/docs/compose-compiler.html |
| Google KSP (Kotlin Symbol Processing) | 2.3.5 | https://github.com/google/ksp |
| Secrets Gradle Plugin | 2.0.1 | https://github.com/google/secrets-gradle-plugin |
| Roborazzi Gradle Plugin | 1.59.0 | https://github.com/takahirom/roborazzi |
| Gradle Foojay Toolchains Resolver | 1.0.0 | https://github.com/gradle/foojay-toolchains |
| Gradle | 9.3.1 | https://gradle.org |

---

## 런타임 의존성 (Production)

### AndroidX / Jetpack — Apache License 2.0

| 라이브러리 | 버전 |
|---|---|
| androidx.core:core-ktx | 1.18.0 |
| androidx.activity:activity-compose | 1.10.1 |
| androidx.compose:compose-bom | 2024.09.00 |
| androidx.compose.material3:material3 | (BOM) |
| androidx.compose.material:material-icons-core | (BOM) |
| androidx.compose.ui:ui | (BOM) |
| androidx.compose.ui:ui-graphics | (BOM) |
| androidx.compose.ui:ui-tooling-preview | (BOM) |
| androidx.compose.ui:ui-tooling (debug only) | (BOM) |
| androidx.lifecycle:lifecycle-runtime-ktx | 2.8.7 |
| androidx.lifecycle:lifecycle-runtime-compose | 2.8.7 |
| androidx.lifecycle:lifecycle-viewmodel-compose | 2.8.7 |
| androidx.room:room-runtime | 2.7.0 |
| androidx.room:room-ktx | 2.7.0 |
| androidx.room:room-compiler (KSP) | 2.7.0 |

출처: https://developer.android.com/jetpack/androidx

### JetBrains Kotlin — Apache License 2.0

| 라이브러리 | 버전 | 출처 |
|---|---|---|
| Kotlin Stdlib | 2.2.10 | https://kotlinlang.org |
| kotlinx-coroutines-core | 1.10.2 | https://github.com/Kotlin/kotlinx.coroutines |
| kotlinx-coroutines-android | 1.10.2 | https://github.com/Kotlin/kotlinx.coroutines |

### Square (Block, Inc.) — Apache License 2.0

| 라이브러리 | 버전 | 출처 |
|---|---|---|
| OkHttp | 4.10.0 | https://square.github.io/okhttp/ |
| OkHttp Logging Interceptor | 4.10.0 | https://square.github.io/okhttp/ |
| Retrofit | 2.12.0 | https://square.github.io/retrofit/ |
| Retrofit Converter (Moshi) | 2.12.0 | https://github.com/square/retrofit |
| Moshi | 1.15.2 | https://github.com/square/moshi |
| Moshi Kotlin Codegen (KSP) | 1.15.2 | https://github.com/square/moshi |

### Firebase (Google) — Apache License 2.0 + Google API Terms

| 라이브러리 | 버전 | 출처 |
|---|---|---|
| Firebase BOM | 34.12.0 | https://firebase.google.com |

> BOM(Bill of Materials)만 선언되어 있고, 실제 Firebase 라이브러리는 현재 코드에서 사용하지 않습니다.

---

## 테스트 의존성 (APK에 포함되지 않음)

| 라이브러리 | 버전 | 라이선스 | 출처 |
|---|---|---|---|
| JUnit 4 | 4.13.2 | Eclipse Public License 1.0 | https://junit.org/junit4 |
| AndroidX Test JUnit | 1.3.0 | Apache License 2.0 | https://developer.android.com/jetpack/androidx/releases/test |
| AndroidX Test Core | 1.6.1 | Apache License 2.0 | (동일) |
| AndroidX Test Runner | 1.6.2 | Apache License 2.0 | (동일) |
| AndroidX Espresso Core | 3.7.0 | Apache License 2.0 | (동일) |
| AndroidX Compose UI Test JUnit4 | (BOM) | Apache License 2.0 | https://developer.android.com/jetpack/androidx/releases/compose-ui |
| AndroidX Compose UI Test Manifest | (BOM) | Apache License 2.0 | (동일) |
| kotlinx-coroutines-test | 1.10.2 | Apache License 2.0 | https://github.com/Kotlin/kotlinx.coroutines |
| Robolectric | 4.16.1 | **MIT License** | https://robolectric.org |
| Roborazzi (core, compose, junit-rule) | 1.59.0 | Apache License 2.0 | https://github.com/takahirom/roborazzi |

---

## 자산 (Assets)

| 항목 | 출처 | 저작권 |
|---|---|---|
| 앱 아이콘 (TossNoti bell) | ChatGPT 4o 이미지 생성 | 본 저장소 소유자 권한 |

---

## 라이선스 전문

### Apache License 2.0
대부분의 의존성이 사용. 전문: https://www.apache.org/licenses/LICENSE-2.0

핵심 요약:
- 사용/수정/배포/상업적 이용 자유
- 저작권 고지 및 라이선스 사본 포함 필수
- 변경한 부분 명시
- 보증 없음

### MIT License
Robolectric 한 가지가 사용. 전문: https://opensource.org/license/mit

### Eclipse Public License 1.0
JUnit 4가 사용. 전문: https://www.eclipse.org/legal/epl-v10.html

---

## 본 앱 자체 코드 라이선스

`LICENSE` 파일 참고. (TossNoti의 자체 작성 코드는 별도 라이선스가 적용됩니다)
