package com.yourday.app.ui.pages;

import com.yourday.app.core.model.AppConfig;
import com.yourday.app.core.services.AgendaService;
import com.yourday.app.ui.MainView;
import com.yourday.app.ui.Ui;
import com.yourday.app.ui.UiContext;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Configurações: tema, cidade, feeds, notificações e backup/restore. */
public class SettingsPage extends VBox implements MainView.Refreshable {

    private final UiContext ctx;
    private final TextField cityField = new TextField();
    private final Button searchCity = new Button("Buscar e salvar");
    private final TextArea feedsArea = new TextArea();
    private final CheckBox notifOn = new CheckBox("Ativar notificações de agenda");
    private final Spinner<Integer> advance = new Spinner<>(1, 120, 10);

    public SettingsPage(UiContext ctx) {
        this.ctx = ctx;
        setSpacing(12);
        getStyleClass().add("content");

        Label title = new Label("Configurações");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Preferências locais do app");
        sub.getStyleClass().add("section-label");

        AppConfig cfg = ctx.agenda().config();

        cityField.setText(cfg.cityName != null ? cfg.cityName : "");
        cityField.getStyleClass().add("field");
        HBox.setHgrow(cityField, Priority.ALWAYS);
        searchCity.getStyleClass().add("primary-btn");
        searchCity.setOnAction(e -> saveCity());

        feedsArea.setPrefRowCount(5);
        feedsArea.getStyleClass().add("field");
        feedsArea.setText(String.join("\n", cfg.feeds));

        Button saveFeeds = new Button("Salvar feeds");
        saveFeeds.getStyleClass().add("primary-btn");
        saveFeeds.setOnAction(e -> {
            List<String> feeds = new ArrayList<>();
            for (String line : feedsArea.getText().split("\n")) {
                String l = line.trim();
                if (!l.isEmpty()) {
                    feeds.add(l);
                }
            }
            ctx.agenda().config().feeds = feeds;
            ctx.agenda().saveConfig();
        });

        notifOn.setSelected(cfg.notifyEnabled);
        advance.getValueFactory().setValue(cfg.notifyAdvanceMinutes);
        Button saveNotif = new Button("Salvar");
        saveNotif.getStyleClass().add("primary-btn");
        saveNotif.setOnAction(e -> {
            ctx.agenda().config().notifyEnabled = notifOn.isSelected();
            ctx.agenda().config().notifyAdvanceMinutes = advance.getValue();
            ctx.agenda().saveConfig();
        });

        // Tema: dois botões segmentados
        VBox themeToggle = buildThemeToggle();

        Button backup = new Button("Exportar backup (JSON)");
        backup.getStyleClass().add("ghost-btn");
        backup.setOnAction(e -> backup());

        Button restore = new Button("Restaurar backup");
        restore.getStyleClass().add("ghost-btn");
        restore.setOnAction(e -> restore());

        HBox backupRow = new HBox(10, backup, restore);
        backupRow.setAlignment(Pos.CENTER_LEFT);

        Label dataDir = Ui.muted("Pasta de dados: " + ctx.agenda().data().dir());

        getChildren().addAll(
                title, sub,
                Ui.card(Ui.sectionTitle("Tema"), themeToggle),
                Ui.card(Ui.sectionTitle("Cidade do clima"),
                        new HBox(8, cityField, searchCity)),
                Ui.card(Ui.sectionTitle("Feeds de notícias (um por linha)"),
                        feedsArea, saveFeeds),
                Ui.card(Ui.sectionTitle("Notificações"),
                        notifOn,
                        new HBox(8, new Label("Antecedência (min):"), advance, saveNotif)),
                Ui.card(Ui.sectionTitle("Backup e restore"), backupRow, dataDir));
    }

    private VBox buildThemeToggle() {
        Button light = new Button("Claro");
        Button dark = new Button("Escuro");
        light.getStyleClass().add("toggle-light");
        dark.getStyleClass().add("toggle-dark");
        HBox seg = new HBox(4, light, dark);
        seg.getStyleClass().add("toggle-segment");

        Runnable apply = () -> {
            boolean isLight = "light".equals(ctx.theme());
            light.getStyleClass().remove("toggle-active-dark");
            light.getStyleClass().remove("toggle-active-light");
            dark.getStyleClass().remove("toggle-active-dark");
            dark.getStyleClass().remove("toggle-active-light");
            String clazz = isLight ? "toggle-active-light" : "toggle-active-dark";
            (isLight ? light : dark).getStyleClass().add(clazz);
        };
        light.setOnAction(e -> {
            ctx.setTheme("light");
            apply.run();
        });
        dark.setOnAction(e -> {
            ctx.setTheme("dark");
            apply.run();
        });
        apply.run();
        VBox box = new VBox(8, seg);
        return box;
    }

    private void saveCity() {
        String city = cityField.getText().trim();
        if (city.isEmpty()) {
            return;
        }
        searchCity.setDisable(true);
        com.yourday.app.core.services.HttpSupport.getAsync(
                "https://geocoding-api.open-meteo.com/v1/search?name="
                        + java.net.URLEncoder.encode(city, java.nio.charset.StandardCharsets.UTF_8)
                        + "&count=1&language=pt&format=json",
                s -> {
                    try {
                        var json = com.google.gson.JsonParser.parseString(s).getAsJsonObject();
                        var results = json.getAsJsonArray("results");
                        javafx.application.Platform.runLater(() -> {
                            searchCity.setDisable(false);
                            if (results == null || results.isEmpty()) {
                                cityField.setStyle("-fx-text-fill: #d64545;");
                                return;
                            }
                            cityField.setStyle("");
                            var first = results.get(0).getAsJsonObject();
                            AppConfig cfg = ctx.agenda().config();
                            cfg.cityLat = first.get("latitude").getAsDouble();
                            cfg.cityLon = first.get("longitude").getAsDouble();
                            String name = first.get("name").getAsString();
                            if (first.has("admin1")) {
                                name += ", " + first.get("admin1").getAsString();
                            }
                            cfg.cityName = name;
                            ctx.agenda().saveConfig();
                            cityField.setText(name);
                        });
                    } catch (Exception e) {
                        javafx.application.Platform.runLater(() -> searchCity.setDisable(false));
                    }
                },
                e -> javafx.application.Platform.runLater(() -> searchCity.setDisable(false)));
    }

    private void backup() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Exportar backup");
        fc.setInitialFileName("yourday-backup.json");
        File file = fc.showSaveDialog(getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            ctx.agenda().data().backup(file.toPath());
        } catch (Exception e) {
            showToast("Falha no backup: " + e.getMessage());
        }
    }

    private void restore() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Restaurar backup");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        File file = fc.showOpenDialog(getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            ctx.agenda().data().restore(file.toPath());
            ctx.refreshAll();
            showToast("Backup restaurado.");
        } catch (Exception e) {
            showToast("Falha ao restaurar: " + e.getMessage());
        }
    }

    private void showToast(String msg) {
        System.out.println(msg);
    }

    @Override
    public void refresh() {
        // Sem rastreamento contínuo: configurações são aplicadas ao salvar.
    }
}