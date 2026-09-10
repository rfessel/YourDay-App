package com.yourday.app.core.services;

import com.yourday.app.core.ics.Ics;
import com.yourday.app.core.ics.Recurrence;
import com.yourday.app.core.model.AppConfig;
import com.yourday.app.core.model.EventItem;
import com.yourday.app.core.model.Lista;
import com.yourday.app.core.model.ListaItem;
import com.yourday.app.core.model.TodoItem;
import com.yourday.app.core.storage.AppData;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fachada sobre AppData + Recurrence: CRUD de eventos/tarefas/config e
 * consultas comuns (eventos do dia, próximos eventos, vencimentos de hoje).
 */
public class AgendaService {

    private final AppData data;
    private final AppConfig config;

    public AgendaService(AppData data) {
        this.data = data;
        this.config = data.loadConfig();
    }

    public AppData data() {
        return data;
    }

    public AppConfig config() {
        return config;
    }

    public synchronized void saveConfig() {
        data.saveConfig(config);
    }

    // ---------------- Eventos ----------------

    public synchronized List<EventItem> events() {
        return data.loadEvents();
    }

    /** Ocorrências (com recorrência expandida) na janela [from, to). */
    public synchronized List<EventItem> occurrences(long from, long to) {
        List<EventItem> out = new ArrayList<>();
        for (EventItem master : data.loadEvents()) {
            out.addAll(Recurrence.expandOccurrences(master, from, to));
        }
        out.sort((a, b) -> Long.compare(a.start, b.start));
        return out;
    }

    public synchronized List<EventItem> eventsForDay(LocalDate day) {
        long start = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long end = start + Ics.DAY_MS;
        return occurrences(start, end);
    }

    /** Próximos eventos (que ainda não terminaram), a partir de agora. */
    public synchronized List<EventItem> nextEvents(int max) {
        long now = System.currentTimeMillis();
        return occurrences(now - 1, now + 90L * Ics.DAY_MS).stream()
                .filter(e -> e.end > now)
                .limit(max)
                .toList();
    }

    public synchronized EventItem addEvent(EventItem e) {
        if (e.id == null || e.id.isEmpty()) {
            e.id = UUID.randomUUID().toString();
        }
        List<EventItem> list = new ArrayList<>(data.loadEvents());
        list.add(e);
        data.saveEvents(list);
        return e;
    }

    public synchronized void updateEvent(EventItem e) {
        List<EventItem> list = data.loadEvents();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id != null && list.get(i).id.equals(e.id)) {
                list.set(i, e);
                data.saveEvents(list);
                return;
            }
        }
        addEvent(e);
    }

    public synchronized void deleteEvent(String id) {
        data.saveEvents(data.loadEvents().stream()
                .filter(e -> !id.equals(e.id))
                .toList());
    }

    // ---------------- Tarefas ----------------

    public synchronized List<TodoItem> todos() {
        return data.loadTodos();
    }

    public synchronized void saveTodo(TodoItem t) {
        List<TodoItem> list = new ArrayList<>(data.loadTodos());
        int idx = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(t.id)) {
                idx = i;
                break;
            }
        }
        if (idx >= 0) {
            list.set(idx, t);
        } else {
            list.add(t);
        }
        data.saveTodos(list);
    }

    public synchronized void addTodo(String title, long dueDate) {
        TodoItem t = TodoItem.of(title);
        t.dueDate = dueDate;
        data.saveTodos(new ArrayList<>(data.loadTodos()) {{ add(t); }});
    }

    public synchronized void removeTodo(String id) {
        data.saveTodos(data.loadTodos().stream()
                .filter(t -> !t.id.equals(id))
                .toList());
    }

    // ---------------- Listas ----------------

    public synchronized List<Lista> lists() {
        return data.loadLists();
    }

    public synchronized void addList(String name) {
        List<Lista> lists = new ArrayList<>(lists());
        lists.add(Lista.of(name));
        data.saveLists(lists);
    }

    public synchronized void removeList(String id) {
        data.saveLists(lists().stream()
                .filter(l -> !l.id.equals(id))
                .toList());
    }

    public synchronized void setListDone(String id, boolean done) {
        data.saveLists(mapList(id, l -> l.done = done));
    }

    public synchronized void addListItem(String listId, String text) {
        data.saveLists(mapList(listId, l -> l.items.add(ListaItem.of(text))));
    }

    public synchronized void removeListItem(String listId, String itemId) {
        data.saveLists(mapList(listId,
                l -> l.items.removeIf(i -> i.id.equals(itemId))));
    }

    public synchronized void toggleListItem(String listId, String itemId) {
        data.saveLists(mapList(listId, l -> l.items.stream()
                .filter(i -> i.id.equals(itemId))
                .forEach(i -> i.done = !i.done)));
    }

    /** Aplica um efeito a uma lista e persiste a lista completa. */
    private List<Lista> mapList(String id, java.util.function.Consumer<Lista> effect) {
        List<Lista> lists = new ArrayList<>(lists());
        for (Lista l : lists) {
            if (l.id.equals(id)) {
                effect.accept(l);
                break;
            }
        }
        data.saveLists(lists);
        return lists;
    }

    // ---------------- helpers ----------------

    public static LocalDate today() {
        return LocalDate.now();
    }

    /** Fim do dia (23:59:59) de uma data para "hoje/amanhã". */
    public static long endOfDay(LocalDate d) {
        return d.atTime(LocalTime.of(23, 59, 59)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static long startOfDay(LocalDate d) {
        return d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static String weekdayName(LocalDate d) {
        return d.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL,
                java.util.Locale.getDefault());
    }

    public static DayOfWeek dayOfWeek(LocalDate d) {
        return d.getDayOfWeek();
    }

    public static LocalDateTime toLocal(long ms) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault());
    }

    @SuppressWarnings("unused")
    public static String hm(long ms) {
        return toLocal(ms).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }
}