package com.turtlesoup.room;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoomServiceTest {

    RoomService service = new RoomService();

    @Test
    void createReturnsRoomWithCode() {
        Room room = service.create("호스트", "제목", "상황", "정답");
        assertThat(room.getCode()).hasSize(4);
        assertThat(room.getHostName()).isEqualTo("호스트");
        assertThat(room.getSolution()).isEqualTo("정답");
        assertThat(service.get(room.getCode())).isSameAs(room);
    }

    @Test
    void codesAreUnique() {
        java.util.Set<String> codes = new java.util.HashSet<>();
        for (int i = 0; i < 200; i++) {
            codes.add(service.create("h", "t", "s", "a").getCode());
        }
        assertThat(codes).hasSize(200);
    }

    @Test
    void getThrowsWhenMissing() {
        assertThatThrownBy(() -> service.get("ZZZZ"))
            .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void tracksParticipantsAndRemovesEmptyRoom() {
        Room room = service.create("호스트", "t", "s", "a");
        String code = room.getCode();
        service.join(code, "철수");
        service.join(code, "영희");
        assertThat(service.get(code).participants()).containsExactly("철수", "영희");

        service.leave(code, "철수");
        assertThat(service.get(code).participants()).containsExactly("영희");
    }

    @Test
    void removeDeletesRoom() {
        Room room = service.create("호스트", "t", "s", "a");
        service.remove(room.getCode());
        assertThatThrownBy(() -> service.get(room.getCode()))
            .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void hintsUsedCapsAtThree() {
        Room room = service.create("호스트", "t", "s", "a");
        assertThat(room.canHint()).isTrue();
        assertThat(room.useHint()).isEqualTo(1);
        room.useHint();
        room.useHint();
        assertThat(room.canHint()).isFalse();
        assertThat(room.getHintsUsed()).isEqualTo(3);
    }
}
