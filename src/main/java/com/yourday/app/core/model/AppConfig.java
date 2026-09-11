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
    public List<ExtraCity> extraCities = new ArrayList<>();
    public boolean notifyEnabled = true;
    public int notifyAdvanceMinutes = 10;
    public List<String> feeds = new ArrayList<>(DEFAULT_FEEDS);

    /** Cidade complementar exibida na aba Clima. */
    public static class ExtraCity {
        public String name;
        public double lat;
        public double lon;

        public ExtraCity() {
        }

        public ExtraCity(String name, double lat, double lon) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }
    }
}