package com.yourday.app.core.ics;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Baixo nível idêntico às semânticas de Date do Google Apps Script / JS usadas
 * pelo motor original do widget (calendar.js). Todas as datas "locais" usam o
 * fuso horário do sistema, como no JS.
 */
public final class Ics {

    public static final long DAY_MS = 86_400_000L;
    public static final ZoneId ZONE = ZoneId.systemDefault();

    private Ics() {
    }

    /** Millis locais no fuso do sistema (equivalente a new Date(y,m,d,h,min,s).getTime()). */
    public static long localEpoch(int y, int m0, int d, int h, int min, int s) {
        return LocalDateTime.of(y, m0 + 1, d, h, min, s).atZone(ZONE).toInstant().toEpochMilli();
    }

    /** UTC (equivalente a Date.UTC(...)). */
    public static long utcEpoch(int y, int m0, int d, int h, int min, int s) {
        return LocalDateTime.of(y, m0 + 1, d, h, min, s).toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    /** getDay() do JS (0=domingo ... 6=sábado), no fuso local. */
    public static int dayOfWeekJs(long ms) {
        DayOfWeek iso = LocalDate.ofInstant(Instant.ofEpochMilli(ms), ZONE).getDayOfWeek();
        return iso.getValue() % 7;
    }

    /** ISO 1..7 (1=segunda ... 7=domingo). */
    public static int isoDay(long ms) {
        return LocalDate.ofInstant(Instant.ofEpochMilli(ms), ZONE).getDayOfWeek().getValue();
    }

    /** Hora do dia em ms (HH*3600000 + MM*60000 + SS*1000) no fuso local. */
    public static int timeOfDayMs(long ms) {
        LocalTime t = LocalTime.ofInstant(Instant.ofEpochMilli(ms), ZONE);
        return t.toSecondOfDay() * 1000;
    }

    public static int daysInMonth(int y, int m0) {
        return YearMonth.of(y, m0 + 1).lengthOfMonth();
    }

    public static long plusDays(long ms, int n) {
        return LocalDate.ofInstant(Instant.ofEpochMilli(ms), ZONE).plusDays(n)
                .atStartOfDay(ZONE).toInstant().toEpochMilli();
    }

    /** Desdobra as linhas dobradas do ICS (folded lines continuam após espaço/tab). */
    public static String unfold(String text) {
        return String.valueOf(text == null ? "" : text)
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("\n[ \t]", "");
    }

    /** Normaliza um valor .ics (decodifica escapes básicos). */
    public static String unescapeIcs(String s) {
        return String.valueOf(s == null ? "" : s)
                .replaceAll("(?i)\\\\n", "\n")
                .replace("\\,", ",")
                .replace("\\;", ";")
                .replace("\\\\", "\\");
    }

    private static final Pattern P_LOCAL = Pattern.compile("^(\\d{4})(\\d{2})(\\d{2})T(\\d{2})(\\d{2})(\\d{2})$");
    private static final Pattern P_UTC = Pattern.compile("^(\\d{4})(\\d{2})(\\d{2})T(\\d{2})(\\d{2})(\\d{2})Z$");
    private static final Pattern P_OFF = Pattern.compile("^(\\d{4})(\\d{2})(\\d{2})T(\\d{2})(\\d{2})(\\d{2})([+-]\\d{2})(\\d{2})$");
    private static final Pattern P_DATE = Pattern.compile("^(\\d{4})(\\d{2})(\\d{2})$");

    /** Converte uma data .ics para epoch ms (mesmas regras do JS original). */
    public static long parseIcsDate(String value) {
        String v = String.valueOf(value == null ? "" : value).trim();
        if (v.isEmpty()) {
            return 0;
        }
        Matcher mLoc = P_LOCAL.matcher(v);
        if (mLoc.matches()) {
            return localEpoch(Integer.parseInt(mLoc.group(1)), Integer.parseInt(mLoc.group(2)) - 1,
                    Integer.parseInt(mLoc.group(3)), Integer.parseInt(mLoc.group(4)),
                    Integer.parseInt(mLoc.group(5)), Integer.parseInt(mLoc.group(6)));
        }
        Matcher mUtc = P_UTC.matcher(v);
        if (mUtc.matches()) {
            return utcEpoch(Integer.parseInt(mUtc.group(1)), Integer.parseInt(mUtc.group(2)) - 1,
                    Integer.parseInt(mUtc.group(3)), Integer.parseInt(mUtc.group(4)),
                    Integer.parseInt(mUtc.group(5)), Integer.parseInt(mUtc.group(6)));
        }
        Matcher mOff = P_OFF.matcher(v);
        if (mOff.matches()) {
            int y = Integer.parseInt(mOff.group(1));
            int mo = Integer.parseInt(mOff.group(2)) - 1;
            int d = Integer.parseInt(mOff.group(3));
            int hm = Integer.parseInt(mOff.group(4)) * 60 + Integer.parseInt(mOff.group(5));
            int off = (mOff.group(7).startsWith("-") ? -1 : 1)
                    * (Integer.parseInt(mOff.group(7).substring(1)) * 60 + Integer.parseInt(mOff.group(8)));
            LocalDateTime utc = LocalDateTime.of(y, mo + 1, d, 0, 0).plusMinutes((long) hm - off);
            return utc.toInstant(ZoneOffset.UTC).toEpochMilli();
        }
        Matcher mDate = P_DATE.matcher(v);
        if (mDate.matches()) {
            return localEpoch(Integer.parseInt(mDate.group(1)), Integer.parseInt(mDate.group(2)) - 1,
                    Integer.parseInt(mDate.group(3)), 0, 0, 0);
        }
        // Fallback estilo -> "yyyy-MM-dd HH:mm:ss" / "yyyy-MM-dd HH:mm" em hora local.
        String plain = v.replace('T', ' ').replace("Z", " UTC");
        String[] parts = plain.split(" ");
        try {
            String[] ymd = parts[0].split("-");
            int h = 0, mi = 0, s = 0;
            if (parts.length > 1) {
                String[] hms = parts[1].split(":");
                h = Integer.parseInt(hms[0]);
                if (hms.length > 1) {
                    mi = Integer.parseInt(hms[1]);
                }
                if (hms.length > 2 && !hms[2].equalsIgnoreCase("UTC")) {
                    s = Integer.parseInt(hms[2]);
                }
            }
            return localEpoch(Integer.parseInt(ymd[0]), Integer.parseInt(ymd[1]) - 1,
                    Integer.parseInt(ymd[2]), h, mi, s);
        } catch (Exception ignore) {
            return 0;
        }
    }

    /** Valor de uma linha "PROP;PARAMS:value" (trecho após o último ":"). */
    public static String propertyValue(String line) {
        int colon = line.indexOf(':');
        return colon < 0 ? "" : line.substring(colon + 1);
    }
}