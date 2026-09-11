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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Clima: cidade atual (principal + complementares) + horária + previsão de 7 dias. */
public class WeatherPage extends VBox implements MainView.Refreshable {

    private final UiContext ctx;
    private final Label status = new Label();
    private final VBox nowBox = new VBox(8);
    private final VBox extrasBox = new VBox(8);
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

        Button reloadBtn = new Button("↻");
        reloadBtn.getStyleClass().add("toolbtn");
        reloadBtn.setTooltip(new javafx.scene.control.Tooltip("Atualizar previsão"));
        reloadBtn.setOnAction(e -> {
            lastFetch = 0;
            refresh();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        VBox titles = new VBox(2, title, sub);
        HBox header = new HBox(10, titles, spacer, reloadBtn);
        header.setAlignment(Pos.CENTER_LEFT);

        status.getStyleClass().add("section-label");

        getChildren().addAll(header, status, nowBox, extrasBox, hoursBox, daysBox);
    }

    @Override
    public void refresh() {
        long since = System.currentTimeMillis() - lastFetch;
        if (lastFetch == 0 || since > 30 * 60_000L) {
            AppConfig cfg = ctx.agenda().config();
            doFetch(cfg.cityLat, cfg.cityLon, cfg.cityName);
        }
        renderExtras();
    }

    private void doFetch(Double lat, Double lon, String cityName) {
        if (lat == null || lon == null) {
            status.setText("Configure sua cidade na busca acima.");
            return;
        }
        status.setText("Carregando previsão…");
        HttpSupport.getAsync(WeatherService.forecastUrl(lat, lon), s -> {
            try {
                WeatherData w = WeatherService.parseForecast(s, cityName);
                Platform.runLater(() -> show(w));
            } catch (Exception e) {
                Platform.runLater(() -> status.setText("Erro ao carregar a previsão."));
            }
        }, e -> Platform.runLater(() -> status.setText("Erro na rede: " + e.getMessage())));
        lastFetch = System.currentTimeMillis();
    }

    private void show(WeatherData w) {
        status.setText("");
        nowBox.getChildren().setAll(Ui.card(nowHeader(w), nowExtra(w)));
        hoursBox.getChildren().setAll(Ui.card(Ui.sectionTitle("Próximas 24 horas"),
                hoursRow(w)));
        daysBox.getChildren().setAll(Ui.card(Ui.sectionTitle("Previsão da semana"),
                daysRow(w)));
        renderExtras();
    }

    private void renderExtras() {
        AppConfig cfg = ctx.agenda().config();
        extrasBox.getChildren().clear();
        if (cfg.extraCities == null || cfg.extraCities.isEmpty()) {
            return;
        }
        VBox card = Ui.card(Ui.sectionTitle("Cidades complementares"));
        for (AppConfig.ExtraCity ec : cfg.extraCities) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            Label name = new Label(ec.name);
            name.getStyleClass().add("card-sub");
            Label icon = new Label("…");
            icon.getStyleClass().add("weather-icon");
            Label temp = new Label("—");
            temp.getStyleClass().add("card-sub");
            HBox.setHgrow(name, Priority.ALWAYS);
            row.getChildren().addAll(name, icon, temp);
            card.getChildren().add(row);
            final HBox r = row;
            HttpSupport.getAsync(WeatherService.forecastUrl(ec.lat, ec.lon), s -> {
                try {
                    WeatherData w = WeatherService.parseForecast(s, ec.name);
                    Platform.runLater(() -> {
                        ((Label) r.getChildren().get(1)).setText(WeatherData.icon(w.now.code));
                        ((Label) r.getChildren().get(2)).setText(Math.round(w.now.temp) + "° · "
                                + WeatherData.condition(w.now.code));
                    });
                } catch (Exception ignored) {
                    Platform.runLater(() ->
                            ((Label) r.getChildren().get(2)).setText("—"));
                }
            }, e -> Platform.runLater(() ->
                    ((Label) r.getChildren().get(2)).setText("—")));
        }
        extrasBox.getChildren().add(card);
    }

    private ScrollPane hoursRow(WeatherData w) {
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
        ScrollPane hs = new ScrollPane(hours);
        hs.setFitToHeight(true);
        hs.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        hs.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        hs.setStyle("-fx-background-color: transparent;");
        hs.setMaxHeight(130);
        return hs;
    }

    private HBox daysRow(WeatherData w) {
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
        return days;
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
}