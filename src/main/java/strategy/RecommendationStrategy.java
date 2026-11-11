package strategy;

import model.BoardGame;
import model.GameNight;

import java.util.List;

public interface RecommendationStrategy {

    String getName();

    List<BoardGame> recommend(List<BoardGame> games, GameNight gameNight);
}
