package com.yourday.app.core.model;

import java.util.UUID;

/** Tarefa com data de vencimento opcional. */
public class TodoItem {

    public String id = UUID.randomUUID().toString();
    public String title = "";
    public boolean done;
    public long createdAt = System.currentTimeMillis();
    /** 0 = sem vencimento; senão epoch ms do fim do dia. */
    public long dueDate;

    public static TodoItem of(String title) {
        TodoItem t = new TodoItem();
        t.title = title;
        return t;
    }

    /** Vencimento em formato yyyy-MM-dd (ou "" se não houver). */
    public String dueDateStr() {
        if (dueDate == 0) {
            return "";
        }
        return java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(dueDate),
                java.time.ZoneId.systemDefault()).toString();
    }
}