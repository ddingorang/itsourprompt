# E2E Test

Playwright 기반의 E2E 테스트 코드입니다.

로그인과 회원가입 화면을 대상으로 자동화 테스트를 실행하며, 테스트 결과를 HTML 및 JUnit 리포트로 확인하거나 TestRail에 업로드할 수 있습니다.

## 프로젝트 구성

* `tests/`: Playwright E2E 테스트 코드
* `scripts/`: 테스트 실행 및 TestRail 업로드를 처리하는 보조 스크립트
* `playwright.config.ts`: Playwright 실행 환경, 브라우저 및 리포터 설정
* `trcli-config.example.yml`: TestRail CLI 접속 설정 예시
* `test-results/`: JUnit XML 및 테스트 실행 결과 생성 위치
* `playwright-report/`: Playwright HTML 리포트 생성 위치

## 기술 스택

* Node.js
* npm
* TypeScript
* Playwright Test
* TestRail CLI (`trcli`)
* JUnit XML

## 디렉터리 구조

```text
.
├── scripts/
│   └── test-testrail.cjs
├── tests/
│   └── auth/
│       ├── login.spec.ts
│       └── signup.spec.ts
├── .gitignore
├── package-lock.json
├── package.json
├── playwright.config.ts
├── README.md
└── trcli-config.example.yml
```

테스트 실행 후 다음 폴더와 파일이 생성될 수 있습니다.

```text
.
├── playwright-report/
│   └── index.html
└── test-results/
    └── junit-report.xml
```

## 설치

프로젝트 디렉터리에서 의존성을 설치합니다.

```bash
npm install
```

Playwright 브라우저가 설치되어 있지 않은 경우 다음 명령어를 실행합니다.

```bash
npx playwright install
```

TestRail 업로드 기능을 사용하려면 Python과 TestRail CLI가 필요합니다.

```bash
python -m pip install trcli
```

설치 여부는 다음 명령어로 확인할 수 있습니다.

```bash
trcli --version
```

## 테스트 실행

Chromium 환경에서 전체 E2E 테스트를 실행합니다.

```bash
npm run test:e2e
```

현재 `test:e2e` 스크립트는 다음 Playwright 명령어를 실행합니다.

```bash
playwright test --project=chromium
```

모든 브라우저 프로젝트에서 테스트하려면 Playwright 명령어를 직접 실행합니다.

```bash
npx playwright test
```

특정 테스트 파일만 실행할 수도 있습니다.

```bash
npx playwright test tests/auth/login.spec.ts
```

Playwright UI 모드로 테스트를 실행하려면 다음 명령어를 사용합니다.

```bash
npx playwright test --ui
```

브라우저 화면을 표시하면서 테스트하려면 다음 명령어를 사용합니다.

```bash
npx playwright test --headed
```

## 테스트 대상 서버 설정

테스트 대상 서버 주소는 `BASE_URL` 환경 변수로 변경할 수 있습니다.

`BASE_URL`을 설정하지 않은 경우 다음 주소를 기본값으로 사용합니다.

```text
http://localhost:5173
```

Linux 또는 macOS 환경에서는 다음과 같이 실행합니다.

```bash
BASE_URL=http://localhost:5173 npm run test:e2e
```

Windows PowerShell에서는 다음과 같이 실행합니다.

```powershell
$env:BASE_URL="http://localhost:5173"
npm run test:e2e
```

테스트 실행 전 대상 애플리케이션이 해당 주소에서 정상적으로 실행 중이어야 합니다.

## 테스트 결과 확인

테스트 실행 중에는 콘솔에서 `list` 리포터 결과를 확인할 수 있습니다.

### HTML 리포트

테스트 실행 후 생성된 HTML 리포트는 다음 명령어로 확인합니다.

```bash
npx playwright show-report playwright-report
```

HTML 리포트 파일은 다음 경로에 생성됩니다.

```text
playwright-report/index.html
```

### JUnit 리포트

JUnit 테스트 결과는 다음 경로에 생성됩니다.

```text
test-results/junit-report.xml
```

JUnit XML 파일은 TestRail 결과 업로드에 사용됩니다.

### 실패 결과 분석

실패한 테스트는 Playwright 설정에 따라 trace, screenshot 및 video가 저장됩니다.

* `trace`: 테스트 실패 시 보관
* `screenshot`: 테스트 실패 시 저장
* `video`: 테스트 실패 시 보관

실패 원인을 분석할 때는 HTML 리포트와 `test-results/` 디렉터리에 생성된 결과물을 함께 확인합니다.

## TestRail 설정

TestRail 연동은 Playwright에서 생성한 JUnit XML 리포트와 `trcli`를 사용합니다.

먼저 예시 설정 파일을 복사하여 실제 설정 파일을 생성합니다.

### Linux 또는 macOS

```bash
cp trcli-config.example.yml trcli-config.yml
```

### Windows PowerShell

```powershell
Copy-Item trcli-config.example.yml trcli-config.yml
```

생성한 `trcli-config.yml`에 실제 TestRail 접속 정보를 입력합니다.

```yaml
host: https://your-domain.testrail.io
project: YOUR_PROJECT_NAME
username: your-email@example.com
verify: true
```

각 항목의 의미는 다음과 같습니다.

* `host`: TestRail 접속 주소
* `project`: TestRail 프로젝트명
* `username`: TestRail 사용자 계정
* `verify`: SSL 인증서 검증 여부

`trcli-config.yml`에는 실제 TestRail 접속 정보가 포함될 수 있으므로 Git에 커밋하지 않습니다.

## TestRail 케이스 매칭

각 Playwright 테스트에는 TestRail 테스트 케이스 ID가 annotation으로 지정되어 있습니다.

```ts
annotation: {
  type: 'test_id',
  description: 'C95',
}
```

`playwright.config.ts`에서 다음 설정을 사용하여 annotation 정보를 JUnit XML의 property로 포함합니다.

```ts
embedAnnotationsAsProperties: true
```

TestRail CLI는 JUnit XML에 포함된 `test_id` property를 기준으로 Playwright 테스트와 TestRail 테스트 케이스를 매칭합니다.

## TestRail 결과 업로드

테스트 실행 후 생성된 JUnit XML을 TestRail에 업로드합니다.

```bash
npm run testrail:upload
```

기본적인 TestRail CLI 명령어 형식은 다음과 같습니다.

```bash
trcli -y \
  -c trcli-config.yml \
  parse_junit \
  -f ./test-results/junit-report.xml \
  --run-id <TESTRAIL_RUN_ID> \
  --case-matcher property
```

`<TESTRAIL_RUN_ID>`에는 결과를 반영할 TestRail Test Run ID를 입력해야 합니다.

예를 들어 Test Run ID가 `16`인 경우 다음과 같이 실행합니다.

```bash
trcli -y \
  -c trcli-config.yml \
  parse_junit \
  -f ./test-results/junit-report.xml \
  --run-id 16 \
  --case-matcher property
```

Test Run ID는 실행 시점에 사용하는 TestRail Run에 따라 변경될 수 있으므로, 업로드 전에 현재 대상 Run ID를 확인해야 합니다.

## 테스트 실행 및 TestRail 업로드

테스트 실행과 TestRail 결과 업로드를 한 번에 처리하려면 다음 명령어를 사용합니다.

```bash
npm run test:testrail
```

`test:testrail` 스크립트는 다음 순서로 동작합니다.

1. `npm run test:e2e`를 실행합니다.
2. `test-results/junit-report.xml` 생성 여부를 확인합니다.
3. 생성된 JUnit XML을 TestRail에 업로드합니다.

테스트가 정상적으로 실행되지 않았거나 JUnit XML이 생성되지 않은 경우 TestRail 업로드가 실패할 수 있습니다.

## Git 제외 파일

다음 파일과 디렉터리는 `.gitignore`를 통해 Git에서 제외합니다.

```gitignore
node_modules/

/test-results/
/playwright-report/
/blob-report/

/playwright/.cache/
/playwright/.auth/

*.log

.env
.env.*

trcli-config.yml
```

환경 변수 구조를 공유해야 하는 경우 실제 값이 없는 `.env.example` 파일을 사용할 수 있습니다.

`trcli-config.example.yml`은 설정 형식을 공유하기 위한 예시 파일이므로 Git에 포함합니다.

## 테스트 대상

현재 인증 화면을 대상으로 E2E 테스트를 수행합니다.

### 로그인

파일 경로:

```text
tests/auth/login.spec.ts
```

테스트 항목:

* 잘못된 ID 입력
* 잘못된 Password 입력
* 정상 로그인

### 회원가입

파일 경로:

```text
tests/auth/signup.spec.ts
```

테스트 항목:

* ID 길이 검증
* ID 중복 확인
* Password 길이 검증
* Password 확인 불일치 검증
* Nickname 길이 검증
* Email 형식 검증
* Email 중복 확인
* 정상 회원가입

## 참고 사항

* 테스트 실행 전 대상 애플리케이션이 실행 중이어야 합니다.
* 기본 접속 주소는 `http://localhost:5173`입니다.
* TestRail 업로드 전 `test-results/junit-report.xml`이 생성되어 있어야 합니다.
* TestRail Test Run ID는 업로드 대상에 맞게 설정해야 합니다.
* `trcli-config.yml`과 `.env`는 Git에 커밋하지 않습니다.
* TestRail 케이스 매칭은 테스트 annotation의 `test_id` 값을 기준으로 합니다.
* 테스트 계정, 비밀번호, API Key 등의 민감 정보는 코드에 직접 작성하지 않습니다.
* 실패 결과는 HTML 리포트와 `test-results/` 디렉터리의 첨부 파일을 함께 확인합니다.
