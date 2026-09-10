package com.yourday.app.core.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yourday.app.core.model.WeatherData;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Clima via Open-Meteo (geocoding + forecast), sem chave de API. */
public final class WeatherService {

    private static final String GEO_URL =
            "https://geocoding-api.open-meteo.com/v1/search?name=%s&count=1&language=pt&format=json";
    private static final String FORECAST_URL =
            "https://api.open-meteo.com/v1/forecast"
                    + "?latitude=%s&longitude=%s&current=temperature_2m,relative_humidity_2m,"
                    + "apparent_temperature,is_day,precipitation,weather_code,wind_speed_10m"
                    + "&hourly=temperature_2m,weather_code&forecast_hours=24"
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min,"
                    + "precipitation_probability_max,sunrise,sunset&timezone=auto&forecast_days=7"
                    + "&temperature_unit=celsius&wind_speed_unit=kmh&precipitation_unit=mm";

    private WeatherService() {
    }

    /** Busca lat/lon e nome canônico de uma cidade. */
    public static Map<String, Object> geocode(String city) throws Exception {
        JsonObject json = JsonParser.parseString(HttpSupport.get(
                GEO_URL.formatted(java.net.URLEncoder.encode(city, java.nio.charset.StandardCharsets.UTF_8))))
                .getAsJsonObject();
        JsonArray results = json.getAsJsonArray("results");
        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("Cidade não encontrada: " + city);
        }
        JsonObject first = results.get(0).getAsJsonObject();
        String name = first.has("name") ? first.get("name").getAsString() : city;
        double lat = first.get("latitude").getAsDouble();
        double lon = first.get("longitude").getAsDouble();
        String full = first.has("admin1") ? name + ", " + first.get("admin1").getAsString() : name;
        return Map.of("name", full, "lat", lat, "lon", lon);
    }

    public static WeatherData fetch(double lat, double lon, String cityName) throws Exception {
        String json = HttpSupport.get(FORECAST_URL.formatted(lat, lon));
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        WeatherData w = new WeatherData();
        w.cityName = cityName;
        w.fetchedAt = System.currentTimeMillis();

        JsonObject current = root.getAsJsonObject("current");
        if (current != null) {
            w.now.temp = num(current, "temperature_2m");
            w.now.code = (int) num(current, "weather_code");
            w.now.feelsLike = num(current, "apparent_temperature");
            w.now.humidity = (int) num(current, "relative_humidity_2m");
            w.now.windKmh = num(current, "wind_speed_10m");
            w.now.isDay = current.get("is_day") != null && current.get("is_day").getAsInt() == 1;
            w.now.precipitation = num(current, "precipitation");
        }

        JsonObject hourly = root.getAsJsonObject("hourly");
        if (hourly != null) {
            JsonArray times = hourly.getAsJsonArray("time");
            JsonArray temps = hourly.getAsJsonArray("temperature_2m");
            JsonArray codes = hourly.getAsJsonArray("weather_code");
            for (int i = 0; i < times.size(); i++) {
                WeatherData.Hour h = new WeatherData.Hour();
                h.time = Instant.parse(times.get(i).getAsString()).toEpochMilli();
                h.temp = temps.get(i).getAsDouble();
                h.code = codes.get(i).getAsInt();
                w.hours.add(h);
            }
        }

        JsonObject daily = root.getAsJsonObject("daily");
        if (daily != null) {
            DateTimeFormatter t = DateTimeFormatter.ISO_LOCAL_DATE;
            JsonArray dates = daily.getAsJsonArray("time");
            JsonArray max = daily.getAsJsonArray("temperature_2m_max");
            JsonArray min = daily.getAsJsonArray("temperature_2m_min");
            JsonArray codes = daily.getAsJsonArray("weather_code");
            JsonArray probs = daily.getAsJsonArray("precipitation_probability_max");
            JsonArray rises = daily.getAsJsonArray("sunrise");
            JsonArray sets = daily.getAsJsonArray("sunset");
            for (int i = 0; i < dates.size(); i++) {
                WeatherData.Day d = new WeatherData.Day();
                d.date = LocalDate.parse(dates.get(i).getAsString(), t);
                d.max = max.get(i).getAsDouble();
                d.min = min.get(i).getAsDouble();
                d.code = codes.get(i).getAsInt();
                d.precipProb = probs != null && i < probs.size() ? probs.get(i).getAsInt() : 0;
                d.sunrise = timeHm(rises, i);
                d.sunset = timeHm(sets, i);
                w.days.add(d);
            }
        }

        ZoneId zone = ZoneId.systemDefault();
        JsonObject utcOffset = root.getAsJsonObject("utc_offset_seconds");
        if (utcOffset != null) {
            // tempo atual da API (já em instantes absolutos); não precisa do offset
        }
        return w;
    }

    private static double num(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? 0 : e.getAsDouble();
    }

    private static String timeHm(JsonArray arr, int i) {
        if (arr == null || i >= arr.size() || arr.get(i) == null) {
            return "";
        }
        Instant in = Instant.parse(arr.get(i).getAsString());
        return in.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"));
    }
}