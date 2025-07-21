package com.urbener.counters;

import com.urbener.postgresql.hibernate.Contador;

/**
 * Política para decidir si un contador debe ser archivado.
 */
/**
 * Interfaz para la política de archivado.
 */
public interface ArchivadoPolicy {
    boolean debeArchivar(Object cont);
}

/**
 * Implementación de ArchivadoPolicy por red.
 */
class ArchivadoPolicyPorRed implements ArchivadoPolicy {
    private short valor;
    public ArchivadoPolicyPorRed(short valor) {
        this.valor = valor;
    }
    @Override
    public boolean debeArchivar(Object cont) {
        // Lógica de archivado por red (ejemplo: siempre archiva)
        return true;
    }
}
