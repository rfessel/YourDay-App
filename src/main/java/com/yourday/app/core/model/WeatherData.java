package com.yourday.app.core.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Dados de clima (Open-Meteo). */
public class WeatherData {

    public static final String FALLBACK_CITY = "São Paulo";

    public static class Now {
        public double temp;
        public int code;
        public double feelsLike;
        public int humidity;
        public double windKmh;
        public boolean isDay;
        public double precipitation;
    }

    public static class Hour {
        public long time;
        public double temp;
        public int code;
    }

    public static class Day {
        public LocalDate date;
        public double max;
        public double min;
        public int code;
        public int precipProb;
        public String sunrise;
        public String sunset;
    }

    public Now now = new Now();
    public final List<Hour> hours = new ArrayList<>();
    public final List<Day> days = new ArrayList<>();
    public String cityName = FALLBACK_CITY;
    public long fetchedAt;
    public String error = "";

    public static WeatherData failed(String msg) {
        WeatherData w = new WeatherData();
        w.error = msg;
        return w;
    }

    /** Rótulo em pt-BR para o código WMO. */
    public static String condition(int code) {
        if (code == 0) return "Céu limpo";
        if (code == 1) return "Quase limpo";
        if (code == 2) return "Parc. nublado";
        if (code == 3) return "Nublado";
        if (code == 45 || code == 48) return "Nevoeiro";
        if (code >= 51 && code <= 57) return "Garoa";
        if (code >= 61 && code <= 67) return "Chuva";
        if (code >= 71 && code <= 77) return "Neve";
        if (code >= 80 && code <= 82) return "Pancadas de chuva";
        if (code >= 85 && code <= 86) return "Neve (rajadas)";
        if (code >= 95) return "Tempestade";
        return "—";
    }

    /** Ícone emoji simples por condição (funciona em qualquer sistema). */
    public static String icon(int code) {
        if (code == 0 || code == 1) return "☀️";
        if (code == 2) return "🌤️";
        if (code == 3) return "☁️";
        if (code == 45 || code == 48) return "🌫️";
        if (code >= 51 && code <= 57) return "🌦️";
        if (code >= 61 && code <= 67) return "🌧️";
        if (code >= 71 && code <= 77) return "❄️";
        if (code >= 80 && code <= 82) return "🌧️";
        if (code >= 85 && code <= 86) return "🌨️";
        if (code >= 95) return "⛈️";
        return "🌡️";
    }
}