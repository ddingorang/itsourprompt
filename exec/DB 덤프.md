# DB 덤프

### 1. 초기 데이터 저장

- GitLab repo 형태의 **문제 저장소** 존재
- 백엔드 서버가 Scheduler로 **매 5분마다** 원격 repo에서 문제를 clone받아 DB에 저장

#### 1.1. problem 구조

- **skeleton** : 문제 스켈레톤 코드
- **problem**.**yml** : 백엔드에서 문제 처리 위한 정보
    - title : 문제 정보
    - type : 일반 or 게임
    - language : 문제 언어(type이 일반일 때)
    
    ![스크린샷 7.png](images/%EC%8A%A4%ED%81%AC%EB%A6%B0%EC%83%B7_7.png)
    
- **spec.md** : 문제 명세
    
    ![스크린샷 8.png](images/%EC%8A%A4%ED%81%AC%EB%A6%B0%EC%83%B7_8.png)
    
- **Repo 디렉터리 구조**

![스크린샷 6.png](images/%EC%8A%A4%ED%81%AC%EB%A6%B0%EC%83%B7_6.png)

- 각 문제는 **위 이미지와 같은 파일 구조를 가져야 함**
    - 파일 구조와 yml 형식을 참고하여 백엔드에서 정보를 파싱
    - problem 테이블에 맞추어 문제가 저장됨
    - **(id, 문제 제목, 문제 명세, 문제 코드, 문제 타입/언어)**

### 2. 문제 저장소

- 아래 credential을 확보
    - **PROBLEM_REPO_TOKEN=<문제 저장소 repo token key>**
    - **PROBLEM_REPO_BASE_URL=https://lab.ssafy.com**
    - **PROBLEM_REPO_PROJECT_ID=<문제 저장소 repo id>**
- repo token은 **repo의 Settings → Access Tokens**에서 발급
- project id는 **repo의 Settings → General**에서 확인

### 3. 초기 테이블 설정 sql

- **backend/src/main/resources/schema.sql**에 존재
- 서버 실행 시 sql 자동 실행됨

### 4. 랭킹 더미 데이터(옵션)

- **backend/scripts/seed-ranking-dummy.sql
- 더미 데이터로 랭킹을 채우고 싶을 때 실행