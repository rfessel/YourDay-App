package com.yourday.app.ui.pages;

import com.yourday.app.core.model.AppConfig;
import com.yourday.app.core.model.WeatherData;
import com.yourday.app.core.services.HttpSupport;
import com.yourday.app.core.services.WeatherService;
import com.yourday.app.ui.MainView;
import com.yourday.app.ui.Ui;
import com.yourday.app.ui.UiContext;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Clima: cidade atual + horária + previsão de 7 dias (Open-Meteo). */
public class WeatherPage extends VBox implements MainView.Refreshable {

    private final UiContext ctx;
    private final TextField searchField = new TextField();
    private final Label status = new Label();
    private final VBox nowBox = new VBox(8);
    private final VBox hoursBox = new VBox(8);
    private final VBox daysBox = new VBox(8);
    private long lastFetch;

    public WeatherPage(UiContext ctx) {
        this.ctx = ctx;
        setSpacing(12);
        getStyleClass().add("content");

        Label title = new Label("Clima");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Previsão por hora e por dia");
        sub.getStyleClass().add("section-label");

        AppConfig cfg = ctx.agenda().config();
        searchField.setPromptText("Buscar cidade (ex.: Curitiba)");
        searchField.setText(cfg.cityName);
        searchField.getStyleClass().add("field");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        Button searchBtn = new Button("Buscar");
        searchBtn.getStyleClass().add("ghost-btn");
        searchBtn.setOnAction(e -> geocodeAndFetch(searchField.getText().trim()));

        Button reloadBtn = new Button("↻");
        reloadBtn.getStyleClass().add("toolbtn");
        reloadBtn.setOnAction(e -> doFetch(cfg.cityLat, cfg.cityLon, cfg.cityName));

        HBox searchRow = new HBox(8, searchField, searchBtn, reloadBtn);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        status.getStyleClass().add("section-label");

        getChildren().addAll(title, sub, searchRow, status, nowBox, hoursBox, daysBox);
    }

    private void geocodeAndFetch(String city) {
        if (city == null || city.isBlank()) {
            return;
        }
        status.setText("Buscando " + city + "…");
        HttpSupport.getAsync(
                "https://geocoding-api.open-meteo.com/v1/search?name="
                        + java.net.URLEncoder.encode(city, java.nio.charset.StandardCharsets.UTF_8)
                        + "&count=1&language=pt&format=json",
                s -> {
                    try {
                        var json = com.google.gson.JsonParser.parseString(s).getAsJsonObject();
                        var results = json.getAsJsonArray("results");
                        if (results == null || results.isEmpty()) {
                            Platform.runLater(() -> status.setText("Cidade não encontrada: " + city));
                            return;
                        }
                        var first = results.get(0).getAsJsonObject();
                        double lat = first.get("latitude").getAsDouble();
                        double lon = first.get("longitude").getAsDouble();
                        String baseName = first.get("name").getAsString();
                        String finalName = first.has("admin1")
                                ? baseName + ", " + first.get("admin1").getAsString() : baseName;
                        AppConfig cfg = ctx.agenda().config();
                        cfg.cityName = finalName;
                        cfg.cityLat = lat;
                        cfg.cityLon = lon;
                        ctx.agenda().saveConfig();
                        Platform.runLater(() -> doFetch(lat, lon, finalName));
                    } catch (Exception e) {
                        Platform.runLater(() -> status.setText("Erro na busca da cidade."));
                    }
                },
                e -> Platform.runLater(() -> status.setText("Erro na busca da cidade: " + e.getMessage())));
    }

    @Override
    public void refresh() {
        long since = System.currentTimeMillis() - lastFetch;
        if (lastFetch == 0 || since > 30 * 60_000L) {
            AppConfig cfg = ctx.agenda().config();
            doFetch(cfg.cityLat, cfg.cityLon, cfg.cityName);
        }
    }

    private void doFetch(Double lat, Double lon, String cityName) {
        if (lat == null || lon == null) {
            status.setText("Configure sua cidade na busca acima.");
            return;
        }
        status.setText("Carregando previsão…");
        HttpSupport.getAsync(forecastUrl(lat, lon), s -> {
            WeatherData w = new WeatherData();
            try {
                var root = com.google.gson.JsonParser.parseString(s).getAsJsonObject();
                w.cityName = cityName;
                w.fetchedAt = System.currentTimeMillis();
                var current = root.getAsJsonObject("current");
                w.now.temp = current.get("temperature_2m").getAsDouble();
                w.now.code = current.get("weather_code").getAsInt();
                w.now.feelsLike = current.get("apparent_temperature").getAsDouble();
                w.now.humidity = current.get("relative_humidity_2m").getAsInt();
                w.now.windKmh = current.get("wind_speed_10m").getAsDouble();
                w.now.isDay = current.get("is_day").getAsInt() == 1;
                w.now.precipitation = current.get("precipitation").getAsDouble();

                var hourly = root.getAsJsonObject("hourly");
                var hTimes = hourly.getAsJsonArray("time");
                var hTemps = hourly.getAsJsonArray("temperature_2m");
                var hCodes = hourly.getAsJsonArray("weather_code");
                for (int i = 0; i < hTimes.size() && i < 24; i++) {
                    WeatherData.Hour h = new WeatherData.Hour();
                    h.time = java.time.Instant.parse(hTimes.get(i).getAsString()).toEpochMilli();
                    h.temp = hTemps.get(i).getAsDouble();
                    h.code = hCodes.get(i).getAsInt();
                    w.hours.add(h);
                }

                var daily = root.getAsJsonObject("daily");
                var dDates = daily.getAsJsonArray("time");
                var dMax = daily.getAsJsonArray("temperature_2m_max");
                var dMin = daily.getAsJsonArray("temperature_2m_min");
                var dCodes = daily.getAsJsonArray("weather_code");
                var dProbs = daily.getAsJsonArray("precipitation_probability_max");
                var dRise = daily.getAsJsonArray("sunrise");
                var dSet = daily.getAsJsonArray("sunset");
                for (int i = 0; i < dDates.size(); i++) {
                    WeatherData.Day d = new WeatherData.Day();
                    d.date = java.time.LocalDate.parse(dDates.get(i).getAsString());
                    d.max = dMax.get(i).getAsDouble();
                    d.min = dMin.get(i).getAsDouble();
                    d.code = dCodes.get(i).getAsInt();
                    d.precipProb = dProbs.get(i).getAsInt();
                    d.sunrise = hm(dRise.get(i).getAsString());
                    d.sunset = hm(dSet.get(i).getAsString());
                    w.days.add(d);
                }
                Platform.runLater(() -> show(w));
            } catch (Exception e) {
                Platform.runLater(() -> status.setText("Erro ao carregar a previsão."));
            }
        }, e -> Platform.runLater(() -> status.setText("Erro na rede: " + e.getMessage())));
        lastFetch = System.currentTimeMillis();
    }

    private static String hm(String iso) {
        try {
            return java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            return "";
        }
    }

    private static String forecastUrl(double lat, double lon) {
        return "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,"
                + "precipitation,weather_code,wind_speed_10m"
                + "&hourly=temperature_2m,weather_code&forecast_hours=24"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,"
                + "precipitation_probability_max,sunrise,sunset&timezone=auto&forecast_days=7";
    }

    private void show(WeatherData w) {
        status.setText("");

        // Agora
        VBox nowCard = Ui.card(nowHeader(w), nowExtra(w));
        nowBox.getChildren().setAll(nowCard);

        // Horária (rolagem horizontal)
        HBox hours = new HBox(6);
        for (WeatherData.Hour h : w.hours) {
            VBox cell = new VBox(3);
            cell.getStyleClass().add("hour-cell");
            String hm = LocalTime.ofInstant(java.time.Instant.ofEpochMilli(h.time),
                    java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"));
            Label t = new Label(hm);
            t.getStyleClass().add("section-label");
            Label icon = new Label(WeatherData.icon(h.code));
            icon.getStyleClass().add("weather-icon");
            Label temp = new Label(Math.round(h.temp) + "°");
            temp.getStyleClass().add("section-label");
            cell.getChildren().addAll(t, icon, temp);
            hours.getChildren().add(cell);
        }
        ScrollPane hoursScroll = scrollHorizontal(hours);
        hoursScroll.setMaxHeight(130);
        hoursBox.getChildren().setAll(Ui.card(Ui.sectionTitle("Próximas 24 horas"), hoursScroll));

        // Diária
        HBox days = new HBox(6);
        for (WeatherData.Day d : w.days) {
            VBox cell = new VBox(4);
            cell.getStyleClass().add("day-cell");
            String dayName = d.date.equals(java.time.LocalDate.now()) ? "Hoje"
                    : d.date.getDayOfWeek().getDisplayName(
                            java.time.format.TextStyle.SHORT, Locale.getDefault());
            Label dl = new Label(dayName);
            dl.getStyleClass().add("section-label");
            Label icon = new Label(WeatherData.icon(d.code));
            icon.getStyleClass().add("weather-icon");
            Label temp = new Label(Math.round(d.max) + "° / " + Math.round(d.min) + "°");
            temp.getStyleClass().add("card-sub");
            Label rain = new Label("☔ " + d.precipProb + "%");
            rain.getStyleClass().add("section-label");
            cell.getChildren().addAll(dl, icon, temp, rain);
            days.getChildren().add(cell);
        }
        daysBox.getChildren().setAll(Ui.card(Ui.sectionTitle("Previsão da semana"), days));
    }

    private HBox nowHeader(WeatherData w) {
        HBox row = new HBox(18);
        row.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label(WeatherData.icon(w.now.code));
        icon.getStyleClass().add("weather-icon-big");
        VBox info = new VBox(2);
        Label temp = new Label(Math.round(w.now.temp) + "°");
        temp.getStyleClass().add("weather-now");
        Label cond = new Label(WeatherData.condition(w.now.code));
        cond.getStyleClass().add("weather-cond");
        Label city = Ui.muted(w.cityName);
        info.getChildren().addAll(temp, cond, city);
        row.getChildren().addAll(icon, info);
        return row;
    }

    private HBox nowExtra(WeatherData w) {
        HBox row = new HBox(18);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().addAll(
                stat("Sensação", Math.round(w.now.feelsLike) + "°"),
                stat("Umidade", w.now.humidity + "%"),
                stat("Vento", Math.round(w.now.windKmh) + " km/h"),
                stat("Chuva", w.now.precipitation + " mm"));
        return row;
    }

    private VBox stat(String label, String value) {
        VBox v = new VBox(1);
        Label l = new Label(label);
        l.getStyleClass().add("section-label");
        Label val = new Label(value);
        val.getStyleClass().add("card-title");
        v.getChildren().addAll(l, val);
        return v;
    }

    private static ScrollPane scrollHorizontal(HBox content) {
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToHeight(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setStyle("-fx-background-color: transparent;");
        return sp;
    }
}