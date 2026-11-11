package strategy;

import model.BoardGame;
import model.GameNight;

import java.util.List;

public class GameRecommender {

    private RecommendationStrategy strategy;

    public GameRecommender(RecommendationStrategy strategy) {
        setStrategy(strategy);
    }

    public void setStrategy(RecommendationStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("Strategy must not be null");
        }

        this.strategy = strategy;
    }

    public RecommendationStrategy getStrategy() {
        return strategy;
    }

    public List<BoardGame> recommend(List<BoardGame> games, GameNight gameNight) {
        return strategy.recommend(games, gameNight);
    }
}
