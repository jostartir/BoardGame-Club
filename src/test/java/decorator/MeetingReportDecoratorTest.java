package decorator;

import model.BoardGame;
import model.GameNight;
import model.GameSession;
import model.Participant;
import model.PlayerResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MeetingReportDecoratorTest {

    private GameNight meeting;
    private GameSession session;
    private Participant alikhan;

    @BeforeEach
    void setUp() {
        alikhan = new Participant(1L, "Alikhan");
        Participant daniyar = new Participant(2L, "Daniyar");
        BoardGame chess = new BoardGame(1L, "Chess", 2, 2, 60, 3, "Abstract");

        meeting = new GameNight(
                1L, "Friday game night", LocalDateTime.of(2026, 11, 20, 18, 0), 120);
        meeting.addParticipant(alikhan);
        meeting.addParticipant(daniyar);

        session = new GameSession(1L, chess, List.of(alikhan, daniyar));
        meeting.startWith(session);
        session.recordResult(new PlayerResult(alikhan, 10));
        session.recordResult(new PlayerResult(daniyar, 8));
        session.finish(35);
    }

    @Test
    void combinesScoresAndDurationWithTheBasicReport() {
        MeetingReport basic = new BasicMeetingReport(meeting);
        MeetingReport withScores = new ScoresReportDecorator(basic, meeting);
        MeetingReport withBoth = new DurationReportDecorator(withScores, meeting);

        String report = withBoth.generate();

        assertTrue(report.startsWith(basic.generate()));
        assertTrue(report.contains("Scores:"));
        assertTrue(report.contains("Alikhan: 10"));
        assertTrue(report.contains("Daniyar: 8"));
        assertTrue(report.contains("Duration:"));
        assertTrue(report.contains("Chess: 35 min"));
    }

    @Test
    void showsCorrectedScoreInTheRegeneratedReport() {
        MeetingReport report = new ScoresReportDecorator(new BasicMeetingReport(meeting), meeting);
        assertTrue(report.generate().contains("Alikhan: 10"));

        session.correctResult(new PlayerResult(alikhan, 15));

        String updated = report.generate();
        assertTrue(updated.contains("Alikhan: 15"));
        assertFalse(updated.contains("Alikhan: 10"));
    }
}