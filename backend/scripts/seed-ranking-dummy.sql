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
-- 문제마다 서로 겹치지 않는 더미 유저 6명씩(총 36명)을 만들어 SUBMITTED attempt를 하나씩
-- 채운다 — 문제별로 랭킹에 오르는 이름이 달라지도록 한 것이다. 대상 problem은 로컬 DB에
-- 이미 동기화되어 있던 6개(id 1,2,3,8,9,11). 대상 problem id를 바꾸려면 v_problem_ids와
-- v_nicknames의 행 수를 맞춰서 고친다.

BEGIN;

DO $$
DECLARE
    v_problem_ids BIGINT[] := ARRAY[1, 2, 3, 8, 9, 11];
    -- 문제별로 겹치지 않는 닉네임 6개씩. 행 순서가 v_problem_ids와 대응한다.
    v_nicknames   TEXT[][] := ARRAY[
        ARRAY['토큰절약가', '버그사냥꾼', '프롬프트장인', '컴파일마스터', '리팩터요정', '테스트통과봇'],
        ARRAY['널포인터', '무한루프', '스택오버플로워', '데드락해결사', '세미콜론사냥꾼', '인덴트장인'],
        ARRAY['커밋메시지장인', '브랜치마법사', '머지컨플릭트', '코드리뷰어', '야근각서', '디버그탐정'],
        ARRAY['캐시무효화', '비동기고수', '동시성마스터', '메모리누수', '가비지컬렉터', '타입추론가'],
        ARRAY['제네릭장인', '람다요정', '스트림마스터', '옵셔널사용자', '어노테이션수집가', '유닛테스트광'],
        ARRAY['통합테스트러', '목객체제작자', '스파이빈', '트랜잭션롤백', '핫픽스전문가', '리버트요정']
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
