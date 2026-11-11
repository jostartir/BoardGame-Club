package model;

public class PlayerResult {

    private final Participant participant;
    private final int score;

    public PlayerResult(Participant participant, int score) {
        if (participant == null) {
            throw new IllegalArgumentException(
                    "Participant must not be null"
            );
        }

        this.participant = participant;
        this.score = score;
    }

    public Participant getParticipant() {
        return participant;
    }

    public int getScore() {
        return score;
    }
}
