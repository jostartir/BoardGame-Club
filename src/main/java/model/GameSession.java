package model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GameSession {

    private final Long id;
    private final BoardGame game;
    private final List<Participant> participants;
    private final List<PlayerResult> results;

    // Пока партия не завершена, длительность равна 0.
    private int actualDurationMinutes;

    public GameSession(
            Long id,
            BoardGame game,
            List<Participant> participants
    ) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "ID must be a positive number"
            );
        }

        if (game == null) {
            throw new IllegalArgumentException(
                    "Game must not be null"
            );
        }

        if (participants == null || participants.isEmpty()) {
            throw new IllegalArgumentException(
                    "Participants must not be empty"
            );
        }

        if (!game.supportsPlayerCount(participants.size())) {
            throw new IllegalArgumentException(
                    "Player count is not supported by this game"
            );
        }

        Set<Long> participantIds = new HashSet<>();

        for (Participant participant : participants) {
            if (participant == null) {
                throw new IllegalArgumentException(
                        "Participant must not be null"
                );
            }

            if (!participantIds.add(participant.getId())) {
                throw new IllegalArgumentException(
                        "Duplicate participant in the session"
                );
            }
        }

        this.id = id;
        this.game = game;
        this.participants = List.copyOf(participants);
        this.results = new ArrayList<>();
        this.actualDurationMinutes = 0;
    }

    public void recordResult(PlayerResult result) {
        if (isCompleted()) {
            throw new IllegalStateException(
                    "Completed session cannot be modified"
            );
        }

        if (result == null) {
            throw new IllegalArgumentException(
                    "Result must not be null"
            );
        }

        Long participantId = result.getParticipant().getId();

        boolean participantExists = participants.stream()
                .anyMatch(participant ->
                        participant.getId().equals(participantId)
                );

        if (!participantExists) {
            throw new IllegalArgumentException(
                    "Participant is not in this session"
            );
        }

        // Если результат уже был, заменяем его новым.
        results.removeIf(existing ->
                existing.getParticipant().getId().equals(participantId)
        );

        results.add(result);
    }

    public void finish(int actualDurationMinutes) {
        if (isCompleted()) {
            throw new IllegalStateException(
                    "Session is already completed"
            );
        }

        if (actualDurationMinutes <= 0) {
            throw new IllegalArgumentException(
                    "Actual duration must be greater than zero"
            );
        }

        if (results.size() != participants.size()) {
            throw new IllegalStateException(
                    "Every participant must have a result"
            );
        }

        this.actualDurationMinutes = actualDurationMinutes;
    }

    public List<Participant> getWinners() {
        if (!isCompleted()) {
            throw new IllegalStateException(
                    "Session must be completed before determining winners"
            );
        }

        int highestScore = results.stream()
                .mapToInt(PlayerResult::getScore)
                .max()
                .orElseThrow();

        return results.stream()
                .filter(result -> result.getScore() == highestScore)
                .map(PlayerResult::getParticipant)
                .toList();
    }

    public boolean isCompleted() {
        return actualDurationMinutes > 0;
    }

    public Long getId() {
        return id;
    }

    public BoardGame getGame() {
        return game;
    }

    public List<Participant> getParticipants() {
        return participants;
    }

    public List<PlayerResult> getResults() {
        return List.copyOf(results);
    }

    public int getActualDurationMinutes() {
        return actualDurationMinutes;
    }
}