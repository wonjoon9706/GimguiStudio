# 프로젝트 요구사항과 설계

## 목적과 환경
현대자동차 Pleos 공모전용 차량 오디오 학습 앱. 실제 Pleos 에뮬레이터에서 핵심 기능이 작동하는 프로토타입을 목표로 한다.
- Windows / Android Studio / Kotlin / Jetpack Compose + Material 3
- 프로젝트: PleosAudioGuide / 패키지: com.example.pleosaudioguide / 단일 :app
- minSdk 28, compileSdk·targetSdk 37
- 테스트 대상: Pleos Connect v2.1.0

## 초기 개발 범위
- 내비: 현재 목적지 이름·위치, 남은 예상 주행시간 조회, 목적지 변경·도착 이벤트 처리.
- 목적지 추천: 준비된 콘텐츠와 장소·지역 매칭 규칙, 남은 시간에 맞는 길이 우선.
- 일반 콘텐츠: 관광·역사 / 일반 상식 / 어학 학습 / 기타 교양 카테고리와 목록에서 직접 선택.
- 플레이어: 수동 선택, 재생·일시정지, 이전·다음, 진행률, 제목·시간, 연속 재생, 플레이리스트 관리.
- 퀴즈: 청취 콘텐츠와 연결된 수동 등록 객관식 문제, 채점·결과·해설. 일반 콘텐츠에도 적용하며 안전한 정차 상태에서만 진행.

## 주요 설계 결정
- 일반 콘텐츠는 내비·추천과 독립적으로 항상 제공한다. 목적지가 있어도 자유롭게 선택할 수 있다.
- 목적지 추천과 일반 목록은 UI에서 구분하고 공통 플레이어·재생 큐를 사용한다.
- 목적지 변경으로 사용자의 재생 콘텐츠나 큐를 자동 교체하지 않는다.
- 직접 등록한 로컬 콘텐츠 데이터로 시작한다. 첫 시연은 음원 부재로 등록 원고를 Pleos TextToSpeech ON_DEVICE 모드로 재생한다(서버·AI 생성 없음). 추정 길이와 실제 재생시간을 구분한다. 녹음 음원은 AudioContent.audioUri에 연결하며 Media3로 재생한다.
- 추천은 관련성 우선, 남은 시간 내 재생 완료 가능 여부를 고려한다. 시간이 없으면 관련성만 사용하며, 관련 콘텐츠가 없으면 일반 콘텐츠를 안내한다.
- 단일 앱 구조를 유지한다. MainActivity는 화면, GuideViewModel은 상태·동작 조율, NavigationSource는 SDK→앱 목적지 변환, ContentCatalog/ContentRecommender는 데이터·규칙, PleosNarration/GuidePlaybackService는 음성·음원 재생을 담당한다. UI·추천 규칙 변경을 각 경계 안에서 처리한다.
- Media3 ExoPlayer + MediaSessionService는 등록 음원용으로 구성했다. 첫 시연의 Pleos TTS는 직접 재생 경로로, 중지·처음부터 재생을 제공한다. TTS의 정확한 진행률·일시정지·시스템 미디어 제어는 미지원이며 녹음 음원 단계에서 검증한다. 내비 화면 전환·백그라운드·오디오 포커스도 추가 검증한다.
- 초기 큐는 메모리 기반 추가·삭제·순서 변경으로 시작한다.
- 내비 도착과 안전한 정차를 구분한다. 확인된 차량 상태로 퀴즈를 허용하고, 상태 불명 시 제한한다.
- 실제 SDK / 테스트 모드를 명시하고 데이터 출처를 숨기지 않는다.

## 홈과 화면 전환 (2026-11-10)
- 홈 순서: 목적지 기반 팟캐스트 → 이어서 듣기(기록 없거나 재생 중이면 숨김) → 오늘의 퀘스트 → 추천 에피소드.
- GuideApp이 고정 하단 메뉴(홈/탐색/보관함/MY POT)와 미니 플레이어를 소유한다. 탭별 스크롤과 선택 탭을 보존하고 공통 GuideViewModel을 유지한다.
- 미니 플레이어는 현재 세션이 있을 때 표시하며 중지/재생·이전·다음을 제공한다. 완료 시 숨기고, 일시정지/중지 상태에서는 재생 재개를 위해 유지한다.
- 이어서 듣기는 SharedPreferences에 마지막 콘텐츠 ID와 음원 위치만 저장한다. 전체 청취 이력은 범위 밖이다. TTS는 위치 이어듣기가 불가하므로 '처음부터 다시 듣기'로 표시한다.
- 오늘의 퀘스트는 준비 중 카드이며 실제 퀘스트 로직은 미구현. 추천 에피소드는 로컬 시연 목록이다. 보관함/MY POT은 준비 화면이다.
- 팀원 탐색 작업 진입점: app/src/main/java/com/example/pleosaudioguide/ui/ExploreScreen.kt.
  - 계약: ExploreScreen(contents, canPlay, onPlay, modifier). onPlay(목록, 인덱스)로 공통 플레이어 호출.
  - 전달받은 modifier를 최상위에 적용한다. 하단 메뉴·미니 플레이어·SDK·별도 ViewModel은 이 화면에서 만들지 않는다.
  - 검색·필터·레이아웃은 이 파일 안에서 확장한다. 공용 EpisodeRow는 HomeScreen.kt에 있다.
- 프레임 파일은 ui/GuideApp.kt, 홈 콘텐츠는 ui/HomeScreen.kt. 탐색 작업은 이 파일들을 수정하지 않아도 된다.

## 오디오 심사 정책
- 공식 Music/Audio 정책을 기준으로 팟캐스트 유형과 MBS 호환 MediaLibraryService로 개발한다. Manifest 오디오 카테고리·런처 메타데이터를 반영했다.
- 홈/탐색 화면 계약은 유지한다. 등록 음원만 시스템 브라우저에서 재생 가능하게 제공하고, 별도 TTS 시연을 표준 미디어 재생 준수로 간주하지 않는다.
- 제출 전 TTS/실제 음원과 시스템 플레이어 통합, 차량 키·포커스·Suspend·CarUxRestrictions 검증이 필요하다.
- 상세 기준/구현/미충족 구분은 docs/AUDIO_POLICY.md를 참조한다. 공모전 별도 공고는 추가 확인 필요.

## SDK 근거와 미확정 사항
2026-11-08 공식 문서 확인 기준:
- NaviHelper 3.0.5: getDestinationInfo() 요청 후 onDestinationInfo()로 수신.
- onDestinationChanged(), onDestinationArrived() 지원. 실제 이벤트 전달은 Navigation 앱 상태·구현에 따라 달라질 수 있다.
- 릴리즈 노트에 DestinationInfo.totalTime → remainTime 변경이 명시되어 남은 시간 필드 존재 확인.
- SDK 바이너리 선언 확인: DestinationInfo.name/address, latitude/longitude(Double), remainTime(Int). 에뮬레이터에서 목적지 이름·주소·좌표와 남은 시간 원본 수신 확인. 시간 단위·미설정 값·갱신 방식은 추가 검증하므로 현재 시간 기반 정렬은 보류한다.
- Maven: https://nexus-playground.pleos.ai/repository/maven-releases/
- 내비 의존성: ai.pleos.playground:NaviHelper:3.0.5 / 조회 권한: pleos.car.permission.NAVI_ROUTE
- 시연 음성: ai.pleos.playground:TextToSpeech:3.3.1 / pleos.car.permission.TTS_SERVICE / Mode.ON_DEVICE, Locale.KOREA.
- 표준 Android 한국어 TTS는 현재 에뮬레이터에서 사용 불가를 확인해 최종 구현에서 제외했다.
- 정차 판정용 Vehicle SDK 검토 예정(한국어 설치 문서: ai.pleos.playground:Vehicle:2.3.2). 정확한 신호·권한·에뮬레이터 지원은 미확정.

공식 문서:
- [NaviHelper 소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro)
- [의존성](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/importing-sdk)
- [권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions)
- [목적지 조회](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations)
- [이벤트](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/event-listeners)
- [SDK 릴리즈 노트](https://document.pleos.ai/docs/pleos-only/release-note/sdk-release-note)
- [Vehicle SDK](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro)
- [Pleos TTS](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro)
- [Media3 백그라운드 재생](https://developer.android.com/media/media3/session/background-playback)

## 초기 범위 제외
Gleo 음성 제어, AI 콘텐츠 생성·추천 고도화, 영구 청취·학습 이력, 개인화 추천.
