package com.yourday.app.ui.pages;

import com.yourday.app.core.model.EventItem;
import com.yourday.app.core.services.AgendaService;
import com.yourday.app.ui.MainView;
import com.yourday.app.ui.Ui;
import com.yourday.app.ui.UiContext;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Agenda: calendário mensal + compromissos do dia selecionado. */
public class AgendaPage extends VBox implements MainView.Refreshable {

    private final UiContext ctx;
    private int viewYear;
    private int viewMonth; // 0-based
    private LocalDate selected;
    private final Label monthTitle = new Label();
    private final GridPane grid = new GridPane();
    private final Label dayTitle = new Label();
    private final VBox eventsBox = new VBox(8);

    public AgendaPage(UiContext ctx) {
        this.ctx = ctx;
        setSpacing(10);
        getStyleClass().add("content");

        LocalDate now = AgendaService.today();
        viewYear = now.getYear();
        viewMonth = now.getMonthValue() - 1;
        selected = now;

        Button prev = new Button("‹");
        prev.getStyleClass().add("toolbtn");
        prev.setOnAction(e -> shiftMonth(-1));
        monthTitle.getStyleClass().add("page-title");
        monthTitle.setAlignment(Pos.CENTER);
        Button next = new Button("›");
        next.getStyleClass().add("toolbtn");
        next.setOnAction(e -> shiftMonth(1));

        Button today = new Button("Hoje");
        today.getStyleClass().add("ghost-btn");
        today.setOnAction(e -> {
            LocalDate t = AgendaService.today();
            viewYear = t.getYear();
            viewMonth = t.getMonthValue() - 1;
            selected = t;
            rebuild();
        });

        HBox monthBar = new HBox(8, prev, monthTitle, next);
        monthBar.setAlignment(Pos.CENTER);
        monthBar.setMaxWidth(Region.USE_PREF_SIZE);

        Button addBtn = new Button("＋ Novo compromisso");
        addBtn.getStyleClass().add("primary-btn");
        addBtn.setOnAction(e -> openDialog(null));

        BorderPane top = new BorderPane();
        BorderPane.setAlignment(monthBar, Pos.CENTER);
        top.setLeft(new HBox(today));
        top.setCenter(monthBar);
        top.setRight(addBtn);

        grid.setHgap(4);
        grid.setVgap(4);
        grid.setAlignment(Pos.CENTER);

        // Calendário centralizado dentro do card
        VBox cal = new VBox(6, weekHeader(), grid);
        HBox calCenter = new HBox(cal);
        calCenter.setAlignment(Pos.CENTER);

        VBox calBox = Ui.card(top, calCenter);
        calBox.setSpacing(8);

        HBox dayHead = new HBox(10, dayTitle);
        dayHead.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(dayTitle, Priority.ALWAYS);

        getChildren().addAll(calBox, dayHead, eventsBox);
        rebuild();
    }

    private GridPane weekHeader() {
        GridPane g = new GridPane();
        g.setHgap(4);
        List<String> letters = Ui.weekdayLetters();
        for (int i = 0; i < 7; i++) {
            Label l = new Label(letters.get(i));
            l.getStyleClass().add("cal-header");
            l.setPrefWidth(40);
            g.add(l, i, 0);
        }
        return g;
    }

    private void shiftMonth(int delta) {
        viewMonth += delta;
        if (viewMonth < 0) {
            viewMonth = 11;
            viewYear--;
        } else if (viewMonth > 11) {
            viewMonth = 0;
            viewYear++;
        }
        rebuild();
    }

    @Override
    public void refresh() {
        rebuild();
    }

    private void rebuild() {
        monthTitle.setText(capitalize(Ui.monthName(LocalDate.of(viewYear, viewMonth + 1, 1)))
                + " " + viewYear);
        grid.getChildren().clear();

        int firstColumn = Ui.dayOfWeekIndex(LocalDate.of(viewYear, viewMonth + 1, 1));
        int dim = LocalDate.of(viewYear, viewMonth + 1, 1).lengthOfMonth();
        LocalDate today = AgendaService.today();

        List<EventItem> monthEvents = ctx.agenda().occurrences(
                AgendaService.startOfDay(LocalDate.of(viewYear, viewMonth + 1, 1)),
                AgendaService.startOfDay(LocalDate.of(viewYear, viewMonth + 1, dim)) + 1);

        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 7; c++) {
                int day = r * 7 + c - firstColumn + 1;
                if (day < 1 || day > dim) {
                    continue;
                }
                LocalDate d = LocalDate.of(viewYear, viewMonth + 1, day);
                grid.add(buildDayCell(d, today, monthEvents), c, r + 1);
            }
        }
        dayTitle.setText(capitalize(Ui.weekdayFull(selected)) + ", " + selected.getDayOfMonth()
                + " de " + Ui.monthName(selected));
        rebuildEvents();
    }

    private StackPane buildDayCell(LocalDate d, LocalDate today, List<EventItem> monthEvents) {
        StackPane cell = new StackPane();
        cell.getStyleClass().add("cal-cell");
        cell.setPrefSize(40, 34);

        Label num = new Label(String.valueOf(d.getDayOfMonth()));
        StackPane.setAlignment(num, Pos.TOP_CENTER);
        cell.getChildren().add(num);

        boolean has = monthEvents.stream().anyMatch(e ->
                AgendaService.toLocal(e.start).toLocalDate().equals(d));
        if (has) {
            Label dots = new Label("●");
            dots.getStyleClass().add("dots");
            StackPane.setAlignment(dots, Pos.BOTTOM_CENTER);
            cell.getChildren().add(dots);
        }

        if (d.equals(today)) {
            cell.getStyleClass().add("cal-cell-today");
        }
        if (d.equals(selected)) {
            cell.getStyleClass().add("cal-cell-selected");
        }

        cell.setOnMouseClicked(e -> {
            selected = d;
            rebuild();
        });
        return cell;
    }

    private void rebuildEvents() {
        eventsBox.getChildren().clear();
        List<EventItem> list = ctx.agenda().eventsForDay(selected);
        if (list.isEmpty()) {
            eventsBox.getChildren().add(Ui.muted("Sem compromissos neste dia."));
            return;
        }
        for (EventItem e : list) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);

            Label time = new Label(e.allDay ? "dia todo" : AgendaService.hm(e.start));
            time.getStyleClass().add("event-time");
            time.setMinWidth(80);

            VBox text = new VBox(1);
            Label title = new Label(e.title);
            title.setWrapText(true);
            text.getChildren().add(title);
            if (!e.location.isEmpty()) {
                text.getChildren().add(Ui.muted("📍 " + e.location));
            }
            HBox.setHgrow(text, Priority.ALWAYS);

            Button edit = new Button("✎");
            edit.getStyleClass().add("toolbtn");
            String masterId = e.id;
            edit.setOnAction(ev -> openDialog(ctx.agenda().events().stream()
                    .filter(m -> m.id != null && m.id.equals(masterId)).findFirst().orElse(e)));

            Button del = new Button("✕");
            del.getStyleClass().add("toolbtn");
            del.setId("delBtn");
            del.setOnAction(ev -> {
                ctx.agenda().deleteEvent(e.id);
                rebuild();
            });

            row.getChildren().addAll(time, text, edit, del);
            eventsBox.getChildren().add(row);
        }
    }

    private void openDialog(EventItem existing) {
        EventDialog dialog = new EventDialog(ctx, existing, selected);
        dialog.showAndWait();
        if (existing == null) {
            selected = dialog.pickedDate();
        }
        rebuild();
    }

    static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Diálogo modal de novo/editar compromisso. */
    static class EventDialog {

        private final UiContext ui;
        private final EventItem existing;
        private final TextField titleField = new TextField();
        private final TextArea descField = new TextArea();
        private final TextField locField = new TextField();
        private final DatePicker datePicker = new DatePicker();
        private final TextField startField = new TextField("09:00");
        private final TextField endField = new TextField("10:00");
        private final CheckBox allDay = new CheckBox("Dia inteiro");
        private final ComboBox<String> repeat = new ComboBox<>();
        private final Stage stage = new Stage();
        private LocalDate pickedDate;

        EventDialog(UiContext ui, EventItem existing, LocalDate defaultDate) {
            this.ui = ui;
            this.existing = existing;
            datePicker.setValue(defaultDate);

            repeat.getItems().addAll("Não repete", "Diariamente", "Semanalmente",
                    "Dias úteis", "Mensalmente", "Anualmente");
            repeat.setValue("Não repete");

            if (existing != null) {
                stage.setTitle("Editar compromisso");
                titleField.setText(existing.title);
                descField.setText(existing.description);
                locField.setText(existing.location);
                LocalDateTime s = AgendaService.toLocal(existing.start);
                datePicker.setValue(s.toLocalDate());
                allDay.setSelected(existing.allDay);
                if (existing.allDay) {
                    startField.setDisable(true);
                    endField.setDisable(true);
                } else {
                    startField.setText(s.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
                    endField.setText(AgendaService.toLocal(existing.end).toLocalTime()
                            .format(DateTimeFormatter.ofPattern("HH:mm")));
                }
                repeat.setValue(toRepeatLabel(existing.rrule));
            } else {
                stage.setTitle("Novo compromisso");
            }

            allDay.selectedProperty().addListener((o, a, b) -> {
                startField.setDisable(b);
                endField.setDisable(b);
            });

            descField.setPrefRowCount(3);
            descField.setPromptText("Descrição (opcional)");

            Button cancel = new Button("Cancelar");
            cancel.setCancelButton(true);
            cancel.getStyleClass().add("ghost-btn");
            cancel.setOnAction(e -> stage.close());

            Button save = new Button(existing == null ? "Adicionar" : "Salvar");
            save.setDefaultButton(true);
            save.getStyleClass().add("primary-btn");
            save.setOnAction(e -> save());

            HBox buttons = new HBox(10, cancel, save);
            buttons.setAlignment(Pos.CENTER_RIGHT);

            VBox box = new VBox(10);
            box.getStyleClass().add("content");
            box.getChildren().addAll(
                    form("Título", titleField),
                    form("Data", datePicker),
                    new HBox(8, form("Início (HH:mm)", startField), form("Término (HH:mm)", endField)),
                    allDay,
                    form("Repetição", repeat),
                    form("Local", locField),
                    form("Descrição", descField),
                    buttons);

            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new javafx.scene.Scene(box, 460, 520));
            stage.getScene().getStylesheets().setAll(
                    com.yourday.app.app.YourDayApp.themeUrl(ui.theme()));
        }

        private static VBox form(String label, javafx.scene.Node field) {
            Label l = new Label(label);
            l.getStyleClass().add("section-label");
            field.getStyleClass().add("field");
            VBox v = new VBox(3, l, field);
            return v;
        }

        private void save() {
            String title = titleField.getText().trim();
            if (title.isEmpty()) {
                titleField.requestFocus();
                return;
            }
            LocalDate d = datePicker.getValue();
            pickedDate = d;
            EventItem e = existing != null ? existing : new EventItem();
            e.title = title;
            e.description = descField.getText().trim();
            e.location = locField.getText().trim();
            e.calendar = "local";

            if (allDay.isSelected()) {
                e.allDay = true;
                e.start = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                e.end = e.start + 86_400_000L;
            } else {
                e.allDay = false;
                LocalTime st = parseTime(startField.getText(), 9);
                LocalTime en = parseTime(endField.getText(), 10);
                e.start = d.atTime(st).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                e.end = d.atTime(en).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                if (e.end <= e.start) {
                    e.end = e.start + 3_600_000L;
                }
            }
            e.rrule = repeatRrule(repeat.getValue(), d);

            if (existing != null) {
                ui.agenda().updateEvent(e);
            } else {
                ui.agenda().addEvent(e);
            }
            stage.close();
        }

        LocalDate pickedDate() {
            return pickedDate == null ? datePicker.getValue() : pickedDate;
        }

        void showAndWait() {
            stage.showAndWait();
        }
    }

    static LocalTime parseTime(String s, int fallback) {
        try {
            String[] p = s.trim().split(":");
            return LocalTime.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
        } catch (Exception e) {
            return LocalTime.of(fallback, 0);
        }
    }

    static String repeatRrule(String label, LocalDate d) {
        if (label == null || label.equals("Não repete")) {
            return "";
        }
        switch (label) {
            case "Diariamente":
                return "FREQ=DAILY";
            case "Semanalmente":
                return "FREQ=WEEKLY";
            case "Dias úteis":
                return "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR";
            case "Mensalmente":
                return "FREQ=MONTHLY;BYMONTHDAY=" + d.getDayOfMonth();
            case "Anualmente":
                return "FREQ=YEARLY";
            default:
                return "";
        }
    }

    static String toRepeatLabel(String rrule) {
        if (rrule == null || rrule.isBlank()) {
            return "Não repete";
        }
        String r = rrule.toUpperCase();
        if (r.contains("FREQ=DAILY")) {
            return "Diariamente";
        }
        if (r.contains("FREQ=WEEKLY") && r.contains("BYDAY=MO,TU,WE,TH,FR")) {
            return "Dias úteis";
        }
        if (r.contains("FREQ=WEEKLY")) {
            return "Semanalmente";
        }
        if (r.contains("FREQ=MONTHLY")) {
            return "Mensalmente";
        }
        if (r.contains("FREQ=YEARLY")) {
            return "Anualmente";
        }
        return "Não repete";
    }
}