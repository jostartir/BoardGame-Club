package model;

public class BoardGame {

    private final Long id;
    private final String title;
    private final int minPlayers;
    private final int maxPlayers;
    private final int durationMinutes;
    private final int difficulty;
    private final String genre;

    public BoardGame(
            Long id,
            String title,
            int minPlayers,
            int maxPlayers,
            int durationMinutes,
            int difficulty,
            String genre
    ) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "ID must be a positive number"
            );
        }

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException(
                    "Game title must not be blank"
            );
        }

        if (minPlayers < 1 || maxPlayers < minPlayers) {
            throw new IllegalArgumentException(
                    "Invalid player count range"
            );
        }

        if (durationMinutes <= 0) {
            throw new IllegalArgumentException(
                    "Duration must be greater than zero"
            );
        }

        if (difficulty < 1 || difficulty > 5) {
            throw new IllegalArgumentException(
                    "Difficulty must be between 1 and 5"
            );
        }

        if (genre == null || genre.isBlank()) {
            throw new IllegalArgumentException(
                    "Genre must not be blank"
            );
        }

        this.id = id;
        this.title = title.trim();
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.durationMinutes = durationMinutes;
        this.difficulty = difficulty;
        this.genre = genre.trim();
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public String getGenre() {
        return genre;
    }

    public boolean supportsPlayerCount(int playerCount) {
        return playerCount >= minPlayers && playerCount <= maxPlayers;
    }
}
