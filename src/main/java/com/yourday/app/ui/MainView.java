package com.yourday.app.ui;

import com.yourday.app.app.YourDayApp;
import com.yourday.app.core.services.AgendaService;
import com.yourday.app.ui.pages.AgendaPage;
import com.yourday.app.ui.pages.ListsPage;
import com.yourday.app.ui.pages.NewsPage;
import com.yourday.app.ui.pages.ResumePage;
import com.yourday.app.ui.pages.SettingsPage;
import com.yourday.app.ui.pages.TodosPage;
import com.yourday.app.ui.pages.WeatherPage;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;

/** Shell do app: barra lateral de navegação + página atual. */
public class MainView extends HBox implements UiContext {

    private final AgendaService agenda;
    private final Map<String, Button> navButtons = new LinkedHashMap<>();
    private final Map<String, Region> pages = new LinkedHashMap<>();
    private final ScrollPane center = new ScrollPane();

    public MainView(AgendaService agenda) {
        this.agenda = agenda;
        getStyleClass().add("root-pane");

        VBox sidebar = buildSidebar();
        getChildren().add(sidebar);

        center.setFitToWidth(true);
        center.setFitToHeight(true);
        center.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        HBox.setHgrow(center, Priority.ALWAYS);
        getChildren().add(center);

        register("resumo", new ResumePage(this));
        register("agenda", new AgendaPage(this));
        register("tarefas", new TodosPage(this));
        register("listas", new ListsPage(this));
        register("noticias", new NewsPage(this));
        register("clima", new WeatherPage(this));
        register("config", new SettingsPage(this));

        show("resumo");
    }

    /** Após o construtor do shell estar completo (para o ref) — no-op aqui. */
    public void setUiContext() {
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(170);
        sidebar.setMinWidth(170);

        Label title = new Label("Your Day");
        title.getStyleClass().add("app-title");

        sidebar.getChildren().add(title);
        addNav(sidebar, "resumo", "◈  Resumo");
        addNav(sidebar, "agenda", "📅  Agenda");
        addNav(sidebar, "tarefas", "✅  Tarefas");
        addNav(sidebar, "listas", "📝  Listas");
        addNav(sidebar, "noticias", "📰  Notícias");
        addNav(sidebar, "clima", "🌤  Clima");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        sidebar.getChildren().add(spacer);

        addNav(sidebar, "config", "⚙  Configurações");

        Label footer = new Label("v0.1.0 · dados em " + agenda.data().dir());
        footer.getStyleClass().add("sidebar-footer");
        footer.setWrapText(true);
        sidebar.getChildren().add(footer);

        return sidebar;
    }

    private void addNav(VBox sidebar, String key, String text) {
        Button b = new Button(text);
        b.getStyleClass().add("nav-button");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setOnAction(e -> show(key));
        navButtons.put(key, b);
        sidebar.getChildren().add(b);
    }

    private void register(String key, Region page) {
        pages.put(key, page);
    }

    public void show(String key) {
        Region page = pages.get(key);
        if (page == null) {
            return;
        }
        center.setContent(page);
        for (Map.Entry<String, Button> e : navButtons.entrySet()) {
            boolean sel = e.getKey().equals(key);
            e.getValue().getStyleClass().remove("nav-button-selected");
            if (sel) {
                e.getValue().getStyleClass().add("nav-button-selected");
            }
        }
        if (page instanceof Refreshable r) {
            r.onShown();
        }
    }

    // ---------------- UiContext ----------------

    @Override
    public AgendaService agenda() {
        return agenda;
    }

    @Override
    public String theme() {
        return agenda.config().theme;
    }

    @Override
    public void setTheme(String theme) {
        agenda.config().theme = theme;
        agenda.saveConfig();
        getScene().getStylesheets().setAll(YourDayApp.themeUrl(theme));
    }

    @Override
    public void refreshAll() {
        for (Region p : pages.values()) {
            if (p instanceof Refreshable r) {
                r.refresh();
            }
        }
    }

    public interface Refreshable {
        default void onShown() {
            refresh();
        }

        void refresh();
    }
}