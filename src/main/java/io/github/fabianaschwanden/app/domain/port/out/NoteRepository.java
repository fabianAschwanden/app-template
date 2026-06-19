package io.github.fabianaschwanden.app.domain.port.out;

import io.github.fabianaschwanden.app.domain.model.Note;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Driven Port — pro Aggregate Root; nimmt und liefert Domänen-Modelle, nie JPA-Entities. */
public interface NoteRepository {

    Note save(Note note);

    List<Note> all();

    Optional<Note> byId(UUID id);
}
