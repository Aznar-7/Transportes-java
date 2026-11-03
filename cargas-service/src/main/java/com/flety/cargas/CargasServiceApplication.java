package com.flety.cargas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class CargasServiceApplication {

    public static void main(String[] args) {
        usarUtc();
        SpringApplication.run(CargasServiceApplication.class, args);
    }

    /**
     * El servicio corre siempre en UTC, sin importar la máquina.
     * <p>
     * El driver de Postgres le manda al servidor la zona horaria de la JVM al conectarse. En Windows con zona
     * de Argentina, Java informa "America/Buenos_Aires", un alias viejo que el Postgres 16 del compose ya no
     * trae (Debian lo pasó al paquete tzdata-legacy), y la conexión falla con
     * "invalid value for parameter TimeZone". Con UTC no hay alias que resolver, y las fechas (todas Instant)
     * y los logs quedan iguales en cualquier máquina.
     * <p>
     * Va antes de SpringApplication.run para que valga desde la primera conexión del pool.
     */
    static void usarUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
}
