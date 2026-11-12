package observer;

import model.BoardGame;
import model.GameNight;
import strategy.GameRecommender;

import java.util.List;
import java.util.function.Consumer;

public class RecommendationUpdater implements ParticipantObserver {

    private final GameRecommender recommender;
    private final List<BoardGame> catalog;
    private final Consumer<List<BoardGame>> onUpdate;

    private List<BoardGame> latestRecommendations = List.of();

    public RecommendationUpdater(GameRecommender recommender, List<BoardGame> catalog) {
        this(recommender, catalog, null);
    }

    public RecommendationUpdater(
            GameRecommender recommender,
            List<BoardGame> catalog,
            Consumer<List<BoardGame>> onUpdate
    ) {
        if (recommender == null) {
            throw new IllegalArgumentException("Recommender must not be null");
        }

        if (catalog == null) {
            throw new IllegalArgumentException("Game catalog must not be null");
        }

        this.recommender = recommender;
        this.catalog = List.copyOf(catalog);
        this.onUpdate = onUpdate;
    }

    @Override
    public void onParticipantChanged(ParticipantEvent event) {
        refresh(event.getGameNight());
    }

    public void refresh(GameNight gameNight) {
        latestRecommendations = recommender.recommend(catalog, gameNight);

        if (onUpdate != null) {
            onUpdate.accept(latestRecommendations);
        }
    }

    public List<BoardGame> getLatestRecommendations() {
        return latestRecommendations;
    }
}
