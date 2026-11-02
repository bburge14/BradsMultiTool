package com.snuffys.multitool;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

/** The Planner's NOTES & TO-DO: a to-do list and a free-text notes box, saved with your RuneScape profile. */
@Singleton
class PlannerNotes {

    private static final String TODO_KEY = "planner.todo";
    private static final String NOTES_KEY = "planner.notes";

    /** A to-do line. */
    static final class Todo {
        String text;
        boolean done;
    }

    private static final class Saved {
        List<Todo> todos = new ArrayList<>();
    }

    private final ConfigManager configManager;
    private final PlannerView view;
    private final Gson gson;

    @Inject
    PlannerNotes(ConfigManager configManager, PlannerView view, Gson gson) {
        this.configManager = configManager;
        this.view = view;
        this.gson = gson;
    }

    synchronized List<Todo> todos() {
        return saved().todos;
    }

    synchronized void add(String text) {
        Saved saved = saved();
        Todo todo = new Todo();
        todo.text = text;
        saved.todos.add(todo);
        save(saved);
    }

    synchronized void setDone(int index, boolean done) {
        Saved saved = saved();
        if (index >= 0 && index < saved.todos.size()) {
            saved.todos.get(index).done = done;
            save(saved);
        }
    }

    synchronized void edit(int index, String text) {
        Saved saved = saved();
        if (index >= 0 && index < saved.todos.size()) {
            saved.todos.get(index).text = text;
            save(saved);
        }
    }

    synchronized void delete(int index) {
        Saved saved = saved();
        if (index >= 0 && index < saved.todos.size()) {
            saved.todos.remove(index);
            save(saved);
        }
    }

    /** Moves a line up (-1) or down (+1). */
    synchronized void move(int index, int by) {
        Saved saved = saved();
        int to = index + by;
        if (index >= 0 && index < saved.todos.size() && to >= 0 && to < saved.todos.size()) {
            saved.todos.add(to, saved.todos.remove(index));
            save(saved);
        }
    }

    /** Takes off every ticked line. */
    synchronized void clearDone() {
        Saved saved = saved();
        if (saved.todos.removeIf(todo -> todo.done)) {
            save(saved);
        }
    }

    String notes() {
        String text = configManager.getRSProfileKey() == null ? null
            : configManager.getRSProfileConfiguration(PlannerChores.GROUP, NOTES_KEY);
        return text == null ? "" : text;
    }

    void setNotes(String text) {
        if (configManager.getRSProfileKey() == null || text.equals(notes())) {
            return;
        }
        if (text.isEmpty()) {
            configManager.unsetRSProfileConfiguration(PlannerChores.GROUP, NOTES_KEY);
        } else {
            configManager.setRSProfileConfiguration(PlannerChores.GROUP, NOTES_KEY, text);
        }
    }

    private Saved saved() {
        String json = configManager.getRSProfileKey() == null ? null : configManager.getRSProfileConfiguration(PlannerChores.GROUP, TODO_KEY);
        try {
            Saved saved = json == null ? null : gson.fromJson(json, Saved.class);
            if (saved != null && saved.todos != null) {
                saved.todos.removeIf(todo -> todo == null || todo.text == null);
                return saved;
            }
        } catch (JsonSyntaxException e) {
            // Unreadable: start again
        }
        return new Saved();
    }

    private void save(Saved saved) {
        if (configManager.getRSProfileKey() != null) {
            configManager.setRSProfileConfiguration(PlannerChores.GROUP, TODO_KEY, gson.toJson(saved));
        }
        view.update();
    }
}
