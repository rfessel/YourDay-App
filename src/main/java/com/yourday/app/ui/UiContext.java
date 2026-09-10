package com.yourday.app.ui;

import com.yourday.app.core.services.AgendaService;

/** Ponte entre as páginas e o shell do app (tema + dados). */
public interface UiContext {

    AgendaService agenda();

    String theme();

    void setTheme(String theme);

    /** Recarrega tudo após mudanças de configuração (ex.: nova cidade). */
    default void refreshAll() {
    }
}