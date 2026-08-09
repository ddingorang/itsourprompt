-- 랭킹 화면 확인용 더미 데이터.
--
-- schema.sql과 달리 서버 부팅 시 자동 실행되지 않는다. 로컬/개발 DB에 psql로 직접 실행한다:
--   psql "$DATABASE_URL" -f backend/scripts/seed-ranking-dummy.sql
--
-- 랭킹은 별도 테이블이 아니라 다음 조건을 만족하는 attempt를 즉석에서 집계해 만든다
-- (JooqRankingQueryRepository 기준):
--   - attempt.status = 'SUBMITTED', attempt.user_id IS NOT NULL (게스트 제외)
--   - 그 attempt의 마지막 턴(=MAX(attempt_turn.ordinal))에 해당하는 code_run.status = 'SUCCEEDED'
--   - 그 턴에 attempt_llm_call(turn_ordinal 있음, purpose='CODE')이 있고
--     model·input_tokens·output_tokens가 전부 채워져 있으며 model_price에 등록된 모델이어야 함
--
-- 문제마다 서로 겹치지 않는 더미 유저 6명씩을 만들어 SUBMITTED attempt를 하나씩
-- 채운다 — 문제별로 랭킹에 오르는 이름이 달라지도록 한 것이다. 대상 problem은
-- 1번부터 14번까지 전체 문제이다. 대상 problem id를 바꾸려면 v_problem_ids와
-- v_nicknames의 행 수를 맞춰서 고친다.

BEGIN;

DO $$
DECLARE
    v_problem_ids BIGINT[] := ARRAY[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14];
    -- 문제별로 겹치지 않는 닉네임 6개씩. 행 순서가 v_problem_ids와 대응한다.
    v_nicknames   TEXT[][] := ARRAY[
        ARRAY['왕초보개발자', '버그왕', '슈퍼울트라개발자', '코딩초보자', '버그픽스전문가', '눈물의코딩'],
        ARRAY['무한루프', '에러수집가', '예외처리장인', '비동기장인', '제이슨파서장인', '페이지를못찾아'],
        ARRAY['디비마스터', '스택풀', '나만의작은디비', '트랜잭션롤백', '널포인터', '널포인터예외처리'],
        ARRAY['제네릭', '람다요정', '옵셔널최고', '인터페이스장인', '추상화의달인', '구현체디자이너'],
        ARRAY['오버라이딩', '객체지향생활체조', '싱글턴', '팩토리메서드', '빌더패턴', '어댑터패턴마스터'],
        ARRAY['스프링', '스프링빈마스터', '순환참조', '의존성주입', '디아이', '컨테이너관리자'],
        ARRAY['테스트', '목객체', '스파이객체', '테스트커버리지', '주도개발', 'TDD마스터'],
        ARRAY['도커고수', '컨테이너', '쿠버네티스마스터', '배포자동화', '씨아이', '지속적배포'],
        ARRAY['깃마스터', '커밋장인', '리베이스장인', '머지충돌해결사', '강제푸시', '풀리퀘스트장인'],
        ARRAY['프론트', '상태관리', '유즈이펙트', '리액트고수', '컴포넌트', '가상돔마스터'],
        ARRAY['뷰마스터', '양방향바인딩', '뷰엑스', '라이프사이클훅', '템플릿', '디렉티브'],
        ARRAY['알고리즘', '이진트리', '시간복잡도최적화', '공간복잡도', '빅오표기법', '동적계획법'],
        ARRAY['네트워크', '핑테스트', '아이피', '핸드쉐이크', '오에스아이', '네트워크마스터'],
        ARRAY['운영체제', '스레드', '컨텍스트스위칭', '데드락', '뮤텍스', '세마포어']
    ];
    v_problem_id  BIGINT;
    v_username    TEXT;
    v_nickname    TEXT;
    v_user_id     BIGINT;
    v_attempt_id  BIGINT;
    v_input       BIGINT;
    v_output      BIGINT;
    v_cached      BIGINT;
    v_reasoning   BIGINT;
    v_latency     BIGINT;
    v_submitted   TIMESTAMPTZ;
    pi            INT;
    i             INT;
BEGIN
    FOR pi IN 1..array_length(v_problem_ids, 1) LOOP
        v_problem_id := v_problem_ids[pi];

        FOR i IN 1..6 LOOP
            v_nickname := v_nicknames[pi][i];
            v_username := 'dummy_ranker_p' || v_problem_id || '_' || i;

            -- 유저는 (문제, 순번)마다 고유하다 — 같은 이름이 여러 문제 랭킹에 겹쳐 뜨지 않는다.
            INSERT INTO users (username, password_hash, nickname, email, created_at)
            VALUES (
                v_username, '$2a$10$00000000000000000000000000000000000000000000000000',
                v_nickname, v_username || '@example.com', now()
            )
            ON CONFLICT (username) DO UPDATE SET nickname = EXCLUDED.nickname
            RETURNING id INTO v_user_id;

            -- 유저마다 토큰 사용량을 다르게 줘서 비용(=랭킹 순위)이 갈리게 한다.
            v_input     := 400 + i * 350 + (v_problem_id * 17) % 200;
            v_output    := 150 + i * 130 + (v_problem_id * 11) % 100;
            v_cached    := (i * 40) % 300;
            v_reasoning := (i * 25) % 150;
            v_latency   := 500 + i * 180;
            v_submitted := now() - ((v_problem_id + i)::text || ' hours')::interval;

            INSERT INTO attempt (problem_id, status, user_id, submitted_at)
            VALUES (v_problem_id, 'SUBMITTED', v_user_id, v_submitted)
            RETURNING id INTO v_attempt_id;

            INSERT INTO attempt_turn (attempt_id, ordinal, user_prompt, ai_summary)
            VALUES (
                v_attempt_id, 0,
                '요구사항대로 동작하도록 구현해줘. 엣지 케이스도 빠뜨리지 말고 처리해줘.',
                '요청하신 로직을 구현하고 테스트를 통과하도록 수정했습니다.'
            );

            -- cost는 랭킹 계산에 쓰이지 않고(랭킹은 매번 model_price로 재계산) 기록용 스냅샷이지만,
            -- 화면에 그대로 노출되므로 현재 단가로 맞춰 채워둔다.
            INSERT INTO attempt_llm_call (
                attempt_id, turn_ordinal, purpose, seq, model,
                input_tokens, output_tokens, cached_input_tokens, reasoning_tokens,
                latency_ms, cost, status, created_at
            )
            SELECT
                v_attempt_id, 0, 'CODE', 1, 'gpt-5.6-luna',
                v_input, v_output, v_cached, v_reasoning,
                v_latency,
                ROUND((
                    (v_input - v_cached) * mp.input
                    + v_cached * COALESCE(mp.cached_input, mp.input)
                    + v_output * mp.output
                ) / 1000000.0, 8),
                'SUCCESS', v_submitted
            FROM model_price mp
            WHERE mp.model = 'gpt-5.6-luna';

            INSERT INTO code_run (id, attempt_id, turn_ordinal, status, exit_code, duration_ms, created_at, finished_at)
            VALUES (
                gen_random_uuid(), v_attempt_id, 0, 'SUCCEEDED', 0, v_latency + 400,
                v_submitted, v_submitted + interval '3 seconds'
            );
        END LOOP;
    END LOOP;
END $$;

COMMIT;

-- 되돌리기: attempt 삭제가 attempt_turn·attempt_llm_call·code_run까지 CASCADE로 지운다.
-- DELETE FROM attempt WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'dummy_ranker_p%');
-- DELETE FROM users WHERE username LIKE 'dummy_ranker_p%';
