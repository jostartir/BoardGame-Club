import adapter.CsvGameCatalogAdapter;
import adapter.CsvReader;
import adapter.GameCatalogSource;
import app.MeetingController;
import model.BoardGame;
import storage.JsonMeetingRepository;
import web.WebServer;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

public class Main {

    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        Path catalogFile = Path.of(args.length > 0 ? args[0] : "games.csv");
        Path dataFile = Path.of(args.length > 1 ? args[1] : "data/meetings.json");

        GameCatalogSource source = new CsvGameCatalogAdapter(new CsvReader());
        List<BoardGame> games;

        try {
            games = source.load(catalogFile);
        } catch (RuntimeException e) {
            System.err.println("Cannot load game catalog from " + catalogFile + ": " + e.getMessage());
            return;
        }

        System.out.println("Loaded " + games.size() + " games from " + catalogFile);

        MeetingController controller;

        try {
            controller = new MeetingController(games, new JsonMeetingRepository(dataFile));
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.err.println("Fix or move " + dataFile + " and start the application again.");
            return;
        }

        System.out.println("Loaded " + controller.getMeetings().size() + " saved meetings from " + dataFile);

        WebServer server = new WebServer(controller);

        try {
            int port = server.start(DEFAULT_PORT);
            String url = "http://localhost:" + port + "/";
            System.out.println("Board Game Club is running at " + url);
            System.out.println("Press Ctrl+C to stop.");
            openBrowser(url);
        } catch (IOException e) {
            System.err.println("Cannot start the web server on port " + DEFAULT_PORT
                    + " (is it already in use?): " + e.getMessage());
        }
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception ignored) {
        }
    }
}
