import com.urbener.postgresql.hibernate.Contador;
import com.urbener.postgresql.hibernate.Cups;
import com.urbener.postgresql.hibernate.ClientesExternos;
import com.saica.cargadatos.ServicioCargaDatos;
import es.radsys.EjbConnectionException;
import org.apache.log4j.Logger;
import java.util.Date;
import java.util.List;

/**
 * Interfaz para acceso y actualización de counters en base de datos.
 */
public interface ContadorRepository {
    Contador buscarContador(String cups, String origin);
    void actualizarContadorRecepcion(Contador cont, Date dateCounterLast, String cups);
    void actualizarEstadoContador(Contador cont, Date receptionDate, String status);
}

/**
 * Implementación de ContadorRepository para acceso a base de datos.
 */
class ContadorRepositoryImpl implements ContadorRepository {
    @Override
    public Contador buscarContador(String cups, String origin) {
        Contador cont = null;
        try {
            List<Object> counters = ServicioCargaDatos.dameIdataBean().dameObjetoConsultaHQL(
                " from Cups as c left join fetch c.contador where c.cups = '" + cups + "'");
            if (counters != null && counters.size() > 0) {
                cont = Cups.class.cast(counters.get(0)).getContador();
                Logger.getLogger(getClass()).debug("Encontrado Cups " + cont);
            }
            if (cont == null) {
                counters = ServicioCargaDatos.dameIdataBean().dameObjetoConsultaHQL(
                    " from ClientesExternos as c left join fetch c.contador where cups = '" + cups + "'");
                if (counters != null && counters.size() > 0) {
                    cont = ClientesExternos.class.cast(counters.get(0)).getContador();
                    Logger.getLogger(getClass()).debug("Encontrado ClientesExternos " + cont);
                }
            }
        } catch (EjbConnectionException e) {
            Logger.getLogger(getClass()).error("Error de conexión a la base de datos buscando contador para CUPS: " + cups + ", origin: " + origin, e);
            throw new RuntimeException("Error de conexión a la base de datos buscando contador para CUPS: " + cups, e);
        } catch (RuntimeException e) {
            Logger.getLogger(getClass()).error("Error inesperado buscando contador para CUPS: " + cups + ", origin: " + origin, e);
            throw new RuntimeException("Error inesperado buscando contador para CUPS: " + cups, e);
        }
        if (cont == null) {
            Logger.getLogger(getClass()).error("CUPS no encontrado: " + cups);
        }
        return cont;
    }

    @Override
    public void actualizarContadorRecepcion(Contador cont, Date dateCounterLast, String cups) {
        Logger.getLogger(getClass()).debug("cups:fecharec " + cups + ":" + dateCounterLast);
        actualizarEstadoContador(cont, dateCounterLast, "Actualizado SFTP");
        Logger.getLogger(getClass()).trace(cont);
    }

    @Override
    public void actualizarEstadoContador(Contador cont, Date receptionDate, String status) {
        cont.setContadorFecharec(receptionDate);
        cont.setContadorUltimacomu(new Date());
        cont.setContadorStatus(status);
        try {
            ServicioCargaDatos.dameIdataBean().saveOrUpdate(cont);
        } catch (Exception e) {
            Logger.getLogger(getClass()).error("Error actualizando Contador: " + cont, e);
        }
    }
}
