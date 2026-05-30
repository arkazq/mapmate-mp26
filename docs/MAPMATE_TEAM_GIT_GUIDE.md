# MapMate 팀 Git / 개발 가이드라인

## 0. 문서 목적

이 문서는 MapMate 팀 프로젝트에서 Git을 어떻게 사용할지, 브랜치를 어떻게 나눌지, Pull Request를 어떤 기준으로 보낼지 정리한 팀 공통 가이드라인입니다.

팀원은 개발을 시작하기 전에 이 문서를 읽고, 아래 규칙을 기준으로 작업합니다.

---

## 1. 저장소 정보

```text
Repository: https://github.com/arkazq/mapmate-mp26.git
```

현재 저장소는 다음 브랜치 구조를 사용합니다.

```text
main
└── develop
    ├── feature/home
    ├── feature/routine
    ├── feature/api
    ├── feature/db
    ├── feature/notification
    └── feature/history
```

| 브랜치 | 역할 |
|---|---|
| `main` | 최종 안정 버전, 발표/제출 가능한 코드만 반영 |
| `develop` | 개발 중인 기능을 통합하는 브랜치 |
| `feature/*` | 개인별 기능 개발 브랜치 |
| `fix/*` | 버그 수정 브랜치 |
| `docs/*` | 문서 수정 브랜치 |
| `chore/*` | 설정, 환경 구성, 기타 작업 브랜치 |

---

## 2. 핵심 운영 원칙

아래 규칙을 기본으로 합니다.

```text
main에 직접 push하지 않는다.
develop에 직접 push하지 않는다.
기능 작업은 feature 브랜치에서 진행한다.
작업이 끝나면 Pull Request를 develop으로 보낸다.
발표/제출 가능한 상태가 되면 develop을 main으로 merge한다.
```

정리하면 다음 흐름입니다.

```text
feature/* → develop → main
```

- `main`은 제출 가능한 최종본만 유지합니다.
- `develop`은 개발된 기능을 모아 테스트하는 브랜치입니다.
- 실제 작업은 `feature/*`, `fix/*`, `docs/*` 같은 작업 브랜치에서 합니다.

---

## 3. 팀원이 처음 해야 할 일

### 3.1 Git 설치 확인

CMD, PowerShell, Git Bash, Android Studio Terminal 중 편한 터미널에서 실행합니다.

```cmd
git --version
```

버전이 출력되면 정상입니다.

---

### 3.2 Git 사용자 정보 설정

각자 본인의 GitHub 계정 기준으로 설정합니다.

```cmd
git config --global user.name "본인_GitHub_ID"
git config --global user.email "본인_GitHub_이메일"
```

예시:

```cmd
git config --global user.name "arkazq"
git config --global user.email "example@gmail.com"
```

설정 확인:

```cmd
git config --global --list
```

참고:

- `user.name`은 GitHub 아이디일 필요는 없지만, 팀에서는 GitHub ID로 통일하는 것을 권장합니다.
- `user.email`은 GitHub 계정에 등록된 이메일을 사용하는 것이 좋습니다.
- `--global`은 현재 PC의 내 사용자 계정 전체 Git 프로젝트에 적용하는 설정입니다.

---

### 3.3 저장소 clone

팀원은 새로 받을 때 `git init`을 하지 않고 `git clone`으로 시작합니다.

```cmd
cd C:\dev
git clone https://github.com/arkazq/mapmate-mp26.git
cd mapmate-mp26
```

다른 위치에 받고 싶으면 `C:\dev` 대신 본인이 원하는 폴더로 이동한 뒤 clone하면 됩니다.

---

### 3.4 develop 브랜치로 이동

```cmd
git switch develop
```

만약 `develop` 브랜치가 바로 잡히지 않으면 아래처럼 실행합니다.

```cmd
git fetch origin
git switch develop
```

현재 브랜치 확인:

```cmd
git branch
```

아래처럼 `develop` 앞에 `*`가 있으면 정상입니다.

```text
* develop
  main
```

---

## 4. 기능 개발 시작 방법

새 기능을 만들 때는 항상 `develop`에서 최신 코드를 받은 뒤, 기능 브랜치를 만듭니다.

예를 들어 루틴 등록 화면을 작업한다면:

```cmd
git switch develop
git pull origin develop
git switch -c feature/routine
```

의미:

```text
1. develop 브랜치로 이동
2. GitHub의 최신 develop 내용을 로컬 develop에 반영
3. 최신 develop 기준으로 feature/routine 브랜치 생성
```

`git pull origin develop`을 실행하는 이유는 다른 팀원이 이미 `develop`에 merge한 최신 작업을 내 로컬에도 반영하기 위해서입니다. 최신 상태에서 작업 브랜치를 만들어야 나중에 충돌 가능성이 줄어듭니다.

---

## 5. 브랜치 이름 규칙

브랜치 이름은 영어 소문자와 하이픈(`-`)을 사용합니다.

### 좋은 예시

```text
feature/home
feature/routine
feature/route-api
feature/db
feature/notification
feature/history
feature/settings

fix/departure-time-calculation
fix/room-save-error

docs/update-readme
docs/add-test-scenario
docs/add-git-guide

chore/project-setup
chore/add-gitignore
```

### 피해야 할 예시

```text
mybranch
test
new
final
성현작업
기능추가
```

브랜치 이름만 봐도 어떤 작업인지 알 수 있어야 합니다.

---

## 6. 작업 후 커밋 방법

파일을 수정한 뒤 상태를 확인합니다.

```cmd
git status
```

변경 파일을 스테이징합니다.

```cmd
git add .
```

커밋합니다.

```cmd
git commit -m "feat: add routine registration screen"
```

---

## 7. 커밋 메시지 규칙

커밋 메시지는 아래 형식을 사용합니다.

```text
type: message
```

예시:

```text
feat: add routine registration screen
fix: correct departure time calculation
docs: add codex development standards
refactor: separate routine repository
chore: update gitignore
test: add departure calculator test
```

| type | 의미 |
|---|---|
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `docs` | 문서 수정 |
| `style` | 코드 의미 변화 없는 스타일 수정 |
| `refactor` | 코드 구조 개선 |
| `test` | 테스트 코드 추가/수정 |
| `chore` | 설정, 빌드, 기타 작업 |

커밋 메시지는 가능하면 영어로 작성합니다.

---

## 8. 원격 저장소에 브랜치 올리기

작업 브랜치를 처음 push할 때는 아래처럼 실행합니다.

```cmd
git push -u origin feature/routine
```

이후 같은 브랜치에서 추가 push할 때는 아래처럼 실행해도 됩니다.

```cmd
git push
```

---

## 9. Pull Request 규칙

기능 작업이 끝나면 GitHub에서 Pull Request를 만듭니다.

PR 방향은 반드시 아래처럼 설정합니다.

```text
base: develop
compare: feature/작업브랜치
```

예시:

```text
base: develop
compare: feature/routine
```

잘못된 예시:

```text
base: main
compare: feature/routine
```

기능 브랜치를 바로 `main`에 merge하지 않습니다.

---

## 10. PR 작성 양식

Pull Request에는 최소한 아래 내용을 작성합니다.

```markdown
## 작업 내용
- 루틴 등록 화면 추가
- 도착 시간 선택 UI 추가
- 반복 요일 선택 UI 추가

## 테스트 방법
- 앱 실행
- 루틴 등록 화면 진입
- 도착 시간과 요일 선택
- 저장 버튼 클릭 확인

## 스크린샷
- UI 변경이 있으면 스크린샷 첨부

## 참고 사항
- 아직 실제 DB 저장은 연결하지 않음
- ViewModel 연동은 다음 PR에서 진행
```

기준:

- UI 변경이 있으면 스크린샷을 첨부합니다.
- 기능 변경이 있으면 테스트 방법을 적습니다.
- 미완성 부분이나 다음 PR에서 처리할 부분은 참고 사항에 적습니다.

---

## 11. 코드 리뷰 규칙

PR을 올린 사람은 본인이 작성한 코드를 설명할 수 있어야 합니다.

리뷰할 때는 아래 항목을 확인합니다.

```text
- base 브랜치가 develop인가?
- main에 직접 merge하려는 PR이 아닌가?
- 작업 범위가 너무 크지 않은가?
- 관련 없는 파일이 수정되지 않았는가?
- API key, local.properties 같은 민감 파일이 포함되지 않았는가?
- 앱이 빌드되는가?
- UI 변경 시 화면이 깨지지 않는가?
- Codex가 생성한 코드라면 작성자가 이해하고 있는가?
```

최소 1명 이상 확인한 뒤 merge합니다.

---

## 12. PR merge 후 해야 할 일

본인의 PR이 `develop`에 merge되면, 로컬 `develop`을 최신화합니다.

```cmd
git switch develop
git pull origin develop
```

기존 feature 브랜치가 더 이상 필요 없으면 삭제할 수 있습니다.

로컬 브랜치 삭제:

```cmd
git branch -d feature/routine
```

원격 브랜치 삭제:

```cmd
git push origin --delete feature/routine
```

단, 브랜치 삭제는 PR merge가 완료된 뒤에만 합니다.

---

## 13. 충돌 방지 방법

작업을 시작하기 전에는 항상 아래 명령어를 실행합니다.

```cmd
git switch develop
git pull origin develop
```

이미 작업 중인 feature 브랜치가 있고, 최신 develop 내용을 반영하고 싶다면:

```cmd
git switch develop
git pull origin develop
git switch feature/routine
git merge develop
```

충돌이 발생하면 충돌 파일을 직접 수정한 뒤:

```cmd
git add .
git commit -m "fix: resolve merge conflict"
```

을 실행합니다.

---

## 14. Git에 올리면 안 되는 파일

아래 파일은 Git에 올리지 않습니다.

```text
local.properties
.env
secrets.properties
*.jks
*.keystore
*.pem
*.key
.gradle/
build/
*/build/
.idea/
*.iml
*.log
.DS_Store
Thumbs.db
```

특히 API Key, 개인 SDK 경로, keystore 파일은 절대 커밋하지 않습니다.

---

## 15. Codex 사용 규칙

Codex를 사용할 때는 프로젝트 기준 문서를 따릅니다.

```text
MAPMATE_CODEX_DEVELOPMENT_STANDARDS.md
```

Codex에게 작업을 요청할 때는 아래처럼 명확히 지시합니다.

```text
Read and follow MAPMATE_CODEX_DEVELOPMENT_STANDARDS.md.

We are working on the MapMate Android project.
Use Kotlin, Jetpack Compose, ViewModel, and StateFlow.
Implement only the routine registration screen.
Do not modify unrelated files.
Do not access Room directly from Composable.
Place UI files under presentation/routine.
```

Codex 사용 시 주의사항:

```text
- Codex가 만든 코드를 그대로 merge하지 않는다.
- 작성자가 코드를 읽고 이해한 뒤 PR을 올린다.
- 추천 로직, 알림, DB migration, API key 처리는 특히 검토한다.
- 관련 없는 파일이 수정되면 되돌린다.
- 한 PR에 여러 기능을 섞지 않는다.
```

---

## 16. MapMate 개발 기준 요약

MapMate의 핵심 흐름은 다음과 같습니다.

```text
루틴 등록
→ 권장 출발 시각 계산
→ 출발 알림
→ 이동 기록 저장
→ 다음 추천에 개인 보정 반영
```

기술 스택은 아래 기준을 따릅니다.

```text
Kotlin
Jetpack Compose
Navigation Compose
ViewModel + StateFlow
Room
DataStore
Retrofit2
Coroutines + Flow
AlarmManager
WorkManager
```

화면 코드는 UI만 담당하고, DB/API/추천 계산 로직은 ViewModel, Repository, UseCase, Calculator 등으로 분리합니다.

---

## 17. 작업 예시

### 17.1 홈 화면 작업

```cmd
git switch develop
git pull origin develop
git switch -c feature/home
```

작업 후:

```cmd
git add .
git commit -m "feat: add home screen"
git push -u origin feature/home
```

GitHub에서 PR 생성:

```text
base: develop
compare: feature/home
```

---

### 17.2 루틴 등록 작업

```cmd
git switch develop
git pull origin develop
git switch -c feature/routine
```

작업 후:

```cmd
git add .
git commit -m "feat: add routine registration screen"
git push -u origin feature/routine
```

GitHub에서 PR 생성:

```text
base: develop
compare: feature/routine
```

---

### 17.3 문서 수정 작업

```cmd
git switch develop
git pull origin develop
git switch -c docs/update-team-guide
```

작업 후:

```cmd
git add .
git commit -m "docs: update team git guide"
git push -u origin docs/update-team-guide
```

GitHub에서 PR 생성:

```text
base: develop
compare: docs/update-team-guide
```

---

## 18. 최종 제출 전 흐름

개발이 어느 정도 완료되면 모든 기능을 `develop`에 합칩니다.

```text
feature/* → develop
```

그다음 `develop`에서 앱 실행, 테스트, 발표 시연을 확인합니다.

문제가 없으면 마지막으로 아래 방향의 PR을 만듭니다.

```text
base: main
compare: develop
```

즉:

```text
develop → main
```

최종 안정 버전을 `main`에 반영합니다.

`main`은 발표/제출 가능한 상태만 유지합니다.

---

## 19. 자주 쓰는 명령어 요약

### 현재 상태 확인

```cmd
git status
```

### 현재 브랜치 확인

```cmd
git branch
```

### develop으로 이동

```cmd
git switch develop
```

### 최신 develop 가져오기

```cmd
git pull origin develop
```

### 새 feature 브랜치 생성

```cmd
git switch -c feature/기능명
```

### 변경사항 커밋

```cmd
git add .
git commit -m "type: message"
```

### 브랜치 push

```cmd
git push -u origin feature/기능명
```

### main으로 직접 작업하지 않기

```text
main에서 코드 수정 금지
main으로 직접 push 금지
```

---

## 20. 핵심 규칙 한 줄 요약

```text
main은 최종본, develop은 통합 개발, 실제 작업은 feature 브랜치에서 하고 PR은 develop으로 보낸다.
```
