package adapter;

import model.BoardGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvGameCatalogAdapterTest {

    private static final String HEADER =
            "id,title,minPlayers,maxPlayers,durationMinutes,difficulty,genre\n";

    @TempDir
    Path tempDir;

    private final GameCatalogSource source = new CsvGameCatalogAdapter(new CsvReader());

    private Path write(String content) throws IOException {
        return Files.writeString(tempDir.resolve("games.csv"), content);
    }

    @Test
    void loadsGamesAndSkipsHeaderAndBlankLines() throws IOException {
        Path file = write(HEADER
                + "1,Carcassonne,2,5,45,2,Strategy\n"
                + "\n"
                + "2,Catan,3,4,90,3,Strategy\n");

        List<BoardGame> games = source.load(file);

        assertEquals(2, games.size());
        assertEquals("Carcassonne", games.get(0).getTitle());
        assertEquals(5, games.get(0).getMaxPlayers());
        assertEquals(90, games.get(1).getDurationMinutes());
    }

    @Test
    void loadsFileWithoutHeader() throws IOException {
        Path file = write("1,Uno,2,10,20,1,Card\n");

        assertEquals(1, source.load(file).size());
    }

    @Test
    void supportsQuotedTitleWithComma() throws IOException {
        Path file = write(HEADER + "1,\"Ticket to Ride, Europe\",2,5,60,2,Family\n");

        assertEquals("Ticket to Ride, Europe", source.load(file).get(0).getTitle());
    }

    @Test
    void reportsLineNumberForWrongColumnCount() throws IOException {
        Path file = write(HEADER + "1,Carcassonne,2,5\n");

        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> source.load(file));

        assertTrue(e.getMessage().contains("Line 2"));
    }

    @Test
    void reportsLineNumberForNonNumericValue() throws IOException {
        Path file = write(HEADER + "1,Carcassonne,two,5,45,2,Strategy\n");

        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> source.load(file));

        assertTrue(e.getMessage().contains("Line 2"));
    }

    @Test
    void reportsLineNumberForInvalidGameData() throws IOException {
        Path file = write(HEADER + "1,Carcassonne,2,5,45,9,Strategy\n");

        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> source.load(file));

        assertTrue(e.getMessage().contains("Line 2"));
    }

    @Test
    void missingFileThrowsUncheckedIoException() {
        Path missing = tempDir.resolve("nope.csv");

        assertThrows(UncheckedIOException.class, () -> source.load(missing));
    }

    @Test
    void rejectsNullArguments() {
        assertThrows(IllegalArgumentException.class, () -> source.load(null));
        assertThrows(IllegalArgumentException.class, () -> new CsvGameCatalogAdapter(null));
    }
}
