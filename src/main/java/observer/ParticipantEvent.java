package observer;

import model.GameNight;
import model.Participant;

public class ParticipantEvent {

    public enum Type {
        ADDED,
        REMOVED
    }

    private final Type type;
    private final GameNight gameNight;
    private final Participant participant;

    public ParticipantEvent(Type type, GameNight gameNight, Participant participant) {
        if (type == null) {
            throw new IllegalArgumentException("Event type must not be null");
        }

        if (gameNight == null) {
            throw new IllegalArgumentException("Game night must not be null");
        }

        if (participant == null) {
            throw new IllegalArgumentException("Participant must not be null");
        }

        this.type = type;
        this.gameNight = gameNight;
        this.participant = participant;
    }

    public Type getType() {
        return type;
    }

    public GameNight getGameNight() {
        return gameNight;
    }

    public Participant getParticipant() {
        return participant;
    }
}
