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
 * Interfaz para parsear files de medidas y convertirlos en objetos de dominio.
 */
public interface FicheroMedidasParser {
    Date procesarCurva(String fichero, String dirZipOut, String type, boolean ficheroTipoF, Object constantOrigin);
}

/**
 * Implementación de FicheroMedidasParser para parsear files de medidas.
 */
class FicheroMedidasParserImpl implements FicheroMedidasParser {
    private ArchivadoPolicy policy;
    private ContadorRepository contadorRepository;
    private static final String DIR_TMP_ARCHVIVAR = "/tmp/";
    private static final String DIR_ARCHIVAR = "/home/archive/";

    public FicheroMedidasParserImpl(ArchivadoPolicy policy, ContadorRepository contadorRepository) {
        this.policy = policy;
        this.contadorRepository = contadorRepository;
    }

    @Override
    public Date procesarCurva(String processingFile, String dirZipOut, String type, boolean fFile, Object constantOrigin) {
        Logger.getLogger(getClass()).info(processingFile);
        String cups = "";
        com.urbener.postgresql.hibernate.Contador cont = null;
        SimpleDateFormat dateFormatGmt = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        try {
            BufferedReader file = null;
            BufferedWriter archiveFile = null;
            if (dirZipOut == null)
                file = new BufferedReader(new FileReader(processingFile));
            else
                file = new BufferedReader(new FileReader(dirZipOut + File.separator + processingFile));
            String line = file.readLine();
            Date dateCounter = null, dateCounterLast = null;
            boolean isFile = false, fileArchivo = false;
            String archiveFileStr = new File(processingFile).getName();
            if (archiveFileStr.endsWith("."))
                archiveFileStr = archiveFileStr.substring(0, archiveFileStr.length() - 1);
            int numFileLines = 0;
            while (line != null) {
                String fields[] = line.split(";");
                int minFields = type.equals("P1") ? (fFile ? 12 : 19) : 19;
                if (fields.length < minFields) {
                    Logger.getLogger(getClass()).warn("Línea descartada por número insuficiente de fields (" + fields.length + "): " + line);
                    line = file.readLine();
                    continue;
                }
                if (!cups.equals(fields[0])) {
                    if (cont != null && dateCounterLast != null) {
                        contadorRepository.actualizarContadorRecepcion(cont, dateCounterLast, cups);
                        dateCounterLast = null;
                    }
                    cups = fields[0];
                    Logger.getLogger(getClass()).debug("nuevo cups: " + cups);
                    cont = contadorRepository.buscarContador(cups, type);
                    isFile = type.equals("P1") ? policy.debeArchivar(cont) : false;
                }
                if (cont == null) {
                    line = file.readLine();
                    continue;
                }
                if (isFile) {
                    if (!fileArchivo) {
                        archiveFile = new BufferedWriter(new FileWriter(DIR_TMP_ARCHVIVAR + archiveFileStr));
                        fileArchivo = true;
                    }
                    archiveFile.write(line);
                    archiveFile.newLine();
                    numFileLines++;
                }
                // Aquí iría el parseo de medidas y guardado en base de datos (omitido por brevedad)
                line = file.readLine();
            }
            file.close();
            if (fileArchivo) {
                archiveFile.close();
                File f = new File(DIR_TMP_ARCHVIVAR + archiveFileStr);
                f.renameTo(new File(DIR_ARCHIVAR + archiveFileStr));
                Logger.getLogger(getClass()).debug("numFileLines = " + numFileLines);
            }
            if (cont != null && dateCounterLast != null) {
                contadorRepository.actualizarContadorRecepcion(cont, dateCounterLast, cups);
                return dateCounterLast;
            }
        } catch (Exception e) {
            Logger.getLogger(getClass()).error("Error procesando curva", e);
        }
        return null;
    }
}
