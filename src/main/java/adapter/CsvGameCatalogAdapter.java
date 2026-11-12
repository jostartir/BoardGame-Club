package adapter;

import model.BoardGame;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CsvGameCatalogAdapter implements GameCatalogSource {

    private static final int COLUMN_COUNT = 7;

    private final CsvReader reader;

    public CsvGameCatalogAdapter(CsvReader reader) {
        if (reader == null) {
            throw new IllegalArgumentException("CSV reader must not be null");
        }

        this.reader = reader;
    }

    @Override
    public List<BoardGame> load(Path file) {
        if (file == null) {
            throw new IllegalArgumentException("File path must not be null");
        }

        List<CsvRecord> records;

        try {
            records = reader.read(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read game catalog: " + file, e);
        }

        List<BoardGame> games = new ArrayList<>();

        for (int i = 0; i < records.size(); i++) {
            CsvRecord record = records.get(i);

            if (i == 0 && isHeader(record)) {
                continue;
            }

            games.add(toBoardGame(record));
        }

        return games;
    }

    private boolean isHeader(CsvRecord record) {
        return record.fields().get(0).equalsIgnoreCase("id");
    }

    private BoardGame toBoardGame(CsvRecord record) {
        List<String> f = record.fields();

        if (f.size() != COLUMN_COUNT) {
            throw new IllegalArgumentException(
                    "Line " + record.lineNumber() + ": expected "
                            + COLUMN_COUNT + " columns but found " + f.size()
            );
        }

        try {
            return new BoardGame(
                    Long.parseLong(f.get(0)),
                    f.get(1),
                    Integer.parseInt(f.get(2)),
                    Integer.parseInt(f.get(3)),
                    Integer.parseInt(f.get(4)),
                    Integer.parseInt(f.get(5)),
                    f.get(6)
            );
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Line " + record.lineNumber() + ": a numeric column contains text", e
            );
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Line " + record.lineNumber() + ": " + e.getMessage(), e
            );
        }
    }
}
