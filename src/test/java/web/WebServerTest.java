package web;

import app.MeetingController;
import model.BoardGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import storage.JsonMeetingRepository;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WebServerTest {

    @TempDir
    Path tempDir;

    private WebServer server;
    private String base;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() throws IOException {
        MeetingController controller = new MeetingController(List.of(
                new BoardGame(1L, "Uno", 2, 10, 20, 1, "Card"),
                new BoardGame(2L, "Monopoly", 2, 8, 120, 2, "Family"),
                new BoardGame(3L, "Chess", 2, 2, 60, 3, "Abstract")),
                new JsonMeetingRepository(tempDir.resolve("meetings.json")));

        server = new WebServer(controller);
        base = "http://localhost:" + server.start(0);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path));

        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/x-www-form-urlencoded");
            request.method(method, HttpRequest.BodyPublishers.ofString(body));
        }

        return client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> addParticipant(String name) throws Exception {
        return send("POST", "/api/participants", "name=" + URLEncoder.encode(name, StandardCharsets.UTF_8));
    }

    @Test
    void servesThePageAndTheCatalog() throws Exception {
        HttpResponse<String> page = send("GET", "/", null);
        HttpResponse<String> games = send("GET", "/api/games", null);

        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("Board Game Club"));
        assertEquals(200, games.statusCode());
        assertTrue(games.body().contains("\"title\":\"Chess\""));
    }

    @Test
    void chessDisappearsWhenThirdParticipantJoins() throws Exception {
        send("POST", "/api/meeting", "title=Friday&date=2026-11-13T18:00&minutes=120");
        addParticipant("Alikhan");
        HttpResponse<String> two = addParticipant("Daniyar");

        assertTrue(two.body().contains("\"title\":\"Chess\""));

        HttpResponse<String> three = addParticipant("Aigerim");

        assertFalse(three.body().substring(three.body().indexOf("\"recommendations\"")).contains("Chess"));
    }

    @Test
    void startingAGameMarksTheMeetingInProgress() throws Exception {
        send("POST", "/api/meeting", "title=Friday&date=2026-11-13T18:00&minutes=120");
        addParticipant("Alikhan");
        addParticipant("Daniyar");

        HttpResponse<String> started = send("POST", "/api/start", "gameId=3");

        assertEquals(200, started.statusCode());
        assertTrue(started.body().contains("\"status\":\"IN_PROGRESS\""));
        assertTrue(started.body().contains("\"game\":\"Chess\""));
    }

    @Test
    void scoresCanBeSavedFinishedAndCorrected() throws Exception {
        send("POST", "/api/meeting", "title=Friday&date=2026-11-13T18:00&minutes=120");
        addParticipant("Alikhan");
        addParticipant("Daniyar");
        send("POST", "/api/start", "gameId=3");

        HttpResponse<String> saved = send("POST", "/api/scores", "participantId=1&score=10");
        assertEquals(200, saved.statusCode());
        assertTrue(saved.body().contains("\"score\":10"));

        HttpResponse<String> replaced = send("POST", "/api/scores", "participantId=1&score=15");
        assertEquals(1, replaced.body().split("\"score\":").length - 1);
        assertTrue(replaced.body().contains("\"score\":15"));

        HttpResponse<String> bad = send("POST", "/api/finish", "minutes=40&score_2=abc");
        assertEquals(400, bad.statusCode());
        assertTrue(bad.body().contains("Score for Daniyar must be a whole number"));

        HttpResponse<String> missing = send("POST", "/api/finish", "minutes=40&score_2=");
        assertTrue(missing.body().contains("Enter a score for Daniyar"));

        HttpResponse<String> done = send("POST", "/api/finish", "minutes=40&score_2=25");
        assertEquals(200, done.statusCode());
        assertTrue(done.body().contains("\"winners\":[\"Daniyar\"]"));

        HttpResponse<String> fixed = send("POST", "/api/scores/correct", "participantId=1&score=30");
        assertTrue(fixed.body().contains("\"winners\":[\"Alikhan\"]"));
        assertTrue(send("GET", "/api/report?duration=1&scores=1", null).body().contains("Alikhan: 30"));
        assertEquals(400, send("POST", "/api/scores", "participantId=1&score=1").statusCode());
    }

    @Test
    void savedMeetingsAreListedAndCanBeOpened() throws Exception {
        send("POST", "/api/meeting", "title=Friday&date=2026-11-13T18:00&minutes=120");
        send("POST", "/api/meeting", "title=Saturday&date=2026-11-14T19:30&minutes=90");

        HttpResponse<String> state = send("GET", "/api/state", null);
        assertTrue(state.body().contains("\"title\":\"Friday\""));
        assertTrue(state.body().contains("\"scheduledAt\":\"2026-11-14T19:30\""));

        HttpResponse<String> opened = send("POST", "/api/meetings/select", "id=1");
        assertTrue(opened.body().startsWith("{\"meeting\":{\"id\":1,\"title\":\"Friday\""));
        assertEquals(400, send("POST", "/api/meetings/select", "id=99").statusCode());
    }

    @Test
    void invalidMeetingDateIsRejected() throws Exception {
        assertEquals(400, send("POST", "/api/meeting", "title=X&date=&minutes=60").statusCode());
        assertEquals(400, send("POST", "/api/meeting", "title=X&date=tomorrow&minutes=60").statusCode());
    }

    @Test
    void invalidRequestsReturnClearErrors() throws Exception {
        HttpResponse<String> noMeeting = addParticipant("Alikhan");
        assertEquals(400, noMeeting.statusCode());
        assertTrue(noMeeting.body().contains("Create or open a meeting first"));

        HttpResponse<String> badMinutes = send("POST", "/api/meeting", "title=X&date=2026-11-13T18:00&minutes=abc");
        assertEquals(400, badMinutes.statusCode());
        assertTrue(badMinutes.body().contains("whole number"));

        send("POST", "/api/meeting", "title=Friday&date=2026-11-13T18:00&minutes=120");
        assertEquals(400, addParticipant("  ").statusCode());
        assertEquals(400, send("POST", "/api/start", "gameId=99").statusCode());
        assertEquals(404, send("GET", "/api/unknown", null).statusCode());
    }

    @Test
    void jsonEscapesSpecialCharacters() {
        assertEquals("\"a\\\"b\\\\c\\nd\"", Json.write("a\"b\\c\nd"));
        assertEquals("{\"x\":[1,true,null]}", Json.write(java.util.Map.of("x", java.util.Arrays.asList(1, true, null))));
    }
}
