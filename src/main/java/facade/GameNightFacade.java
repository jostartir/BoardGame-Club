package facade;

import model.BoardGame;
import model.GameNight;
import model.GameSession;

public class GameNightFacade {
    public GameSession startMeeting(GameNight meeting, BoardGame game, Long sessionId){
        if (meeting == null || game == null){
            throw new IllegalArgumentException("Meeting and game are required");
        }

        if (game.getDurationMinutes() > meeting.getAvailableMinutes()){
            throw new IllegalArgumentException("Game takes longer than the meeting allows");
        }

        GameSession session = new GameSession(sessionId, game, meeting.getParticipants()
        );

        meeting.startWith(session);
        return session;
    }
}
