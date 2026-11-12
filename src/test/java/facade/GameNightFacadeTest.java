package facade;

import model.BoardGame;
import model.GameNight;
import model.GameSession;
import model.MeetingStatus;
import model.Participant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameNightFacadeTest {

    private final GameNightFacade facade = new GameNightFacade();
    private GameNight meeting;

    @BeforeEach
    void setUp() {
        meeting = new GameNight(
                1L, "Friday game night", LocalDateTime.of(2026, 11, 20, 18, 0), 60);
        meeting.addParticipant(new Participant(1L, "Alikhan"));
        meeting.addParticipant(new Participant(2L, "Daniyar"));
    }

    @Test
    void startsGameThatFitsExactlyInAvailableTime() {
        BoardGame chess = new BoardGame(1L, "Chess", 2, 2, 60, 3, "Abstract");

        GameSession session = facade.startMeeting(meeting, chess, 1L);

        assertEquals(MeetingStatus.IN_PROGRESS, meeting.getStatus());
        assertEquals(List.of(session), meeting.getSessions());
        assertEquals("Chess", session.getGame().getTitle());
        assertEquals(meeting.getParticipants(), session.getParticipants());
    }

    @Test
    void leavesMeetingPlannedWhenGameTakesTooLong() {
        BoardGame longGame = new BoardGame(2L, "Long game", 2, 4, 61, 2, "Strategy");

        assertThrows(IllegalArgumentException.class,
                () -> facade.startMeeting(meeting, longGame, 1L));

        assertEquals(MeetingStatus.PLANNED, meeting.getStatus());
        assertTrue(meeting.getSessions().isEmpty());
    }

    @Test
    void leavesMeetingPlannedWhenThereAreTooFewPlayers() {
        BoardGame trioGame = new BoardGame(3L, "Trio game", 3, 4, 30, 2, "Party");

        assertThrows(IllegalArgumentException.class,
                () -> facade.startMeeting(meeting, trioGame, 1L));

        assertEquals(MeetingStatus.PLANNED, meeting.getStatus());
        assertTrue(meeting.getSessions().isEmpty());
    }
}