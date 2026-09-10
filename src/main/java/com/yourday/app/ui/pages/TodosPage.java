package com.yourday.app.ui.pages;

import com.yourday.app.core.model.TodoItem;
import com.yourday.app.core.services.AgendaService;
import com.yourday.app.ui.MainView;
import com.yourday.app.ui.Ui;
import com.yourday.app.ui.UiContext;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

/** Tarefas com prazo opcional + histórico dos concluídos recentes. */
public class TodosPage extends VBox implements MainView.Refreshable {

    private final UiContext ctx;
    private final TextField input = new TextField();
    private final DatePicker duePicker = new DatePicker();
    private final VBox pendingBox = new VBox(6);
    private final VBox doneBox = new VBox(6);

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
        VBox doneCard = Ui.card(Ui.sectionTitle("Concluídos (7 dias)"), doneBox);

        getChildren().addAll(title, sub, inputRow, pendingCard, doneCard);
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
        LocalDate weekAgo = today.minusDays(7);

        List<TodoItem> pending = all.stream()
                .filter(t -> !t.done)
                .sorted(Comparator.comparingLong(t -> t.dueDate == 0 ? Long.MAX_VALUE : t.dueDate))
                .toList();
        List<TodoItem> done = all.stream()
                .filter(t -> t.done)
                .sorted(Comparator.comparingLong((TodoItem t) -> t.createdAt).reversed())
                .toList();

        pendingBox.getChildren().clear();
        if (pending.isEmpty()) {
            pendingBox.getChildren().add(Ui.muted("Nenhuma tarefa pendente."));
        }
        for (TodoItem t : pending) {
            pendingBox.getChildren().add(pendingRow(t, today));
        }

        doneBox.getChildren().clear();
        long recent = done.stream().filter(t ->
                LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(t.createdAt),
                        ZoneId.systemDefault()).toLocalDate().isAfter(weekAgo) ||
                        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(t.createdAt),
                                ZoneId.systemDefault()).toLocalDate().equals(weekAgo)).count();
        if (recent == 0) {
            doneBox.getChildren().add(Ui.muted("Nada concluído recentemente."));
        }
        for (TodoItem t : done) {
            LocalDate dd = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(t.createdAt),
                    ZoneId.systemDefault()).toLocalDate();
            if (dd.isBefore(weekAgo)) {
                continue;
            }
            doneBox.getChildren().add(doneRow(t, dd));
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

    private HBox doneRow(TodoItem t, LocalDate dd) {
        HBox row = new HBox(8);
        row.getStyleClass().add("todo-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setOpacity(0.65);

        Label text = new Label("✓ " + t.title);
        text.setWrapText(true);
        HBox.setHgrow(text, Priority.ALWAYS);
        Label when = Ui.muted("em " + dd.getDayOfMonth() + " " + Ui.monthName(dd));
        row.getChildren().addAll(text, when);
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