package com.yourday.app.ui.pages;

import com.yourday.app.core.model.EventItem;
import com.yourday.app.core.model.TodoItem;
import com.yourday.app.core.model.WeatherData;
import com.yourday.app.core.services.AgendaService;
import com.yourday.app.core.services.WeatherService;
import com.yourday.app.ui.MainView;
import com.yourday.app.ui.Ui;
import com.yourday.app.ui.UiContext;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.yourday.app.core.services.AgendaService.endOfDay;
import static com.yourday.app.core.services.AgendaService.startOfDay;

/** Página inicial: clima, próximos compromissos e tarefas do dia. */
public class ResumePage extends VBox implements MainView.Refreshable {

    private final UiContext ctx;
    private final VBox weatherBox = new VBox(6);
    private final VBox eventsBox = new VBox(8);
    private final VBox todosBox = new VBox(8);
    private final Label weatherStatus = new Label();
    private long lastWeatherFetch;

    public ResumePage(UiContext ctx) {
        this.ctx = ctx;
        setSpacing(12);
        getStyleClass().add("content");

        LocalDate today = AgendaService.today();
        Label title = new Label("Resumo · " + Ui.weekdayFull(today)
                + ", " + today.getDayOfMonth() + " de " + Ui.monthName(today));
        title.getStyleClass().add("page-title");

        Label sub = new Label("Seu dia em um relance");
        sub.getStyleClass().add("section-label");

        Button refresh = new Button("↻");
        refresh.getStyleClass().add("toolbtn");
        refresh.setOnAction(e -> doRefresh());

        HBox header = new HBox(10, new VBox(2, title, sub), refresh);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(header, Priority.ALWAYS);

        weatherBox.setSpacing(8);
        weatherBox.getChildren().add(Ui.card(Ui.sectionTitle("Clima agora"), weatherStatus));

        eventsBox.setSpacing(8);
        eventsBox.getChildren().add(Ui.card(Ui.sectionTitle("Próximos compromissos"), new Label("carregando…")));

        todosBox.setSpacing(8);
        todosBox.getChildren().add(Ui.card(Ui.sectionTitle("Tarefas de hoje"), new Label("carregando…")));

        getChildren().addAll(header, weatherBox, eventsBox, todosBox);
        refresh();
    }

    @Override
    public void refresh() {
        refreshLocalData();
        long since = System.currentTimeMillis() - lastWeatherFetch;
        if (lastWeatherFetch == 0 || since > 30 * 60_000L) {
            fetchWeather();
        }
    }

    private void doRefresh() {
        refreshLocalData();
        fetchWeather();
    }

    private void refreshLocalData() {
        LocalDate today = AgendaService.today();
        List<EventItem> upcoming = ctx.agenda().nextEvents(6);
        buildEvents(today, upcoming);

        List<TodoItem> due = ctx.agenda().todos().stream()
                .filter(t -> !t.done && (t.dueDate == 0 || t.dueDate <= endOfDay(today)))
                .toList();
        buildTodos(due);
    }

    private void buildEvents(LocalDate today, List<EventItem> upcoming) {
        VBox content = new VBox(6);
        if (upcoming.isEmpty()) {
            content.getChildren().add(Ui.muted("Nenhum compromisso próximo."));
        }
        for (EventItem e : upcoming) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);

            String when;
            LocalDate d = e.allDay
                    ? AgendaService.toLocal(e.start).toLocalDate()
                    : AgendaService.toLocal(e.start).toLocalDate();
            long nowZero = startOfDay(today);
            long dayDelta = (startOfDay(d) - nowZero) / (24 * 3600_000L);
            if (dayDelta == 0) {
                when = "Hoje";
            } else if (dayDelta == 1) {
                when = "Amanhã";
            } else if (dayDelta < 7) {
                when = Ui.weekdayFull(d);
            } else {
                when = d.getDayOfMonth() + " " + Ui.monthName(d);
            }

            Label dayLbl = new Label(when);
            dayLbl.setStyle("-fx-font-weight:bold;-fx-text-fill:-fx-accent;");
            dayLbl.setMinWidth(90);
            Label timeLbl = new Label(e.allDay ? "dia todo" : AgendaService.hm(e.start));
            timeLbl.getStyleClass().add("event-time");
            timeLbl.setMinWidth(58);
            Label titleLbl = new Label(e.title);
            titleLbl.setWrapText(true);
            HBox.setHgrow(titleLbl, Priority.ALWAYS);

            row.getChildren().addAll(dayLbl, timeLbl, titleLbl);
            content.getChildren().add(row);
        }
        VBox card = Ui.card(Ui.sectionTitle("Próximos compromissos"), content);
        eventsBox.getChildren().setAll(card);
    }

    private void buildTodos(List<TodoItem> due) {
        VBox content = new VBox(6);
        if (due.isEmpty()) {
            content.getChildren().add(Ui.muted("Nenhuma tarefa pendente para hoje."));
        }
        for (TodoItem t : due) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            Label txt = new Label(t.title);
            txt.setWrapText(true);
            HBox.setHgrow(txt, Priority.ALWAYS);
            String suffix = "";
            if (t.dueDate != 0) {
                LocalDate v = LocalDate.now();
                LocalDate dd = AgendaService.toLocal(t.dueDate).toLocalDate();
                suffix = dd.isBefore(v) ? " · atrasada"
                        : (dd.equals(v) ? " · para hoje" : "");
            }
            Label dueLbl = new Label(suffix);
            dueLbl.getStyleClass().add("section-label");
            row.getChildren().addAll(txt, dueLbl);
            content.getChildren().add(row);
        }
        VBox card = Ui.card(Ui.sectionTitle("Tarefas de hoje"), content);
        todosBox.getChildren().setAll(card);
    }

    private void fetchWeather() {
        weatherStatus.setText("Carregando clima…");
        String city = ctx.agenda().config().cityName;
        double lat = ctx.agenda().config().cityLat == null ? -23.5475 : ctx.agenda().config().cityLat;
        double lon = ctx.agenda().config().cityLon == null ? -46.6361 : ctx.agenda().config().cityLon;
        var cfg = ctx.agenda().config();
        var agenda = ctx.agenda();
        com.yourday.app.core.services.HttpSupport.getAsync(
                "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                        + "&current=temperature_2m,weather_code,apparent_temperature,relative_humidity_2m,wind_speed_10m,is_day"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1",
                s -> {
                    WeatherData w = new WeatherData();
                    try {
                        var root = com.google.gson.JsonParser.parseString(s).getAsJsonObject();
                        var cur = root.getAsJsonObject("current");
                        w.now.temp = cur.get("temperature_2m").getAsDouble();
                        w.now.code = cur.get("weather_code").getAsInt();
                        w.now.feelsLike = cur.get("apparent_temperature").getAsDouble();
                        w.now.humidity = cur.get("relative_humidity_2m").getAsInt();
                        w.now.windKmh = cur.get("wind_speed_10m").getAsDouble();
                        var daily = root.getAsJsonObject("daily");
                        w.now.precipitation = 0;
                        w.cityName = cfg.cityName;
                        w.fetchedAt = System.currentTimeMillis();
                        Platform.runLater(() -> showWeather(w));
                    } catch (Exception ex) {
                        Platform.runLater(() -> weatherStatus.setText("Erro ao carregar o clima."));
                    }
                },
                e -> Platform.runLater(() -> weatherStatus.setText("Erro ao carregar o clima: " + e.getMessage())));
        lastWeatherFetch = System.currentTimeMillis();
    }

    private void showWeather(WeatherData w) {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label(WeatherData.icon(w.now.code));
        icon.getStyleClass().add("weather-icon-big");

        VBox info = new VBox(2);
        Label temp = new Label(Math.round(w.now.temp) + "°");
        temp.getStyleClass().add("weather-now");
        Label cond = new Label(WeatherData.condition(w.now.code));
        cond.getStyleClass().add("weather-cond");
        Label meta = new Label(String.format("%s · sensação %.0f° · %d%% umid. · %.0f km/h",
                w.cityName, w.now.feelsLike, w.now.humidity, w.now.windKmh));
        meta.getStyleClass().add("section-label");
        info.getChildren().addAll(temp, cond, meta);

        String dt = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        Label updated = new Label("Atualizado às " + dt);
        updated.getStyleClass().add("updated-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(icon, info, spacer);

        weatherStatus.setText("");
        weatherBox.getChildren().setAll(Ui.card(Ui.sectionTitle("Clima agora"), row, updated));
    }
}

