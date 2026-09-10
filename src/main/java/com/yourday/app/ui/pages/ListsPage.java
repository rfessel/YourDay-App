package com.yourday.app.ui.pages;

import com.yourday.app.core.model.Lista;
import com.yourday.app.core.model.ListaItem;
import com.yourday.app.ui.MainView;
import com.yourday.app.ui.Ui;
import com.yourday.app.ui.UiContext;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/** Aba Listas: listas gerais e de compras (como a ListasPage do widget). */
public class ListsPage extends VBox implements MainView.Refreshable {

    private final UiContext ctx;
    private final VBox listBox = new VBox(8);
    private final VBox doneBox = new VBox(8);
    private final TextField newListField = new TextField();
    private final Button historyBtn = new Button();
    private final boolean[] showHistory = {false};
    private String expandedId;

    public ListsPage(UiContext ctx) {
        this.ctx = ctx;
        setSpacing(12);
        getStyleClass().add("content");

        Label title = new Label("Suas listas");
        title.getStyleClass().add("page-title");
        Label sub = Ui.muted("Listas gerais, compras ou o que precisar organizar…");

        newListField.setPromptText("Nome da nova lista…");
        newListField.getStyleClass().add("field");
        HBox.setHgrow(newListField, Priority.ALWAYS);
        newListField.setOnAction(e -> addList());

        Button add = new Button("+");
        add.getStyleClass().add("primary-btn");
        add.setOnAction(e -> addList());

        HBox inputRow = new HBox(8, newListField, add);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        historyBtn.getStyleClass().add("ghost-btn");
        historyBtn.setOnAction(e -> {
            showHistory[0] = !showHistory[0];
            render();
        });

        getChildren().addAll(title, sub, inputRow, listBox, historyBtn, doneBox);
        refresh();
    }

    private void addList() {
        String name = newListField.getText().trim();
        if (!name.isEmpty()) {
            ctx.agenda().addList(name);
            newListField.clear();
            refresh();
        }
    }

    @Override
    public void refresh() {
        render();
    }

    private void render() {
        List<Lista> all = ctx.agenda().lists();
        List<Lista> active = new ArrayList<>();
        List<Lista> done = new ArrayList<>();
        for (Lista l : all) {
            (l.done ? done : active).add(l);
        }

        listBox.getChildren().clear();
        if (active.isEmpty()) {
            listBox.getChildren().add(Ui.muted("Nenhuma lista. Crie uma acima."));
        }
        for (Lista l : active) {
            boolean expanded = l.id.equals(expandedId);
            listBox.getChildren().add(buildListCard(l, expanded));
        }

        historyBtn.setVisible(!done.isEmpty());
        historyBtn.setText((showHistory[0] ? "▼ " : "▶ ") + "Histórico de listas concluídas ("
                + done.size() + ")");

        doneBox.getChildren().clear();
        doneBox.setVisible(showHistory[0] && !done.isEmpty());
        if (showHistory[0] && !done.isEmpty()) {
            for (Lista l : done) {
                doneBox.getChildren().add(buildDoneRow(l));
            }
        }
    }

    private VBox buildListCard(Lista l, boolean expanded) {
        VBox card = new VBox(8);
        card.getStyleClass().add("card");

        Button expandBtn = toolBtn(expanded ? "▲" : "▼");
        expandBtn.setOnAction(e -> {
            expandedId = expanded ? null : l.id;
            render();
        });

        Label name = new Label(l.name);
        name.setStyle("-fx-font-weight:bold;-fx-font-size:13px;");
        HBox.setHgrow(name, Priority.ALWAYS);

        Label count = new Label(l.items.size() + " item(ns)");
        count.getStyleClass().add("section-label");
        count.setMinWidth(76);

        Button complete = toolBtn("✓");
        complete.setOnAction(e -> {
            ctx.agenda().setListDone(l.id, true);
            render();
        });
        Button addItem = toolBtn("+");
        addItem.setOnAction(e -> {
            expandedId = l.id;
            render();
        });
        Button remove = toolBtn("×");
        remove.setOnAction(e -> {
            ctx.agenda().removeList(l.id);
            if (l.id.equals(expandedId)) {
                expandedId = null;
            }
            render();
        });

        HBox header = new HBox(6, expandBtn, name, count, complete, addItem, remove);
        header.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().add(header);

        if (expanded) {
            card.getChildren().add(new Separator());
            for (ListaItem item : l.items) {
                card.getChildren().add(buildItemRow(l, item));
            }
            card.getChildren().add(buildItemInputRow(l));
        }
        return card;
    }

    private HBox buildItemRow(Lista l, ListaItem item) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        CheckBox cb = new CheckBox();
        cb.setSelected(item.done);
        cb.setOnAction(e -> {
            ctx.agenda().toggleListItem(l.id, item.id);
            render();
        });

        Label label = new Label(item.text);
        label.setWrapText(true);
        HBox.setHgrow(label, Priority.ALWAYS);
        if (item.done) {
            label.getStyleClass().add("list-item-done");
        }

        Button remove = toolBtn("×");
        remove.setOnAction(e -> {
            ctx.agenda().removeListItem(l.id, item.id);
            render();
        });

        row.getChildren().addAll(cb, label, remove);
        return row;
    }

    private HBox buildItemInputRow(Lista l) {
        TextField input = new TextField();
        input.setPromptText("Novo item…");
        input.getStyleClass().add("field");
        HBox.setHgrow(input, Priority.ALWAYS);
        Runnable submit = () -> {
            String text = input.getText().trim();
            if (!text.isEmpty()) {
                ctx.agenda().addListItem(l.id, text);
                render();
            }
        };
        input.setOnAction(e -> submit.run());

        Button add = new Button("+");
        add.getStyleClass().add("primary-btn");
        add.setOnAction(e -> submit.run());

        HBox row = new HBox(8, input, add);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private VBox buildDoneRow(Lista l) {
        VBox card = new VBox(6);
        card.getStyleClass().add("card");

        Label name = new Label(l.name);
        name.setStyle("-fx-font-style:italic;-fx-opacity:0.7;");
        HBox.setHgrow(name, Priority.ALWAYS);

        Label count = new Label(l.items.size() + " item(ns)");
        count.getStyleClass().add("section-label");
        count.setMinWidth(76);

        Button restore = toolBtn("↺");
        restore.setOnAction(e -> {
            ctx.agenda().setListDone(l.id, false);
            render();
        });
        Button remove = toolBtn("×");
        remove.setOnAction(e -> {
            ctx.agenda().removeList(l.id);
            render();
        });

        HBox row = new HBox(6, name, count, restore, remove);
        row.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().add(row);
        return card;
    }

    private Button toolBtn(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("toolbtn");
        b.setMinSize(28, 28);
        return b;
    }
}