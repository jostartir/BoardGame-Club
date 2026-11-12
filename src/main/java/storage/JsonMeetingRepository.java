package storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import model.BoardGame;
import model.GameNight;
import model.GameSession;
import model.MeetingStatus;
import model.Participant;
import model.PlayerResult;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class JsonMeetingRepository {

    private final Path file;
    private final Gson gson = new GsonBuilder()
            .setStrictness(Strictness.STRICT)
            .setPrettyPrinting()
            .create();

    public JsonMeetingRepository(Path file) {
        this.file = Objects.requireNonNull(file, "Storage file is required")
                .toAbsolutePath()
                .normalize();
    }

    public void saveAll(List<GameNight> meetings) throws IOException {
        List<MeetingData> data = meetings.stream().map(MeetingData::from).toList();
        String json = gson.toJson(data);

        Path directory = Files.createDirectories(file.getParent());
        Path temporaryFile = Files.createTempFile(directory, "meetings-", ".tmp");
        try {
            Files.writeString(temporaryFile, json);
            try {
                Files.move(temporaryFile, file,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    public List<GameNight> loadAll() throws IOException {
        String json;
        try {
            json = Files.readString(file);
        } catch (NoSuchFileException e) {
            return new ArrayList<>();
        }

        try {
            MeetingData[] data = gson.fromJson(json, MeetingData[].class);
            if (data == null) {
                throw new IllegalArgumentException("Expected a JSON array of meetings");
            }

            List<GameNight> meetings = new ArrayList<>();
            for (MeetingData savedMeeting : data) {
                meetings.add(savedMeeting.toMeeting());
            }
            return meetings;
        } catch (JsonParseException | IllegalArgumentException | IllegalStateException
                 | DateTimeException | NullPointerException e) {
            throw new IOException("Cannot load meetings from " + file + ": " + e.getMessage(), e);
        }
    }

    private record MeetingData(
            Long id,
            String title,
            String scheduledAt,
            int availableMinutes,
            MeetingStatus status,
            List<ParticipantData> participants,
            List<SessionData> sessions
    ) {
        static MeetingData from(GameNight meeting) {
            return new MeetingData(meeting.getId(), meeting.getTitle(),
                    meeting.getScheduledAt().toString(), meeting.getAvailableMinutes(),
                    meeting.getStatus(),
                    meeting.getParticipants().stream().map(ParticipantData::from).toList(),
                    meeting.getSessions().stream().map(SessionData::from).toList());
        }

        GameNight toMeeting() {
            GameNight meeting = new GameNight(
                    id, title, LocalDateTime.parse(scheduledAt), availableMinutes);
            for (ParticipantData participant : participants) {
                meeting.addParticipant(participant.toParticipant());
            }
            if (sessions.size() > 1) {
                throw new IllegalArgumentException("A meeting supports at most one session");
            }
            if (!sessions.isEmpty()) {
                meeting.startWith(sessions.getFirst().toSession());
            }
            if (meeting.getStatus() != status) {
                throw new IllegalArgumentException("Meeting status does not match its sessions");
            }
            return meeting;
        }
    }

    private record SessionData(
            Long id,
            GameData game,
            List<ParticipantData> participants,
            List<ResultData> results,
            Integer actualDurationMinutes
    ) {
        static SessionData from(GameSession session) {
            return new SessionData(session.getId(), GameData.from(session.getGame()),
                    session.getParticipants().stream().map(ParticipantData::from).toList(),
                    session.getResults().stream().map(ResultData::from).toList(),
                    session.getActualDurationMinutes());
        }

        GameSession toSession() {
            List<Participant> players = participants.stream()
                    .map(ParticipantData::toParticipant)
                    .toList();
            GameSession session = new GameSession(id, game.toGame(), players);
            for (ResultData result : results) {
                session.recordResult(new PlayerResult(result.participant().toParticipant(), result.score()));
            }
            if (actualDurationMinutes < 0) {
                throw new IllegalArgumentException("Session duration cannot be negative");
            }
            if (actualDurationMinutes > 0) {
                session.finish(actualDurationMinutes);
            }
            return session;
        }
    }

    private record GameData(
            Long id,
            String title,
            int minPlayers,
            int maxPlayers,
            int durationMinutes,
            int difficulty,
            String genre
    ) {
        static GameData from(BoardGame game) {
            return new GameData(game.getId(), game.getTitle(), game.getMinPlayers(),
                    game.getMaxPlayers(), game.getDurationMinutes(), game.getDifficulty(), game.getGenre());
        }

        BoardGame toGame() {
            return new BoardGame(id, title, minPlayers, maxPlayers, durationMinutes, difficulty, genre);
        }
    }

    private record ParticipantData(Long id, String name) {
        static ParticipantData from(Participant participant) {
            return new ParticipantData(participant.getId(), participant.getName());
        }

        Participant toParticipant() {
            return new Participant(id, name);
        }
    }

    private record ResultData(ParticipantData participant, Integer score) {
        static ResultData from(PlayerResult result) {
            return new ResultData(ParticipantData.from(result.getParticipant()), result.getScore());
        }
    }
}