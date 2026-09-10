package com.yourday.app.ui;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Helpers visuais compartilhados entre as páginas. */
public final class Ui {

    private Ui() {
    }

    public static Label sectionTitle(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("section-title");
        return l;
    }

    public static Label muted(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("section-label");
        return l;
    }

    public static VBox card(Node... children) {
        VBox v = new VBox(8);
        v.getStyleClass().add("card");
        v.getChildren().addAll(children);
        return v;
    }

    public static ScrollPane scroll(Node content) {
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        return sp;
    }

    /** Nomes curtos dos dias da semana, começando no domingo (como o widget). */
    public static List<String> weekdayLetters() {
        LocalDate sunday = LocalDate.of(2023, 1, 8); // era domingo
        List<String> out = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            out.add(sunday.plusDays(i).getDayOfWeek()
                    .getDisplayName(TextStyle.SHORT, Locale.getDefault()));
        }
        return out;
    }

    public static String weekdayFull(LocalDate d) {
        return d.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault());
    }

    public static String monthName(LocalDate d) {
        return d.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault());
    }

    public static String pad2(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }

    /** getDay-style 0..6 (0=domingo) para alinhar a grade do calendário. */
    public static int dayOfWeekIndex(LocalDate d) {
        DayOfWeek iso = d.getDayOfWeek();
        return iso.getValue() % 7;
    }
}