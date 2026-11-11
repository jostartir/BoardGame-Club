package strategy;

import model.BoardGame;

import java.util.Comparator;

public class ByDifficultyStrategy extends AbstractRecommendationStrategy {

    @Override
    public String getName() {
        return "By difficulty (easiest first)";
    }

    @Override
    protected Comparator<BoardGame> getComparator() {
        return Comparator.comparingInt(BoardGame::getDifficulty)
                .thenComparingInt(BoardGame::getDurationMinutes)
                .thenComparing(BoardGame::getTitle);
    }
}
