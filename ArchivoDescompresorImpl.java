package com.urbener.counters;

import java.io.IOException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.File;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;

/**
 * Implementación de ArchivoDescompresor para descomprimir archivos .gz, .bz2 y .zip
 */
public class ArchivoDescompresorImpl implements ArchivoDescompresor {
    @Override
    public DescompresionResultado descomprimirArchivo(String archive) throws IOException {
        String fileLower = archive.toLowerCase();
        if (fileLower.endsWith(".gz")) {
            String out = gunzip(archive);
            return new DescompresionResultado(out, DescompresionTipo.SIMPLE, true);
        } else if (fileLower.endsWith(".bz2")) {
            String out = bz2(archive);
            return new DescompresionResultado(out, DescompresionTipo.SIMPLE, true);
        } else if (fileLower.endsWith(".zip")) {
            String outDir = archive.substring(0, archive.length() - 4);
            try {
                ZipFile zipFile = new ZipFile(archive);
                zipFile.extractAll(outDir);
            } catch (ZipException e) {
                throw new IOException("Error descomprimiendo ZIP", e);
            }
            return new DescompresionResultado(outDir, DescompresionTipo.DIRECTORIO, true);
        }
        return null;
    }

    @Override
    public String gunzip(String gz) throws IOException {
        byte[] buffer = new byte[1024];
        String gz_out = gz.substring(0, gz.length() - 3);
        FileInputStream fin = null;
        FileOutputStream out = null;
        CompressorInputStream input = null;
        try {
            fin = new FileInputStream(gz);
            BufferedInputStream bis = new BufferedInputStream(fin);
            input = new CompressorStreamFactory().createCompressorInputStream(CompressorStreamFactory.GZIP, bis);
            out = new FileOutputStream(gz_out);
            int len;
            while ((len = input.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        } catch (Exception ex) {
            throw new IOException("Error descomprimiendo GZ", ex);
        } finally {
            if (input != null) try { input.close(); } catch (IOException e) {}
            if (out != null) try { out.close(); } catch (IOException e) {}
            if (fin != null) try { fin.close(); } catch (IOException e) {}
        }
        return gz_out;
    }

    @Override
    public String bz2(String bz2) throws IOException {
        byte[] buffer = new byte[1024];
        String gz_out = bz2.substring(0, bz2.length() - 3);
        FileInputStream fin = null;
        FileOutputStream out = null;
        CompressorInputStream input = null;
        try {
            fin = new FileInputStream(bz2);
            BufferedInputStream bis = new BufferedInputStream(fin);
            input = new CompressorStreamFactory().createCompressorInputStream(bis);
            out = new FileOutputStream(gz_out);
            int len;
            while ((len = input.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        } catch (Exception ex) {
            throw new IOException("Error descomprimiendo BZ2", ex);
        } finally {
            if (input != null) try { input.close(); } catch (IOException e) {}
            if (out != null) try { out.close(); } catch (IOException e) {}
            if (fin != null) try { fin.close(); } catch (IOException e) {}
        }
        return gz_out;
    }
}
