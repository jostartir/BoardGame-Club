package strategy;

import model.BoardGame;
import model.GameNight;

import java.util.Comparator;
import java.util.List;

public abstract class AbstractRecommendationStrategy implements RecommendationStrategy {

    @Override
    public List<BoardGame> recommend(List<BoardGame> games, GameNight gameNight) {
        if (games == null) {
            throw new IllegalArgumentException("Games list must not be null");
        }

        if (gameNight == null) {
            throw new IllegalArgumentException("Game night must not be null");
        }

        return games.stream()
                .filter(game -> isSuitable(game, gameNight))
                .sorted(getComparator())
                .toList();
    }

    private boolean isSuitable(BoardGame game, GameNight gameNight) {
        return game.supportsPlayerCount(gameNight.getParticipantCount())
                && game.getDurationMinutes() <= gameNight.getAvailableMinutes();
    }

    protected abstract Comparator<BoardGame> getComparator();
}
