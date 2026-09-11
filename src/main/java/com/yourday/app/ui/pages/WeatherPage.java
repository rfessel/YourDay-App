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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Clima: seletor de cidade no rodapé (fixo) + cards roláveis + cidades complementares. */
public class WeatherPage extends VBox implements MainView.Refreshable {

    private static final long FETCH_COOLDOWN_MS = 15 * 60_000L;

    private final UiContext ctx;
    private final Label status = new Label();

    private final VBox nowBox = new VBox(8);
    private final VBox hoursBox = new VBox(8);
    private final VBox daysBox = new VBox(8);
    private final VBox extrasBox = new VBox(4);
    private final ComboBox<String> cityCombo = new ComboBox<>();

    private String currentCity;
    private final Map<String, WeatherData> cache = new LinkedHashMap<>();
    private final Map<String, Long> fetchedAt = new LinkedHashMap<>();

    public WeatherPage(UiContext ctx) {
        this.ctx = ctx;
        setSpacing(10);
        getStyleClass().add("content");

        Label title = new Label("Clima");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Previsão por hora e por dia");
        sub.getStyleClass().add("section-label");

        Button reloadBtn = new Button("↻");
        reloadBtn.getStyleClass().add("toolbtn");
        reloadBtn.setTooltip(new Tooltip("Atualizar previsão"));
        reloadBtn.setOnAction(e -> refresh(true));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        VBox titles = new VBox(2, title, sub);
        HBox header = new HBox(10, titles, spacer, reloadBtn);
        header.setAlignment(Pos.CENTER_LEFT);

        status.getStyleClass().add("section-label");

        // Área rolável com os cards (principal, horas, semana)
        VBox content = new VBox(12, nowBox, hoursBox, daysBox);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setStyle("-fx-background-color: transparent;");
        scroll.setMinHeight(0);
        scroll.setPrefHeight(1);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // Rodapé fixo: seletor de cidade + cidades complementares
        cityCombo.getStyleClass().add("field");
        cityCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(cityCombo, Priority.ALWAYS);
        cityCombo.setOnAction(e -> onCitySelected());

        Label cityLbl = new Label("Cidade:");
        cityLbl.getStyleClass().add("section-label");
        HBox comboRow = new HBox(8, cityLbl, cityCombo);
        comboRow.setAlignment(Pos.CENTER_LEFT);

        Label others = Ui.sectionTitle("Outras cidades");
        VBox footer = new VBox(8, comboRow, others, extrasBox);

        getChildren().addAll(header, status, scroll, footer);
    }

    // ---------------- Seleção de cidade ----------------

    private List<String> cityNames() {
        List<String> out = new ArrayList<>();
        AppConfig cfg = ctx.agenda().config();
        if (cfg.cityName != null && !cfg.cityName.isBlank()) {
            out.add(cfg.cityName);
        }
        if (cfg.extraCities != null) {
            for (AppConfig.ExtraCity ec : cfg.extraCities) {
                if (!out.contains(ec.name)) {
                    out.add(ec.name);
                }
            }
        }
        return out;
    }

    private double[] coordsOf(String name) {
        AppConfig cfg = ctx.agenda().config();
        if (name != null && name.equals(cfg.cityName)) {
            return cfg.cityLat != null && cfg.cityLon != null
                    ? new double[]{cfg.cityLat, cfg.cityLon} : null;
        }
        if (cfg.extraCities != null) {
            for (AppConfig.ExtraCity ec : cfg.extraCities) {
                if (ec.name.equals(name)) {
                    return new double[]{ec.lat, ec.lon};
                }
            }
        }
        return null;
    }

    private void onCitySelected() {
        String sel = cityCombo.getValue();
        if (sel == null || sel.equals(currentCity)) {
            return;
        }
        currentCity = sel;
        reorderCombo(sel);
        ensureData(sel, false);
        renderAll();
        renderExtras();
    }

    /** Coloca a cidade exibida como primeira opção do dropdown. */
    private void reorderCombo(String selected) {
        List<String> names = cityNames();
        names.remove(selected);
        List<String> ordered = new ArrayList<>();
        ordered.add(selected);
        ordered.addAll(names);
        cityCombo.getItems().setAll(ordered);
        cityCombo.setValue(selected);
    }

    // ---------------- Busca de dados ----------------

    @Override
    public void refresh() {
        refresh(false);
    }

    private void refresh(boolean force) {
        AppConfig cfg = ctx.agenda().config();
        List<String> names = cityNames();
        if (currentCity == null || !names.contains(currentCity)) {
            currentCity = cfg.cityName != null && !cfg.cityName.isBlank()
                    ? cfg.cityName : (names.isEmpty() ? null : names.get(0));
        }
        if (currentCity == null) {
            status.setText("Nenhuma cidade configurada. Vá em Configurações → Clima.");
            return;
        }
        reorderCombo(currentCity);
        if (force) {
            fetchedAt.clear();
        }
        for (String name : names) {
            ensureData(name, force);
        }
        renderAll();
        renderExtras();
    }

    private void ensureData(String name, boolean force) {
        if (coordsOf(name) == null) {
            return;
        }
        long last = fetchedAt.getOrDefault(name, 0L);
        if (!force && cache.containsKey(name)
                && System.currentTimeMillis() - last < FETCH_COOLDOWN_MS) {
            return;
        }
        doFetch(name);
    }

    private void doFetch(String name) {
        double[] c = coordsOf(name);
        if (c == null) {
            status.setText("Configure sua cidade em Configurações → Clima.");
            return;
        }
        status.setText("Carregando previsão de " + name + "…");
        HttpSupport.getAsync(WeatherService.forecastUrl(c[0], c[1]), s -> {
            try {
                WeatherData w = WeatherService.parseForecast(s, name);
                Platform.runLater(() -> {
                    cache.put(name, w);
                    fetchedAt.put(name, System.currentTimeMillis());
                    status.setText("");
                    if (name.equals(currentCity)) {
                        renderAll();
                    }
                    renderExtras();
                });
            } catch (Exception e) {
                Platform.runLater(() -> status.setText("Erro ao carregar a previsão."));
            }
        }, e -> Platform.runLater(() -> status.setText("Erro na rede: " + e.getMessage())));
    }

    // ---------------- Renderização ----------------

    private WeatherData currentData() {
        return cache.get(currentCity);
    }

    private void renderAll() {
        WeatherData w = currentData();
        nowBox.getChildren().clear();
        hoursBox.getChildren().clear();
        daysBox.getChildren().clear();
        if (w == null) {
            return;
        }
        nowBox.getChildren().add(Ui.card(nowHeader(w), nowExtra(w)));
        hoursBox.getChildren().add(Ui.card(Ui.sectionTitle("Próximas horas"), hoursRow(w)));
        daysBox.getChildren().add(Ui.card(Ui.sectionTitle("Previsão da semana"), daysRow(w)));
    }

    private void renderExtras() {
        extrasBox.getChildren().clear();
        List<String> others = new ArrayList<>();
        for (String name : cityNames()) {
            if (!name.equals(currentCity)) {
                others.add(name);
            }
        }
        if (others.isEmpty()) {
            Label none = new Label("Nenhuma outra cidade configurada.");
            none.getStyleClass().add("section-label");
            extrasBox.getChildren().add(none);
            return;
        }
        for (String name : others) {
            WeatherData w = cache.get(name);
            extrasBox.getChildren().add(extraRow(name, w));
            if (w == null) {
                ensureData(name, false);
            }
        }
    }

    /** Cartão compacto de uma outra cidade (rodapé fixo). */
    private HBox extraRow(String name, WeatherData w) {
        HBox row = new HBox(10);
        row.getStyleClass().add("card");
        row.setAlignment(Pos.CENTER_LEFT);
        Label nm = new Label(name);
        nm.getStyleClass().add("card-sub");
        HBox.setHgrow(nm, Priority.ALWAYS);
        Label icon = new Label(w != null ? WeatherData.icon(w.now.code) : "…");
        icon.getStyleClass().add("weather-icon");
        Label temp = new Label(w != null ? Math.round(w.now.temp) + "°" : "—");
        temp.getStyleClass().add("weather-now");
        Label cond = new Label(w != null ? WeatherData.condition(w.now.code) : "carregando…");
        cond.getStyleClass().add("section-label");
        Label range = new Label(w != null && !w.days.isEmpty() ? "Máx " + Math.round(w.days.get(0).max)
                + "° · Mín " + Math.round(w.days.get(0).min) + "°" : "");
        range.getStyleClass().add("section-label");
        row.getChildren().addAll(nm, icon, temp, cond, range);
        return row;
    }

    private FlowPane hoursRow(WeatherData w) {
        FlowPane hours = new FlowPane(8, 10);
        hours.setPrefWrapLength(Double.MAX_VALUE);
        for (WeatherData.Hour h : w.hours) {
            VBox cell = new VBox(3);
            cell.getStyleClass().add("hour-cell");
            cell.setMinWidth(92);
            cell.setPrefWidth(92);
            String hm = LocalTime.ofInstant(java.time.Instant.ofEpochMilli(h.time),
                    java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"));
            Label t = new Label(hm);
            t.getStyleClass().add("section-label");
            Label icon = new Label(WeatherData.icon(h.code));
            icon.getStyleClass().add("weather-icon");
            Label temp = new Label(Math.round(h.temp) + "°");
            temp.getStyleClass().add("card-sub");
            cell.getChildren().addAll(t, icon, temp);
            hours.getChildren().add(cell);
        }
        return hours;
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