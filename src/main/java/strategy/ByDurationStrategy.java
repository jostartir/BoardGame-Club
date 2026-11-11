package strategy;

import model.BoardGame;

import java.util.Comparator;

public class ByDurationStrategy extends AbstractRecommendationStrategy {

    @Override
    public String getName() {
        return "By duration (shortest first)";
    }

    @Override
    protected Comparator<BoardGame> getComparator() {
        return Comparator.comparingInt(BoardGame::getDurationMinutes)
                .thenComparingInt(BoardGame::getDifficulty)
                .thenComparing(BoardGame::getTitle);
    }
}
