import adapter.CsvGameCatalogAdapter;
import adapter.CsvReader;
import adapter.GameCatalogSource;
import model.BoardGame;
import model.GameNight;
import model.Participant;
import strategy.ByDifficultyStrategy;
import strategy.ByDurationStrategy;
import strategy.GameRecommender;
import strategy.RecommendationStrategy;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        Path catalogFile = Path.of(args.length > 0 ? args[0] : "games.csv");

        GameCatalogSource source = new CsvGameCatalogAdapter(new CsvReader());
        List<BoardGame> games = source.load(catalogFile);
        System.out.println("Loaded " + games.size() + " games from " + catalogFile);

        GameNight night = new GameNight(1L, "Friday game night", LocalDateTime.now(), 120);
        night.addParticipant(new Participant(1L, "Alikhan"));
        night.addParticipant(new Participant(2L, "Daniyar"));

        System.out.println("Meeting: " + night.getParticipantCount()
                + " players, " + night.getAvailableMinutes() + " minutes");

        GameRecommender recommender = new GameRecommender(new ByDurationStrategy());

        for (RecommendationStrategy strategy
                : List.of(new ByDurationStrategy(), new ByDifficultyStrategy())) {
            recommender.setStrategy(strategy);
            System.out.println();
            System.out.println(strategy.getName() + ":");

            List<BoardGame> recommended = recommender.recommend(games, night);

            if (recommended.isEmpty()) {
                System.out.println("  no suitable games");
            }

            for (BoardGame game : recommended) {
                System.out.println("  " + game.getTitle()
                        + " (" + game.getMinPlayers() + "-" + game.getMaxPlayers() + " players, "
                        + game.getDurationMinutes() + " min, difficulty " + game.getDifficulty() + ")");
            }
        }
    }
}
