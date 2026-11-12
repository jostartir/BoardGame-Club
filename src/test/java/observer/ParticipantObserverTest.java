package observer;

import model.BoardGame;
import model.GameNight;
import model.Participant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import strategy.ByDurationStrategy;
import strategy.GameRecommender;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParticipantObserverTest {

    private final BoardGame duoGame = new BoardGame(1L, "DuoGame", 2, 2, 30, 1, "Card");
    private final BoardGame trioGame = new BoardGame(2L, "TrioGame", 3, 4, 40, 2, "Party");

    private GameNight night;
    private ParticipantService service;
    private RecommendationUpdater updater;

    @BeforeEach
    void setUp() {
        night = new GameNight(1L, "Friday", LocalDateTime.now(), 120);
        service = new ParticipantService();
        updater = new RecommendationUpdater(
                new GameRecommender(new ByDurationStrategy()),
                List.of(duoGame, trioGame));
        service.subscribe(updater);

        service.addParticipant(night, new Participant(1L, "Alikhan"));
        service.addParticipant(night, new Participant(2L, "Daniyar"));
    }

    @Test
    void twoPlayerGameAppearsForTwoParticipants() {
        assertEquals(List.of(duoGame), updater.getLatestRecommendations());
    }

    @Test
    void addingThirdParticipantUpdatesRecommendations() {
        service.addParticipant(night, new Participant(3L, "Aigerim"));

        assertEquals(List.of(trioGame), updater.getLatestRecommendations());
    }

    @Test
    void removingParticipantUpdatesRecommendations() {
        service.addParticipant(night, new Participant(3L, "Aigerim"));
        service.removeParticipant(night, 3L);

        assertEquals(List.of(duoGame), updater.getLatestRecommendations());
    }

    @Test
    void rejectedChangeDoesNotNotifyObservers() {
        List<ParticipantEvent> events = new ArrayList<>();
        service.subscribe(events::add);

        assertThrows(IllegalArgumentException.class,
                () -> service.addParticipant(night, new Participant(1L, "Duplicate")));
        assertThrows(IllegalArgumentException.class,
                () -> service.removeParticipant(night, 99L));

        assertTrue(events.isEmpty());
    }

    @Test
    void unsubscribedObserverIsNotNotified() {
        List<ParticipantEvent> events = new ArrayList<>();
        ParticipantObserver observer = events::add;

        service.subscribe(observer);
        service.unsubscribe(observer);
        service.addParticipant(night, new Participant(3L, "Aigerim"));

        assertTrue(events.isEmpty());
    }

    @Test
    void eventDescribesTheChange() {
        List<ParticipantEvent> events = new ArrayList<>();
        service.subscribe(events::add);

        service.addParticipant(night, new Participant(3L, "Aigerim"));
        service.removeParticipant(night, 3L);

        assertEquals(2, events.size());
        assertEquals(ParticipantEvent.Type.ADDED, events.get(0).getType());
        assertEquals(ParticipantEvent.Type.REMOVED, events.get(1).getType());
        assertEquals("Aigerim", events.get(1).getParticipant().getName());
    }

    @Test
    void rejectsNullArguments() {
        assertThrows(IllegalArgumentException.class, () -> service.subscribe(null));
        assertThrows(IllegalArgumentException.class,
                () -> service.addParticipant(null, new Participant(5L, "X")));
        assertThrows(IllegalArgumentException.class,
                () -> new RecommendationUpdater(null, List.of()));
    }
}
