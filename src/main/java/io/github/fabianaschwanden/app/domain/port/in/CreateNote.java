package io.github.fabianaschwanden.app.domain.port.in;

import io.github.fabianaschwanden.app.domain.model.Note;

/** Driving Port — Use-Case-Interface. */
public interface CreateNote {

    Note create(String title, String body);
}
