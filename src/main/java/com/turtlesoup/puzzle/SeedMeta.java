package com.turtlesoup.puzzle;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * 시드 버전 기록용 단일 행 엔티티(id=1 고정).
 * PuzzleSeeder가 여기 저장된 버전과 코드의 SEED_VERSION을 비교해,
 * 다르면 문제를 지우고 다시 심는다(재시드). → 문제/힌트 바꾸면 배포만으로 반영.
 */
@Entity
public class SeedMeta {

    @Id
    private Integer id;   // 항상 1
    private int version;

    protected SeedMeta() {}

    public SeedMeta(Integer id, int version) {
        this.id = id;
        this.version = version;
    }

    public int getVersion() { return version; }
}
