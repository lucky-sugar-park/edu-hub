ALTER TABLE enrollment
    ADD COLUMN waiting_key  BIGINT      NULL,
    ADD COLUMN confirmed_at DATETIME(6) NULL;

-- FR-04로 이미 저장된 데이터 보정: 대기자는 신청 순서(id)대로 1부터, 확정자는 신청 시각을 확정 시각으로.
UPDATE enrollment e
    JOIN (SELECT id, ROW_NUMBER() OVER (PARTITION BY session_id ORDER BY id) AS rn
          FROM enrollment WHERE status = 'WAITING') w ON e.id = w.id
SET e.waiting_key = w.rn;
UPDATE enrollment SET confirmed_at = applied_at WHERE status = 'CONFIRMED';

-- 같은 회차 안에서 정렬 키 중복 금지(NULL은 여러 개 허용). 순번 계산과 max/min 조회에도 쓴다.
CREATE UNIQUE INDEX uk_enrollment_waiting_key ON enrollment (session_id, waiting_key);
