package com.urbener.counters;

import java.util.Date;
import java.text.SimpleDateFormat;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import org.apache.log4j.Logger;

/**
 * Implementación de FicheroMedidasParser para parsear files de medidas.
 */
public class FicheroMedidasParserImpl implements FicheroMedidasParser {
    private ArchivadoPolicy policy;
    private static final String DIR_TMP_ARCHVIVAR = "/tmp/";
    private static final String DIR_ARCHIVAR = "/home/archive/";

    public FicheroMedidasParserImpl(ArchivadoPolicy policy) {
        this.policy = policy;
    }

    @Override
    public Date procesarCurva(String processingFile, String dirZipOut, String type, boolean fFile, Object constantOrigin) {
        Logger.getLogger(getClass()).info(processingFile);
        // ...aquí iría la lógica de parseo, similar a la de CargaDatosCUPSsftp.procesarCurva...
        // Por brevedad, se omite la implementación completa.
        return null;
    }
}
