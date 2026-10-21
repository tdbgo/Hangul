# 외부 입력창 호환성

2026-10-22 기준 beta.11 후보의 범위입니다. 여기서 자동 검사는 실제 로더가 배포 JAR의 Mixin을 적용한 뒤 입력 위젯 메서드를 실행하는 검사입니다. 네이티브 창, 게임 월드, Windows 입력기는 열지 않습니다.

## REI

REI는 바닐라 `EditBox` 대신 자체 텍스트 위젯을 사용합니다. 새 어댑터는 이 위젯의 커서·선택 영역에 조합 문자열을 표시하고 확정 전 저장값을 보존합니다. 검색 콜백은 조합 렌더링 중 호출하지 않으며, 확정된 문자만 REI의 기존 입력 경로로 전달합니다. 초성·두벌식 검색 복구와 F6 비상 모드는 이 어댑터의 기능이 아닙니다.

| 자동 검사 프로필 | REI | 함께 고정한 필수 모드 |
| --- | --- | --- |
| Fabric 26.2 | 26.2.820 | Cloth Config 26.2.155, Architectury 21.1.11, Fabric API 0.161.0+26.2 |
| Fabric 26.3 | 26.3.824 | Cloth Config 26.3.159, Architectury 22.0.5, Fabric API 0.161.0+26.3 |
| NeoForge 26.3 / 26.3.0.95 | 26.3.824 | Cloth Config 26.3.159, Architectury 22.0.5 |

위 세 프로필을 beta.11 파일로 다시 검사해 통과했습니다. 검사는 중간 조합 표시, 선택 교체, 취소, 한자 문자 확정, 렌더링 중 예외 후 복원, 길이 제한 거절, 포커스·표시 해제 시 조합 정리를 다룹니다. 500프레임의 표시 문자열 재사용과 긴 질의의 가로 스크롤·복원도 검사합니다. 글꼴 폭은 테스트용 결정론적 값을 사용하므로 실제 렌더러와 후보창 좌표의 확인으로 간주하지 않습니다. 후보 해시와 실행 결과는 [배포 준비 상태](RELEASE_READINESS.md)에 기록합니다.

NeoForge 26.3.0.69-beta에서 Architectury 22.0.3을 함께 사용한 첫 검사는 `MixinBlockTransformer`의 주입 검증에서 실패했습니다. 실패 기록을 보존한 뒤 22.0.5 파일로 고정을 갱신했고 같은 Hangul 후보로 재검사했습니다. 22.0.3 조합을 통과로 표시하거나 외부 JAR의 주입 검사를 무시하지 않습니다. 이 프로필의 다른 모드는 REI 사용에 필요한 파일이며 Hangul 단독 설치의 의존성이 아닙니다.

REI가 없으면 어댑터를 적용하지 않습니다. 지원한 위젯 구조가 달라지면 진단을 남기고 선택적 어댑터만 건너뜁니다. 필수 바닐라 입력 훅의 실패 검사에는 영향을 주지 않습니다. REI, Cloth Config, Architectury, Fabric API는 Hangul의 필수 의존성이 아니며 JAR에 포함하지 않습니다. 선택적 타입 선언에 사용하는 basic-math는 컴파일 전용입니다.

## IMBlocker

IMBlocker 7.3.2와의 동시 설치는 호환으로 승인하지 않습니다. Fabric 26.2·26.3과 NeoForge 26.3에서 `MinecraftClientMixin`의 `onScreenChanged`가 이미 이동한 `Minecraft.setScreen`을 대상으로 주입하려다 실패했습니다. 각 프로필에서 Hangul을 로더 목록에서 뺀 대조군에서도 같은 오류가 재현되었습니다. 입력 표시를 비교하기 전 로더 단계에서 실패한 결과이며, Hangul을 빼면 해결된다고 안내하지 않습니다.

Hangul은 IMBlocker가 있으면 자체 REI 어댑터를 적용하지 않도록 했습니다. 이는 두 어댑터의 중복 처리를 방지하는 정책일 뿐, 외부 모드의 Mixin 오류를 수정하거나 동시 설치를 보장하는 기능은 아닙니다. 외부 JAR을 수정하거나 대상 메서드 검사를 강제로 무시하지 않습니다.

동시 설치 검사는 고정한 7.3.2 파일의 알려진 오류만 판정합니다. 다른 원인의 실패, 파일 변경, 예상 밖 성공은 별도 검토가 필요하도록 실패 처리합니다. `KNOWN_UPSTREAM_FAILURE`는 지원 통과가 아니라 비호환 오류의 재현을 뜻합니다. 새 IMBlocker 버전에는 파일·체크섬 갱신과 재검사가 필요합니다.

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
