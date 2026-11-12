package app;

import builder.GameNightBuilder;
import decorator.BasicMeetingReport;
import decorator.DurationReportDecorator;
import decorator.MeetingReport;
import decorator.ScoresReportDecorator;
import facade.GameNightFacade;
import model.BoardGame;
import model.GameNight;
import model.GameSession;
import model.MeetingStatus;
import model.Participant;
import model.PlayerResult;
import observer.ParticipantObserver;
import observer.ParticipantService;
import observer.RecommendationUpdater;
import storage.JsonMeetingRepository;
import strategy.ByDifficultyStrategy;
import strategy.ByDurationStrategy;
import strategy.GameRecommender;
import strategy.RecommendationStrategy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class MeetingController {

    private final List<RecommendationStrategy> strategies =
            List.of(new ByDurationStrategy(), new ByDifficultyStrategy());

    private final List<BoardGame> catalog;
    private final JsonMeetingRepository repository;
    private final List<GameNight> meetings;

    private final ParticipantService participantService = new ParticipantService();
    private final GameRecommender recommender = new GameRecommender(strategies.get(0));
    private final RecommendationUpdater updater;
    private final GameNightFacade facade = new GameNightFacade();

    private Consumer<List<BoardGame>> recommendationListener = games -> { };

    private GameNight meeting;
    private long nextMeetingId = 1;
    private long nextParticipantId = 1;
    private long nextSessionId = 1;

    public MeetingController(List<BoardGame> catalog, JsonMeetingRepository repository) throws IOException {
        this.catalog = List.copyOf(catalog);
        this.repository = repository;
        this.meetings = new ArrayList<>(repository.loadAll());

        continueNumberingAfterSavedData();

        this.updater = new RecommendationUpdater(
                recommender, this.catalog, games -> recommendationListener.accept(games));
        participantService.subscribe(updater);
    }

    private void continueNumberingAfterSavedData() {
        for (GameNight saved : meetings) {
            nextMeetingId = Math.max(nextMeetingId, saved.getId() + 1);

            for (Participant participant : saved.getParticipants()) {
                nextParticipantId = Math.max(nextParticipantId, participant.getId() + 1);
            }

            for (GameSession session : saved.getSessions()) {
                nextSessionId = Math.max(nextSessionId, session.getId() + 1);

                for (Participant participant : session.getParticipants()) {
                    nextParticipantId = Math.max(nextParticipantId, participant.getId() + 1);
                }
            }
        }
    }

    // ----- meetings -----

    public void createMeeting(String title, LocalDateTime scheduledAt, int availableMinutes) {
        GameNight created = new GameNightBuilder()
                .id(nextMeetingId)
                .title(title)
                .scheduledAt(scheduledAt)
                .availableMinutes(availableMinutes)
                .build();

        nextMeetingId++;
        meetings.add(created);
        meeting = created;
        updater.refresh(meeting);
        save();
    }

    public void selectMeeting(long meetingId) {
        for (GameNight saved : meetings) {
            if (saved.getId() == meetingId) {
                meeting = saved;
                updater.refresh(meeting);
                return;
            }
        }

        throw new IllegalArgumentException("Meeting not found");
    }

    public List<GameNight> getMeetings() {
        return List.copyOf(meetings);
    }

    public GameNight getMeeting() {
        return meeting;
    }

    public boolean hasMeeting() {
        return meeting != null;
    }

    public boolean isMeetingPlanned() {
        return meeting != null && meeting.getStatus() == MeetingStatus.PLANNED;
    }

    // ----- participants -----

    public void addParticipant(String name) {
        requirePlannedMeeting();

        String trimmed = name == null ? "" : name.trim();

        for (Participant existing : meeting.getParticipants()) {
            if (existing.getName().equalsIgnoreCase(trimmed)) {
                throw new IllegalArgumentException(
                        "A participant named " + trimmed + " is already in the meeting");
            }
        }

        participantService.addParticipant(meeting, new Participant(nextParticipantId, name));
        nextParticipantId++;
        save();
    }

    public void removeParticipant(Long participantId) {
        requirePlannedMeeting();
        participantService.removeParticipant(meeting, participantId);
        save();
    }

    public List<Participant> getParticipants() {
        return meeting == null ? List.of() : meeting.getParticipants();
    }

    public void subscribe(ParticipantObserver observer) {
        participantService.subscribe(observer);
    }

    // ----- recommendations -----

    public List<RecommendationStrategy> getStrategies() {
        return strategies;
    }

    public RecommendationStrategy getSelectedStrategy() {
        return recommender.getStrategy();
    }

    public void selectStrategy(RecommendationStrategy strategy) {
        recommender.setStrategy(strategy);

        if (meeting != null) {
            updater.refresh(meeting);
        }
    }

    public void setRecommendationListener(Consumer<List<BoardGame>> listener) {
        this.recommendationListener = listener == null ? games -> { } : listener;
    }

    public List<BoardGame> getRecommendations() {
        return updater.getLatestRecommendations();
    }

    // ----- catalog and starting a game -----

    public List<BoardGame> getCatalog() {
        return catalog;
    }

    public BoardGame findGame(long gameId) {
        return catalog.stream()
                .filter(game -> game.getId() == gameId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Game not found in the catalog"));
    }

    public GameSession startGame(BoardGame game) {
        requirePlannedMeeting();

        GameSession session = facade.startMeeting(meeting, game, nextSessionId);
        nextSessionId++;
        save();
        return session;
    }

    // ----- scores -----

    public void saveScore(long participantId, int score) {
        GameSession session = requireGameInProgress();
        session.recordResult(new PlayerResult(findPlayer(session, participantId), score));
        save();
    }

    public GameSession finishGame(Map<Long, Integer> scoresByParticipantId, int actualMinutes) {
        GameSession session = requireGameInProgress();

        for (Participant participant : session.getParticipants()) {
            Integer score = scoresByParticipantId.get(participant.getId());

            if (score != null) {
                session.recordResult(new PlayerResult(participant, score));
            }
        }

        for (Participant participant : session.getParticipants()) {
            boolean hasResult = session.getResults().stream()
                    .anyMatch(result -> result.getParticipant().getId().equals(participant.getId()));

            if (!hasResult) {
                throw new IllegalArgumentException("Enter a score for " + participant.getName());
            }
        }

        session.finish(actualMinutes);
        save();
        return session;
    }

    public void correctScore(long participantId, int score) {
        GameSession session = requireFinishedGame();
        session.correctResult(new PlayerResult(findPlayer(session, participantId), score));
        save();
    }

    public GameSession getCurrentSession() {
        if (meeting == null || meeting.getSessions().isEmpty()) {
            return null;
        }

        List<GameSession> sessions = meeting.getSessions();
        return sessions.get(sessions.size() - 1);
    }

    public String buildReport(boolean withDurations, boolean withScores) {
        if (meeting == null) {
            throw new IllegalStateException("Create or open a meeting first");
        }

        MeetingReport report = new BasicMeetingReport(meeting);

        if (withDurations) {
            report = new DurationReportDecorator(report, meeting);
        }

        if (withScores) {
            report = new ScoresReportDecorator(report, meeting);
        }

        return report.generate();
    }

    // ----- helpers -----

    private Participant findPlayer(GameSession session, long participantId) {
        return session.getParticipants().stream()
                .filter(participant -> participant.getId() == participantId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Participant is not in this game"));
    }

    private GameSession requireGameInProgress() {
        GameSession session = getCurrentSession();

        if (session == null || session.isCompleted()) {
            throw new IllegalStateException("There is no game in progress");
        }

        return session;
    }

    private GameSession requireFinishedGame() {
        GameSession session = getCurrentSession();

        if (session == null || !session.isCompleted()) {
            throw new IllegalStateException("There is no finished game to correct");
        }

        return session;
    }

    private void requirePlannedMeeting() {
        if (meeting == null) {
            throw new IllegalStateException("Create or open a meeting first");
        }

        if (meeting.getStatus() != MeetingStatus.PLANNED) {
            throw new IllegalStateException("The meeting has already started");
        }
    }

    private void save() {
        try {
            repository.saveAll(meetings);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save meetings: " + e.getMessage(), e);
        }
    }
}
