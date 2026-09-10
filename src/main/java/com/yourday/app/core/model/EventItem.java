package com.yourday.app.core.model;

/** Compromisso (evento). Pode ter rrule em ICS para recorrência. */
public class EventItem {

    public String id;
    public String calendar = "local";
    public String title = "";
    public String description = "";
    public String location = "";
    public long start;
    public long end;
    public boolean allDay;
    /** RRULE em formato ICS (ex.: "FREQ=WEEKLY;BYDAY=MO,WE,FR"); vazio = sem recorrência. */
    public String rrule = "";

    public static EventItem single(long start, long end, String title, boolean allDay) {
        EventItem e = new EventItem();
        e.start = start;
        e.end = end;
        e.title = title;
        e.allDay = allDay;
        return e;
    }

    /** Duração em ms (usada para reproduzir ocorrências). */
    public long duration() {
        return Math.max(0, end - start);
    }
}