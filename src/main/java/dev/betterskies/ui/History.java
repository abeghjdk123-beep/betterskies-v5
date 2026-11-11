package dev.betterskies.ui;

import dev.betterskies.config.Profile;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Undo/redo of whole-sky snapshots. A drag or a color-picker session is one step:
 * call {@link #begin} when it starts and {@link #end} when it finishes.
 */
public final class History {
    private static final int MAX = 120;
    private final Deque<Profile> undo = new ArrayDeque<>();
    private final Deque<Profile> redo = new ArrayDeque<>();
    private Profile pending;

    /** Remembers the state before an edit starts (no-op if one is already open). */
    public void begin(Profile current) {
        if (pending == null) {
            pending = current.copy();
        }
    }

    /** Finishes an edit; records it if anything actually changed. */
    public void end(Profile current) {
        if (pending == null) {
            return;
        }
        if (!pending.equals(current)) {
            undo.push(pending);
            while (undo.size() > MAX) {
                undo.removeLast();
            }
            redo.clear();
        }
        pending = null;
    }

    public boolean editing() {
        return pending != null;
    }

    /** A one-shot change. */
    public void record(Profile before) {
        undo.push(before);
        while (undo.size() > MAX) {
            undo.removeLast();
        }
        redo.clear();
    }

    public Profile undo(Profile current) {
        if (undo.isEmpty()) {
            return null;
        }
        redo.push(current.copy());
        return undo.pop();
    }

    public Profile redo(Profile current) {
        if (redo.isEmpty()) {
            return null;
        }
        undo.push(current.copy());
        return redo.pop();
    }

    public int undoCount() {
        return undo.size();
    }

    public int redoCount() {
        return redo.size();
    }
}
