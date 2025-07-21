package com.urbener.counters;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.log4j.Logger;

import com.saica.cargadatos.ServicioCargaDatos;
import com.urbener.postgresql.hibernate.Analogicas;
import com.urbener.postgresql.hibernate.ClientesExternos;
import com.urbener.postgresql.hibernate.Contador;
import com.urbener.postgresql.hibernate.Cups;
import com.urbener.postgresql.hibernate.DatosContadores;
import com.urbener.postgresql.hibernate.Puntos;

import es.radsys.EjbConnectionException;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;

/**
 * Procesa files de intercambio de medidas y los carga en base de datos.
 * 
 * Los nombres de fichero cumplen el formato básico:
 * 
 * [TIPO]_[XXXX]_[YYYYY]_[fecha]<_fecha>.[version]<.exts>
 * 
 * Pueden venir comprimidos o no, e incluir files para la verificación de integridad.
 * 
 * El formato básico del fichero es un fichero CSV, separado por ';' con la siguiente estructura genérica:
 * 
 * CUPS;Fecha y hora;Inv/Verano;Medidas
 * 
 * CUPS: Indicador único del contador
 * Inv/Verano: 0 = Invierno
 * Medidas: Número y formato dependiente del type de fichero
 *
 */
public class CargaDatosCUPSsftp {

	private static final short NUM_RED_ARCHIVAR = 24;
	private static final String DIR_TMP_ARCHVIVAR = "/tmp/";
	private static final String DIR_ARCHIVAR = "/home/archive/";
	private static String CUPS_PROCESAR;

	public void run(List<String> downloadedFiles) {
		Date f = null;
		// Procesar files descargados
		int numFile = 1, total = downloadedFiles.size();
		for (String fileStr : downloadedFiles) {
			Logger.getLogger(getClass()).info("Procesar " + fileStr + ", " + (numFile++) + " de " + total);
			// Procesar
			f = this.procesarFichero(fileStr);
			Logger.getLogger(getClass()).info("Procesar " + fileStr + " finalizado, última fecha procesada: " + f);
		}
	}

	/**
	 * Método para establecer el type de procesamiento según el type de fichero.
	 * @param fileStr path del fichero
	 */
	protected Date procesarFichero(String fileStr) {
		Date f = null;
		Logger.getLogger(getClass()).debug("Procesar fichero: " + fileStr);
		if (fileStr.endsWith("sha1") || fileStr.endsWith("txt") || fileStr.endsWith("md5")) {
			Logger.getLogger(getClass()).info("fichero no contemplado: " + fileStr);
			return f;
		}
		if (fileStr.contains("P1_") || fileStr.contains("P1D_") || fileStr.contains("F1_")) {
			// 03/03/2020 a veces el fichero no viene comprimido, sino acabado en .0 o .1
			// en este caso procesar directamente
			if (fileStr.endsWith(".0") || fileStr.endsWith(".1"))
				f = procesarP1(fileStr, null, fileStr.contains("F1_"));
			else
				f = procesarFicheroP1(fileStr);
		} else if (fileStr.contains("P2D_") || fileStr.contains("P2_")) {
			// 03/03/2020 a veces el fichero no viene comprimido, sino acabado en .0 o .1
			// en este caso procesar directamente
			if (fileStr.endsWith(".0") || fileStr.endsWith(".1"))
				f = procesarP2(fileStr, null);
			else
				f = procesarFicheroP2(fileStr);
		} else {
			Logger.getLogger(getClass()).debug("Tipo de fichero no contemplado.");
		}

		return f;
	}

	/**
	 * Procesar files P1 comprimidos.
	 * @param zip path del fichero comprimido
	 */
	private Date procesarFicheroP1(String zip) {
		Logger.getLogger(getClass()).info("Procesar " + zip);
		// files type P1D valores enteros con formato decimal
		boolean fFile = zip.contains("F1_");
		// dentro del zip hay files de nombre type: P1_0031_20190515_20190517.1
		// con contenido:
		// ES0031300633899013HR1P;11;2019/05/15
		// 23:00:00;1;4;0;0;0;1;0;0;0;0;0;0;0;0;128;0;128;;;
		// ES0031300633899013HR1P;11;2019/05/15
		// 22:00:00;1;8;0;0;0;1;0;0;0;0;0;0;0;0;128;0;128;;;
		// ES0031300633899013HR1P;11;2019/05/15
		// 21:00:00;1;9;0;0;0;2;0;0;0;0;0;0;0;0;128;0;128;;;

		// ES0021000001955442JW0P;11;2019/02/23
		// 21:00:00;0;5;0;0;0;0;0;0;0;0;0;1;0;0;128;0;128;1;0

		// 29/01/2020 añadir files gzip .gz
		String zip_out = null;
		// 16/02/2022 retornan un Date
		Date recDate = null;
		// los files gz sólo llevan un fichero de texto comprimido gzip
		if (zip.toLowerCase().endsWith(".gz")) {
			String fichero_ugz = gunzip(zip);
			if (fichero_ugz == null) {
				Logger.getLogger(getClass()).error("error al descomprimir " + zip);
				return recDate;
			}
			// procesar
			recDate = procesarP1(fichero_ugz, null, fFile);
			// borrar fichero
			File fichero_ugz_file = new File(fichero_ugz);
			fichero_ugz_file.delete();
		} else if (zip.toLowerCase().endsWith(".bz2")) {
			String fichero_ugz = bz2(zip);
			if (fichero_ugz == null) {
				Logger.getLogger(getClass()).error("error al descomprimir " + zip);
				return recDate;
			}
			// procesar
			recDate = procesarP1(fichero_ugz, null, fFile);
			// borrar fichero
			File fichero_ugz_file = new File(fichero_ugz);
			fichero_ugz_file.delete();
		} else if (zip.toLowerCase().endsWith(".zip")) {
			zip_out = zip.substring(0, zip.length() - 4);
			Logger.getLogger(getClass()).info("zipFile= " + zip);
			Logger.getLogger(getClass()).info("zipOutDir= " + zip_out);

			try {
				ZipFile zipFile = new ZipFile(zip);
				zipFile.extractAll(zip_out);
			} catch (ZipException e) {
				e.printStackTrace();
			}

			// si no hay zip_out
			if (zip_out == null) {
				Logger.getLogger(getClass()).error("error al descomprimir " + zip);
				return recDate;
			}

			File dirZipOut = new File(zip_out);

			String files[] = dirZipOut.list();

			for (String processingFile : files) {

				recDate = procesarP1(processingFile, zip_out, fFile);

			}
			// al final borrar directorio temporary creado
			this.borrarDir(zip_out);
		} else {
			Logger.getLogger(getClass()).info("formato no contemplado: " + zip);
		}

		return recDate;
	}

	/**
	 * Procesar files P2 comprimidos.
	 * @param zip path del fichero comprimido
	 */
	private Date procesarFicheroP2(String zip) {
		Logger.getLogger(getClass()).info("Procesar " + zip);

		// 29/01/2020 añadir files gzip .gz
		String zip_out = null;
		// 16/02/2022 retornan un Date
		Date recDate = null;
		// los files gz sólo llevan un fichero de texto comprimido gzip
		if (zip.toLowerCase().endsWith(".gz")) {
			String fichero_ugz = gunzip(zip);
			if (fichero_ugz == null) {
				Logger.getLogger(getClass()).error("error al descomprimir " + zip);
				return recDate;
			}
			// procesar
			recDate = procesarP2(fichero_ugz, null);
			// borrar fichero
			File fichero_ugz_file = new File(fichero_ugz);
			fichero_ugz_file.delete();
		} else if (zip.toLowerCase().endsWith(".bz2")) {
			String fichero_ugz = bz2(zip);
			if (fichero_ugz == null) {
				Logger.getLogger(getClass()).error("error al descomprimir " + zip);
				return recDate;
			}
			// procesar
			recDate = procesarP2(fichero_ugz, null);
			// borrar fichero
			File fichero_ugz_file = new File(fichero_ugz);
			fichero_ugz_file.delete();
		} else if (zip.toLowerCase().endsWith(".zip")) {
			zip_out = zip.substring(0, zip.length() - 4);
			Logger.getLogger(getClass()).info("zipFile= " + zip);
			Logger.getLogger(getClass()).info("zipOutDir= " + zip_out);

			try {
				ZipFile zipFile = new ZipFile(zip);
				zipFile.extractAll(zip_out);
			} catch (ZipException e) {
				e.printStackTrace();
			}

			// si no hay zip_out
			if (zip_out == null) {
				Logger.getLogger(getClass()).error("error al descomprimir " + zip);
				return recDate;
			}

			File dirZipOut = new File(zip_out);

			String files[] = dirZipOut.list();

			for (String processingFile : files) {

				recDate = procesarP2(processingFile, zip_out);

			}
			// al final borrar directorio temporary creado
			this.borrarDir(zip_out);
		} else {
			Logger.getLogger(getClass()).info("formato no contemplado: " + zip);
		}

		return recDate;
	}

	private String bz2(String bz2) {
		byte[] buffer = new byte[1024];
		String gz_out = bz2.substring(0, bz2.length() - 3);

		try {

			FileInputStream fin = new FileInputStream(bz2);
			BufferedInputStream bis = new BufferedInputStream(fin);
			CompressorInputStream input = new CompressorStreamFactory().createCompressorInputStream(bis);

			FileOutputStream out = new FileOutputStream(gz_out);

			int len;
			while ((len = input.read(buffer)) > 0) {
				out.write(buffer, 0, len);
			}

			fin.close();
			out.close();

		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}

		return gz_out;
	}

	/**
	 * Método encargado de importar los datos de los files de curva horaria type P1.
	 * @param processingFile	path del fichero
	 * @param dirZipOut	path del directorio temporary descomprimido
	 * @param fFile	fichero type F (diferente orden de los fields del fichero)
	 */
	private Date procesarP1(String processingFile, String dirZipOut, boolean fFile) {

		Logger.getLogger(getClass()).info(processingFile);

		String cups = "";
		Contador cont = null;

		SimpleDateFormat dateFormatGmt = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

		try {
			BufferedReader file = null;
			BufferedWriter archiveFile = null;

			if (dirZipOut == null)
				file = new BufferedReader(new FileReader(processingFile));
			else
				file = new BufferedReader(new FileReader(dirZipOut + File.separator + processingFile));

			// encontrar CUPS y contador asociado
			String line = file.readLine();

			Date dateCounter = null, dateCounterLast = null;

			boolean isFile = false, fileArchivo = false;

			String archiveFileStr = new File(processingFile).getName();

			// 01/06/2022 a veces al descomprimir viene con un '.' al final, se quita
			if (archiveFileStr.endsWith("."))
				archiveFileStr = archiveFileStr.substring(0, archiveFileStr.length() - 1);

			int numFileLines = 0;

			while (line != null) {
				String fields[] = line.split(";");
				if (cups.compareTo(fields[0]) != 0) {
					if (cont != null && dateCounterLast != null) {
						Logger.getLogger(getClass())
								.debug("cups:fecharec " + cups + ":" + dateFormatGmt.format(dateCounterLast));
						// 25/09/2020 actualizar Contador fecha rec.
						cont.setContadorFecharec(dateCounterLast);
						cont.setContadorUltimacomu(new Date());
						cont.setContadorStatus("Actualizado SFTP");
						ServicioCargaDatos.dameIdataBean().saveOrUpdate(cont);
						Logger.getLogger(getClass()).trace(cont);
						dateCounterLast = null;
					} // nuevo contador
					cups = fields[0];
					Logger.getLogger(getClass()).debug("nuevo cups: " + cups);
					// buscar contador
					cont = buscarContador(cups, "P1");

					isFile = hasArchivado(cont);
				}

				// 21/11/2019 si no se ha encontrado contador se siguen leyendo líneas hasta
				// nuevo CUPS
				if (cont == null) {
					line = file.readLine();
					continue;
				}

				if (isFile) {
					// crear fichero si no se ha hecho antes y escribir línea
					if (!fileArchivo) {
						archiveFile = new BufferedWriter(new FileWriter(DIR_TMP_ARCHVIVAR + archiveFileStr));
						fileArchivo = true;
					}
					archiveFile.write(line);
					archiveFile.newLine();
					numFileLines++;
				}

				// introducir datos horarios de la señal asociada al contador

				MensajeASDU_CURV_extendido msg_c = new MensajeASDU_CURV_extendido();

				dateCounter = null;

				try {
					dateCounter = dateFormatGmt.parse(fields[2]);
					if (dateCounterLast == null || dateCounterLast.before(dateCounter))
						dateCounterLast = new Date(dateCounter.getTime());

					Logger.getLogger(getClass())
							.trace(fields[2] + " " + dateCounter + " " + fields[4] + " " + fields.length);

					msg_c.setFechaRespuesta(dateCounter);
					for (int medida = 1; medida <= 8; medida++) {
						if (fFile) {
							try {
								Double valor = Double.parseDouble(fields[medida + 3]);
								msg_c.setDato(medida, valor.intValue());
							} catch (Exception e) {
								Logger.getLogger(getClass()).trace(
										line + ", medida=" + medida + ", Double=" + fields[medida + 3] + " " + e);
								// e.printStackTrace();
							}
						} else {
							try {
								// files type P1 valores enteros (37) pero type P1D valores decimales
								// (37.000)
								Double valor = Double.parseDouble(fields[2 * (medida + 1)]);
								msg_c.setDato(medida, valor.intValue());
								// TODO valores 128 o superiores
								Integer flag = Integer.parseInt(fields[1 + 2 * (medida + 1)]);
								if (flag < 128)
									msg_c.setFlag(medida, flag.byteValue());
							} catch (Exception e) {
								Logger.getLogger(getClass()).trace(line + ", medida=" + medida + ", Double="
										+ fields[2 * (medida + 1)] + " " + e);
								// e.printStackTrace();
							}
						}
					}

				} catch (Exception e1) {
					e1.printStackTrace();
					dateCounter = null;
				}

				if (dateCounter == null) {
					line = file.readLine();
					continue;
				}

				com.urbener.postgresql.hibernate.DatosContadores dc_p = new com.urbener.postgresql.hibernate.DatosContadores();

				dc_p.setId(cont.getContadorId());
				dc_p.setFecha(dateCounter);
				dc_p.setDatos(msg_c.getTrama());
				dc_p.setOrigen(DatosContadores.P1);
				try {
					ServicioCargaDatos.dameIdataBean().saveOrUpdate(dc_p);
					Logger.getLogger(getClass()).trace(dc_p);
				} catch (EjbConnectionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

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
				Logger.getLogger(getClass())
						.debug("cups:fecharec " + cups + ":" + dateFormatGmt.format(dateCounterLast));
				// 25/09/2020 actualizar Contador fecha rec.
				cont.setContadorFecharec(dateCounterLast);
				cont.setContadorUltimacomu(new Date());
				cont.setContadorStatus("Actualizado SFTP");
				ServicioCargaDatos.dameIdataBean().saveOrUpdate(cont);
				Logger.getLogger(getClass()).trace(cont);
				return dateCounterLast;
			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;

	}

	/**
	 * Método encargado de importar los datos de los files de curva cuarto horaria type P2.
	 * @param processingFile	path del fichero
	 */
	private Date procesarP2(String processingFile, String dirZipOut) {
		Logger.getLogger(getClass()).info(processingFile);

		String cups = "";
		Contador cont = null;

		SimpleDateFormat dateFormatGmt = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

		try {
			BufferedReader file = null;

			if (dirZipOut == null)
				file = new BufferedReader(new FileReader(processingFile));
			else
				file = new BufferedReader(new FileReader(dirZipOut + File.separator + processingFile));

			// encontrar CUPS y contador asociado
			String line = file.readLine();

			Date dateCounter = null, dateCounterLast = null;

			while (line != null) {
				String fields[] = line.split(";");
				if (cups.compareTo(fields[0]) != 0) {
					if (cont != null && dateCounterLast != null) {
						Logger.getLogger(getClass())
								.debug("cups:fecharec " + cups + ":" + dateFormatGmt.format(dateCounterLast));
						// 25/09/2020 actualizar Contador fecha rec.
						cont.setContadorFecharec(dateCounterLast);
						cont.setContadorUltimacomu(new Date());
						cont.setContadorStatus("Actualizado SFTP");
						ServicioCargaDatos.dameIdataBean().saveOrUpdate(cont);
						Logger.getLogger(getClass()).trace(cont);
						dateCounterLast = null;
					} // nuevo contador
					cups = fields[0];
					Logger.getLogger(getClass()).debug("nuevo cups: " + cups);
					// buscar contador
					cont = buscarContador(cups, "P2");

				}

				// 21/11/2019 si no se ha encontrado contador se siguen leyendo líneas hasta
				// nuevo CUPS
				if (cont == null) {
					line = file.readLine();
					continue;
				}

				// introducir datos horarios de la señal asociada al contador

				MensajeASDU_CURV_extendido msg_c = new MensajeASDU_CURV_extendido();

				dateCounter = null;

				try {
					dateCounter = dateFormatGmt.parse(fields[2]);
					if (dateCounterLast == null || dateCounterLast.before(dateCounter))
						dateCounterLast = new Date(dateCounter.getTime());

					Logger.getLogger(getClass())
							.trace(fields[2] + " " + dateCounter + " " + fields[4] + " " + fields.length);

					msg_c.setFechaRespuesta(dateCounter);
					for (int medida = 1; medida <= 8; medida++) {
						try {
							Integer valor = Integer.parseInt(fields[2 * (medida + 1)]);
							msg_c.setDato(medida, valor.intValue());
							// TODO valores 128 o superiores
							Integer flag = Integer.parseInt(fields[1 + 2 * (medida + 1)]);
							if (flag < 128)
								msg_c.setFlag(medida, flag.byteValue());
						} catch (Exception e) {
							Logger.getLogger(getClass()).trace(
									line + ", medida=" + medida + ", Double=" + fields[2 * (medida + 1)] + " " + e);
							// e.printStackTrace();
						}
					}

				} catch (Exception e1) {
					e1.printStackTrace();
					dateCounter = null;
				}

				if (dateCounter == null) {
					line = file.readLine();
					continue;
				}

				DatosContadores dc_p = new DatosContadores();

				dc_p.setId(cont.getContadorId());
				dc_p.setFecha(dateCounter);
				dc_p.setDatos(msg_c.getTrama());
//				dc_p.setOrigen(DatosContadores.P2);
				try {
					ServicioCargaDatos.dameIdataBean().saveOrUpdate(dc_p);
					Logger.getLogger(getClass()).trace(dc_p);
				} catch (EjbConnectionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				line = file.readLine();
			}

			file.close();

			if (cont != null && dateCounterLast != null) {
				Logger.getLogger(getClass())
						.debug("cups:fecharec " + cups + ":" + dateFormatGmt.format(dateCounterLast));
				// 25/09/2020 actualizar Contador fecha rec.
				cont.setContadorFecharec(dateCounterLast);
				cont.setContadorUltimacomu(new Date());
				cont.setContadorStatus("Actualizado SFTP");
				ServicioCargaDatos.dameIdataBean().saveOrUpdate(cont);
				Logger.getLogger(getClass()).trace(cont);
				return dateCounterLast;
			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	private boolean hasArchivado(Contador cont) {

		if (cont != null && cont.getNumsenana() != null) {
			try {
				Analogicas ana = (Analogicas) ServicioCargaDatos.dameIdataBean().get(Analogicas.class.getName(),
						cont.getNumsenana().shortValue());
				Puntos punto = (Puntos) ServicioCargaDatos.dameIdataBean().get(Puntos.class.getName(),
						ana.getPuntos().getNumpunto());
				if (punto.getRedes().getNumred() == NUM_RED_ARCHIVAR) {
					Logger.getLogger(getClass()).debug("CUPS Archivar: " + cont);
					return true;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return false;
	}

	/**
	 * Método para descomprimir files type GZ
	 * @param gz	path del fichero a descomprimir
	 * @return	path del directorio con el contenido de gz descomprimido
	 */
	private String gunzip(String gz) {

		byte[] buffer = new byte[1024];
		String gz_out = gz.substring(0, gz.length() - 3);

		try {

			GZIPInputStream gzis = new GZIPInputStream(new FileInputStream(gz));

			FileOutputStream out = new FileOutputStream(gz_out);

			int len;
			while ((len = gzis.read(buffer)) > 0) {
				out.write(buffer, 0, len);
			}

			gzis.close();
			out.close();

		} catch (IOException ex) {
			ex.printStackTrace();
			return null;
		}

		return gz_out;

	}

	/**
	 * Método auxiliar para buscar el contador asociado a un CUPS dado.
	 * @param cups	CUPS de búsqueda
	 * @param origin type de fichero que llama al método, se usa para escribir en los files de traza el type de dato que se ha cargado del cups concreto (CN, P1, ...)
	 * @return	Contador asociado al que importar los datos de curva o cierres
	 */
	private Contador buscarContador(String cups, String origin) {
		// 06/10/2021 se añade concepto de CUPS_PROCESAR
		if (CUPS_PROCESAR != null && cups.compareTo(CUPS_PROCESAR) != 0)
			return null;

		Contador cont = null;
		Object tmp = null;
		try {
			// buscar CUPS con 20 primeros caracteres
			String cups_ = cups.substring(0, cups.length() - 2);
			List<Object> counters = ServicioCargaDatos.dameIdataBean().dameObjetoConsultaHQL(
					" from Cups as c left join fetch c.contador where c.cups like '%" + cups_ + "%'");
			if (counters != null && counters.size() > 0) {
				cont = Cups.class.cast(counters.get(0)).getContador();
				tmp = counters.get(0);
				Logger.getLogger(getClass()).debug("Encontrado Cups " + cont);
			}
			if (cont == null) {
				counters = ServicioCargaDatos.dameIdataBean().dameObjetoConsultaHQL(
						" from ClientesExternos as c left join fetch c.contador where cups like '%" + cups_ + "%'");
				if (counters != null && counters.size() > 0) {
					cont = ClientesExternos.class.cast(counters.get(0)).getContador();
					tmp = counters.get(0);
					Logger.getLogger(getClass()).debug("Encontrado ClientesExternos " + cont);
				}
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (cont == null) {
			Logger.getLogger(getClass()).error("CUPS no encontrado: " + cups);
		} else {
			successLog.info(cups + ":" + cont + ":" + origin);
		}
		return cont;
	}

	static final Logger successLog = Logger.getLogger("cupsLogger");

	/**
	 * Método auxiliar para borrar directorios temporales creados a la hora d eimportar los datos de los files comprimidos.
	 * @param dir	String con la path del directorio
	 */
	private void borrarDir(String dir) {
		File file = new File(dir);
		Logger.getLogger(getClass()).info("borrarDir: " + file.getName());
		if (file.isDirectory() && file.list().length == 0)
			file.delete();
		else
			for (String fichero_dir_borrar : file.list()) {
				Logger.getLogger(getClass()).info("borrar: " + fichero_dir_borrar);
				File fich_dir_borrar_file = new File(
						file.getPath() + System.getProperty("file.separator") + fichero_dir_borrar);
				if (fich_dir_borrar_file.isDirectory()) {
					borrarDir(fich_dir_borrar_file.getName());
				} else {
					fich_dir_borrar_file.delete();
				}
			}

		if (file.isDirectory() && file.list().length == 0)
			file.delete();

	}
}
