package com.yourday.app.core.ics;

import com.yourday.app.core.model.EventItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expansão de recorrências — port fiel do expandOccurrences() do calendar.js
 * (FREQ=DAILY/WEEKLY/MONTHLY/YEARLY, INTERVAL, UNTIL, BYDAY/BYMONTHDAY/BYMONTH).
 */
public final class Recurrence {

    public static final long DAY_MS = 86_400_000L;
    private static final int GUARD = 1024;

    private static final Map<String, Integer> WD_ISO = Map.of(
            "MO", 1, "TU", 2, "WE", 3, "TH", 4, "FR", 5, "SA", 6, "SU", 7);

    public record ByDay(int ord, int wd) {
    }

    private Recurrence() {
    }

    /** "1MO"/"-1FR" -> { ord, wd(iso) } ; "MO" -> ord=0 (todos). */
    static List<ByDay> parseByDayEntries(String rrule) {
        Matcher m = Pattern.compile("BYDAY=([A-Z0-9\\-,]+)").matcher(String.valueOf(rrule));
        if (!m.find()) {
            return null;
        }
        List<ByDay> out = new ArrayList<>();
        for (String part : m.group(1).split(",")) {
            Matcher rm = Pattern.compile("^(-?\\d+)?([A-Z]{2})$").matcher(part.trim());
            if (!rm.matches()) {
                continue;
            }
            Integer wd = WD_ISO.get(rm.group(2).toUpperCase());
            if (wd == null) {
                continue;
            }
            int ord = rm.group(1) != null
                    ? Math.max(-53, Math.min(53, Integer.parseInt(rm.group(1)))) : 0;
            out.add(new ByDay(ord, wd));
        }
        return out.isEmpty() ? null : out;
    }

    /** BYMONTHDAY: lista de dias (positivos ou negativos, -1 = último). */
    static List<Integer> parseByMonthDay(String rrule) {
        Matcher m = Pattern.compile("BYMONTHDAY=([^;\\s]+)").matcher(String.valueOf(rrule));
        if (!m.find()) {
            return null;
        }
        List<Integer> out = new ArrayList<>();
        for (String p : m.group(1).split(",")) {
            int v = Integer.parseInt(p);
            if (v != 0 && Math.abs(v) <= 31) {
                out.add(v);
            }
        }
        return out.isEmpty() ? null : out;
    }

    /** BYMONTH: lista de meses 1..12. */
    static List<Integer> parseByMonth(String rrule) {
        Matcher m = Pattern.compile("BYMONTH=([^;\\s]+)").matcher(String.valueOf(rrule));
        if (!m.find()) {
            return null;
        }
        List<Integer> out = new ArrayList<>();
        for (String p : m.group(1).split(",")) {
            int v = Integer.parseInt(p);
            if (v >= 1 && v <= 12) {
                out.add(v);
            }
        }
        return out.isEmpty() ? null : out;
    }

    /** Dia do mês do n-ésimo weekday (wd em ISO). n>0 = 1º/2º/..., n<0 = último. 0 = não existe. */
    static int nthWeekdayIndex(int y, int m0, int wd, int n) {
        int dim = Ics.daysInMonth(y, m0);
        int lastIso = Ics.isoDay(Ics.localEpoch(y, m0, dim, 0, 0, 0));
        if (n > 0) {
            int firstIso = Ics.isoDay(Ics.localEpoch(y, m0, 1, 0, 0, 0));
            int day = 1 + ((wd - firstIso + 7) % 7) + (n - 1) * 7;
            return day <= dim ? day : 0;
        }
        int dayB = dim - ((lastIso - wd + 7) % 7) - (Math.abs(n) - 1) * 7;
        return dayB >= 1 ? dayB : 0;
    }

    /** Dias candidatos do mês para FREQ=MONTHLY/YEARLY (mesma lógica do original). */
    static List<Integer> monthCandidateDays(int y, int m0, List<Integer> byMonthDay,
                                            List<ByDay> byDay, int dtStartDay) {
        int dim = Ics.daysInMonth(y, m0);
        List<Integer> out = new ArrayList<>();
        if (byDay != null) {
            for (ByDay e : byDay) {
                if (e.ord() != 0) {
                    int day = nthWeekdayIndex(y, m0, e.wd(), e.ord());
                    if (day != 0) {
                        out.add(day);
                    }
                } else {
                    for (int d = 1; d <= dim; d++) {
                        int iso = ((Ics.dayOfWeekJs(Ics.localEpoch(y, m0, d, 0, 0, 0)) + 6) % 7) + 1;
                        if (iso == e.wd()) {
                            out.add(d);
                        }
                    }
                }
            }
        } else if (byMonthDay != null) {
            for (int v : byMonthDay) {
                int dn = v > 0 ? v : dim + 1 + v;
                out.add((dn >= 1 && dn <= dim) ? dn : 0);
            }
        } else {
            out.add(dtStartDay <= dim ? dtStartDay : 0);
        }
        return out;
    }

    /**
     * Expande as ocorrências de um evento na janela [targetStart, targetEnd).
     * Sem RRULE retorna a ocorrência única se ela cruzar a janela.
     */
    public static List<EventItem> expandOccurrences(EventItem ev, long targetStart, long targetEnd) {
        if (ev.start == 0) {
            return List.of();
        }
        long start = ev.start;
        long end = ev.end != 0 ? ev.end : start + (ev.allDay ? DAY_MS : 3_600_000L);
        long dur = end - start;

        List<EventItem> out = new ArrayList<>();
        String rrule = String.valueOf(ev.rrule == null ? "" : ev.rrule).toUpperCase();
        if (rrule.isEmpty()) {
            if (end > targetStart && start < targetEnd) {
                out.add(occurrence(ev, start, dur));
            }
            return out;
        }

        long until = 0;
        Matcher untilM = Pattern.compile("UNTIL=([^;\\s]+)").matcher(rrule);
        if (untilM.find()) {
            until = Ics.parseIcsDate(untilM.group(1));
        }
        int interval = 1;
        Matcher intM = Pattern.compile("INTERVAL=(\\d+)").matcher(rrule);
        if (intM.find()) {
            interval = Math.max(1, Integer.parseInt(intM.group(1)));
        }

        boolean isDaily = rrule.contains("FREQ=DAILY");
        boolean isWeekly = rrule.contains("FREQ=WEEKLY");
        boolean isMonthly = rrule.contains("FREQ=MONTHLY");
        boolean isYearly = rrule.contains("FREQ=YEARLY");
        if (!isDaily && !isWeekly && !isMonthly && !isYearly) {
            if (end > targetStart && start < targetEnd) {
                out.add(occurrence(ev, start, dur));
            }
            return out;
        }

        int tod = Ics.timeOfDayMs(start);
        int guard = 0;

        if (isDaily) {
            long step = (long) interval * DAY_MS;
            long cursor = start;
            if (start < targetStart && step > 0) {
                cursor = start + (long) Math.ceil((targetStart - start) / (double) step) * step;
            }
            while (cursor < targetEnd && guard < GUARD) {
                if (until != 0 && cursor > until + DAY_MS) {
                    break;
                }
                if (cursor + dur > targetStart) {
                    out.add(occurrence(ev, cursor, dur));
                }
                cursor += step;
                guard++;
            }
            return out;
        }

        if (isWeekly) {
            // BYDAY semanal usa apenas os dias (ordinal é ignorado, como no JS).
            List<Integer> byday = new ArrayList<>();
            List<ByDay> bd = parseByDayEntries(rrule);
            if (bd != null) {
                for (ByDay b : bd) {
                    if (!byday.contains(b.wd())) {
                        byday.add(b.wd());
                    }
                }
            }
            if (byday.isEmpty()) {
                byday.add(Ics.isoDay(start));
            }
            byday.sort(Integer::compareTo);

            int monoOffset = (Ics.dayOfWeekJs(start) + 6) % 7;
            long monday0 = Ics.plusDays(start, -monoOffset);
            long wstep = (long) interval * 7 * DAY_MS;
            long wstart = monday0;
            if (wstart < targetStart && wstep > 0) {
                wstart = monday0 + (long) Math.floor((targetStart - monday0) / (double) wstep) * wstep;
            }
            while (wstart < targetEnd && guard < GUARD) {
                for (int wi : byday) {
                    long occStart = wstart + (wi - 1) * DAY_MS + tod;
                    long occEnd = occStart + dur;
                    if (until != 0 && occStart > until + DAY_MS) {
                        continue;
                    }
                    if (occStart >= targetEnd) {
                        continue;
                    }
                    if (occEnd > targetStart) {
                        out.add(occurrence(ev, occStart, dur));
                    }
                }
                wstart += wstep;
                guard++;
            }
            out.sort(Comparator.comparingLong(evv -> evv.start));
            return out;
        }

        List<Integer> byMonthDay = parseByMonthDay(rrule);
        List<ByDay> byDay = parseByDayEntries(rrule);

        if (isMonthly) {
            expandMonthly(start, tod, dur, ev, until, interval, byMonthDay, byDay, targetStart, targetEnd, out);
            out.sort(Comparator.comparingLong(evv -> evv.start));
            return out;
        }

        expandYearly(start, tod, dur, ev, until, interval, byMonthDay, byDay, targetStart, targetEnd, out);
        out.sort(Comparator.comparingLong(evv -> evv.start));
        return out;
    }

    private static void expandMonthly(long start, int tod, long dur, EventItem ev, long until, int interval,
                                      List<Integer> byMonthDay, List<ByDay> byDay,
                                      long targetStart, long targetEnd, List<EventItem> out) {
        int d0Day = java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(start), Ics.ZONE).getDayOfMonth();
        int startIdx = startIdx(start);
        int win0 = startIdx(targetStart);
        int win1 = startIdx(targetEnd);
        int step = Math.max(1, interval);
        int idx = startIdx + Math.floorDiv(win0 - startIdx, step) * step;
        int guard = 0;
        while (idx <= win1 && guard < GUARD) {
            int y = Math.floorDiv(idx, 12);
            int m = Math.floorMod(idx, 12);
            List<Integer> days = monthCandidateDays(y, m, byMonthDay, byDay, d0Day);
            for (int day : days) {
                if (day == 0) {
                    continue;
                }
                long occStart = Ics.localEpoch(y, m, day, 0, 0, 0) + tod;
                if (until != 0 && occStart > until + DAY_MS) {
                    continue;
                }
                if (occStart >= targetEnd) {
                    continue;
                }
                if (occStart + dur > targetStart) {
                    out.add(occurrence(ev, occStart, dur));
                }
            }
            idx += step;
            guard++;
        }
    }

    private static void expandYearly(long start, int tod, long dur, EventItem ev, long until, int interval,
                                     List<Integer> byMonthDay, List<ByDay> byDay,
                                     long targetStart, long targetEnd, List<EventItem> out) {
        int d0Day = java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(start), Ics.ZONE).getDayOfMonth();
        int d0Month = java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(start), Ics.ZONE).getMonthValue();
        int startYr = java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(start), Ics.ZONE).getYear();
        int win0 = java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(targetStart), Ics.ZONE).getYear();
        int win1 = java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(targetEnd), Ics.ZONE).getYear();
        List<Integer> months = parseByMonth(String.valueOf(ev.rrule));
        if (months == null) {
            months = new ArrayList<>(List.of(d0Month));
        }
        int stepYr = Math.max(1, interval);
        int yr = startYr + Math.floorDiv(win0 - startYr, stepYr) * stepYr;
        int guard = 0;
        while (yr <= win1 && guard < GUARD) {
            for (int mm : months) {
                int m = mm - 1;
                List<Integer> days = monthCandidateDays(yr, m, byMonthDay, byDay, d0Day);
                for (int day : days) {
                    if (day == 0) {
                        continue;
                    }
                    long occStart = Ics.localEpoch(yr, m, day, 0, 0, 0) + tod;
                    if (until != 0 && occStart > until + DAY_MS) {
                        continue;
                    }
                    if (occStart >= targetEnd) {
                        continue;
                    }
                    if (occStart + dur > targetStart) {
                        out.add(occurrence(ev, occStart, dur));
                    }
                }
            }
            yr += stepYr;
            guard++;
        }
    }

    private static int startIdx(long ms) {
        java.time.LocalDate d = java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(ms), Ics.ZONE);
        return d.getYear() * 12 + (d.getMonthValue() - 1);
    }

    private static EventItem occurrence(EventItem ev, long cursor, long dur) {
        EventItem o = new EventItem();
        o.id = ev.id;
        o.calendar = ev.calendar;
        o.title = ev.title;
        o.description = ev.description;
        o.location = ev.location;
        o.start = cursor;
        o.end = cursor + dur;
        o.allDay = ev.allDay;
        o.rrule = ""; // ocorrência concreta não repete
        return o;
    }

    static long parseDurationMs(String duration) {
        Matcher dm = Pattern.compile("PT?(\\d+D)?(\\d+H)?(\\d+M)?", Pattern.CASE_INSENSITIVE)
                .matcher(duration == null ? "" : duration);
        long ms = 0;
        if (dm.find()) {
            if (dm.group(1) != null) {
                ms += Integer.parseInt(dm.group(1)) * DAY_MS;
            }
            if (dm.group(2) != null) {
                ms += Integer.parseInt(dm.group(2)) * 3_600_000L;
            }
            if (dm.group(3) != null) {
                ms += Integer.parseInt(dm.group(3)) * 60_000L;
            }
        }
        return ms;
    }

    /** Mapa reutilizável de BYDAY para o parser (harmoniza com WD_ISO). */
    static Map<String, Integer> dayNames() {
        return new HashMap<>(WD_ISO);
    }
}