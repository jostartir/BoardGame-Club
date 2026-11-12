package observer;

import model.GameNight;
import model.Participant;

import java.util.ArrayList;
import java.util.List;

public class ParticipantService {

    private final List<ParticipantObserver> observers = new ArrayList<>();

    public void subscribe(ParticipantObserver observer) {
        if (observer == null) {
            throw new IllegalArgumentException("Observer must not be null");
        }

        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void unsubscribe(ParticipantObserver observer) {
        observers.remove(observer);
    }

    public void addParticipant(GameNight gameNight, Participant participant) {
        if (gameNight == null) {
            throw new IllegalArgumentException("Game night must not be null");
        }

        gameNight.addParticipant(participant);

        notifyObservers(new ParticipantEvent(
                ParticipantEvent.Type.ADDED, gameNight, participant));
    }

    public void removeParticipant(GameNight gameNight, Long participantId) {
        if (gameNight == null) {
            throw new IllegalArgumentException("Game night must not be null");
        }

        Participant removed = gameNight.getParticipants().stream()
                .filter(participant -> participant.getId().equals(participantId))
                .findFirst()
                .orElse(null);

        gameNight.removeParticipant(participantId);

        notifyObservers(new ParticipantEvent(
                ParticipantEvent.Type.REMOVED, gameNight, removed));
    }

    private void notifyObservers(ParticipantEvent event) {
        for (ParticipantObserver observer : List.copyOf(observers)) {
            observer.onParticipantChanged(event);
        }
    }
}
