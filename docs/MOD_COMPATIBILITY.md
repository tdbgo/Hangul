# 외부 입력창 호환성

2026-11-16 기준 beta.12 후보의 범위입니다. 자동 위젯 검사는 실제 로더가 후보 JAR의 Mixin을 적용한 뒤 입력 메서드를 실행합니다. 별도 Windows 후보창 검사는 아래에서 구분합니다.

## REI

REI는 바닐라 `EditBox` 대신 자체 텍스트 위젯을 사용합니다. 새 어댑터는 이 위젯의 커서·선택 영역에 조합 문자열을 표시하고 확정 전 저장값을 보존합니다. 검색 콜백은 조합 렌더링 중 호출하지 않으며, 확정된 문자만 REI의 기존 입력 경로로 전달합니다. 초성·두벌식 검색 복구와 F6 비상 모드는 이 어댑터의 기능이 아닙니다.

| 자동 검사 프로필 | REI | 함께 고정한 필수 모드 |
| --- | --- | --- |
| Fabric 26.2 | 26.2.820 | Cloth Config 26.2.155, Architectury 21.1.12, Fabric API 0.161.0+26.2 |
| Fabric 26.3 | 26.3.824 | Cloth Config 26.3.159, Architectury 22.0.5, Fabric API 0.166.2+26.3 |
| NeoForge 26.3 / 26.3.0.103 | 26.3.824 | Cloth Config 26.3.159, Architectury 22.0.5 |

위 세 프로필을 beta.12 파일로 다시 검사해 통과했습니다. 검사는 중간 조합 표시, 선택 교체, 취소, 한자 문자 확정, 렌더링 중 예외 후 복원, 길이 제한 거절, 포커스·표시 해제 시 조합 정리를 다룹니다. 500프레임의 표시 문자열 재사용과 긴 질의의 가로 스크롤·복원도 검사합니다. 글꼴 폭은 테스트용 결정론적 값을 사용하므로 실제 렌더러와 후보창 좌표의 확인으로 간주하지 않습니다. 후보 해시와 실행 결과는 [배포 준비 상태](RELEASE_READINESS.md)에 기록합니다.

NeoForge 26.3.0.69-beta에서 Architectury 22.0.3을 함께 사용한 첫 검사는 `MixinBlockTransformer`의 주입 검증에서 실패했습니다. 실패 기록을 보존한 뒤 22.0.5 파일로 고정을 갱신했고 같은 Hangul 후보로 재검사했습니다. 22.0.3 조합을 통과로 표시하거나 외부 JAR의 주입 검사를 무시하지 않습니다. 이 프로필의 다른 모드는 REI 사용에 필요한 파일이며 Hangul 단독 설치의 의존성이 아닙니다.

REI가 없으면 어댑터를 적용하지 않습니다. 지원한 위젯 구조가 달라지면 진단을 남기고 선택적 어댑터만 건너뜁니다. 필수 바닐라 입력 훅의 실패 검사에는 영향을 주지 않습니다. REI, Cloth Config, Architectury, Fabric API는 Hangul의 필수 의존성이 아니며 JAR에 포함하지 않습니다. 선택적 타입 선언에 사용하는 basic-math는 컴파일 전용입니다.

## IMBlocker

IMBlocker 7.3.3과의 동시 설치는 호환으로 승인하지 않습니다. Fabric 26.2·26.3과 NeoForge 26.3에서 `MinecraftClientMixin`의 `onScreenChanged`가 이미 이동한 `Minecraft.setScreen`을 대상으로 주입하려다 실패했습니다. 각 프로필에서 Hangul을 로더 목록에서 뺀 대조군에서도 같은 오류가 재현됐습니다. 세 프로필의 여섯 실패는 로더 단계의 비호환 결과이며 호환 통과 건수에 넣지 않습니다. 7.3.2의 이전 검사 기록도 보존합니다.

Hangul은 IMBlocker가 있으면 자체 REI 어댑터를 적용하지 않도록 했습니다. 이는 두 어댑터의 중복 처리를 방지하는 정책일 뿐, 외부 모드의 Mixin 오류를 수정하거나 동시 설치를 보장하는 기능은 아닙니다. 외부 JAR을 수정하거나 대상 메서드 검사를 강제로 무시하지 않습니다.

동시 설치 검사는 고정한 버전의 예상 결과만 판정합니다. 버전 ID·릴리스 이름·SHA-512를 함께 확인하고, 다른 원인의 실패나 예상 밖 성공은 별도 검토 대상으로 처리합니다. `KNOWN_UPSTREAM_FAILURE`는 지원 통과가 아니라 비호환 오류의 재현을 뜻합니다. 새 버전이 나오면 파일 고정과 검사를 함께 갱신합니다. 실행별 로그는 서로 덮어쓰지 않습니다.

## Windows NeoForge 한자 후보창

NeoForge `26.3.0.103`과 FML `12.0.12`의 기본 초기 로딩 창에서 한자 후보창이 보이지 않는 현상을 사용자가 확인했습니다. 같은 Hangul 파일로 별도 프로필의 `config/fml.toml`에서 `earlyWindowControl = false`만 설정하고 재시작하자 후보창 표시·선택·Esc 취소가 정상이라는 확인을 받았습니다. 실제 Windows 입력 결과는 사용자 보고이며 자동 입력 검사와 구분합니다.

후보창이 보이지 않으면 게임을 종료한 뒤 **사용 중인 인스턴스의** `config/fml.toml`에서 위 설정을 바꾸고 다시 실행할 수 있습니다. 초기 로딩 화면만 끄는 우회 설정이며 Hangul이 다른 모드의 설정을 자동 변경하지 않습니다.

FML 소스는 초기 창에서 SDL에 조합 표시와 후보 목록을 앱이 직접 그린다고 선언합니다. 현재 Hangul은 운영체제 후보창에 커서 좌표를 전달할 뿐 후보 목록을 자체 렌더링하지 않습니다. 위 대조 결과는 [NeoForge #3447](https://github.com/neoforged/NeoForge/issues/3447#issuecomment-6722928418)의 초기 창·후보 UI 분석과 일치하지만, 모든 입력기·언어·로더에서 같은 원인이라고 단정하지 않습니다. 외부 JAR 수정이나 Win32 창 프로시저 교체는 하지 않습니다.

`python scripts/inspect-neoforge-ime.py`는 선언한 로더의 공식 POM에서 실제 FML 버전을 찾고 체크섬이 일치하는 소스를 검사합니다. 정책 보고서는 네이티브 후보창의 표시 성공으로 계산하지 않습니다.

## EMI

[EMI 공식 배포 목록](https://modrinth.com/mod/emi/versions)에는 현재 Hangul의 지원 범위인 26.x용 파일이 없습니다. 구버전 JAR의 지원 선언을 바꾸거나 억지로 로드하지 않았고, EMI 호환을 선언하지 않습니다. 공식 26.x 파일이 나오면 실제 위젯과 로더를 기준으로 별도 어댑터를 검토합니다.

## 검사 실행

Java 25와 Python 3.11 이상을 사용합니다. 먼저 배포 후보를 빌드한 뒤 실행합니다.

```powershell
python scripts/verify-mod-compatibility.py fabric-26.2 --candidate build/compatibility/hangul.jar --check-imblocker
python scripts/verify-mod-compatibility.py fabric-26.3 --candidate build/compatibility/hangul.jar --check-imblocker
python scripts/verify-mod-compatibility.py neoforge-26.3 --candidate build/compatibility/hangul-neoforge.jar --check-imblocker
```

`mod-compatibility.json`은 공식 Modrinth 버전 ID와 SHA-512를 고정합니다. 검사에만 쓰는 파일은 `.toolchains/compatibility/`에 저장되고 배포하지 않습니다. 준비 단계는 파일 해시, 대상 게임·로더, 필수 의존성 목록을 확인하며, 매 실행마다 최신 파일로 임의 교체하지 않습니다. 고정 목록이 바뀌면 별도 해시 이름의 폴더를 만들어 이전 검사 파일을 보존합니다. 실행 로그는 `.toolchains/checks/`에 남습니다.

## 배포 전 실제 확인

REI 지원을 실사용 검증 완료로 표시하려면 별도 26.2·26.3 프로필에서 한글 조합, 한자 후보 선택·취소, 선택 영역 교체, 검색 확정, 긴 질의의 가로 스크롤, GUI 배율·전체화면 전환을 확인해야 합니다. 네이티브 창과 운영체제 입력 주입을 사용하지 않는 자동 검사는 이 항목을 대신하지 않으며, 이전 바닐라 검사 결과로 새 REI 입력창을 통과 처리하지 않습니다.
