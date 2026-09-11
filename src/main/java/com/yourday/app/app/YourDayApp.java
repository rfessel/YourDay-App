package com.yourday.app.app;

import com.yourday.app.core.model.EventItem;
import com.yourday.app.core.services.AgendaService;
import com.yourday.app.core.services.Notifier;
import com.yourday.app.core.storage.AppData;
import com.yourday.app.ui.MainView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Your Day — aplicação JavaFX multiplataforma (projeto 3). */
public class YourDayApp extends Application {

    private static final int WIDTH = 1040;
    private static final int HEIGHT = 700;

    private static javafx.application.HostServices hostServices;

    private ScheduledExecutorService watcher;

    /** Abre um link no navegador padrão do sistema. */
    public static void openLink(String url) {
        if (url == null) {
            return;
        }
        String clean = url.startsWith("http") || url.startsWith("https")
                ? url : "https://" + url;
        if (hostServices != null) {
            hostServices.showDocument(clean);
            return;
        }
        // Fallback: abrir com o desktop do AWT (Linux sem javafx.web etc.).
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(clean));
        } catch (Exception ignore) {
            // Nenhum navegador disponível; ignora silenciosamente.
        }
    }

    @Override
    public void start(Stage stage) {
        hostServices = getHostServices();
        AppData data = AppData.openDefault();
        AgendaService agenda = new AgendaService(data);
        MainView mainView = new MainView(agenda);
        mainView.setUiContext();

        Scene scene = new Scene(mainView, WIDTH, HEIGHT);
        scene.getStylesheets().setAll(themeUrl(agenda.config().theme));

        stage.setTitle("Your Day");
        stage.setMinWidth(560);
        stage.setMinHeight(420);
        stage.setScene(scene);
        stage.show();

        startNotificationWatcher(agenda);
    }

    private void startNotificationWatcher(AgendaService agenda) {
        watcher = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "yourday-notifier");
            t.setDaemon(true);
            return t;
        });
        watcher.scheduleWithFixedDelay(() -> {
            try {
                if (!agenda.config().notifyEnabled || agenda.config().notifyAdvanceMinutes <= 0) {
                    return;
                }
                long now = System.currentTimeMillis();
                long horizon = now + agenda.config().notifyAdvanceMinutes * 60_000L;
                List<EventItem> upcoming = agenda.occurrences(now, horizon);
                for (EventItem e : upcoming) {
                    if (e.start < now) {
                        continue;
                    }
                    String key = e.id + "|" + e.start;
                    if (!Notifier.isNew(key)) {
                        continue;
                    }
                    long minutes = (e.start - now) / 60_000L;
                    String when = minutes < 1 ? "agora"
                            : minutes + " min" + (minutes > 1 ? "s" : "");
                    String time = AgendaService.hm(e.start);
                    Notifier.notify("🔔 " + e.title,
                            "Começa às " + time + " (em " + when + ")");
                }
            } catch (Throwable ignore) {
                // watcher nunca pode derrubar o app
            }
        }, 30, 60, TimeUnit.SECONDS);
    }

    public static String themeUrl(String theme) {
        String t = theme == null || theme.isBlank() ? "dark" : theme;
        return YourDayApp.class.getClassLoader()
                .getResource("com/yourday/app/ui/theme-" + t + ".css")
                .toExternalForm();
    }

    @Override
    public void stop() {
        if (watcher != null) {
            watcher.shutdownNow();
        }
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}