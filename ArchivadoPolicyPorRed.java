package com.urbener.counters;

import com.saica.cargadatos.ServicioCargaDatos;
import com.urbener.postgresql.hibernate.Analogicas;
import com.urbener.postgresql.hibernate.Contador;
import com.urbener.postgresql.hibernate.Puntos;

/**
 * Implementación de ArchivadoPolicy basada en el número de red.
 */
public class ArchivadoPolicyPorRed implements ArchivadoPolicy {
    private final short numRedArchivar;

    public ArchivadoPolicyPorRed(short numRedArchivar) {
        this.numRedArchivar = numRedArchivar;
    }

    @Override
    public boolean debeArchivar(Contador cont) {
        if (cont != null && cont.getNumsenana() != null) {
            try {
                Analogicas ana = (Analogicas) ServicioCargaDatos.dameIdataBean().get(Analogicas.class.getName(),
                        cont.getNumsenana().shortValue());
                Puntos punto = (Puntos) ServicioCargaDatos.dameIdataBean().get(Puntos.class.getName(),
                        ana.getPuntos().getNumpunto());
                if (punto.getRedes().getNumred() == numRedArchivar) {
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }
}
