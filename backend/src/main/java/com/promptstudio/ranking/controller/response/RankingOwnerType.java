package com.promptstudio.ranking.controller.response;

/**
 * 랭킹 한 줄의 주인이 누구인지. 게스트도 랭킹에 든다 — 로그인 여부는 프롬프트 효율과 무관하다.
 */
public enum RankingOwnerType {

    USER,
    GUEST
}
