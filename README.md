# Hangul 한글

![Hangul icon](src/main/resources/assets/hangul/icon.png)

Minecraft Java Edition 26.1–26.2와 26.3 Snapshot 1–8용 경량 Fabric 한글 입력 패치입니다. 입력 모드를 별도로 관리하게 만들기보다, 평소 Windows 입력 방식이 게임 안에서도 자연스럽게 이어지도록 설계했습니다.

Lightweight native Korean (Hangul) IME input and Korean-aware search for Minecraft chat, books, signs, commands, items, recipes, and Hanja conversion.

공식 프로젝트명과 모드 ID는 `Hangul`과 `hangul`입니다. `Hangeul`과 `hangeul`은 검색 및 이전 식별자 호환을 위한 별칭으로 지원합니다.

[Modrinth에서 다운로드](https://modrinth.com/mod/hangul) · [소스 코드](https://github.com/tdbgo/Hangul) · [문제 신고](https://github.com/tdbgo/Hangul/issues)

## 기본 사용법

모드를 설치한 뒤에는 별도의 전환키를 누를 필요가 없습니다.

1. Windows에서 평소처럼 한/영키를 사용합니다.
2. 채팅, 책, 표지판, 명령어 블록 등 원하는 입력창에 입력합니다.
3. 조합 중인 글자가 커서 위치에 즉시 표시되고, 확정되면 정상 입력값으로 들어갑니다.

`F6`은 OS IME가 고장 난 환경에서 단일행 입력창에만 쓰는 비상용 **강제 한글 모드**입니다. 다시 `F6`을 누르면 기본 **자동 입력 [IME]** 모드로 돌아갑니다. 오른쪽 Alt/한영키는 모드가 가로채지 않습니다.

## 입력 구조

- 기본값은 OS IME 자동 추종
- Minecraft 26.1+의 IME preedit 이벤트를 입력줄 안에 직접 합성 렌더링
- 단일행 조합 문자열에는 밑줄과 실제 IME 커서 위치 표시
- 조합 중에는 실제 `EditBox` 값과 서버 명령어 추천 패킷을 변경하지 않음
- 문자 확정 시에만 바닐라 입력 경로로 값 반영
- 커서 중간 입력과 선택 영역 교체를 시각적으로 지원
- 책과 같은 자동 줄바꿈·다중행 편집기에서 인라인 조합 지원
- 표지판의 각 줄과 3D 변환된 커서 위치에서 인라인 조합 지원
- 채팅·명령어·명령어 블록·서버 주소 등 바닐라 단일행 입력 지원
- 운영체제 한자 후보창을 현재 게임 커서 위치에 표시
- 창작 인벤토리와 조합법 책에서 초성 검색 지원: `ㄷㅇㅇㅁㄷ` → `다이아몬드`
- 영문 상태로 잘못 입력한 두벌식 검색 복구: `rmarhl` → `금괴`
- 공백으로 떨어진 여러 검색어가 모두 포함된 결과를 찾는 다중 토큰 폴백
- 한글 IME 조합 중인 마지막 글자까지 검색 결과에 즉시 반영
- 한글 상태로 영문 명령어의 물리 키를 누른 경우와 `디버그` 같은 음차 명령의 Tab 추천 복구
- F6 강제 모드에서는 독립 두벌식 조합기 사용
- Fabric API, Mod Menu, 네이티브 라이브러리 불필요
- Minecraft 26.1–26.2 및 26.3 Snapshot 1–8 / Fabric Loader 0.19.3 이상 / Java 25 이상

## Hangul의 강점

- **조용한 네이티브 경험:** 정상 동작할 때 상태창이나 별도 전환키를 의식할 필요가 없습니다.
- **입력값 안전성:** 미완성 조합 문자열은 화면에만 보이고 저장값·명령어·서버 패킷에는 확정 후 한 번만 반영됩니다.
- **입력창 전체성:** 채팅뿐 아니라 책, 표지판, 명령어 블록과 한자 후보 위치까지 같은 원칙으로 처리합니다.
- **한국어 탐색:** 초성, 두벌식 자판 실수, 다중 단어, 음차 명령어를 바닐라 검색 구조 위에서 지원합니다.
- **사용자 서버 친화성:** 고정 명령어 번역표가 아니라 서버가 공개한 리터럴을 분석하므로 새 영문 명령에도 적용됩니다.
- **작은 실행 표면:** Fabric API, Mod Menu, 네이티브 DLL과 상시 전체 목록 스캔이 없습니다.

검색어: Minecraft 한글 입력, 한글 채팅, 초성 검색, 한글 검색, Korean input, Hangul IME, Hangeul, Korean IME

## 한글 검색 사용법

창작 인벤토리 검색창과 조합법 책에서 다음 입력을 그대로 사용할 수 있습니다.

- `ㄷㅇㅇㅁㄷ`: 이름에 `다이아몬드`처럼 해당 초성이 들어간 항목
- `rmarhl`: 두벌식으로 변환한 `금괴` 검색
- `주괴 철`: 단어 순서와 관계없이 `철 주괴`처럼 두 단어가 모두 들어간 항목

초성은 언어가 바뀌거나 검색 트리가 다시 만들어질 때 한 번 색인됩니다. 매 프레임 전체 아이템을 순회하지 않으며, IME 조합 중 검색도 실제 입력값이나 네트워크 패킷을 변경하지 않습니다.

명령어 입력에서 한글 상태인 줄 모르고 영문 명령어의 물리 키를 누른 경우, `/` 뒤의 한글을 영문 키로 역변환해 현재 서버가 허용한 리터럴 명령을 Tab 후보로 보여 줍니다. `디버그` → `debug`, `메시지` → `message`, `게임모드` → `gamemode`처럼 한글로 음차한 명령도 자음 발음 키가 맞으면 찾습니다. 고정 번역 사전이 아니므로 사용자 서버 명령에도 자동 적용되지만, `날씨` → `weather`처럼 뜻이 다른 단어를 번역하는 기능은 아닙니다.

## 설치

1. 사용 중인 Minecraft 버전에 맞는 Fabric Loader 0.19.3 이상을 설치합니다.
2. [Modrinth](https://modrinth.com/mod/hangul)에서 최신 JAR을 내려받아 Minecraft의 `mods` 폴더에 넣습니다.
3. 게임을 실행하면 자동 IME 모드로 시작합니다.

## 빌드

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
```

빌드는 Java 25를 선택한 뒤 클린 빌드, 리맵, 두벌식 조합, 초성·자판 변환, 명령어 복구 및 인라인 preedit 회귀 테스트를 수행합니다.

구조와 성능 경계는 [아키텍처 문서](docs/ARCHITECTURE.md), 버전별 검증 범위는 [호환성 문서](docs/COMPATIBILITY.md), 기여 코드·자산의 출처 기준은 [기여 안내](CONTRIBUTING.md), 포함된 이미지의 출처는 [자산 출처 문서](docs/ASSET_PROVENANCE.md), 보안 신고는 [보안 정책](SECURITY.md), 공개 전 검사는 [릴리스 체크리스트](docs/RELEASE_CHECKLIST.md)에 정리되어 있습니다.

## 현재 범위

바닐라의 단일행 `EditBox`, 다중행 `MultiLineEditBox`, 표지판 전용 `TextFieldHelper` 경로를 지원합니다. 따라서 채팅, 명령어, 명령어 블록, 서버 주소, 책과 깃펜, 표지판에서 동작합니다. 자체 텍스트 위젯을 구현한 외부 모드는 별도 어댑터가 필요할 수 있습니다.

## 한자 변환

별도 한자 사전을 내장하지 않고 Windows IME의 변환 기능을 그대로 사용합니다. 한글을 조합한 뒤 사용 중인 IME의 한자 변환 키(일반적으로 한자 키 또는 오른쪽 Ctrl)를 누르면 후보창이 현재 게임 커서 가까이에 표시됩니다. 실제 키는 Windows 입력기 설정에 따라 다를 수 있습니다.

## 라이선스

프로젝트 소스 코드는 [MIT License](LICENSE)로 배포됩니다. Gradle Wrapper처럼 자체 라이선스 고지를 포함한 제3자 빌드 도구는 각자의 라이선스를 유지합니다.

Hangul은 독립적인 비공식 프로젝트입니다. 공식 Minecraft 제품이 아니며 Mojang 또는 Microsoft의 승인·연계·지원을 받지 않습니다.
