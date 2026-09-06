-- MariaDB 수동 배포용. 애플리케이션에서 자동 실행하지 않는다.
ALTER TABLE promise
    ADD COLUMN IF NOT EXISTS participation_deadline DATETIME(6) NULL,
    ADD COLUMN IF NOT EXISTS participation_deadline_locked BOOLEAN NOT NULL DEFAULT FALSE;

-- 기존 ENUM 컬럼에도 취소 상태를 허용한다.
ALTER TABLE participant
    MODIFY COLUMN status ENUM('ACCEPTED', 'CANCELLED', 'DECLINED', 'PENDING') NULL;

UPDATE promise p
SET p.participation_deadline_locked = TRUE
WHERE EXISTS (
    SELECT 1 FROM participant pt
    WHERE pt.promise_id = p.id
      AND pt.guest_id <> p.creator_id
      AND pt.status IN ('ACCEPTED', 'CANCELLED')
);

-- 기존 participation_deadline의 NULL은 유지한다.
-- 애플리케이션에서 기존 약속의 시작 시각을 마감으로 취급한다.
