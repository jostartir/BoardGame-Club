package adapter;

import java.util.List;

public record CsvRecord(int lineNumber, List<String> fields) {
}
