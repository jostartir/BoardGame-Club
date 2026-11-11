package strategy;

import model.BoardGame;
import model.GameNight;
import model.Participant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationStrategyTest {

    private final BoardGame quickHard = new BoardGame(1L, "QuickHard", 2, 4, 20, 4, "Strategy");
    private final BoardGame mediumEasy = new BoardGame(2L, "MediumEasy", 2, 4, 40, 1, "Party");
    private final BoardGame longMedium = new BoardGame(3L, "LongMedium", 2, 4, 90, 2, "Strategy");
    private final BoardGame tooLong = new BoardGame(4L, "TooLong", 2, 4, 240, 3, "Strategy");
    private final BoardGame needsFive = new BoardGame(5L, "NeedsFive", 5, 6, 30, 1, "Party");

    private final List<BoardGame> games =
            List.of(longMedium, quickHard, tooLong, needsFive, mediumEasy);

    private GameNight night;

    @BeforeEach
    void setUp() {
        night = new GameNight(1L, "Friday", LocalDateTime.now(), 100);
        night.addParticipant(new Participant(1L, "Alikhan"));
        night.addParticipant(new Participant(2L, "Daniyar"));
        night.addParticipant(new Participant(3L, "Aigerim"));
    }

    @Test
    void byDurationSortsShortestFirstAndFiltersUnsuitableGames() {
        List<BoardGame> result = new ByDurationStrategy().recommend(games, night);

        assertEquals(List.of(quickHard, mediumEasy, longMedium), result);
    }

    @Test
    void byDifficultySortsEasiestFirstAndFiltersUnsuitableGames() {
        List<BoardGame> result = new ByDifficultyStrategy().recommend(games, night);

        assertEquals(List.of(mediumEasy, longMedium, quickHard), result);
    }

    @Test
    void switchingStrategyChangesTheOrder() {
        GameRecommender recommender = new GameRecommender(new ByDurationStrategy());
        List<BoardGame> first = recommender.recommend(games, night);

        recommender.setStrategy(new ByDifficultyStrategy());
        List<BoardGame> second = recommender.recommend(games, night);

        assertNotEquals(first, second);
        assertEquals(first.size(), second.size());
    }

    @Test
    void returnsEmptyListWhenNothingFits() {
        GameNight shortNight = new GameNight(2L, "Short", LocalDateTime.now(), 5);
        shortNight.addParticipant(new Participant(1L, "Alikhan"));

        assertTrue(new ByDurationStrategy().recommend(games, shortNight).isEmpty());
    }

    @Test
    void rejectsNullArguments() {
        RecommendationStrategy strategy = new ByDurationStrategy();

        assertThrows(IllegalArgumentException.class, () -> strategy.recommend(null, night));
        assertThrows(IllegalArgumentException.class, () -> strategy.recommend(games, null));
        assertThrows(IllegalArgumentException.class, () -> new GameRecommender(null));
    }
}
