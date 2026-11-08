package com.flety.cargas.mercado;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Hace de transporte-service: un servidor HTTP de verdad, en un puerto libre, que responde lo que el test le cargue.
 * Así el pedido pasa por TransporteClientHttp, el RestClient y el JSON reales, y solo queda afuera el otro servicio.
 * Lo que no se cargó responde 404, igual que transporte-service.
 */
class TransporteSimulado {

    private record Respuesta(int status, String json) {
    }

    private final Map<String, Respuesta> respuestas = new ConcurrentHashMap<>();
    private final List<String> pedidos = new CopyOnWriteArrayList<>();
    private final HttpServer servidor;
    private volatile boolean caido;

    TransporteSimulado() {
        try {
            servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        servidor.createContext("/", intercambio -> {
            pedidos.add(intercambio.getRequestURI().toString());
            Respuesta respuesta = caido
                    ? new Respuesta(500, "{}")
                    : respuestas.getOrDefault(intercambio.getRequestURI().getPath(), new Respuesta(404, "{}"));
            byte[] cuerpo = respuesta.json().getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(respuesta.status(), cuerpo.length);
            intercambio.getResponseBody().write(cuerpo);
            intercambio.close();
        });
        servidor.start();
    }

    String url() {
        return "http://localhost:" + servidor.getAddress().getPort();
    }

    void responder(String ruta, String json) {
        respuestas.put(ruta, new Respuesta(200, json));
    }

    void caer(boolean caido) {
        this.caido = caido;
    }

    /** Los pedidos que llegaron, con su query string. */
    List<String> pedidos() {
        return pedidos;
    }
}
