package com.yourday.app.ui.pages;

import com.yourday.app.core.model.AppConfig;
import com.yourday.app.core.services.WeatherService;
import com.yourday.app.ui.MainView;
import com.yourday.app.ui.Ui;
import com.yourday.app.ui.UiContext;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Configurações organizadas por abas (Geral, Clima, Notícias, Dados). */
public class SettingsPage extends VBox implements MainView.Refreshable {

    private final UiContext ctx;
    private final TextArea feedsArea = new TextArea();
    private final CheckBox notifOn = new CheckBox("Ativar notificações de agenda");
    private final Spinner<Integer> advance = new Spinner<>(1, 120, 10);

    // Aba Clima
    private final TextField cityField = new TextField();
    private final Label climaStatus = new Label();
    private final VBox cityResultsBox = new VBox(4);
    private final VBox extraCitiesBox = new VBox(4);
    private final Label principalLabel = new Label();
    private final Button buscarBtn = new Button("Buscar");
    private final Button salvarBtn = new Button("Salvar");

    public SettingsPage(UiContext ctx) {
        this.ctx = ctx;
        setSpacing(12);
        getStyleClass().add("content");

        Label title = new Label("Configurações");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Preferências locais do app");
        sub.getStyleClass().add("section-label");

        AppConfig cfg = ctx.agenda().config();
        int pref = cfg.notifyAdvanceMinutes > 0 ? cfg.notifyAdvanceMinutes : 10;

        feedsArea.setPrefRowCount(6);
        feedsArea.getStyleClass().add("field");
        feedsArea.setText(String.join("\n", cfg.feeds));

        notifOn.setSelected(cfg.notifyEnabled);
        advance.getValueFactory().setValue(pref);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setSide(Side.TOP);
        tabs.getTabs().addAll(
                geralTab(),
                climaTab(),
                noticiasTab(),
                dadosTab());

        getChildren().addAll(title, sub, tabs);
        VBox.setVgrow(tabs, Priority.ALWAYS);
    }

    // ---------------- Abas ----------------

    private Tab geralTab() {
        Button saveNotif = new Button("Salvar");
        saveNotif.getStyleClass().add("primary-btn");
        saveNotif.setOnAction(e -> {
            ctx.agenda().config().notifyEnabled = notifOn.isSelected();
            ctx.agenda().config().notifyAdvanceMinutes = advance.getValue();
            ctx.agenda().saveConfig();
            saveNotif.setText("Salvo ✓");
        });
        HBox notifRow = new HBox(8, new Label("Antecedência (min):"), advance, saveNotif);
        notifRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = pageBox(
                Ui.card(Ui.sectionTitle("Tema"), buildThemeToggle()),
                Ui.card(Ui.sectionTitle("Notificações"),
                        notifOn, notifRow));
        return tab("Geral", content);
    }

    private Tab climaTab() {
        AppConfig cfg = ctx.agenda().config();
        cityField.setText(cfg.cityName != null ? cfg.cityName : "");
        cityField.getStyleClass().add("field");
        cityField.setPromptText("Digite uma cidade…");
        HBox.setHgrow(cityField, Priority.ALWAYS);

        buscardBtn("buscar");
        buscardBtn("salvar");

        Button geoBtn = new Button("Minha localização");
        geoBtn.getStyleClass().add("ghost-btn");
        geoBtn.setOnAction(e -> locateByIp());

        HBox row = new HBox(8, cityField, buscarBtn, salvarBtn, geoBtn);
        row.setAlignment(Pos.CENTER_LEFT);

        climaStatus.getStyleClass().add("section-label");
        SelectiveBox resultsBox = new SelectiveBox("Resultados da busca", cityResultsBox);
        SelectiveBox extrasBox = new SelectiveBox("Cidades complementares", extraCitiesBox);
        principalsLabel();

        VBox content = pageBox(
                Ui.card(Ui.sectionTitle("Cidade do clima"),
                        row,
                        climaStatus,
                        resultsBox.box(),
                        principalLabel,
                        extrasBox.box()));
        return tab("Clima", content);
    }

    /** Botões Buscar e Salvar (dois botões distintos sobre o mesmo campo). */
    private void buscardBtn(String kind) {
        Button b = kind.equals("buscar") ? buscarBtn : salvarBtn;
        b.getStyleClass().add(kind.equals("buscar") ? "ghost-btn" : "primary-btn");
        if (kind.equals("buscar")) {
            b.setOnAction(e -> doBuscar());
        } else {
            b.setOnAction(e -> doSalvar());
        }
    }

    /** Rótulo da cidade principal atualizada após cada mudança. */
    private void principalsLabel() {
        AppConfig cfg = ctx.agenda().config();
        principalLabel.getStyleClass().add("section-label");
        principalLabel.setText(cfg.cityName != null && !cfg.cityName.isBlank()
                ? "Cidade principal: " + cfg.cityName : "Nenhuma cidade principal definida.");
        renderExtraCities(cfg);
    }

    private Tab noticiasTab() {
        Button saveFeeds = new Button("Salvar feeds");
        saveFeeds.getStyleClass().add("primary-btn");
        saveFeeds.setOnAction(e -> saveFeeds());

        VBox content = pageBox(
                Ui.card(Ui.sectionTitle("Feeds de notícias (um por linha)"),
                        feedsArea, saveFeeds));
        return tab("Notícias", content);
    }

    private Tab dadosTab() {
        Button backup = new Button("Exportar backup (JSON)");
        backup.getStyleClass().add("ghost-btn");
        backup.setOnAction(e -> backup());

        Button restore = new Button("Restaurar backup");
        restore.getStyleClass().add("ghost-btn");
        restore.setOnAction(e -> restore());

        HBox backupRow = new HBox(10, backup, restore);
        backupRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = pageBox(
                Ui.card(Ui.sectionTitle("Backup e restore"),
                        backupRow,
                        Ui.muted("Pasta de dados: " + ctx.agenda().data().dir())));
        return tab("Dados", content);
    }

    private static Tab tab(String name, VBox content) {
        Tab t = new Tab(name, content);
        t.setClosable(false);
        return t;
    }

    private static VBox pageBox(javafx.scene.Node... cards) {
        VBox box = new VBox(12, cards);
        box.getStyleClass().add("content");
        return box;
    }

    /** Caixa com título opcional + conteúdo (usada para resultados e adicionais). */
    private record SelectiveBox(String title, VBox content) {
        VBox box() {
            VBox w = new VBox(4);
            if (title != null && !title.isBlank()) {
                Label l = new Label(title);
                l.getStyleClass().add("section-label");
                w.getChildren().add(l);
            }
            w.getChildren().add(content);
            return w;
        }
    }

    // ---------------- Ações: Clima ----------------

    private void doBuscar() {
        String q = cityField.getText().trim();
        if (q.isEmpty()) {
            return;
        }
        buscarBtn.setDisable(true);
        climaStatus.setText("Buscando " + q + "…");
        cityResultsBox.getChildren().clear();
        cities.search(q, 5, this::renderCityResults, e -> busResultError(e, q));
    }

    private void busResultError(Exception e, String q) {
        Platform.runLater(() -> {
            buscarBtn.setDisable(false);
            climaStatus.setText("Erro na busca de \"" + q + "\": " + e.getMessage());
        });
    }

    private void renderCityResults(List<WeatherService.CityResult> results) {
        Platform.runLater(() -> {
            buscarBtn.setDisable(false);
            cityResultsBox.getChildren().clear();
            if (results.isEmpty()) {
                climaStatus.setText("Nenhuma cidade encontrada.");
                return;
            }
            climaStatus.setText(results.size() + " resultado(s).");
            for (WeatherService.CityResult r : results) {
                HBox row = new HBox(6);
                row.setAlignment(Pos.CENTER_LEFT);
                Label name = new Label(r.label());
                name.getStyleClass().add("card-sub");
                HBox.setHgrow(name, Priority.ALWAYS);
                Button main = new Button("← principal");
                main.getStyleClass().add("toolbtn");
                main.setOnAction(e -> setAsMain(r));
                Button add = new Button("+ adicional");
                add.getStyleClass().add("toolbtn");
                add.setOnAction(e -> addExtraCity(r));
                row.getChildren().addAll(name, main, add);
                cityResultsBox.getChildren().add(row);
            }
        });
    }

    /** Salva o texto digitado como cidade principal (geocodifica e grava). */
    private void doSalvar() {
        String q = cityField.getText().trim();
        if (q.isEmpty()) {
            return;
        }
        salvarBtn.setDisable(true);
        climaStatus.setText("Salvando " + q + "…");
        cities.search(q, 1, list -> {
            if (list.isEmpty()) {
                Platform.runLater(() -> {
                    salvarBtn.setDisable(false);
                    climaStatus.setText("Cidade não encontrada: " + q);
                });
                return;
            }
            setAsMain(list.get(0), "Cidade salva ✓");
            Platform.runLater(() -> salvarBtn.setDisable(false));
        }, e -> Platform.runLater(() -> {
            salvarBtn.setDisable(false);
            climaStatus.setText("Erro ao salvar: " + e.getMessage());
        }));
    }

    private void setAsMain(WeatherService.CityResult r) {
        setAsMain(r, "Cidade principal definida ✓");
    }

    private void setAsMain(WeatherService.CityResult r, String msg) {
        AppConfig cfg = ctx.agenda().config();
        cfg.cityName = r.label();
        cfg.cityLat = r.lat();
        cfg.cityLon = r.lon();
        ctx.agenda().saveConfig();
        Platform.runLater(() -> {
            cityField.setText(cfg.cityName);
            principalsLabel();
            cityResultsBox.getChildren().clear();
            climaStatus.setText(msg);
        });
    }

    private void addExtraCity(WeatherService.CityResult r) {
        AppConfig cfg = ctx.agenda().config();
        if (cfg.extraCities.stream().anyMatch(e -> e.name.equalsIgnoreCase(r.label()))) {
            climaStatus.setText("Já adicionada: " + r.label());
            return;
        }
        cfg.extraCities.add(new AppConfig.ExtraCity(r.label(), r.lat(), r.lon()));
        ctx.agenda().saveConfig();
        renderExtraCities(cfg);
        cityResultsBox.getChildren().clear();
        climaStatus.setText("Cidade complementar adicionada ✓");
    }

    private void renderExtraCities(AppConfig cfg) {
        extraCitiesBox.getChildren().clear();
        List<AppConfig.ExtraCity> ecs = cfg.extraCities == null ? List.of() : cfg.extraCities;
        if (ecs.isEmpty()) {
            Label none = new Label("Nenhuma cidade complementar.");
            none.getStyleClass().add("section-label");
            extraCitiesBox.getChildren().add(none);
            return;
        }
        for (int i = 0; i < ecs.size(); i++) {
            AppConfig.ExtraCity ec = ecs.get(i);
            HBox row = new HBox(6);
            row.setAlignment(Pos.CENTER_LEFT);
            Label name = new Label(ec.name);
            name.getStyleClass().add("card-sub");
            HBox.setHgrow(name, Priority.ALWAYS);
            Button del = new Button("✕");
            del.getStyleClass().add("toolbtn");
            final int idx = i;
            del.setOnAction(e -> {
                cfg.extraCities.remove(idx);
                ctx.agenda().saveConfig();
                renderExtraCities(cfg);
            });
            row.getChildren().addAll(name, del);
            extraCitiesBox.getChildren().add(row);
        }
    }

    private void locateByIp() {
        climaStatus.setText("Obtendo localização…");
        cities.geo(list -> setAsMain(
                list.stream().findFirst().orElseThrow(), "Localização definida ✓"),
                e -> Platform.runLater(() ->
                        climaStatus.setText("Erro na localização: " + e.getMessage())));
    }

    /** Adaptador: executa a busca de cidades em thread de fundo com feedback. */
    private interface CitiesOps {
        void search(String q, int count,
                    java.util.function.Consumer<List<WeatherService.CityResult>> onOk,
                    java.util.function.Consumer<Exception> onErr);

        void geo(java.util.function.Consumer<List<WeatherService.CityResult>> onOk,
                 java.util.function.Consumer<Exception> onErr);
    }

    private final CitiesOps cities = new CitiesOps() {
        @Override
        public void search(String q, int count,
                           java.util.function.Consumer<List<WeatherService.CityResult>> onOk,
                           java.util.function.Consumer<Exception> onErr) {
            com.yourday.app.core.services.HttpSupport.getAsync(
                    "https://geocoding-api.open-meteo.com/v1/search?name="
                            + java.net.URLEncoder.encode(q, java.nio.charset.StandardCharsets.UTF_8)
                            + "&count=" + count + "&language=pt&format=json",
                    s -> {
                        try {
                            onOk.accept(WeatherService.searchCitiesResponse(s));
                        } catch (Exception e) {
                            onErr.accept(e);
                        }
                    }, onErr);
        }

        @Override
        public void geo(java.util.function.Consumer<List<WeatherService.CityResult>> onOk,
                        java.util.function.Consumer<Exception> onErr) {
            com.yourday.app.core.services.HttpSupport.getAsync(
                    "https://ip-api.com/json/?fields=status,message,lat,lon,city,regionName&lang=pt",
                    s -> {
                        try {
                            onOk.accept(List.of(WeatherService.parseGeoResponse(s)));
                        } catch (Exception e) {
                            onErr.accept(e);
                        }
                    }, onErr);
        }
    };

    // ---------------- Ações: demais ----------------

    private void saveFeeds() {
        List<String> feeds = new ArrayList<>();
        for (String line : feedsArea.getText().split("\n")) {
            String l = line.trim();
            if (!l.isEmpty()) {
                feeds.add(l);
            }
        }
        ctx.agenda().config().feeds = feeds;
        ctx.agenda().saveConfig();
    }

    private HBox buildThemeToggle() {
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
        return seg;
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
            System.out.println("Falha no backup: " + e.getMessage());
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
            System.out.println("Backup restaurado.");
        } catch (Exception e) {
            System.out.println("Falha ao restaurar: " + e.getMessage());
        }
    }

    @Override
    public void refresh() {
        // Configurações são aplicadas ao salvar em cada aba.
    }
}