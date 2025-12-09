# Memocore


![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple?logo=kotlin)
![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android)
![Min SDK](https://img.shields.io/badge/Min%20SDK-26-blue?logo=android)
![Target SDK](https://img.shields.io/badge/Target%20SDK-35-blue?logo=android)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![ObjectBox](https://img.shields.io/badge/DB-ObjectBox-FF0099)


**Memocore**는 사용자의 개인 문서(PDF, DOCX, MD)를 기반으로 AI와 채팅할 수 있는 **오프라인 중심의 프라이버시 보호 AI 워크스페이스**입니다. 인터넷 연결 없이도 온디바이스(On-Device) 모델을 활용하여 안전하게 질문하고 답변을 받을 수 있으며, 필요 시 클라우드 모델(Gemini)과도 연동이동 가능한 하이브리드 아키텍처를 갖추고 있습니다.

## 🚀 프로젝트 핵심 가치
*   **프라이버시 중심 (Privacy-First):** 모든 데이터는 로컬에 안전하게 저장되며, 온디바이스 AI를 통해 민감한 정보 유출을 방지합니다.
*   **하이브리드 AI 엔진:** 강력한 성능의 **Cloud 모델**과 보안성이 뛰어난 **On-Device 모델**을 상황에 맞춰 선택하여 사용할 수 있습니다.
*   **고성능 벡터 검색:** ObjectBox 데이터베이스와 HNSW 인덱싱을 활용하여 대용량 문서에서도 빠르고 정확한 문맥 검색(RAG)을 지원합니다.

## ✨ 주요 기능
*   **다양한 문서 지원:** PDF, Word(.docx), Markdown(.md) 파일을 불러와 대화할 수 있습니다.
*   **스마트 채팅 (RAG):** 사용자의 질문과 관련된 문서의 맥락을 정확히 파악하여 답변합니다.
*   **에이전트 모드 (ReAct):** 단순 답변을 넘어, AI가 스스로 검색하고 판단하여 복합적인 질문을 해결합니다.
*   **Markdown 편집:** 앱 내에서 Markdown 문서를 직접 생성하고 수정할 수 있습니다.
*   **URL 문서 가져오기:** 웹 페이지 내용을 즉시 문서로 변환하여 채팅에 활용할 수 있습니다.

## 🛠 기술 아키텍처 (Tech Stack)
이 프로젝트는 최신 안드로이드 개발 표준과 **MVVM (Model-View-ViewModel)** 패턴을 따릅니다.

*   **Language:** [Kotlin](https://kotlinlang.org/)
*   **UI Framework:** [Jetpack Compose](https://developer.android.com/jetpack/compose) (Reactive UI)
*   **Architecture:** Clean Architecture + MVVM
*   **DI (Dependency Injection):** [Koin](https://insert-koin.io/)
*   **Local Database (Vector Store):** [ObjectBox](https://objectbox.io/) (NoSQL, Vector Search)
*   **AI & ML:**
    *   **Cloud:** Google AI Client SDK (Gemini)
    *   **On-Device:** MediaPipe ListRT (TFLite) for Gemma 2B / Nano
    *   **Embeddings:** ONNX Runtime with `all-MiniLM-L6-V2`

## 📂 프로젝트 구조
```
app/src/main/java/com/danpun9/memocore/
├── data/           # Data Layer (ObjectBox Entities, Repository)
├── domain/         # Domain Layer (ChatAgent, UseCases, LLM Interfaces)
├── ui/             # UI Layer (Screens, ViewModels, Compose Components)
└── di/             # Koin Modules
```

## ⚡ 설치 및 실행 방법 (Getting Started)

### 사전 요구 사항 (Prerequisites)
*   Reference: `libs.versions.toml`
*   Android Studio (Ladybug, Koala, or newer recommendation)
*   JDK 21
*   Android Device with NPU support recommended for LiteRT (Galaxy S23+ recommended)

### 빌드 및 실행
1.  **프로젝트 클론:**
    ```bash
    git clone https://github.com/your-username/memocore.git
    ```
2.  **프로젝트 열기:** Android Studio에서 `Android-Document-QA` 폴더를 엽니다.
3.  **Gradle Sync:** 프로젝트 의존성을 다운로드합니다.
4.  **앱 실행:** 연결된 디바이스 또는 에뮬레이터에서 앱을 실행합니다.

### 모델 설정
*   **Gemini (Cloud):** 앱 실행 후 `Settings` 메뉴에서 API Key를 입력해야 합니다.
*   **Local Model:** `Settings` > `Manage Local Models`에서 호환되는 `.tflite`모델을 다운로드하거나 로드해야 합니다.

## ⚠️ 알려진 제한 사항 및 주의사항
*   **메모리 사용량:** 대용량 PDF(50MB 이상) 로드 시 메모리 부족(OOM) 현상이 발생할 수 있습니다.
*   **컨텍스트 윈도우:** 로컬 모델 사용 시, 긴 대화가 이어지면 컨텍스트 제한으로 인해 답변 품질이 저하될 수 있습니다. (향후 개선 예정)
*   **초기 로딩:** 벡터 검색을 위한 임베딩 모델(ONNX) 초기화에 시간이 소요될 수 있습니다.


---