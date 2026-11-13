# POTQUEST / PleosAudioGuide

현대자동차 Pleos 공모전용 차량 오디오 학습 앱. 목적지 기반 추천과 일반 콘텐츠 청취를 독립적으로 제공한다.

## 시작하기
1. Git 저장소를 Clone한 후 Android Studio에서 프로젝트 루트를 연다.
2. 필요한 Android SDK를 설치하고 Gradle Sync를 실행한다. SDK 경로는 각 PC의 local.properties에 설정하며 공유하지 않는다.
3. 빌드 설정: minSdk 28, compileSdk/targetSdk 37, AGP 9.5.1, Gradle 9.6.0. Gradle JDK는 Android Studio에서 프로젝트 요구 버전에 맞게 설정한다(Java 소스/타깃 11과 Gradle 실행 JDK는 별개).
4. 오디(Odii) 음원 연동: 공공데이터포털에서 '한국관광공사_관광지 오디오 가이드정보_GW'를 활용신청하고, 일반 인증키(Decoding)를 각자 local.properties에 `ODII_SERVICE_KEY=발급키`로 추가한다. 키는 커밋하지 않는다. 키가 없어도 빌드되며 홈에 '인증키 미설정'이 표시된다.
5. 실제 내비·음성 검증은 Pleos Connect v2.1.0 에뮬레이터에서 수행한다. Pleos Maven 저장소에 접근할 수 있어야 한다.
6. 실행 구성을 app으로, 실행 장치를 Pleos 에뮬레이터로 선택하고 Run한다. 설치 후 기기 앱 목록에서 POTQUEST를 선택한다.
7. Git push/pull만으로 기기에 앱이 설치되지는 않는다. 최신 main을 받은 뒤 Run 또는 아래 명령으로 설치한다.

```powershell
.\gradlew.bat :app:installDebug
```

기기 테스트가 끝난 뒤 앱이 제거됐으면 다시 Run/installDebug로 설치한다.

Windows 빌드/테스트:
```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
# 에뮬레이터가 연결된 경우
.\gradlew.bat :app:connectedDebugAndroidTest
```

## 퀴즈 서버 실행
- Node.js 22 이상을 설치하고 quiz-server/.env.example을 같은 폴더의 .env로 복사한다.
- .env에 본인 OPENAI_API_KEY를 설정한다. Odii 키와 별개이며 앱/채팅/Git에 넣지 않는다.
- quiz-server 폴더에서 npm start를 실행하고 서버를 켜 둔다. 자세한 설정은 [서버 안내](quiz-server/README.md)를 참고한다.
- 에뮬레이터의 앱은 기본 http://10.0.2.2:8787/quiz로 연결한다. Git pull만으로 서버 실행/키 설정이 되지는 않는다.
- 운행별 청취/콘텐츠별 이어듣기/퀴즈는 Room에 저장한다. 누적 80% 청취한 대본이 출제 대상이며 안전한 주차 상태에서만 퀴즈가 열린다.
- 종료 안내 버튼 → 문제 사전 준비 → 가볍게/충분히/깊게 선택 → 풀이/해설/결과 순서다. 확보한 문제가 적으면 해당 분량만 활성화한다.
- 공용 인터넷 서버 배포/인증은 아직 없으며 PC 로컬 서버를 사용하는 프로토타입이다.

## 협업 흐름
- main은 공유 기준 브랜치로 사용하고 기능별 브랜치에서 작업한다.
- 작업 전 AGENTS.md, docs/PROJECT.md, docs/PROGRESS.md와 관련 코드를 확인한다.
- 최신 main에서 브랜치를 만든다. 기존 미커밋 변경은 먼저 정리한다.
```powershell
git switch main
git pull --ff-only
git switch -c feature/explore
```
- 작은 단위로 수정하고 빌드·테스트 후 해당 파일만 선택해 커밋한다.
```powershell
git add app/src/main/java/com/example/pleosaudioguide/ui/ExploreScreen.kt
git commit -m "Add explore categories"
git push -u origin feature/explore
```
- GitHub에서 main 대상 Pull Request를 만들고 팀원이 확인한 뒤 병합한다. main 직접 push와 강제 push는 피한다.
- 작업 도중 main 변경을 반영할 때는 git fetch origin 후 기능 브랜치에서 git merge origin/main을 사용한다. 충돌은 내용을 확인해 해결하고 다시 빌드한다.
- 기능·설계 변경과 직접 수행한 검증 결과는 관련 docs 문서도 함께 갱신한다.

## 탐색 담당자 작업 범위
- 진입점: app/src/main/java/com/example/pleosaudioguide/ui/ExploreScreen.kt
- ExploreScreen(contents, canPlay, onPlay, modifier) 계약을 유지하고 검색·카테고리·목록 UI를 확장한다.
- 콘텐츠 재생은 onPlay(목록, 선택 인덱스)를 호출한다.
- 전달된 modifier를 화면 루트에 적용한다.
- 공통 하단 메뉴·미니 플레이어·재생 ViewModel·SDK는 새로 만들지 않는다.
- 공통 프레임은 ui/GuideApp.kt, 홈은 ui/HomeScreen.kt. 공통 함수 시그니처나 데이터 모델 변경이 필요하면 담당자끼리 먼저 합의한다.
- 탐색 화면의 기본 목록·재생 연결은 이미 구현돼 있다. 중복 개발하지 않는다.

## 공유 제외
local.properties, 빌드 산출물, IDE 개인 설정, 비밀키·서명 파일·토큰은 커밋하지 않는다.
Gradle Wrapper, 앱 소스·리소스·테스트, Gradle 설정, AGENTS.md와 docs는 공유한다.

## 현재 제한
퀘스트 보상·보관함은 준비 중이며 MY POT에는 최근 운행 퀴즈 재진입이 있다. 실제 음원은 콘텐츠별 이어듣기를 지원하며, 시연 TTS는 중지 후 처음부터 다시 듣는다. LLM 출제 정확성/실제 차량 시나리오는 추가 검증이 필요하다.
남은 시간 원본값은 수신하지만 단위 검증과 시간 기반 추천 정렬은 미완료다.
자세한 완료/검증/다음 작업은 docs/PROGRESS.md를 참고한다.

## 팀 저장소
https://github.com/wonjoon9706/GimguiStudio

Clone 주소: https://github.com/wonjoon9706/GimguiStudio.git
공유 기준 브랜치: main. 팀원은 저장소 접근 권한을 받은 뒤 Clone하고 기능 브랜치에서 작업한다.
