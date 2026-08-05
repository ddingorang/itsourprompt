package com.promptstudio.relay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * 방 참가자 한 명.
 *
 * <p>{@code seatOrder}는 입장 시점이 아니라 게임 시작 시점에 {@code joinedAt} 순으로 부여한다.
 * 입장할 때 번호를 주면 시작 전에 누군가 나갈 때마다 재정렬해야 하고, 그 사이에 들어온 사람과
 * 번호가 겹칠 수 있다.
 */
@Entity
@Table(name = "relay_participant")
public class RelayParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false, updatable = false)
    private Long roomId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "seat_order")
    private Integer seatOrder;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    protected RelayParticipant() {
    }

    private RelayParticipant(Long roomId, Long userId, Instant joinedAt) {
        this.roomId = roomId;
        this.userId = userId;
        this.joinedAt = joinedAt;
    }

    public static RelayParticipant join(Long roomId, Long userId) {
        return new RelayParticipant(roomId, userId, Instant.now());
    }

    /**
     * 게임 시작 시점에 입장 순서대로 부여된다. 한 번 정해지면 바뀌지 않는다.
     */
    public void assignSeat(int seatOrder) {
        if (this.seatOrder != null) {
            throw new IllegalStateException("Participant " + id + " already has seat " + this.seatOrder);
        }

        this.seatOrder = seatOrder;
    }

    /**
     * 게임 중 이탈. 좌석은 남겨 둔다 — 그 좌석의 턴이 오면 스킵되고,
     * 스킵된 턴은 어템프트에 쌓이지 않으므로 피드백 개수 검증이 자동으로 맞아떨어진다.
     */
    public void leave() {
        if (leftAt == null) {
            this.leftAt = Instant.now();
        }
    }

    /**
     * 이탈했던 참가자가 돌아왔다. 지우지 않으면 돌아온 사람이 계속 이탈자로 보여
     * 자기 차례가 오는 족족 스킵된다.
     */
    public void rejoin() {
        this.leftAt = null;
    }

    public boolean hasLeft() {
        return leftAt != null;
    }

    public Long id() {
        return id;
    }

    public Long roomId() {
        return roomId;
    }

    public Long userId() {
        return userId;
    }

    public Integer seatOrder() {
        return seatOrder;
    }

    public Instant joinedAt() {
        return joinedAt;
    }

    public Instant leftAt() {
        return leftAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RelayParticipant participant)) {
            return false;
        }

        return id != null && id.equals(participant.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
