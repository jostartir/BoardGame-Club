package model;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class GameNight {

    private final Long id;
    private final String title;
    private final LocalDateTime scheduledAt;
    private final int availableMinutes;
    private final List<Participant> participants;
    private MeetingStatus status = MeetingStatus.PLANNED;
    private final List<GameSession> sessions = new ArrayList<>();

    public GameNight(
            Long id,
            String title,
            LocalDateTime scheduledAt,
            int availableMinutes
    ) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "ID must be a positive number"
            );
        }

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException(
                    "Meeting title must not be blank"
            );
        }

        if (scheduledAt == null) {
            throw new IllegalArgumentException(
                    "Meeting date and time must not be null"
            );
        }

        if (availableMinutes <= 0) {
            throw new IllegalArgumentException(
                    "Available time must be greater than zero"
            );
        }

        this.id = id;
        this.title = title.trim();
        this.scheduledAt = scheduledAt;
        this.availableMinutes = availableMinutes;
        this.participants = new ArrayList<>();
    }

    public void addParticipant(Participant participant) {
        if (participant == null) {
            throw new IllegalArgumentException(
                    "Participant must not be null"
            );
        }

        for (Participant existing : participants) {
            if (existing.getId().equals(participant.getId())) {
                throw new IllegalArgumentException(
                        "Participant is already in the meeting"
                );
            }
        }

        participants.add(participant);
    }

    public void removeParticipant(Long participantId) {
        if (participantId == null || participantId <= 0) {
            throw new IllegalArgumentException(
                    "Participant ID must be a positive number"
            );
        }

        boolean removed = participants.removeIf(
                participant -> participant.getId().equals(participantId)
        );

        if (!removed) {
            throw new IllegalArgumentException(
                    "Participant was not found in the meeting"
            );
        }
    }

    public void startWith(GameSession firstSession){
        if (status != MeetingStatus.PLANNED){
            throw new IllegalStateException("Only a planned meeting can be started");
        }

        if (firstSession == null){
            throw new IllegalArgumentException("Game session must not be null");
        }

        sessions.add(firstSession);
        status = MeetingStatus.IN_PROGRESS;
    }

    public MeetingStatus getStatus(){
        return  status;
    }

    public List<GameSession> getSessions(){
        return List.copyOf(sessions);
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public int getAvailableMinutes() {
        return availableMinutes;
    }

    public List<Participant> getParticipants() {
        return List.copyOf(participants);
    }

    public int getParticipantCount() {
        return participants.size();
    }
}
