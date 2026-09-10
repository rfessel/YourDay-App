package com.yourday.app.ui.pages;

import com.yourday.app.core.model.TodoItem;
import com.yourday.app.core.services.AgendaService;
import com.yourday.app.ui.MainView;
import com.yourday.app.ui.Ui;
import com.yourday.app.ui.UiContext;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

/** Tarefas com prazo opcional + popup de tarefas finalizadas. */
public class TodosPage extends VBox implements MainView.Refreshable {

    private final UiContext ctx;
    private final TextField input = new TextField();
    private final DatePicker duePicker = new DatePicker();
    private final VBox pendingBox = new VBox(6);
    private final Button doneBtn = new Button();

    public TodosPage(UiContext ctx) {
        this.ctx = ctx;
        setSpacing(12);
        getStyleClass().add("content");

        Label title = new Label("Tarefas");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Planeje o seu dia e acompanhe o que já concluiu");
        sub.getStyleClass().add("section-label");

        input.setPromptText("Nova tarefa…");
        input.getStyleClass().add("field");
        duePicker.getStyleClass().add("field");
        duePicker.setPromptText("Prazo (opcional)");
        duePicker.setEditable(false);

        Button addBtn = new Button("Adicionar");
        addBtn.getStyleClass().add("primary-btn");
        addBtn.setOnAction(e -> addTodo());
        input.setOnAction(e -> addTodo());

        HBox inputRow = new HBox(8, input, duePicker, addBtn);
        inputRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(input, Priority.ALWAYS);

        VBox pendingCard = Ui.card(Ui.sectionTitle("Pendentes"), pendingBox);

        doneBtn.getStyleClass().add("ghost-btn");
        doneBtn.setOnAction(e -> openDonePopup());

        Region grow = new Region();
        VBox.setVgrow(grow, Priority.ALWAYS);
        HBox footer = new HBox(doneBtn);
        footer.setAlignment(Pos.BOTTOM_RIGHT);

        getChildren().addAll(title, sub, inputRow, pendingCard, grow, footer);
        refresh();
    }

    private void addTodo() {
        String t = input.getText().trim();
        if (t.isEmpty()) {
            return;
        }
        long due = 0;
        LocalDate d = duePicker.getValue();
        if (d != null) {
            due = AgendaService.endOfDay(d);
        }
        ctx.agenda().addTodo(t, due);
        input.clear();
        duePicker.setValue(null);
        refresh();
    }

    @Override
    public void refresh() {
        List<TodoItem> all = ctx.agenda().todos();
        LocalDate today = AgendaService.today();

        long doneCount = all.stream().filter(t -> t.done).count();
        doneBtn.setText("✓ Finalizados (" + doneCount + ")");

        List<TodoItem> pending = all.stream()
                .filter(t -> !t.done)
                .sorted(Comparator.comparingLong(t -> t.dueDate == 0 ? Long.MAX_VALUE : t.dueDate))
                .toList();

        pendingBox.getChildren().clear();
        if (pending.isEmpty()) {
            pendingBox.getChildren().add(Ui.muted("Nenhuma tarefa pendente."));
        }
        for (TodoItem t : pending) {
            pendingBox.getChildren().add(pendingRow(t, today));
        }
    }

    private HBox pendingRow(TodoItem t, LocalDate today) {
        HBox row = new HBox(8);
        row.getStyleClass().add("todo-row");
        row.setAlignment(Pos.CENTER_LEFT);

        CheckBox check = new CheckBox();
        check.setOnAction(e -> {
            t.done = check.isSelected();
            ctx.agenda().saveTodo(t);
            refresh();
        });

        Label text = new Label(t.title);
        text.setWrapText(true);
        HBox.setHgrow(text, Priority.ALWAYS);

        Label due = new Label();
        due.getStyleClass().add("section-label");
        String s = dueLabel(t, today);
        if (s != null) {
            due.setText(s);
            due.setStyle(s.contains("atrasada")
                    ? "-fx-text-fill: #d64545; -fx-font-weight: bold;" : "");
        }

        Button del = new Button("✕");
        del.getStyleClass().add("toolbtn");
        del.setOnAction(e -> {
            ctx.agenda().removeTodo(t.id);
            refresh();
        });

        row.getChildren().addAll(check, text, due, del);
        return row;
    }

    private void openDonePopup() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Tarefas finalizadas");

        Label heading = new Label("Tarefas finalizadas");
        heading.getStyleClass().add("page-title");

        VBox list = new VBox(6);
        ScrollPane scroll = Ui.scroll(list);
        scroll.setPrefViewportHeight(380);

        Button close = new Button("Fechar");
        close.setDefaultButton(true);
        close.getStyleClass().add("ghost-btn");
        close.setOnAction(e -> stage.close());
        HBox footer = new HBox(close);
        footer.setAlignment(Pos.CENTER_RIGHT);

        VBox box = new VBox(10);
        box.getStyleClass().add("content");
        box.getChildren().addAll(heading, scroll, footer);

        stage.setScene(new Scene(box, 520, 480));
        stage.getScene().getStylesheets().setAll(
                com.yourday.app.app.YourDayApp.themeUrl(ctx.theme()));
        renderPopupList(list);
        stage.showAndWait();
        refresh();
    }

    private void renderPopupList(VBox list) {
        list.getChildren().clear();
        List<TodoItem> done = ctx.agenda().todos().stream()
                .filter(t -> t.done)
                .sorted(Comparator.comparingLong((TodoItem t) -> t.createdAt).reversed())
                .toList();
        if (done.isEmpty()) {
            list.getChildren().add(Ui.muted("Nenhuma tarefa finalizada."));
            return;
        }
        for (TodoItem t : done) {
            list.getChildren().add(popupDoneRow(t, list));
        }
    }

    private HBox popupDoneRow(TodoItem t, VBox list) {
        HBox row = new HBox(8);
        row.getStyleClass().add("todo-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setOpacity(0.8);

        Label text = new Label("✓ " + t.title);
        text.setWrapText(true);
        HBox.setHgrow(text, Priority.ALWAYS);

        LocalDate dd = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(t.createdAt),
                ZoneId.systemDefault()).toLocalDate();
        Label when = Ui.muted("finalizada em " + dd.getDayOfMonth() + " " + Ui.monthName(dd));
        when.setMinWidth(120);

        Button restore = new Button("↺");
        restore.getStyleClass().add("toolbtn");
        restore.setOnAction(e -> {
            t.done = false;
            ctx.agenda().saveTodo(t);
            renderPopupList(list);
        });

        Button del = new Button("✕");
        del.getStyleClass().add("toolbtn");
        del.setOnAction(e -> {
            ctx.agenda().removeTodo(t.id);
            renderPopupList(list);
        });

        row.getChildren().addAll(text, when, restore, del);
        return row;
    }

    static String dueLabel(TodoItem t, LocalDate today) {
        if (t.dueDate == 0) {
            return null;
        }
        LocalDate dd = AgendaService.toLocal(t.dueDate).toLocalDate();
        if (dd.isBefore(today)) {
            return "atrasada";
        }
        if (dd.equals(today)) {
            return "para hoje";
        }
        if (dd.equals(today.plusDays(1))) {
            return "amanhã";
        }
        return "para " + dd.getDayOfMonth() + " " + Ui.monthName(dd);
    }
}