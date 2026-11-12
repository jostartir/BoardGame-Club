package decorator;

import model.GameNight;
import model.GameSession;

import java.util.List;

public class DurationReportDecorator implements MeetingReport{

    private final MeetingReport report;
    private final GameNight meeting;

    public DurationReportDecorator(MeetingReport report, GameNight meeting){
        this.report = report;
        this.meeting = meeting;
    }

    @Override
    public String generate(){
        StringBuilder text = new StringBuilder(report.generate());
        text.append("\nDuration:\n");

        List<GameSession> sessions = meeting.getSessions();
        if (sessions.isEmpty()){
            text.append("No sessions yes.\n");
        }

        for (GameSession session : sessions){
            text.append("- ").append(session.getGame().getTitle()).append(": ");

            if (session.isCompleted()){
                text.append(session.getActualDurationMinutes()).append(" min\n");
            }else{
                text.append("Not finished\n");
            }
        }

        return text.toString();
    }


}
