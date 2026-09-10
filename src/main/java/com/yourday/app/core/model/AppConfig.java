package com.yourday.app.core.model;

import java.util.ArrayList;
import java.util.List;

/** Configurações do usuário + locais (cidade, feeds). */
public class AppConfig {

    public static final List<String> DEFAULT_FEEDS = List.of(
            "https://g1.globo.com/rss/g1/",
            "https://www.bbc.com/portuguese/index.xml",
            "https://noticias.uol.com.br/rss/ultimasnoticias.xml");

    public String theme = "dark";
    public String cityName = WeatherData.FALLBACK_CITY;
    public Double cityLat;
    public Double cityLon;
    public boolean notifyEnabled = true;
    public int notifyAdvanceMinutes = 10;
    public List<String> feeds = new ArrayList<>(DEFAULT_FEEDS);
}