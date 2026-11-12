package decorator;

import model.GameNight;
import model.GameSession;

import java.util.List;

public class BasicMeetingReport implements MeetingReport{
    private final GameNight meeting;

    public BasicMeetingReport(GameNight meeting){
        this.meeting = meeting;
    }

    @Override
    public String generate(){
        StringBuilder text = new StringBuilder();
        text.append("Meeting: ").append(meeting.getTitle()).append("\n");
        text.append("Games:\n");

        List<GameSession> sessions = meeting.getSessions();
        if (sessions.isEmpty()){
            text.append("No sessions yet.\n");
        }

        for (GameSession session: sessions){
            text.append("- ").append(session.getGame().getTitle()).append("\n");
        }

        return text.toString();
    }
}
