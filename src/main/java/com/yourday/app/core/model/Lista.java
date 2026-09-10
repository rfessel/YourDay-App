package com.yourday.app.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Lista geral ou de compras (como a aba Listas do widget). */
public class Lista {

    public String id = UUID.randomUUID().toString();
    public String name = "";
    public boolean done;
    public List<ListaItem> items = new ArrayList<>();

    public static Lista of(String name) {
        Lista l = new Lista();
        l.name = name;
        return l;
    }

    public long doneCount() {
        return items.stream().filter(i -> i.done).count();
    }
}