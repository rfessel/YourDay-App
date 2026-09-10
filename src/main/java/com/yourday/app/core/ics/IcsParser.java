package com.yourday.app.core.ics;

import com.yourday.app.core.model.EventItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser de texto .ics — port do parseEvent()/icsBlocks()/allEvents() do
 * calendar.js. Expande recorrências na janela pedida.
 */
public final class IcsParser {

    private IcsParser() {
    }

    /** Eventos de um texto .ics que caem em [fromMs, toMs). */
    public static List<EventItem> allEvents(String text, String source, long fromMs, long toMs) {
        List<EventItem> out = new ArrayList<>();
        List<Map<String, String>> blocks = icsBlocks(text, "VEVENT");
        for (Map<String, String> block : blocks) {
            EventItem master = parseRaw(block);
            master.calendar = source;
            out.addAll(Recurrence.expandOccurrences(master, fromMs, toMs));
        }
        out.sort((a, b) -> Long.compare(a.start, b.start));
        return out;
    }

    /** Decodifica apenas o VEVENT mestre, sem expandir recorrência. */
    public static EventItem parseRaw(Map<String, String> ev) {
        String dtParams = String.valueOf(ev.getOrDefault("DTSTART_PARAMS", "")).toUpperCase();
        boolean allDay = dtParams.contains("VALUE=DATE");
        long start = Ics.parseIcsDate(ev.get("DTSTART"));

        long end;
        if (ev.get("DTEND") != null) {
            end = Ics.parseIcsDate(ev.get("DTEND"));
        } else if (ev.get("DURATION") != null) {
            end = start + Recurrence.parseDurationMs(ev.get("DURATION"));
        } else {
            end = start + (allDay ? Ics.DAY_MS : 3_600_000L);
        }

        EventItem item = new EventItem();
        item.start = start;
        item.end = end;
        item.allDay = allDay;
        item.title = Ics.unescapeIcs(ev.getOrDefault("SUMMARY", "(sem título)"));
        item.description = Ics.unescapeIcs(ev.getOrDefault("DESCRIPTION", ""));
        item.location = Ics.unescapeIcs(ev.getOrDefault("LOCATION", ""));
        item.rrule = ev.getOrDefault("RRULE", "");
        item.id = ev.getOrDefault("UID", "");
        return item;
    }

    /** Blocos BEGIN:tagname ... END:tagname com propriedades (params preservados). */
    public static List<Map<String, String>> icsBlocks(String text, String tag) {
        String unfolded = Ics.unfold(text);
        List<Map<String, String>> out = new ArrayList<>();
        int idx = 0;
        String begin = "BEGIN:" + tag;
        String end = "END:" + tag;
        while (true) {
            int b = unfolded.indexOf(begin, idx);
            if (b < 0) {
                break;
            }
            int e = unfolded.indexOf(end, b);
            if (e < 0) {
                break;
            }
            String body = unfolded.substring(b + begin.length(), e);
            out.add(parseBlockLines(body));
            idx = e + end.length();
        }
        return out;
    }

    private static Map<String, String> parseBlockLines(String body) {
        Map<String, String> prop = new HashMap<>();
        for (String line : body.split("\n")) {
            String ln = line.trim();
            if (ln.isEmpty()) {
                continue;
            }
            int eq = ln.indexOf(':');
            if (eq < 0) {
                continue;
            }
            String namePart = ln.substring(0, eq);
            String name = namePart.split(";")[0].toUpperCase();
            int semi = namePart.indexOf(';');
            String params = semi >= 0 ? namePart.substring(semi + 1) : "";
            String val = ln.substring(eq + 1);
            if (!prop.containsKey(name)) {
                prop.put(name, val);
                prop.put(name + "_PARAMS", params);
            } else if (name.equals("RRULE") || name.equals("EXDATE") || name.equals("RDATE")) {
                prop.put(name, prop.get(name) + "\n" + val);
            }
        }
        return prop;
    }

    /** Heurística simples: o texto parece ser .ics? */
    public static boolean looksLikeIcs(String text) {
        String t = String.valueOf(text);
        return t.contains("BEGIN:VCALENDAR") || t.contains("BEGIN:VEVENT")
                || t.contains("BEGIN:VTODO");
    }
}