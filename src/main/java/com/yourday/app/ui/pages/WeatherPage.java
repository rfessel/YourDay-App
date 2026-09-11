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
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Clima: cards roláveis no centro + rodapé fixo (outras cidades e seletor). */
public class WeatherPage extends BorderPane implements MainView.Refreshable {

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

        // Seletor de cidade (item exibido primeiro, demais em seguida)
        cityCombo.setOnAction(e -> onCitySelected());
        HBox.setHgrow(cityCombo, Priority.ALWAYS);
        cityCombo.setMaxWidth(Double.MAX_VALUE);

        Label cityLbl = new Label("Cidade:");
        cityLbl.getStyleClass().add("section-label");
        HBox comboRow = new HBox(8, cityLbl, cityCombo);
        comboRow.setAlignment(Pos.CENTER_LEFT);

        // Centro rolável: cabeçalho + status + cartões
        VBox body = new VBox(10);
        body.getChildren().addAll(header, status, nowBox, hoursBox, daysBox);
        setCenter(Ui.scroll(body));

        // Rodapé fixo na parte inferior (como no widget): outras cidades e,
        // na última linha, o seletor de cidade.
        Label others = Ui.sectionTitle("Outras cidades");
        VBox footer = new VBox(6);
        footer.getStyleClass().add("page-footer");
        footer.getChildren().addAll(others, extrasBox, comboRow);
        setBottom(footer);
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
        VBox hoursCard = new VBox(6);
        hoursCard.getStyleClass().add("card");
        hoursCard.getChildren().addAll(Ui.sectionTitle("Próximas horas"), chartLegend(), hoursChart(w));
        hoursBox.getChildren().add(hoursCard);
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

    /** Cartão compacto de uma outra cidade. */
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

    /** Legenda: linha azul (temperatura) e linha vermelha (chuva), como no widget. */
    private VBox chartLegend() {
        VBox legend = new VBox(4);
        legend.getChildren().addAll(legendItem(CHART_TEMP, "Temperatura"), legendItem(CHART_RAIN, "Chuva"));
        return legend;
    }

    private static final Color CHART_TEMP = Color.rgb(64, 140, 242);
    private static final Color CHART_RAIN = Color.rgb(230, 64, 64);

    private HBox legendItem(Color color, String text) {
        HBox row = new HBox(4);
        row.setAlignment(Pos.CENTER_LEFT);
        Rectangle bar = new Rectangle(14, 3);
        bar.setArcWidth(4);
        bar.setArcHeight(4);
        bar.setFill(color);
        Label l = new Label(text);
        l.getStyleClass().add("section-label");
        row.getChildren().addAll(bar, l);
        return row;
    }

    /**
     * Gráfico das próximas horas em duas linhas: temperatura (azul, escala própria)
     * e chance de chuva (vermelha, 0..100%), como no widget KDE.
     */
    private StackPane hoursChart(WeatherData w) {
        StackPane pane = new StackPane();
        Canvas cv = new Canvas();
        cv.setHeight(130);
        pane.getChildren().add(cv);
        pane.widthProperty().addListener((o, ov, nv) -> drawHoursChart(cv, pane, w));
        pane.layoutBoundsProperty().addListener((o, ov, nv) -> drawHoursChart(cv, pane, w));
        Platform.runLater(() -> drawHoursChart(cv, pane, w));
        return pane;
    }

    private static final int CHART_TOP = 18;
    private static final int CHART_BOTTOM = 18;
    private static final int CHART_LEFT = 6;
    private static final int CHART_RIGHT = 10;

    private static double xFor(int i, int n, double plotW) {
        return CHART_LEFT + (n > 1 ? i * plotW / (n - 1) : plotW / 2);
    }

    private static double yFor(double t, double tmin, double tmax, double plotH) {
        return CHART_TOP + (1 - (t - tmin) / (tmax - tmin)) * plotH;
    }

    private static double yRain(double v, double plotH) {
        return CHART_TOP + (1 - Math.max(0, Math.min(100, v)) / 100) * plotH;
    }

    private void drawHoursChart(Canvas cv, StackPane pane, WeatherData w) {
        double padX = 6;
        double wdt = pane.getWidth() - padX * 2;
        if (wdt <= 0) {
            return;
        }
        cv.setWidth(wdt);
        GraphicsContext g = cv.getGraphicsContext2D();
        g.clearRect(0, 0, cv.getWidth(), cv.getHeight());

        List<WeatherData.Hour> pts = w.hours.size() > 6 ? w.hours.subList(0, 6) : w.hours;
        int n = pts.size();
        if (n < 2) {
            return;
        }
        boolean dark = !"light".equals(ctx.agenda().config().theme);
        double cw = cv.getWidth();
        double plotW = cw - CHART_LEFT - CHART_RIGHT;
        double plotH = cv.getHeight() - CHART_TOP - CHART_BOTTOM;

        double tmin = Double.POSITIVE_INFINITY;
        double tmax = Double.NEGATIVE_INFINITY;
        for (WeatherData.Hour h : pts) {
            tmin = Math.min(tmin, h.temp);
            tmax = Math.max(tmax, h.temp);
        }
        if (!Double.isFinite(tmin) || !Double.isFinite(tmax)) {
            tmin = 0;
            tmax = 1;
        }
        if (tmax - tmin < 4) {
            double pad = (4 - (tmax - tmin)) / 2;
            tmin -= pad;
            tmax += pad;
        }

        Color text = dark ? Color.rgb(237, 237, 237) : Color.rgb(33, 33, 33);
        Color grid = dark ? Color.rgb(255, 255, 255, 0.07) : Color.rgb(0, 0, 0, 0.07);

        g.setStroke(grid);
        g.setLineWidth(1);
        for (int gi = 0; gi <= 2; gi++) {
            double gy = CHART_TOP + gi * plotH / 2;
            g.strokeLine(CHART_LEFT, gy, CHART_LEFT + plotW, gy);
        }

        g.setStroke(CHART_RAIN);
        g.setLineWidth(3);
        g.beginPath();
        for (int i = 0; i < n; i++) {
            double y = yRain(pts.get(i).precipProb, plotH);
            if (i == 0) {
                g.moveTo(xFor(i, n, plotW), y);
            } else {
                g.lineTo(xFor(i, n, plotW), y);
            }
        }
        g.stroke();
        g.setLineWidth(2);

        g.setStroke(CHART_TEMP);
        g.setLineWidth(2);
        g.beginPath();
        for (int i = 0; i < n; i++) {
            double y = yFor(pts.get(i).temp, tmin, tmax, plotH);
            if (i == 0) {
                g.moveTo(xFor(i, n, plotW), y);
            } else {
                g.lineTo(xFor(i, n, plotW), y);
            }
        }
        g.stroke();

        g.setFont(Font.font(9));
        g.setTextAlign(TextAlignment.CENTER);
        double bottomY = cv.getHeight() - 5;
        for (int i = 0; i < n; i++) {
            WeatherData.Hour h = pts.get(i);
            double dx = xFor(i, n, plotW);
            double dy = yFor(h.temp, tmin, tmax, plotH);
            double dyR = yRain(h.precipProb, plotH);

            g.setFill(CHART_RAIN);
            g.fillOval(dx - 2, dyR - 2, 4, 4);
            g.setFill(CHART_TEMP);
            g.fillOval(dx - 3, dy - 3, 6, 6);

            g.setFill(text);
            g.fillText(Math.round(h.temp) + "°", dx, dy - 8);
            if (h.precipProb > 0) {
                g.setFill(CHART_RAIN);
                double pyl = dyR + 11;
                if (pyl > bottomY - 4) {
                    pyl = dyR - 9;
                }
                g.fillText(Math.round(h.precipProb) + "%", dx, pyl);
            }
            g.setFill(text);
            int hour = LocalTime.ofInstant(java.time.Instant.ofEpochMilli(h.time),
                    java.time.ZoneId.systemDefault()).getHour();
            g.fillText(hour + "h", dx, bottomY);
        }
    }

    private HBox daysRow(WeatherData w) {
        HBox days = new HBox(6);
        for (WeatherData.Day d : w.days) {
            VBox cell = new VBox(4);
            cell.getStyleClass().add("day-cell");
            HBox.setHgrow(cell, Priority.ALWAYS);
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
        VBox[] stats = {
                stat("Sensação", Math.round(w.now.feelsLike) + "°"),
                stat("Umidade", w.now.humidity + "%"),
                stat("Vento", Math.round(w.now.windKmh) + " km/h"),
                stat("Chuva", w.now.precipitation + " mm")};
        for (VBox s : stats) {
            HBox wrapper = new HBox(s);
            HBox.setHgrow(wrapper, Priority.ALWAYS);
            row.getChildren().add(wrapper);
        }
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