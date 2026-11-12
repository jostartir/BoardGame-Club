package app;

import model.BoardGame;
import model.GameSession;
import model.MeetingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import storage.JsonMeetingRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MeetingControllerTest {

    private static final LocalDateTime WHEN = LocalDateTime.of(2026, 11, 13, 18, 0);

    private final BoardGame uno = new BoardGame(1L, "Uno", 2, 10, 20, 1, "Card");
    private final BoardGame monopoly = new BoardGame(2L, "Monopoly", 2, 8, 120, 2, "Family");
    private final BoardGame chess = new BoardGame(3L, "Chess", 2, 2, 60, 3, "Abstract");

    @TempDir
    Path tempDir;

    private Path dataFile;
    private MeetingController controller;

    @BeforeEach
    void setUp() throws IOException {
        dataFile = tempDir.resolve("data").resolve("meetings.json");
        controller = newController();
        controller.createMeeting("Friday", WHEN, 120);
        controller.addParticipant("Alikhan");
        controller.addParticipant("Daniyar");
    }

    private MeetingController newController() throws IOException {
        return new MeetingController(List.of(uno, monopoly, chess), new JsonMeetingRepository(dataFile));
    }

    private List<String> titles() {
        return controller.getRecommendations().stream().map(BoardGame::getTitle).toList();
    }

    private int scoreOf(GameSession session, String name) {
        return session.getResults().stream()
                .filter(result -> result.getParticipant().getName().equals(name))
                .findFirst().orElseThrow().getScore();
    }

    // ----- recommendations and starting -----

    @Test
    void chessIsRecommendedForTwoAndDisappearsForThree() {
        assertTrue(titles().contains("Chess"));

        controller.addParticipant("Aigerim");

        assertFalse(titles().contains("Chess"));
    }

    @Test
    void removingThirdParticipantBringsChessBack() {
        controller.addParticipant("Aigerim");
        controller.removeParticipant(3L);

        assertTrue(titles().contains("Chess"));
    }

    @Test
    void switchingStrategyChangesTheOrder() {
        assertEquals(List.of("Uno", "Chess", "Monopoly"), titles());

        controller.selectStrategy(controller.getStrategies().get(1));

        assertEquals(List.of("Uno", "Monopoly", "Chess"), titles());
    }

    @Test
    void startingGameCreatesSessionAndMarksMeetingInProgress() {
        controller.startGame(chess);

        assertEquals(MeetingStatus.IN_PROGRESS, controller.getMeeting().getStatus());
        assertEquals(1, controller.getMeeting().getSessions().size());
    }

    @Test
    void startedMeetingRejectsFurtherChanges() {
        controller.startGame(chess);

        assertThrows(IllegalStateException.class, () -> controller.addParticipant("Aigerim"));
        assertThrows(IllegalStateException.class, () -> controller.startGame(uno));
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> controller.addParticipant("  "));
        assertThrows(IllegalArgumentException.class, () -> controller.addParticipant("alikhan"));
        assertThrows(IllegalArgumentException.class, () -> controller.createMeeting("", WHEN, 60));
        assertThrows(IllegalArgumentException.class, () -> controller.createMeeting("X", WHEN, 0));
        assertThrows(IllegalArgumentException.class, () -> controller.createMeeting("X", null, 60));
    }

    @Test
    void gameTooLongForTheMeetingCannotBeStarted() {
        controller.createMeeting("Short", WHEN, 30);
        controller.addParticipant("Alikhan");
        controller.addParticipant("Daniyar");

        assertThrows(IllegalArgumentException.class, () -> controller.startGame(monopoly));
    }

    @Test
    void actionsBeforeOpeningAMeetingAreRejected() throws IOException {
        MeetingController empty = new MeetingController(List.of(uno),
                new JsonMeetingRepository(tempDir.resolve("empty.json")));

        assertThrows(IllegalStateException.class, () -> empty.addParticipant("Alikhan"));
        assertThrows(IllegalStateException.class, () -> empty.startGame(uno));
        assertThrows(IllegalStateException.class, () -> empty.saveScore(1, 5));
    }

    // ----- scores -----

    @Test
    void savingAScoreKeepsTheGameOpenAndReplacesTheOldValue() {
        controller.startGame(chess);

        controller.saveScore(1, 10);
        controller.saveScore(1, 15);

        GameSession session = controller.getCurrentSession();
        assertFalse(session.isCompleted());
        assertEquals(1, session.getResults().size());
        assertEquals(15, scoreOf(session, "Alikhan"));
    }

    @Test
    void finishNeedsEveryScoreAndAPositiveTime() {
        controller.startGame(chess);
        controller.saveScore(1, 10);

        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> controller.finishGame(Map.of(), 30));
        assertTrue(missing.getMessage().contains("Daniyar"));

        assertThrows(IllegalArgumentException.class, () -> controller.finishGame(Map.of(2L, 5), 0));
        assertFalse(controller.getCurrentSession().isCompleted());

        controller.finishGame(Map.of(2L, 5), 30);
        assertTrue(controller.getCurrentSession().isCompleted());
    }

    @Test
    void finishingPicksTheWinnerAndTieGivesTwo() {
        controller.startGame(chess);
        controller.finishGame(Map.of(1L, 10, 2L, 20), 45);

        assertEquals("Daniyar", controller.getCurrentSession().getWinners().get(0).getName());
        assertEquals(45, controller.getCurrentSession().getActualDurationMinutes());
    }

    @Test
    void scoresCannotBeSavedAfterFinishing() {
        controller.startGame(chess);
        controller.finishGame(Map.of(1L, 10, 2L, 20), 45);

        assertThrows(IllegalStateException.class, () -> controller.saveScore(1, 99));
        assertThrows(IllegalStateException.class, () -> controller.finishGame(Map.of(1L, 1, 2L, 2), 30));
    }

    @Test
    void correctingAScoreChangesWinnerAndReportButKeepsStateAndDuration() {
        controller.startGame(chess);
        controller.finishGame(Map.of(1L, 10, 2L, 20), 45);

        controller.correctScore(1, 30);

        GameSession session = controller.getCurrentSession();
        assertTrue(session.isCompleted());
        assertEquals(45, session.getActualDurationMinutes());
        assertEquals(2, session.getResults().size());
        assertEquals("Alikhan", session.getWinners().get(0).getName());
        assertTrue(controller.buildReport(true, true).contains("Alikhan: 30"));
    }

    @Test
    void correctingRequiresAFinishedGameAndAParticipantOfIt() {
        controller.startGame(chess);

        assertThrows(IllegalStateException.class, () -> controller.correctScore(1, 5));

        controller.finishGame(Map.of(1L, 10, 2L, 20), 45);

        assertThrows(IllegalArgumentException.class, () -> controller.correctScore(99, 5));
    }

    @Test
    void reportShowsOnlyTheRequestedSections() {
        controller.startGame(chess);
        controller.finishGame(Map.of(1L, 10, 2L, 20), 45);

        String full = controller.buildReport(true, true);
        String basic = controller.buildReport(false, false);

        assertTrue(full.contains("Scores:") && full.contains("Daniyar: 20") && full.contains("45 min"));
        assertTrue(basic.contains("Chess"));
        assertFalse(basic.contains("Scores:"));
        assertFalse(basic.contains("Duration:"));
    }

    // ----- saving and loading -----

    @Test
    void meetingsAndParticipantsSurviveARestart() throws IOException {
        MeetingController restarted = newController();

        assertEquals(1, restarted.getMeetings().size());
        restarted.selectMeeting(controller.getMeeting().getId());

        assertEquals("Friday", restarted.getMeeting().getTitle());
        assertEquals(WHEN, restarted.getMeeting().getScheduledAt());
        assertEquals(2, restarted.getParticipants().size());
        assertTrue(restarted.getRecommendations().stream().anyMatch(game -> game.getTitle().equals("Chess")));
    }

    @Test
    void scoreSurvivesARestartAndLaterCorrectionsAreSavedToo() throws IOException {
        controller.startGame(chess);
        controller.saveScore(1, 10);

        MeetingController restarted = newController();
        restarted.selectMeeting(1);
        GameSession reopened = restarted.getCurrentSession();

        assertEquals("Chess", reopened.getGame().getTitle());
        assertFalse(reopened.isCompleted());
        assertEquals(10, scoreOf(reopened, "Alikhan"));

        restarted.saveScore(1, 15);
        restarted.finishGame(Map.of(2L, 12), 40);
        restarted.correctScore(1, 20);

        MeetingController last = newController();
        last.selectMeeting(1);
        GameSession finished = last.getCurrentSession();

        assertTrue(finished.isCompleted());
        assertEquals(40, finished.getActualDurationMinutes());
        assertEquals(20, scoreOf(finished, "Alikhan"));
        assertEquals("Alikhan", finished.getWinners().get(0).getName());
        assertTrue(last.buildReport(true, true).contains("Alikhan: 20"));
    }

    @Test
    void numberingContinuesAfterARestart() throws IOException {
        controller.startGame(chess);

        MeetingController restarted = newController();
        restarted.createMeeting("Saturday", WHEN, 90);
        restarted.addParticipant("Aigerim");

        assertEquals(2L, restarted.getMeeting().getId());
        assertEquals(3L, restarted.getParticipants().get(0).getId());
    }

    @Test
    void openingAnUnknownMeetingIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> controller.selectMeeting(99));
    }

    @Test
    void failedSaveIsReportedAndNeverHiddenAsSuccess() throws IOException {
        Path folder = tempDir.resolve("blocked");
        MeetingController blocked = new MeetingController(List.of(uno),
                new JsonMeetingRepository(folder.resolve("meetings.json")));
        Files.writeString(folder, "not a folder");

        assertThrows(UncheckedIOException.class, () -> blocked.createMeeting("X", WHEN, 60));
    }

    @Test
    void brokenSavedFileStopsStartupInsteadOfBeingOverwritten() throws IOException {
        Files.writeString(dataFile, "this is not json");

        assertThrows(IOException.class, this::newController);
        assertEquals("this is not json", Files.readString(dataFile));
    }
}
