package io.github.fabianaschwanden.app.application.service;

import io.github.fabianaschwanden.app.domain.model.Note;
import io.github.fabianaschwanden.app.domain.port.in.CreateNote;
import io.github.fabianaschwanden.app.domain.port.in.FindNotes;
import io.github.fabianaschwanden.app.domain.port.out.NoteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Application Service — orchestriert den Use Case und hält die Transaktionsgrenze, keine Geschäftsregeln. */
@ApplicationScoped
public class NoteService implements CreateNote, FindNotes {

    private final NoteRepository noteRepository;

    public NoteService(NoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    @Override
    @Transactional
    public Note create(String title, String body) {
        return noteRepository.save(new Note(UUID.randomUUID(), title, body, Instant.now()));
    }

    @Override
    public List<Note> all() {
        return noteRepository.all();
    }

    @Override
    public Optional<Note> byId(UUID id) {
        return noteRepository.byId(id);
    }
}
