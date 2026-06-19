package io.github.fabianaschwanden.app.domain.port.in;

import io.github.fabianaschwanden.app.domain.model.Note;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Driving Port — Use-Case-Interface. */
public interface FindNotes {

    List<Note> all();

    Optional<Note> byId(UUID id);
}
