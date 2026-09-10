package com.yourday.app.core.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.yourday.app.core.model.AppConfig;
import com.yourday.app.core.model.EventItem;
import com.yourday.app.core.model.Lista;
import com.yourday.app.core.model.TodoItem;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistência local em JSON com escrita atômica (tmp + rename).
 * Pasta padrão: ~/.yourday  (override via -Dyourday.data=... para testes).
 */
public class AppData {

    private static final String EVENTS_JSON = "events.json";
    private static final String TODOS_JSON = "todos.json";
    private static final String LISTS_JSON = "lists.json";
    private static final String CONFIG_JSON = "config.json";
    private static final String BACKUP_JSON = "yourday-backup.json";

    private final Path dir;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Object lock = new Object();

    public AppData(Path dir) {
        this.dir = dir;
    }

    public static Path defaultDir() {
        String override = System.getProperty("yourday.data");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), ".yourday");
    }

    public Path dir() {
        return dir;
    }

    public static AppData openDefault() {
        AppData d = new AppData(defaultDir());
        d.ensure();
        return d;
    }

    private void ensure() {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível criar " + dir, e);
        }
    }

    public List<EventItem> loadEvents() {
        synchronized (lock) {
            return readList(EVENTS_JSON, EventItem[].class);
        }
    }

    public void saveEvents(List<EventItem> events) {
        synchronized (lock) {
            writeAtomic(EVENTS_JSON, events);
        }
    }

    public List<TodoItem> loadTodos() {
        synchronized (lock) {
            return readList(TODOS_JSON, TodoItem[].class);
        }
    }

    public void saveTodos(List<TodoItem> todos) {
        synchronized (lock) {
            writeAtomic(TODOS_JSON, todos);
        }
    }

    public List<Lista> loadLists() {
        synchronized (lock) {
            return readList(LISTS_JSON, Lista[].class);
        }
    }

    public void saveLists(List<Lista> lists) {
        synchronized (lock) {
            writeAtomic(LISTS_JSON, lists);
        }
    }

    public AppConfig loadConfig() {
        synchronized (lock) {
            Path p = dir.resolve(CONFIG_JSON);
            if (Files.exists(p)) {
                try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                    AppConfig c = gson.fromJson(r, AppConfig.class);
                    if (c != null) {
                        return c;
                    }
                } catch (IOException ignore) {
                }
            }
            return new AppConfig();
        }
    }

    public void saveConfig(AppConfig config) {
        synchronized (lock) {
            writeAtomic(CONFIG_JSON, config);
        }
    }

    /** Exporta tudo para um único arquivo JSON (backup). */
    public void backup(Path out) throws IOException {
        synchronized (lock) {
            Backup bundle = new Backup();
            bundle.events = loadEventsNoLock();
            bundle.todos = loadTodosNoLock();
            bundle.lists = loadListsNoLock();
            bundle.config = loadConfigNoLock();
            Path tmp = out.resolveSibling(out.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                gson.toJson(bundle, w);
            }
            Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Restaura tudo a partir de um backup. */
    public void restore(Path in) throws IOException {
        synchronized (lock) {
            Backup bundle;
            try (Reader r = Files.newBufferedReader(in, StandardCharsets.UTF_8)) {
                bundle = gson.fromJson(r, Backup.class);
            }
            if (bundle == null) {
                throw new IOException("Arquivo inválido: " + in);
            }
            if (bundle.events != null) {
                saveEventsNoLock(bundle.events);
            }
            if (bundle.todos != null) {
                saveTodosNoLock(bundle.todos);
            }
            if (bundle.lists != null) {
                saveListsNoLock(bundle.lists);
            }
            if (bundle.config != null) {
                saveConfigNoLock(bundle.config);
            }
        }
    }

    private <T> List<T> readList(String name, Class<T[]> clazz) {
        Path p = dir.resolve(name);
        if (!Files.exists(p)) {
            return new ArrayList<>();
        }
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            T[] arr = gson.fromJson(r, clazz);
            List<T> list = new ArrayList<>();
            if (arr != null) {
                for (T t : arr) {
                    list.add(t);
                }
            }
            return list;
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private void writeAtomic(String name, Object value) {
        Path target = dir.resolve(name);
        Path tmp = dir.resolve(name + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            gson.toJson(value, w);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao salvar " + name, e);
        }
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao mover " + name, e);
        }
    }

    // helpers sem lock (para uso dentro do backup)
    private List<EventItem> loadEventsNoLock() {
        return readList(EVENTS_JSON, EventItem[].class);
    }

    private List<TodoItem> loadTodosNoLock() {
        return readList(TODOS_JSON, TodoItem[].class);
    }

    private List<Lista> loadListsNoLock() {
        return readList(LISTS_JSON, Lista[].class);
    }

    private AppConfig loadConfigNoLock() {
        Path p = dir.resolve(CONFIG_JSON);
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                AppConfig c = gson.fromJson(r, AppConfig.class);
                if (c != null) {
                    return c;
                }
            } catch (IOException ignore) {
            }
        }
        return new AppConfig();
    }

    private void saveEventsNoLock(List<EventItem> events) {
        writeAtomic(EVENTS_JSON, events);
    }

    private void saveTodosNoLock(List<TodoItem> todos) {
        writeAtomic(TODOS_JSON, todos);
    }

    private void saveListsNoLock(List<Lista> lists) {
        writeAtomic(LISTS_JSON, lists);
    }

    private void saveConfigNoLock(AppConfig config) {
        writeAtomic(CONFIG_JSON, config);
    }

    private static class Backup {
        List<EventItem> events;
        List<TodoItem> todos;
        List<Lista> lists;
        AppConfig config;
    }
}