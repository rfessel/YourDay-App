package com.yourday.app.core.model;

import java.util.UUID;

/** Item dentro de uma lista. */
public class ListaItem {

    public String id = UUID.randomUUID().toString();
    public String text = "";
    public boolean done;

    public static ListaItem of(String text) {
        ListaItem i = new ListaItem();
        i.text = text;
        return i;
    }
}