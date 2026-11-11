package builder;

import model.GameNight;
import model.Participant;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class GameNightBuilder {
    private Long id;
    private String title;
    private LocalDateTime scheduledAt;

    private int availableMinutes = 120;
    private final List<Participant> participants = new ArrayList<>();

    public GameNightBuilder id(Long id){
        this.id = id;
        return this;
    }

    public GameNightBuilder title(String title){
        this.title = title;
        return this;
    }

    public GameNightBuilder scheduledAt(LocalDateTime scheduledAt){
        this.scheduledAt = scheduledAt;
        return this;
    }

    public GameNightBuilder availableMinutes(int availableMinutes){
        this.availableMinutes = availableMinutes;
        return this;
    }

    public GameNightBuilder addParticipant(Participant participant){
        participants.add(participant);
        return this;
    }

    public GameNight build(){
        GameNight meeting = new GameNight(id,title,scheduledAt,availableMinutes);
        for (int i = 0; i < participants.size(); i++) {
            Participant participant = participants.get(i);
            meeting.addParticipant(participant);
        }

        return meeting;
    }

}
