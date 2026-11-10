# 오디오 정책 적용 현황

확인일: 2026-11-10. Pleos 공식 개발·App Market 심사 기준이다.
공모전 별도 공고/첨부 문서는 아직 확보하지 못했으므로 공모전 전체 요건 충족으로 해석하지 않는다.

## 기준과 적용
| 기준 | 현재 상태 |
|---|---|
| 오디오가 주 기능, Music/Audio 하위 유형 구분 | 팟캐스트(에피소드형 음성)로 설계. 학습 주제는 팟캐스트 아래 분류 |
| audio 앱 카테고리·런처 메타데이터 | Manifest 반영 |
| MBS 호환 콘텐츠 탐색·MediaSession | Media3 MediaLibraryService + 플랫폼 브라우저 액션 반영 |
| 브라우징 구조·미디어 ID·검색 | 팟캐스트 > 학습 카테고리 > 등록 음원, ID 해석·검색·페이지 처리 구현 |
| 접속·프로필 변경 시 자동 재생 금지 | 서비스 접속/조회에서 재생 시작 안 함. 프로필 변경 실제 검증 필요 |
| 재생·일시정지·중지·이전/다음·차량 키 | 등록 음원 Media3 경로는 기반 제공, 실제 차량 키 검증 필요. 현재 TTS 시연은 별도 경로라 미충족 |
| 오디오 포커스·통화·긴급 안내·Gleo | ExoPlayer MEDIA/SPEECH 포커스 위임. TTS 경로의 속성/포커스와 차량 시나리오는 미검증 |
| 태스크 제거 시 정지/종료 | Media3 서비스 stop→stopSelf→super 반영. TTS 서비스 수명 통합은 미완료 |
| 주행 UX 제한 | CarUxRestrictions 연동 필요. 현재 미구현. DO 메타데이터를 임의로 선언하지 않음 |
| 퀴즈·검색·키보드 | 퀴즈 미구현 상태 유지. 추가 시 CarUxRestrictions와 안전한 정차 확인 필요 |
| 음성 앞단 150~200ms 묵음 | 공식 권장사항. TTS 스트림의 SDK 처리 여부/음원 패딩 검증 필요. 단순 지연을 적용 완료로 간주하지 않음 |
| 제목·썸네일 | 서비스 항목에 제목·기본 아트워크 제공. 실제 콘텐츠별 이미지 교체 필요 |

## 제출 전 핵심 차이
- 현재 등록된 콘텐츠는 TTS 시연 원고이며 audioUri가 없다.
- 시스템 브라우저에는 재생 가능한 등록 음원만 노출한다. 현재 카테고리 안이 비어 있는 것은 의도한 상태다.
- 화면에서 TTS를 듣는 것과 차량 표준 미디어 재생 준수는 다르다.
- 제출용은 실제 음원을 등록하고 MediaLibraryService 재생 경로로 통합하거나, TTS를 서비스/Player로 통합해 동일한 제어·포커스 계약을 충족해야 한다.
- TTS 시연 그대로 제출 요건을 충족한다고 보고하지 않는다.
- 홈 UI 자체를 금지하는 근거는 확인하지 못했다. 주행 중 허용되는 조작은 플랫폼 제한에 따라 제어해야 한다.
- 일반 심사 문서의 '미디어 앱 주행 금지' 설명은 문서 내 동영상·게임·브라우저 범위를 확인해 적용한다. 오디오 전체 재생 금지로 확대 해석하지 않는다.
- 콘텐츠 저작권·제출 권한은 실제 사용할 음원·이미지마다 확인한다.

## 검증 순서
1. 브라우저 연결·팟캐스트 루트·자동 재생 없음 검증.
2. 실제 음원 등록 후 시스템/앱 재생 상태 일치, 검색 기반 재생, 핸들/미디어 키 검증.
3. 통화/긴급/내비 안내/Gleo에 대한 포커스 상실·복귀, 태스크 제거, Suspend/Resume 검증.
4. CarUxRestrictions 변화에 따른 검색·설정·퀴즈 제한과 상태 복원 검증.
5. 공모전 원문 및 UI 가이드 대조 후 제출 점검.

## 공식 근거
- [Music/Audio 개발·심사](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/music-audio-app-developement-guide)
- [오디오 포커스](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/audio-focus-development)
- [주행 제한](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide)
- [선행 묵음](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/voice-guidance-preroll-silence-guide)
- [운전자 주의 분산](https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-driver-distraction-elements)
- [Media3 서비스의 플랫폼 MBS 호환](https://developer.android.com/media/media3/session/serve-content)
