package model;

public class Participant {

    private final Long id;
    private final String name;

    public Participant(Long id, String name) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "ID must be a positive number"
            );
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Participant name must not be blank"
            );
        }

        this.id = id;
        this.name = name.trim();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
