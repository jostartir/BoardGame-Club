package adapter;

import model.BoardGame;

import java.nio.file.Path;
import java.util.List;

public interface GameCatalogSource {

    List<BoardGame> load(Path file);
}
