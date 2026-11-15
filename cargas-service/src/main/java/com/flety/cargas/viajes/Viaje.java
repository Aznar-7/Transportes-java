package com.flety.cargas.viajes;

import com.flety.cargas.mercado.Carga;
import com.flety.cargas.mercado.EstadoCarga;
import com.flety.cargas.mercado.Propuesta;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * El acuerdo entre un dador y un transportista para mover una carga. Nace CONFIRMADO cuando el dador acepta
 * una propuesta (FLY-26) y avanza por sus hitos con el patrón State (FLY-27).
 * <p>
 * El estado no tiene setter: la única forma de cambiarlo es cambiarEstado, que siempre deja un EventoViaje.
 * Así es imposible que un viaje cambie de estado sin que quede registrado en su línea de tiempo.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Viaje {

    /** Desde la entrega, el dador tiene 48 h para dar conformidad o abrir una disputa. */
    public static final Duration VENTANA_CONFORMIDAD = Duration.ofHours(48);

    /** Cancelar con menos de esta anticipación al inicio de la carga deja la cancelación con penalidad (FLY-28). */
    public static final Duration ANTICIPACION_SIN_PENALIDAD = Duration.ofHours(24);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Bloqueo optimista: si dos pedidos cambian el mismo viaje a la vez, el segundo recibe 409. */
    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "carga_id", nullable = false, updatable = false)
    private Carga carga;

    /** Una propuesta genera a lo sumo un viaje: la columna es única. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "propuesta_id", nullable = false, unique = true, updatable = false)
    private Propuesta propuesta;

    /**
     * sub del dador y del transportista: con estos dos se decide quién es parte del viaje (RN-T1).
     * Se copian de la carga y de la propuesta para no tener que cargarlas en cada consulta.
     */
    @Column(nullable = false, updatable = false)
    private String dadorSub;

    @Column(nullable = false, updatable = false)
    private String transportistaSub;

    /** Ids de transporte-service: solo el número, nunca una FK entre bases. */
    @Column(nullable = false, updatable = false)
    private Long transportistaId;

    @Column(nullable = false, updatable = false)
    private Long unidadId;

    /** Patente de la unidad, copiada al confirmar para que el dador sepa qué camión espera. */
    @Column(updatable = false)
    private String unidadDominio;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "nombre", column = @Column(name = "transportista_nombre", updatable = false)),
            @AttributeOverride(name = "telefono", column = @Column(name = "transportista_telefono", updatable = false))
    })
    private Contacto contactoTransportista;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "nombre", column = @Column(name = "dador_nombre", updatable = false)),
            @AttributeOverride(name = "telefono", column = @Column(name = "dador_telefono", updatable = false))
    })
    private Contacto contactoDador;

    /** El precio de la propuesta aceptada: el del dador o la contraoferta. */
    @Column(nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal precioAcordado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoEstadoViaje estado;

    /** Los completa la entrega (marcarEntregado); conformadoPor, la conformidad (marcarConformado, FLY-30). */
    private Instant entregadoEn;

    private Instant ventanaConformidadHasta;

    @Enumerated(EnumType.STRING)
    private ConformadoPor conformadoPor;

    /** Los completa la cancelación (marcarCancelado, FLY-28); quedan en null mientras el viaje siga en pie. */
    @Enumerated(EnumType.STRING)
    private ParteDelViaje canceladoPor;

    private Instant canceladoEn;

    @Column(length = 500)
    private String motivoCancelacion;

    /**
     * Si se canceló con menos de 24 h para la carga. Es Boolean y no boolean: null quiere decir "no se canceló",
     * y además la columna se agrega a una tabla que ya tiene viajes (ddl-auto update), donde un NOT NULL sin
     * valor por defecto fallaría.
     */
    private Boolean conPenalidad;

    @Column(nullable = false, updatable = false)
    private Instant creadoEn;

    /**
     * La línea de tiempo. Con PERSIST, los eventos nuevos se guardan junto con el viaje; sin REMOVE ni
     * orphanRemoval, sacar uno de la lista no lo borra de la base.
     */
    @OneToMany(mappedBy = "viaje", cascade = CascadeType.PERSIST)
    @OrderBy("ocurridoEn ASC, id ASC")
    @Getter(AccessLevel.NONE)
    private List<EventoViaje> eventos = new ArrayList<>();

    /**
     * Crea el viaje CONFIRMADO a partir de la propuesta que eligió el dador, con su primer evento.
     *
     * @param actorSub sub del dador que aceptó la propuesta
     */
    public Viaje(Carga carga, Propuesta propuesta, String unidadDominio, Contacto contactoTransportista,
                 Contacto contactoDador, String actorSub) {
        this.carga = carga;
        this.propuesta = propuesta;
        this.dadorSub = carga.getDadorSub();
        this.transportistaSub = propuesta.getTransportistaSub();
        this.transportistaId = propuesta.getTransportistaId();
        this.unidadId = propuesta.getUnidadId();
        this.unidadDominio = unidadDominio;
        this.contactoTransportista = contactoTransportista;
        this.contactoDador = contactoDador;
        this.precioAcordado = propuesta.getPrecio();
        this.estado = TipoEstadoViaje.CONFIRMADO;
        eventos.add(new EventoViaje(this, null, TipoEstadoViaje.CONFIRMADO, actorSub, null));
    }

    // Patrón State (FLY-27): cada acción se delega en el objeto del estado actual, que decide si la permite y a
    // qué estado pasa. Si no la permite, lanza TransicionNoPermitidaException (409) y el viaje queda como estaba.
    // Viaje no tiene ni un if sobre su estado: agregar o cambiar una transición se hace en la clase del estado.

    public void cargar(String actorSub) {
        estadoActual().cargar(this, actorSub);
    }

    public void iniciarTransito(String actorSub) {
        estadoActual().iniciarTransito(this, actorSub);
    }

    /**
     * @return el comprobante de la entrega, para que el service lo guarde. Si el estado no permite entregar,
     * la excepción sale antes y no se arma ningún comprobante.
     */
    public ComprobanteEntrega registrarEntrega(DatosEntrega entrega, String actorSub) {
        estadoActual().registrarEntrega(this, entrega, actorSub);
        return new ComprobanteEntrega(this, entrega.numeroRemito(), entrega.recibioNombre(), entrega.recibioDni());
    }

    public void conformar(ConformadoPor por, String actorSub) {
        estadoActual().conformar(this, por, actorSub);
    }

    /**
     * @return la disputa abierta, para que el service la guarde (como el comprobante de la entrega). Si el estado o la
     * ventana no lo permiten, la excepción sale antes y no se arma ninguna disputa.
     */
    public Disputa disputar(String motivo, String descripcion, String actorSub) {
        estadoActual().disputar(this, motivo, descripcion, actorSub);
        return new Disputa(this, motivo, descripcion, actorSub);
    }

    public void cancelar(String motivo, String actorSub) {
        estadoActual().cancelar(this, motivo, actorSub);
    }

    public void resolverDisputa(boolean aFavorDelTransportista, String resolucion, String operadorSub) {
        estadoActual().resolverDisputa(this, aFavorDelTransportista, resolucion, operadorSub);
    }

    /** Se resuelve en cada llamada a partir del enum guardado: no hay un segundo campo que pueda desincronizarse. */
    private EstadoViaje estadoActual() {
        return EstadosViaje.de(estado);
    }

    /**
     * Lo llama EnTransito al registrar la entrega: desde ahora corre la ventana de conformidad. Si en 48 h el
     * dador no da conformidad ni abre una disputa, la da el sistema (FLY-30).
     */
    void marcarEntregado(DatosEntrega entrega, String actorSub) {
        this.entregadoEn = Instant.now();
        this.ventanaConformidadHasta = entregadoEn.plus(VENTANA_CONFORMIDAD);
        cambiarEstado(TipoEstadoViaje.ENTREGADO, actorSub,
                "Remito " + entrega.numeroRemito() + ", recibió " + entrega.recibioNombre());
    }

    /**
     * Lo llama Entregado al conformar, ya con la ventana controlada: es el mismo camino para el dador y para el
     * Reloj, y solo cambia quién queda registrado. El viaje terminó, así que la carga queda CERRADA en la misma
     * transacción (como en la cancelación, cambian dos entidades juntas).
     */
    void marcarConformado(ConformadoPor por, String actorSub) {
        this.conformadoPor = por;
        cambiarEstado(TipoEstadoViaje.CONFORMADO, actorSub, switch (por) {
            case DADOR -> "Conformó el dador";
            case SISTEMA -> "Conformidad automática: pasaron 48 h sin conformidad ni disputa";
            case OPERADOR -> "Disputa resuelta a favor del transportista";
        });
        carga.setEstado(EstadoCarga.CERRADA);
    }

    /** Lo llama Entregado al abrir una disputa: el viaje queda frenado hasta que un operador la resuelva. */
    void marcarEnDisputa(String motivo, String actorSub) {
        cambiarEstado(TipoEstadoViaje.EN_DISPUTA, actorSub, "Disputa abierta: " + motivo);
    }

    /**
     * Lo llama EnDisputa cuando el operador resuelve en contra del transportista. El viaje terminó igual, así que la
     * carga también queda CERRADA, en la misma transacción.
     */
    void marcarCerradoConDisputa(String resolucion, String operadorSub) {
        cambiarEstado(TipoEstadoViaje.CERRADO_CON_DISPUTA, operadorSub, "Disputa resuelta en contra del transportista: "
                + resolucion);
        carga.setEstado(EstadoCarga.CERRADA);
    }

    /**
     * Lo llama Confirmado al cancelar: guarda quién canceló, cuándo y por qué, y deja el evento con el motivo.
     * <p>
     * Penalidad: si faltan menos de 24 h para el inicio de la ventana de carga, la otra parte ya se organizó
     * (el transportista viajó a buscarla, el dador preparó la mercadería) y la cancelación queda con penalidad.
     * Exactamente 24 h todavía es sin penalidad. Si la ventana ya empezó, también es con penalidad.
     */
    void marcarCancelado(String motivo, String actorSub) {
        this.canceladoPor = parteDe(actorSub);
        this.canceladoEn = Instant.now();
        this.motivoCancelacion = motivo;
        this.conPenalidad = canceladoEn.isAfter(carga.getCargaDesde().minus(ANTICIPACION_SIN_PENALIDAD));
        cambiarEstado(TipoEstadoViaje.CANCELADO, actorSub, "Canceló el " + canceladoPor
                + (conPenalidad ? " con penalidad" : "") + ": " + motivo);
        // La cancelación cambia dos entidades: el viaje y su carga. Las dos se guardan en la misma transacción
        // (CancelarViajeService), así que o cambian las dos o ninguna. Carga tiene @Version: si otro pedido la
        // modificó mientras tanto, falla el UPDATE de la carga y se deshace también la cancelación del viaje.
        carga.setEstado(estadoDeLaCargaAlCancelar());
    }

    /**
     * Qué pasa con la carga depende de quién cancela:
     * <ul>
     *     <li>el dador: ya no quiere mover la carga, así que queda CANCELADA;</li>
     *     <li>el transportista, con la ventana de carga todavía abierta: el dador no tiene la culpa y todavía está a
     *     tiempo de conseguir otro, así que la carga vuelve a PUBLICADA y puede recibir propuestas nuevas
     *     (el índice único parcial de viaje.carga_id no cuenta los viajes CANCELADOS);</li>
     *     <li>el transportista, con la ventana ya cerrada: nadie más puede cargarla a tiempo, así que queda VENCIDA,
     *     como cualquier carga que se queda sin viaje al cerrar su ventana (RN-17).</li>
     * </ul>
     * La ventana está abierta mientras no haya pasado cargaHasta.
     */
    private EstadoCarga estadoDeLaCargaAlCancelar() {
        if (canceladoPor == ParteDelViaje.DADOR) {
            return EstadoCarga.CANCELADA;
        }
        return canceladoEn.isBefore(carga.getCargaHasta()) ? EstadoCarga.PUBLICADA : EstadoCarga.VENCIDA;
    }

    /**
     * Qué parte del viaje es ese sub. Quien no es parte no llega acá (el service responde 403 antes); si llegara,
     * se corta en vez de anotar una cancelación a nombre de nadie.
     */
    public ParteDelViaje parteDe(String sub) {
        if (dadorSub.equals(sub)) {
            return ParteDelViaje.DADOR;
        }
        if (transportistaSub.equals(sub)) {
            return ParteDelViaje.TRANSPORTISTA;
        }
        throw new IllegalArgumentException("El usuario " + sub + " no es parte del viaje " + id);
    }

    /**
     * El único camino para cambiar el estado, y siempre deja un EventoViaje. No valida si la transición está
     * permitida: eso ya lo decidió la clase de estado que llama acá (por eso es del paquete y no pública).
     */
    void cambiarEstado(TipoEstadoViaje nuevo, String actorSub, String detalle) {
        eventos.add(new EventoViaje(this, estado, nuevo, actorSub, detalle));
        this.estado = nuevo;
    }

    public boolean esParte(String sub) {
        return dadorSub.equals(sub) || transportistaSub.equals(sub);
    }

    /** Solo lectura: los eventos se agregan con cambiarEstado, nunca desde afuera. */
    public List<EventoViaje> getEventos() {
        return Collections.unmodifiableList(eventos);
    }

    @PrePersist
    void alCrear() {
        creadoEn = Instant.now();
    }
}
