# POTQUEST 퀴즈 중계 서버

공모전 에뮬레이터용 로컬 서버. Node.js 22 이상, 추가 npm 의존성 없음.
OpenAI Responses API + Structured Outputs를 사용한다. API 키는 Android 앱에 넣지 않는다.

## 실행
1. 이 폴더의 .env.example을 복사해 .env를 만들고 OPENAI_API_KEY에 본인 키를 입력한다. 키를 채팅/커밋에 넣지 않는다.
2. Android Studio Terminal의 작업 폴더를 quiz-server로 이동하고 npm start를 실행한다.
3. http://127.0.0.1:8787/health 에서 ready=true인지 확인한다. 키 미설정이면 false다. true는 키 값이 설정됐다는 뜻이며 유효성·모델 접근은 실제 생성 요청에서 확인한다.
4. Pleos 에뮬레이터에서 앱 실행 → 실제 주행/청취 → P·정지·UX 해제 → 종료 안내 → 퀴즈로 진입한다.

기본 모델은 gpt-4.1-mini이며 .env의 OPENAI_MODEL로 바꿀 수 있다.
이미 실행한 서버는 .env 변경 후 재시작한다. 실제 API 사용에는 해당 계정의 키·모델 접근·사용 가능 잔액이 필요하다.

## Android 연결
- debug 기본 주소: http://10.0.2.2:8787/quiz (Android Emulator가 PC 루프백에 접근하는 주소).
- 주소를 바꾸려면 프로젝트 local.properties에 QUIZ_API_URL을 지정하고 Sync/Run한다.
- debug에서만 10.0.2.2/127.0.0.1/localhost의 HTTP를 허용한다. release는 HTTPS 중계 서버를 지정해야 한다.
- 기본 HOST는 127.0.0.1이다. 클라우드는 HOST=0.0.0.0, 서비스가 제공한 PORT와 HTTPS 주소를 사용한다.
- Dockerfile은 Node22에서 서버 코드만 복사하며 .env/API 키는 이미지에 포함하지 않는다. 키는 배포 서비스의 환경 변수 OPENAI_API_KEY로 설정한다. 컨테이너 없이 npm start도 .env 없이 서비스 환경 변수만으로 실행 가능하다.
- 배포 준비 단계이며 실제 서비스/주소는 아직 미정이다. 외부 공개 전 인증·요청 제한·비용 제한을 준비해야 한다. 기본 동시 생성1건 제한만으로 공개 운영 보호가 완성되는 것은 아니다.
- 실제 HTTPS /quiz 주소 확정 후 앱 QUIZ_API_URL 변경·재빌드·외부 환경 실생성 검증이 필요하다. 현재 APK의 로컬 주소를 외부 서비스로 자동 대체하지 않는다.
- Git pull/push는 서버 실행이나 API 키 설정을 대신하지 않는다.

## 심사용 공통 토큰
- 사용자가 별도 로그인 없는 시연 방식을 선택했다. 서버 QUIZ_ACCESS_TOKEN과 앱 local.properties QUIZ_ACCESS_TOKEN을 일치시켜 재빌드한다. 앱은 Authorization: Bearer 헤더로 전달한다. 실제 토큰은 Git/문서에 기록하지 않는다. APK 토큰은 추출 가능하므로 사용자 인증/완전한 남용 방지로 간주하지 않는다. OpenAI 키는 서버에만 둔다.
- HOST가 루프백 이외이면 QUIZ_ACCESS_TOKEN 없이는 시작하지 않는다. MAX_GENERATIONS(기본100)는 프로세스 시작 이후 실제 모델 호출 시도 상한이며 실패도 소모한다. 재시작하면 초기화되므로 영구/금액 한도는 아니다. 동시 요청1건 제한도 유지한다.
- 거부된 토큰은 모델 호출을 하지 않는다. 토큰 변경 시 심사용 APK도 다시 만들어야 한다. 공개 전 HTTPS 및 외부 기기 실제 생성 검증이 필요하다.

## 요청·검증
POST /quiz: tripId, count(1~10 최대 개수, 앱은 10 요청), sources([{id,title,script}]).
응답: tripId, count(검증된 실제 개수 1~요청 개수), questions([{sourceId,prompt,options[3],correctIndex,explanation,evidence}]).
운행 ID·시각·위치·청취 구간은 OpenAI에 보내지 않고 대본 ID/제목/본문과 문제 수만 보낸다.
입력 크기·중복 ID·문제 수·보기·출처·2~600자 원문 근거를 검증한다. 근거 비교는 NFKC 정규화와 공백/문장부호 차이를 허용하되 소수점·숫자 구분 기호는 보존한다. 정답 보기 문구가 인용 안에 그대로 있어야 한다는 조건은 제거했다. 의미적 정답 여부는 모델 지시와 실제 품질 검증에 의존한다. 짧은 대본은 장소/인물/특징의 쉬운 1~3문제를 우선한다. health의 validation=light-evidence-v1으로 최신 서버 실행을 구분할 수 있다.
짧은 대본은 만들 수 있는 문제만 반환한다. 잘못된 근거/중복 문제는 제외하고 1개 이상이면 제공한다. 0개/거부/불완전 응답/오류는 실패로 반환하며 임의 예시 문제로 대체하지 않는다. 실행 중 서버는 코드 변경 후 재시작해야 한다.
추론 내용의 의미적 정확성까지 프로그램 검증으로 보장하지는 않는다.
동시 생성은 한 요청으로 제한하며 연결 취소·90초 제한을 적용한다. 키·대본·전체 응답은 로그에 남기지 않는다.

## 검증
npm test (또는 node --test, 프로젝트 루트에서 ./gradlew.bat :app:testQuizServer). 테스트 전용 fetch 응답을 사용하며 실제 LLM 생성 검증과 다르다.
실제 키가 없으면 네트워크/API 모델의 출제 품질과 실제 성공은 검증할 수 없다.
앱은 생성된 문제와 풀이를 운행/문제 수별 Room에 저장해 같은 퀴즈를 다시 열 때 재호출하지 않는다.

공식 근거:
- [Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=responses)
- [GPT-4.1 mini](https://developers.openai.com/api/docs/models/gpt-4.1-mini)
