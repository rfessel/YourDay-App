package com.yourday.app.core.ics;

import com.yourday.app.core.model.EventItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecurrenceTest {

    static long ms(int y, int m, int d, int h, int mi) {
        return Ics.localEpoch(y, m - 1, d, h, mi, 0);
    }

    static EventItem ev(String start, String rrule, long durMinutes) {
        EventItem e = new EventItem();
        e.start = Ics.parseIcsDate(start);
        e.end = e.start + durMinutes * 60_000L;
        e.rrule = rrule;
        e.title = "T";
        return e;
    }

    static String date(long ms) {
        return LocalDate.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()).toString();
    }

    @Test
    void parseIcsDateVariants() {
        assertTrue(Ics.parseIcsDate("") == 0);
        // data pura -> meia-noite local
        assertEquals(ms(2026, 9, 10, 0, 0), Ics.parseIcsDate("20260910"));
        // local
        assertEquals(ms(2026, 9, 10, 10, 30), Ics.parseIcsDate("20260910T103000"));
        // UTC
        assertEquals(java.time.LocalTime.of(10, 30),
                Instant.ofEpochMilli(Ics.parseIcsDate("20260910T103000Z"))
                        .atZone(ZoneOffset.UTC).toLocalTime());
        // com fuso -0300 -> 10:00 em UTC-3 equivale a 10:00 local
        assertEquals(ms(2026, 5, 31, 10, 0), Ics.parseIcsDate("20260531T100000-0300"));
    }

    @Test
    void dailyWindowSkipsToCursor() {
        EventItem e = ev("20260901T090000", "FREQ=DAILY", 60);
        // janela começando em 2026-09-10: primeira ocorrência deve ser 10/09 09:00
        List<EventItem> occ = Recurrence.expandOccurrences(e, ms(2026, 9, 10, 0, 0), ms(2026, 9, 13, 0, 0));
        assertEquals(3, occ.size());
        assertEquals(ms(2026, 9, 10, 9, 0), occ.get(0).start);
    }

    @Test
    void weeklyByDaySorted() {
        EventItem e = ev("20260907T080000", "FREQ=WEEKLY;BYDAY=MO,WE,FR", 30);
        List<EventItem> occ = Recurrence.expandOccurrences(e, ms(2026, 9, 7, 0, 0), ms(2026, 9, 14, 0, 0));
        // 07 (seg), 09 (qua), 11 (sex)
        assertEquals(List.of("2026-09-07", "2026-09-09", "2026-09-11"), occ.stream().map(o -> date(o.start)).toList());
    }

    @Test
    void monthlyFirstMonday() {
        // 1ª segunda-feira do mês a partir de set/2026 no intervalo jan/2027-mar/2027
        EventItem e = ev("20260907T120000", "FREQ=MONTHLY;BYDAY=1MO", 60);
        List<EventItem> occ = Recurrence.expandOccurrences(e, ms(2027, 1, 1, 0, 0), ms(2027, 4, 1, 0, 0));
        // jan: 4, fev: 1, mar: 1 de 2027
        assertEquals("2027-01-04", date(occ.get(0).start));
        assertEquals("2027-02-01", date(occ.get(1).start));
        assertEquals("2027-03-01", date(occ.get(2).start));
    }

    @Test
    void monthlyLastFriday() {
        EventItem e = ev("20260731T100000", "FREQ=MONTHLY;BYDAY=-1FR", 60);
        List<EventItem> occ = Recurrence.expandOccurrences(e, ms(2026, 7, 1, 0, 0), ms(2026, 10, 1, 0, 0));
        // jul: 31 (sex), ago: 28, set: 25
        assertEquals(List.of("2026-07-31", "2026-08-28", "2026-09-25"), occ.stream().map(o -> date(o.start)).toList());
    }

    @Test
    void monthlyByMonthDay() {
        EventItem e = ev("20260115T090000", "FREQ=MONTHLY;BYMONTHDAY=15", 60);
        List<EventItem> occ = Recurrence.expandOccurrences(e, ms(2026, 1, 15, 0, 0), ms(2026, 4, 1, 0, 0));
        assertEquals(List.of("2026-01-15", "2026-02-15", "2026-03-15"), occ.stream().map(o -> date(o.start)).toList());
    }

    @Test
    void yearlyByMonth() {
        EventItem e = ev("20250110T080000", "FREQ=YEARLY;BYMONTH=6", 60);
        List<EventItem> occ = Recurrence.expandOccurrences(e, ms(2026, 5, 1, 0, 0), ms(2027, 1, 1, 0, 0));
        assertEquals("2026-06-10", date(occ.get(0).start));
    }

    @Test
    void yearlyFirstFridayOfJune() {
        EventItem e = ev("20250502T080000", "FREQ=YEARLY;BYMONTH=6;BYDAY=1FR", 60);
        List<EventItem> occ = Recurrence.expandOccurrences(e, ms(2026, 1, 1, 0, 0), ms(2027, 1, 1, 0, 0));
        assertEquals("2026-06-05", date(occ.get(0).start));
    }

    @Test
    void untilStops() {
        EventItem e = ev("20260901T090000", "FREQ=DAILY;UNTIL=20260905T000000", 60);
        List<EventItem> occ = Recurrence.expandOccurrences(e, ms(2026, 9, 1, 0, 0), ms(2026, 9, 10, 0, 0));
        // ocorre 01..05 (o filtro é cursor > until + 1 dia)
        assertEquals(5, occ.size());
        assertEquals("2026-09-05", date(occ.get(4).start));
    }

    @Test
    void singleEventInWindow() {
        EventItem e = ev("20260910T150000", "", 60);
        assertFalse(Recurrence.expandOccurrences(e, ms(2026, 9, 10, 14, 0), ms(2026, 9, 10, 17, 0)).isEmpty());
        assertTrue(Recurrence.expandOccurrences(e, ms(2026, 9, 11, 0, 0), ms(2026, 9, 12, 0, 0)).isEmpty());
    }
}