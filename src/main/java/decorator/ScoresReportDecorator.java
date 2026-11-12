package decorator;

import model.GameNight;
import model.GameSession;
import model.PlayerResult;

import java.util.List;

public class ScoresReportDecorator implements MeetingReport{
    private final MeetingReport report;
    private final GameNight meeting;

    public ScoresReportDecorator(MeetingReport report, GameNight meeting){
        this.report = report;
        this.meeting = meeting;
    }

    @Override
    public String generate(){
        StringBuilder text = new StringBuilder(report.generate());
        text.append("\nScores:\n");

        List<GameSession> sessions = meeting.getSessions();
        if (sessions.isEmpty()){
            text.append("No sessions yet.\n");
        }

        for (GameSession session : sessions){
            text.append(session.getGame().getTitle()).append("\n");

            List<PlayerResult> results = session.getResults();
            if (results.isEmpty()){
                text.append("No scores recorded.\n");
            }

            for (PlayerResult result : results){
                text.append("- ").append(result.getParticipant().getName()).append(": ").append(result.getScore()).append("\n");
            }
        }

        return text.toString();
    }
}
