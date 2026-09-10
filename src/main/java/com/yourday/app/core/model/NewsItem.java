package com.yourday.app.core.model;

/**
 * Item de notícia (RSS/Atom). Campos brutos + derivados (lidos de forma
 * tolerante para vários formatos de feed).
 */
public class NewsItem {

    public String title = "";
    public String link = "";
    public String summary = "";
    public String sourceName = "";
    /** Epoch ms da publicação (0 se desconhecida). */
    public long pubDate;
}