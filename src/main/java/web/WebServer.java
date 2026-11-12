package web;

import app.MeetingController;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import model.BoardGame;
import model.GameNight;
import model.GameSession;
import model.Participant;
import model.PlayerResult;
import strategy.RecommendationStrategy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class WebServer {

    private static final Map<String, String> STATIC_FILES = Map.of(
            "/", "index.html",
            "/index.html", "index.html",
            "/style.css", "style.css",
            "/app.js", "app.js"
    );

    private final MeetingController controller;
    private HttpServer server;

    public WebServer(MeetingController controller) {
        this.controller = controller;
    }

    public int start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        return server.getAddress().getPort();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ----- routing -----

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();

            if (path.startsWith("/api/")) {
                handleApi(exchange, exchange.getRequestMethod(), path);
            } else {
                serveStatic(exchange, path);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            sendJson(exchange, 400, Map.of("error", e.getMessage()));
        } catch (UncheckedIOException e) {
            sendJson(exchange, 500, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(exchange, 500, Map.of("error", "Something went wrong on the server"));
        } finally {
            exchange.close();
        }
    }

    private void handleApi(HttpExchange exchange, String method, String path) throws IOException {
        if (method.equals("GET") && path.equals("/api/games")) {
            sendJson(exchange, 200, gamesJson(controller.getCatalog()));
        } else if (method.equals("GET") && path.equals("/api/state")) {
            sendJson(exchange, 200, stateJson());
        } else if (method.equals("POST") && path.equals("/api/meeting")) {
            Map<String, String> form = readForm(exchange);
            controller.createMeeting(form.get("title"), parseDateTime(form.get("date")),
                    parseInt(form.get("minutes"), "Available minutes must be a whole number, for example 120"));
            sendJson(exchange, 200, stateJson());
        } else if (method.equals("POST") && path.equals("/api/meetings/select")) {
            controller.selectMeeting(parseLong(readForm(exchange).get("id"), "Choose a saved meeting"));
            sendJson(exchange, 200, stateJson());
        } else if (method.equals("POST") && path.equals("/api/participants")) {
            controller.addParticipant(readForm(exchange).get("name"));
            sendJson(exchange, 200, stateJson());
        } else if (method.equals("DELETE") && path.startsWith("/api/participants/")) {
            long id = parseLong(path.substring("/api/participants/".length()), "Invalid participant id");
            controller.removeParticipant(id);
            sendJson(exchange, 200, stateJson());
        } else if (method.equals("POST") && path.equals("/api/strategy")) {
            int index = parseInt(readForm(exchange).get("id"), "Invalid strategy");
            List<RecommendationStrategy> strategies = controller.getStrategies();

            if (index < 0 || index >= strategies.size()) {
                throw new IllegalArgumentException("Unknown recommendation order");
            }

            controller.selectStrategy(strategies.get(index));
            sendJson(exchange, 200, stateJson());
        } else if (method.equals("POST") && (path.equals("/api/scores") || path.equals("/api/scores/correct"))) {
            Map<String, String> form = readForm(exchange);
            long participantId = parseLong(form.get("participantId"), "Invalid participant");
            int score = parseInt(form.get("score"), "Score must be a whole number, for example 15");

            if (path.equals("/api/scores")) {
                controller.saveScore(participantId, score);
            } else {
                controller.correctScore(participantId, score);
            }

            sendJson(exchange, 200, stateJson());
        } else if (method.equals("POST") && path.equals("/api/finish")) {
            Map<String, String> form = readForm(exchange);
            controller.finishGame(readScores(form), parseInt(form.get("minutes"),
                    "Played minutes must be a whole number, for example 30"));
            sendJson(exchange, 200, stateJson());
        } else if (method.equals("GET") && path.equals("/api/report")) {
            Map<String, String> query = parseForm(exchange.getRequestURI().getRawQuery());
            String report = controller.buildReport(
                    "1".equals(query.get("duration")), "1".equals(query.get("scores")));
            sendBytes(exchange, 200, "text/plain; charset=utf-8", report.getBytes(StandardCharsets.UTF_8));
        } else if (method.equals("POST") && path.equals("/api/start")) {
            long gameId = parseLong(readForm(exchange).get("gameId"), "Select a game first");
            controller.startGame(controller.findGame(gameId));
            sendJson(exchange, 200, stateJson());
        } else {
            sendJson(exchange, 404, Map.of("error", "Unknown request: " + method + " " + path));
        }
    }

    private void serveStatic(HttpExchange exchange, String path) throws IOException {
        if (path.equals("/favicon.ico")) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String fileName = STATIC_FILES.get(path);

        if (fileName == null) {
            sendBytes(exchange, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
            return;
        }

        try (InputStream in = WebServer.class.getResourceAsStream("/web/" + fileName)) {
            if (in == null) {
                sendBytes(exchange, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
                return;
            }

            sendBytes(exchange, 200, contentType(fileName), in.readAllBytes());
        }
    }

    // ----- JSON shapes -----

    private Map<String, Object> stateJson() {
        GameNight meeting = controller.getMeeting();
        Map<String, Object> state = new LinkedHashMap<>();

        state.put("meeting", meeting == null ? null : meetingJson(meeting));
        state.put("meetings", meetingsJson());
        state.put("canEdit", controller.isMeetingPlanned());

        List<Object> participants = new ArrayList<>();

        for (Participant participant : controller.getParticipants()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", participant.getId());
            item.put("name", participant.getName());
            participants.add(item);
        }

        state.put("participants", participants);

        List<Object> strategies = new ArrayList<>();
        List<RecommendationStrategy> all = controller.getStrategies();

        for (int i = 0; i < all.size(); i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("name", all.get(i).getName());
            strategies.add(item);
        }

        state.put("strategies", strategies);
        state.put("selectedStrategy", all.indexOf(controller.getSelectedStrategy()));
        state.put("recommendations", gamesJson(controller.getRecommendations()));
        return state;
    }

    private List<Object> meetingsJson() {
        List<Object> list = new ArrayList<>();

        for (GameNight saved : controller.getMeetings()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", saved.getId());
            item.put("title", saved.getTitle());
            item.put("scheduledAt", saved.getScheduledAt().toString());
            item.put("status", saved.getStatus());
            item.put("participantCount", saved.getParticipantCount());

            if (saved.getSessions().isEmpty()) {
                item.put("game", null);
                item.put("gameFinished", false);
            } else {
                GameSession session = saved.getSessions().get(0);
                item.put("game", session.getGame().getTitle());
                item.put("gameFinished", session.isCompleted());
            }

            list.add(item);
        }

        return list;
    }

    private Map<String, Object> meetingJson(GameNight meeting) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", meeting.getId());
        json.put("title", meeting.getTitle());
        json.put("scheduledAt", meeting.getScheduledAt().toString());
        json.put("availableMinutes", meeting.getAvailableMinutes());
        json.put("participantCount", meeting.getParticipantCount());
        json.put("status", meeting.getStatus());

        List<Object> sessions = new ArrayList<>();

        for (GameSession session : meeting.getSessions()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", session.getId());
            item.put("game", session.getGame().getTitle());
            item.put("gameMinutes", session.getGame().getDurationMinutes());
            item.put("completed", session.isCompleted());
            item.put("actualMinutes", session.getActualDurationMinutes());

            List<Object> players = new ArrayList<>();

            for (Participant participant : session.getParticipants()) {
                Map<String, Object> player = new LinkedHashMap<>();
                player.put("id", participant.getId());
                player.put("name", participant.getName());
                players.add(player);
            }

            item.put("players", players);

            List<Object> results = new ArrayList<>();
            List<Object> winners = new ArrayList<>();

            for (PlayerResult result : session.getResults()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("participantId", result.getParticipant().getId());
                row.put("name", result.getParticipant().getName());
                row.put("score", result.getScore());
                results.add(row);
            }

            if (session.isCompleted()) {
                for (Participant winner : session.getWinners()) {
                    winners.add(winner.getName());
                }
            }

            item.put("results", results);
            item.put("winners", winners);
            sessions.add(item);
        }

        json.put("sessions", sessions);
        return json;
    }

    private List<Object> gamesJson(List<BoardGame> games) {
        List<Object> list = new ArrayList<>();

        for (BoardGame game : games) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", game.getId());
            item.put("title", game.getTitle());
            item.put("minPlayers", game.getMinPlayers());
            item.put("maxPlayers", game.getMaxPlayers());
            item.put("durationMinutes", game.getDurationMinutes());
            item.put("difficulty", game.getDifficulty());
            item.put("genre", game.getGenre());
            list.add(item);
        }

        return list;
    }

    // ----- helpers -----

    private Map<String, String> readForm(HttpExchange exchange) throws IOException {
        return parseForm(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    }

    private Map<Long, Integer> readScores(Map<String, String> form) {
        Map<Long, Integer> scores = new LinkedHashMap<>();

        for (Participant participant : controller.getParticipants()) {
            String value = form.get("score_" + participant.getId());

            if (value != null && !value.isBlank()) {
                scores.put(participant.getId(), parseInt(value,
                        "Score for " + participant.getName() + " must be a whole number"));
            }
        }

        return scores;
    }

    private Map<String, String> parseForm(String body) {
        Map<String, String> form = new LinkedHashMap<>();

        if (body == null) {
            return form;
        }

        for (String pair : body.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }

            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);

            form.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }

        return form;
    }

    private LocalDateTime parseDateTime(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Choose the meeting date and time");
        }

        try {
            return LocalDateTime.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("The meeting date and time are not valid");
        }
    }

    private int parseInt(String text, String errorMessage) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException | NullPointerException e) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private long parseLong(String text, String errorMessage) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException | NullPointerException e) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private String contentType(String fileName) {
        if (fileName.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }

        if (fileName.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }

        return "text/javascript; charset=utf-8";
    }

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        sendBytes(exchange, status, "application/json; charset=utf-8",
                Json.write(body).getBytes(StandardCharsets.UTF_8));
    }

    private void sendBytes(HttpExchange exchange, int status, String type, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
