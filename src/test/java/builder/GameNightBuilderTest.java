package builder;

import model.GameNight;
import model.Participant;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameNightBuilderTest {

    private static final LocalDateTime DATE = LocalDateTime.of(2026, 11, 20, 18, 0);

    @Test
    void buildsMeetingWithSelectedSettingsAndParticipants() {
        GameNight meeting = new GameNightBuilder()
                .id(1L)
                .title("Friday game night")
                .scheduledAt(DATE)
                .availableMinutes(90)
                .addParticipant(new Participant(1L, "Alikhan"))
                .addParticipant(new Participant(2L, "Daniyar"))
                .build();

        assertEquals("Friday game night", meeting.getTitle());
        assertEquals(DATE, meeting.getScheduledAt());
        assertEquals(90, meeting.getAvailableMinutes());
        assertEquals(List.of("Alikhan", "Daniyar"),
                meeting.getParticipants().stream().map(Participant::getName).toList());
    }

    @Test
    void uses120MinutesWhenDurationIsOmitted() {
        GameNight meeting = new GameNightBuilder()
                .id(1L)
                .title("Friday game night")
                .scheduledAt(DATE)
                .build();

        assertEquals(120, meeting.getAvailableMinutes());
        assertTrue(meeting.getParticipants().isEmpty());
    }

    @Test
    void rejectsDuplicateParticipantIds() {
        GameNightBuilder builder = new GameNightBuilder()
                .id(1L)
                .title("Friday game night")
                .scheduledAt(DATE)
                .addParticipant(new Participant(1L, "Alikhan"))
                .addParticipant(new Participant(1L, "Daniyar"));

        assertThrows(IllegalArgumentException.class, builder::build);
    }
}