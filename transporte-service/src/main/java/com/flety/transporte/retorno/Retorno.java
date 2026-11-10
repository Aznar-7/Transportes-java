package com.flety.transporte.retorno;

import com.flety.commons.excepciones.ConflictoException;
import com.flety.commons.geo.Coordenada;
import com.flety.commons.geo.Ruta;
import com.flety.transporte.unidad.Unidad;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Retorno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidad_id")
    private Unidad unidad;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "provincia", column = @Column(name = "origen_provincia", nullable = false)),
            @AttributeOverride(name = "localidad", column = @Column(name = "origen_localidad", nullable = false)),
            @AttributeOverride(name = "lat", column = @Column(name = "origen_lat", nullable = false)),
            @AttributeOverride(name = "lng", column = @Column(name = "origen_lng", nullable = false))})
    private Ubicacion origen;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "provincia", column = @Column(name = "destino_provincia", nullable = false)),
            @AttributeOverride(name = "localidad", column = @Column(name = "destino_localidad", nullable = false)),
            @AttributeOverride(name = "lat", column = @Column(name = "destino_lat", nullable = false)),
            @AttributeOverride(name = "lng", column = @Column(name = "destino_lng", nullable = false))})
    private Ubicacion destino;

    @Column(nullable = false)
    private Instant salidaDesde;

    @Column(nullable = false)
    private Instant salidaHasta;

    @Column(nullable = false)
    private double desvioMaximoKm;

    @Column(nullable = false)
    private double rutaDistanciaKm;

    @Column(nullable = false)
    private double rutaDuracionMin;

    @ElementCollection
    @CollectionTable(name = "retorno_punto", joinColumns = @JoinColumn(name = "retorno_id"))
    @OrderColumn(name = "orden")
    private List<PuntoRuta> rutaPuntos = new ArrayList<>();

    /** true si la API de rutas no respondió y la ruta es la línea recta origen–destino. */
    @Column(nullable = false)
    private boolean rutaAproximada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoRetorno estado;

    protected Retorno() {
    }

    public Retorno(Unidad unidad, Ubicacion origen, Ubicacion destino, Instant salidaDesde, Instant salidaHasta,
                   double desvioMaximoKm, Ruta ruta, boolean rutaAproximada) {
        this.unidad = unidad;
        this.origen = origen;
        this.destino = destino;
        this.salidaDesde = salidaDesde;
        this.salidaHasta = salidaHasta;
        this.desvioMaximoKm = desvioMaximoKm;
        this.rutaDistanciaKm = ruta.distanciaKm();
        this.rutaDuracionMin = ruta.duracionMin();
        ruta.puntos().forEach(p -> rutaPuntos.add(new PuntoRuta(p)));
        this.rutaAproximada = rutaAproximada;
        this.estado = EstadoRetorno.PUBLICADO;
    }

    /** Baja lógica: el retorno queda en la base como CANCELADO, para que no se pierda su historia. */
    public void cancelar() {
        exigirPublicado();
        estado = EstadoRetorno.CANCELADO;
    }

    /** Lo usa el reloj de FLY-15 cuando pasa la ventana de salida (RN-17). */
    public void vencer() {
        exigirPublicado();
        estado = EstadoRetorno.VENCIDO;
    }

    private void exigirPublicado() {
        if (estado != EstadoRetorno.PUBLICADO) {
            throw new ConflictoException("El retorno ya está " + estado);
        }
    }

    public Long getId() { return id; }
    public Unidad getUnidad() { return unidad; }
    public Ubicacion getOrigen() { return origen; }
    public Ubicacion getDestino() { return destino; }
    public Instant getSalidaDesde() { return salidaDesde; }
    public Instant getSalidaHasta() { return salidaHasta; }
    public double getDesvioMaximoKm() { return desvioMaximoKm; }
    public double getRutaDistanciaKm() { return rutaDistanciaKm; }
    public double getRutaDuracionMin() { return rutaDuracionMin; }
    public boolean isRutaAproximada() { return rutaAproximada; }
    public EstadoRetorno getEstado() { return estado; }

    public List<Coordenada> getRutaPuntos() {
        return rutaPuntos.stream().map(PuntoRuta::coordenada).toList();
    }
}
