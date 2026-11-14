package br.com.policialadrao.common;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PixelAssets {
    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();

    private PixelAssets() { }

    public static BufferedImage image(String name) {
        return CACHE.computeIfAbsent(name, PixelAssets::load);
    }

    private static BufferedImage load(String name) {
        String path = "/assets/" + name;
        try (InputStream stream = PixelAssets.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Imagem não encontrada: " + path);
            BufferedImage image = ImageIO.read(stream);
            if (image == null) throw new IllegalStateException("Formato de imagem inválido: " + path);
            return image;
        } catch (IOException ex) {
            throw new IllegalStateException("Não foi possível carregar " + path, ex);
        }
    }
}
