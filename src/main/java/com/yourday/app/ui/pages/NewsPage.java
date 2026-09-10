package com.yourday.app.ui.pages;

import com.yourday.app.core.model.NewsItem;
import com.yourday.app.core.services.HttpSupport;
import com.yourday.app.core.services.NewsService;
import com.yourday.app.ui.MainView;
import com.yourday.app.ui.Ui;
import com.yourday.app.ui.UiContext;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Notícias RSS/Atom: cabeçalho com botão de atualizar + hora abaixo (igual ao widget). */
public class NewsPage extends VBox implements MainView.Refreshable {

    private final UiContext ctx;
    private final VBox listBox = new VBox(8);
    private final GridPane headerRow = new GridPane();
    private final Label statusLabel = new Label();
    private List<String> errors = new ArrayList<>();
    private long lastFetch;

    public NewsPage(UiContext ctx) {
        this.ctx = ctx;
        setSpacing(12);
        getStyleClass().add("content");

        Label title = new Label("Notícias");
        title.getStyleClass().add("page-title");
        Label sub = new Label("As principais notícias dos seus feeds");
        sub.getStyleClass().add("section-label");

        // Coluna à direita: botão de atualizar com a hora de atualização abaixo.
        Button refresh = new Button("↻");
        refresh.getStyleClass().add("toolbtn");
        refresh.setOnAction(e -> fetch());

        Label updated = new Label("");
        updated.getStyleClass().add("updated-label");

        VBox rightCol = new VBox(1, refresh, updated);
        rightCol.setAlignment(Pos.CENTER);

        headerRow.add(new VBox(2, title, sub), 0, 0);
        headerRow.add(rightCol, 1, 0);
        ColumnConstraints grow = new ColumnConstraints();
        grow.setHgrow(javafx.scene.layout.Priority.ALWAYS);
        headerRow.getColumnConstraints().add(grow);
        headerRow.getColumnConstraints().add(new ColumnConstraints(56));
        headerRow.setHgap(10);

        statusLabel.getStyleClass().add("section-label");

        getChildren().addAll(headerRow, statusLabel, listBox);
    }

    @Override
    public void refresh() {
        long since = System.currentTimeMillis() - lastFetch;
        if (lastFetch == 0 || since > 10 * 60_000L) {
            fetch();
        }
    }

    private void fetch() {
        List<String> feeds = ctx.agenda().config().feeds;
        errors = new ArrayList<>();
        statusLabel.setText("A carregar notícias…");
        listBox.getChildren().clear();

        List<Collected> results = new ArrayList<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (String feed : feeds) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    List<NewsItem> items = NewsService.fetch(feed, host(feed));
                    synchronized (results) {
                        results.add(new Collected(feed, items, null));
                    }
                } catch (Exception e) {
                    synchronized (results) {
                        results.add(new Collected(feed, List.of(), e.getMessage()));
                    }
                }
            }, HttpSupport.EXEC));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> Platform.runLater(() -> showResults(results)));
    }

    private static String host(String url) {
        try {
            return java.net.URI.create(url).getHost().replaceFirst("^www\\.", "");
        } catch (Exception e) {
            return url;
        }
    }

    private void showResults(List<Collected> results) {
        lastFetch = System.currentTimeMillis();
        String time = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

        List<String> errors = new ArrayList<>();
        List<NewsItem> all = new ArrayList<>();
        for (Collected c : results) {
            if (c.error != null) {
                errors.add(c.source + ": " + c.error);
            }
            all.addAll(c.items);
        }
        all.sort((a, b) -> Long.compare(b.pubDate, a.pubDate));

        Label updated = findUpdatedLabel();
        if (updated != null) {
            updated.setText("Atualizado às " + time);
        }

        if (!errors.isEmpty()) {
            statusLabel.setStyle("-fx-text-fill:#d64545; -fx-font-size: 11px;");
            statusLabel.setText("Alguns feeds falharam: " + String.join("; ", errors));
        } else {
            statusLabel.setStyle("");
            statusLabel.setText(all.size() + " notícias de "
                    + ctx.agenda().config().feeds.size() + " feeds");
        }

        listBox.getChildren().clear();
        if (all.isEmpty()) {
            listBox.getChildren().add(Ui.muted("Nenhuma notícia carregada."));
            return;
        }
        for (NewsItem n : all) {
            listBox.getChildren().add(headlineRow(n));
        }
    }

    private HBox headlineRow(NewsItem n) {
        HBox row = new HBox(10);
        row.getStyleClass().add("card");
        row.setAlignment(Pos.CENTER_LEFT);

        VBox text = new VBox(2);
        Label title = new Label((n.title == null ? "" : n.title));
        title.getStyleClass().add("headline");
        text.getChildren().add(title);

        String meta = n.sourceName;
        if (n.pubDate > 0) {
            String d = java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(n.pubDate),
                    java.time.ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("dd/MM"));
            meta += " · " + d;
        }
        Label src = Ui.muted(meta);
        text.getChildren().add(src);
        HBox.setHgrow(text, Priority.ALWAYS);

        if (n.summary != null && !n.summary.isEmpty()) {
            Label sum = new Label(n.summary);
            sum.setWrapText(true);
            sum.setMaxWidth(600);
            sum.getStyleClass().add("section-label");
            sum.setStyle("-fx-font-size:11px; -fx-wrap-text:true;");
            text.getChildren().add(sum);
        }

        row.getChildren().add(text);
        row.setOnMouseClicked(e -> openLink(n.link));
        return row;
    }

    private Label findUpdatedLabel() {
        GridPane hr = headerRow;
        if (hr.getChildren().size() >= 2) {
            VBox col = (VBox) hr.getChildren().get(1);
            return (Label) col.getChildren().get(1);
        }
        return null;
    }

    private void openLink(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        com.yourday.app.app.YourDayApp.openLink(url);
    }

    private record Collected(String source, List<NewsItem> items, String error) {
    }
}