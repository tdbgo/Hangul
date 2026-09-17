# 검색 노출과 버전 표기

2026-09-17 검토. 아래 26.3 문구는 beta.7 배포와 함께 적용할 제안이며, 현재 공개된 beta.6의 지원 범위를 바꾸지 않습니다.

## 권장 표기

| 위치 | 제안 |
| --- | --- |
| Modrinth 프로젝트명 | `Hangul 한글` 유지 |
| Modrinth 요약 | `Minecraft 26.3 한글 채팅·한국어 입력 모드. Native Korean IME (Hangul/Hangeul) for chat, books and signs. 26.3: Fabric·Quilt·NeoForge / Forge: 26.2까지.` |
| 본문 첫 제목 | `Hangul 한글 — Minecraft 26.3 한글 채팅 모드` |
| Fabric·Quilt 릴리스 제목 | `Hangul 1.3.0-beta.7 — Minecraft 26.1–26.3 (Fabric / Quilt)` |
| NeoForge 릴리스 제목 | `Hangul 1.3.0-beta.7 — Minecraft 26.1–26.3 (NeoForge)` |
| Forge 릴리스 제목 | `Hangul 1.3.0-beta.7 — Minecraft 26.1–26.2 (Forge)` |
| 자체 소개 페이지의 HTML title | `Hangul — 마인크래프트 26.3 한글 채팅 모드` |

프로젝트명은 유지하고, 버전이 바뀌는 정보는 요약·본문·릴리스 제목에 넣는 방식을 권장합니다. 프로젝트명까지 버전을 넣고 싶다면 `Hangul 한글 · 26.3`도 가능하지만, Forge 파일도 함께 있는 프로젝트이므로 모든 파일이 26.3용이라는 오해를 줄 수 있습니다. 다운로드 파일을 구분하는 제목이 먼저입니다.

자체 소개 페이지는 현재 제목에 26.2를 표시하고 있습니다. beta.7 공개 후 제목, Open Graph·Twitter 제목, 설명과 실제 본문의 지원 표를 함께 갱신해야 합니다. URL은 그대로 유지합니다. 이 저장소에는 해당 사이트의 구현이 없으므로 여기서는 변경안을 관리합니다.

## 기존 본문에서 바꿀 부분

기능 설명과 예시, 스크린샷은 유지합니다. 제목과 설치 안내, 영문 지원 범위만 갱신합니다.

설치 안내:

- Fabric·Quilt: Minecraft 26.1, 26.1.1, 26.1.2, 26.2, **26.3 정식판**
- NeoForge: 위 정식판 지원. 26.3에서는 **NeoForge 26.3.0.3-beta**를 사용합니다.
- Forge: Minecraft 26.1, 26.1.1, 26.1.2, 26.2. **26.3용 Forge 파일은 제공하지 않습니다.**
- 26.3 Snapshot 1–10과 Pre-release 1–2는 Fabric·Quilt에서만 시험 지원합니다.
- 나머지 요구 사항과 로더별 설치 안내를 유지하고, 검증 문서 링크는 실제 공개된 beta.7 태그로 갱신합니다.

영문 지원 범위:

> Minecraft 26.3 is supported on Fabric, Quilt and NeoForge. NeoForge 26.3 requires loader 26.3.0.3-beta. Minecraft 26.1 through 26.2 remain supported on all four loaders, including Forge. Use one loader-specific file; Fabric and Quilt share a JAR. The listed 26.3 snapshots and pre-releases remain experimental on Fabric/Quilt only.

## 확인한 검색 결과와 한계

Modrinth 공개 검색 API에서 `한글`, `한국어`, `Korean`, `Hangeul`, `한글 채팅`을 각각 조회했을 때 Hangul이 결과에 포함되었습니다. 같은 시점의 `26.3` 검색에는 포함되지 않았습니다. 각 조회는 기본 관련도순의 최대 100개 결과를 확인했으며 검색 순위나 향후 노출을 보장하지 않습니다.

텍스트 검색과 버전 필터는 별개입니다. Modrinth는 `versions`와 로더 분류를 검색 조건으로 사용합니다. 제목에 26.3을 써도 배포 버전의 `game_versions`에 26.3이 없으면 정식판 호환 필터를 대신할 수 없습니다. 업로드 시 Fabric·Quilt와 NeoForge 파일에만 26.3을 지정하고, Forge에는 지정하지 않습니다.

Google은 명확하고 간결한 제목을 권장하며 반복적인 키워드 나열을 피하도록 안내합니다. 따라서 한글·한국어·Korean·Hangul·Hangeul을 제목마다 반복하기보다 자연스러운 요약과 실제 기능 설명에 나누어 사용합니다. 검색 결과 제목은 Google이 다시 구성할 수 있으며, 수정 즉시 반영되거나 순위가 오르는 것은 아닙니다.

## 공개 후 확인

1. Modrinth에서 새 파일의 버전·로더·해시를 다시 조회합니다.
2. `26.3` 버전 필터와 Fabric·Quilt·NeoForge 필터에서 새 파일이 제공되는지 확인합니다. Forge 조합에는 26.3 파일이 없어야 합니다.
3. 기존 다섯 검색어와 `26.3 한글`, `Minecraft 26.3 Korean`을 다시 확인합니다. 한 번의 결과를 순위 개선의 증거로 삼지 않습니다.
4. 자체 사이트의 제목·설명·본문을 같은 지원 범위로 맞춥니다. Search Console 접근 권한이 있는 운영자가 재색인을 요청하고 이후 노출·클릭 변화를 확인할 수 있습니다.

## 근거

- [Google: 검색 결과 제목 작성](https://developers.google.com/search/docs/appearance/title-link)
- [Modrinth: 검색 API와 버전·로더 필터](https://docs.modrinth.com/api/operations/searchprojects/)
- [Modrinth: 버전 업로드 메타데이터](https://docs.modrinth.com/api/operations/createversion/)
- [현재 Modrinth 페이지](https://modrinth.com/mod/hangul)
- [현재 자체 소개 페이지](https://block.playcity.kr/mods/hangul)
