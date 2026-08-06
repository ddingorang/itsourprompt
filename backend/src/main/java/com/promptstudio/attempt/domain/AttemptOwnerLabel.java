package com.promptstudio.attempt.domain;

/**
 * 어템프트 주인을 화면에 부르는 이름 — 로그인 사용자는 닉네임, 게스트는 세션 UUID 앞 네 자다.
 *
 * <p>같은 주인은 어디에서 보든 같은 이름이어야 한다. 그래서 규칙을 표시 계층(응답 매퍼)이 아니라
 * 주인을 아는 도메인에 둔다 — 매퍼에 두면 주인을 싣는 응답이 하나 늘 때마다 규칙이 한 벌씩 복제된다.
 *
 * <p>'게스트' 같은 접두어는 붙이지 않는다. 그건 화면이 정하는 문구다.
 */
public final class AttemptOwnerLabel {

    /**
     * UUID 앞 네 자. 게스트끼리 구분할 수만 있으면 되고, 길수록 세션 ID를 그대로 흘리는 셈이 된다.
     */
    private static final int GUEST_LABEL_LENGTH = 4;

    /**
     * @param owner    소유자 없는 과거 기록이면 null이고, 그때 표시 이름도 null이다
     * @param nickname 주인이 로그인 사용자일 때의 닉네임. 조인하지 않은 경로는 모를 수 있고 그러면 null이다
     */
    public static String of(AttemptOwner owner, String nickname) {
        if (owner == null) {
            return null;
        }

        if (owner.isUser()) {
            return nickname;
        }

        return owner.guestSessionId().toString().substring(0, GUEST_LABEL_LENGTH);
    }

    private AttemptOwnerLabel() {
    }
}
