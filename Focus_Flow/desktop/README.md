# FOCUS FLOW 데스크톱 앱 (Windows)

작업 표시줄의 **FOCUS FLOW** 아이콘 한 번으로 서버 실행 → 준비 대기 → 앱 창(주소창 없음) 열기까지 처리합니다.
IntelliJ는 필요 없습니다.

## 설치 / 재설치
```
powershell -ExecutionPolicy Bypass -File desktop\install-desktop-app.ps1
```
- `desktop\bin\FocusFlow.exe`를 빌드하고(Windows 내장 C# 컴파일러 사용) 바탕화면·시작 메뉴에 `FOCUS FLOW` 바로가기를 만듭니다.
- 프로젝트 폴더를 옮겼거나 런처 코드를 고친 뒤에도 다시 실행하면 됩니다. (FOCUS FLOW 창을 닫고 실행)
- `-TidyOldShortcuts`: 예전 바탕화면 바로가기(Chrome PWA `Sign in · FOCUS FLOW`, `start-focusflow - Shortcut`)를 `%LOCALAPPDATA%\FocusFlow\old-shortcuts`로 옮깁니다. 삭제하지 않습니다.

작업 표시줄 고정은 Windows 정책상 자동화할 수 없습니다: 바탕화면 `FOCUS FLOW` 우클릭 → **추가 옵션 표시** → **작업 표시줄에 고정**.

## 동작
| 상황 | 동작 |
|---|---|
| 서버가 꺼져 있음 | MySQL(`DB_PORT`) 확인 → 소스가 바뀌었으면 `gradlew bootJar` → 서버 실행 → 응답 대기 → 앱 창 |
| 서버가 이미 실행 중 | 새 서버를 띄우지 않고 창만 엽니다 |
| 동시에 여러 번 클릭 | 뮤텍스로 순서대로 처리, 서버는 하나만 |
| 앱 창을 모두 닫음 | **런처가 띄운 서버만** Ctrl+C로 정상 종료(Spring graceful shutdown). 5초 안에 다시 열면 유지 |
| IntelliJ·`start-focusflow.bat`로 띄운 서버 | 사용만 하고 절대 종료하지 않습니다 |

- 서버는 프로젝트 폴더에서 실행되므로 `application-local.properties`를 그대로 사용합니다. 런처는 그중 `DB_PORT`만 읽습니다.
- 실행 jar는 `%LOCALAPPDATA%\FocusFlow\app\focus-flow.jar` 복사본을 써서 IntelliJ/Gradle 빌드와 파일 잠금 충돌이 없습니다.
- 앱 창은 전용 Chrome 프로필(`%LOCALAPPDATA%\FocusFlow\browser`)의 `--app` 창입니다. Chrome이 없으면 Edge를 씁니다.

## 로그
`%LOCALAPPDATA%\FocusFlow\logs\` — `launcher.log`, `server.log`, `build.log`

## 명령줄
- `FocusFlow.exe --status` 서버 상태
- `FocusFlow.exe --stop` 런처가 띄운 서버 종료

## 아이콘
- 원본: `icon\focus-flow-icon.svg`(마스터), `focus-flow-icon-small.svg`(16–32px용 단순화), `focus-flow-icon-maskable.svg`(PWA)
- 생성물: `icon\png\*.png`(16–1024px), `icon\focus-flow.ico`, `src\main\resources\static\{favicon.ico,favicon.svg,icons\*,manifest.json}`
- 다시 생성: `powershell -ExecutionPolicy Bypass -File desktop\icon\render-icons.ps1`
