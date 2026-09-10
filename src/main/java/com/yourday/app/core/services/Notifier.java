package com.yourday.app.core.services;

import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.util.HashSet;
import java.util.Set;

/** Notificações de desktop via bandeja do sistema (funciona em qualquer SO). */
public final class Notifier {

    private static TrayIcon tray;
    private static final Set<String> SEEN = new HashSet<>();

    static {
        try {
            if (SystemTray.isSupported()) {
                java.awt.Image img = Toolkit.getDefaultToolkit()
                        .createImage(new byte[0]);
                tray = null; // imagem vazia inválida; criaremos com pixel abaixo
            }
        } catch (Throwable ignore) {
            tray = null;
        }
    }

    private Notifier() {
    }

    /** Garante um ícone de bandeja mínimo (1x1) caso exista suporte. */
    private static synchronized void ensureTray() {
        if (tray != null || !SystemTray.isSupported()) {
            return;
        }
        try {
            int[] px = {0x00000000};
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, 0x00FFFF);
            tray = new TrayIcon(img, "Your Day");
            tray.setImageAutoSize(true);
            SystemTray.getSystemTray().add(tray);
        } catch (Throwable e) {
            tray = null;
        }
    }

    /** Mostra uma notificação; retorna false se o SO não suportar bandeja. */
    public static synchronized boolean notify(String title, String message) {
        ensureTray();
        if (tray == null) {
            return false;
        }
        try {
            tray.displayMessage(title, message, TrayIcon.MessageType.INFO);
            return true;
        } catch (Throwable ignore) {
            return false;
        }
    }

    /** Evita repetir a mesma notificação (chave com expiração por minuto). */
    public static synchronized boolean isNew(String key) {
        long minuteBucket = System.currentTimeMillis() / 60_000L;
        String k = minuteBucket + "|" + key;
        if (SEEN.contains(k)) {
            return false;
        }
        SEEN.add(k);
        if (SEEN.size() > 512) {
            SEEN.clear();
        }
        return true;
    }
}