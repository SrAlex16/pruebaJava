package com.urbener.contadores;

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
 * Procesa ficheros de intercambio de medidas y los carga en base de datos.
 * 
 * Los nombres de fichero cumplen el formato básico:
 * 
 * [TIPO]_[XXXX]_[YYYYY]_[fecha]<_fecha>.[version]<.exts>
 * 
 * Pueden venir comprimidos o no, e incluir ficheros para la verificación de integridad.
 * 
 * El formato básico del fichero es un fichero CSV, separado por ';' con la siguiente estructura genérica:
 * 
 * CUPS;Fecha y hora;Inv/Verano;Medidas
 * 
 * CUPS: Indicador único del contador
 * Inv/Verano: 0 = Invierno
 * Medidas: Número y formato dependiente del tipo de fichero
 *
 */
public class CargaDatosCUPSsftp {

	private static final short NUM_RED_ARCHIVAR = 24;
	private static final String DIR_TMP_ARCHVIVAR = "/tmp/";
	private static final String DIR_ARCHIVAR = "/home/archivo/";
	private static String CUPS_PROCESAR;

	public void run(List<String> ficheros_descargados) {
		Date f = null;
		// Procesar ficheros descargados
		int num_file = 1, total = ficheros_descargados.size();
		for (String file_str : ficheros_descargados) {
			Logger.getLogger(getClass()).info("Procesar " + file_str + ", " + (num_file++) + " de " + total);
			// Procesar
			f = this.procesarFichero(file_str);
			Logger.getLogger(getClass()).info("Procesar " + file_str + " finalizado, última fecha procesada: " + f);
		}
	}

	/**
	 * Método para establecer el tipo de procesamiento según el tipo de fichero.
	 * @param file_str ruta del fichero
	 */
	protected Date procesarFichero(String file_str) {
		Date f = null;
		Logger.getLogger(getClass()).debug("Procesar fichero: " + file_str);
		if (file_str.endsWith("sha1") || file_str.endsWith("txt") || file_str.endsWith("md5")) {
			Logger.getLogger(getClass()).info("fichero no contemplado: " + file_str);
			return f;
		}
		if (file_str.contains("P1_") || file_str.contains("P1D_") || file_str.contains("F1_")) {
			// 03/03/2020 a veces el fichero no viene comprimido, sino acabado en .0 o .1
			// en este caso procesar directamente
			if (file_str.endsWith(".0") || file_str.endsWith(".1"))
				f = procesarP1(file_str, null, file_str.contains("F1_"));
			else
				f = procesarFicheroP1(file_str);
		} else if (file_str.contains("P2D_") || file_str.contains("P2_")) {
			// 03/03/2020 a veces el fichero no viene comprimido, sino acabado en .0 o .1
			// en este caso procesar directamente
			if (file_str.endsWith(".0") || file_str.endsWith(".1"))
				f = procesarP2(file_str, null);
			else
				f = procesarFicheroP2(file_str);
		} else {
			Logger.getLogger(getClass()).debug("Tipo de fichero no contemplado.");
		}

		return f;
	}

	/**
	 * Procesar ficheros P1 comprimidos.
	 * @param zip ruta del fichero comprimido
	 */
	private Date procesarFicheroP1(String zip) {
		Logger.getLogger(getClass()).info("Procesar " + zip);
		// ficheros tipo P1D valores enteros con formato decimal
		boolean fichero_tipo_f = zip.contains("F1_");
		// dentro del zip hay ficheros de nombre tipo: P1_0031_20190515_20190517.1
		// con contenido:
		// ES0031300633899013HR1P;11;2019/05/15
		// 23:00:00;1;4;0;0;0;1;0;0;0;0;0;0;0;0;128;0;128;;;
		// ES0031300633899013HR1P;11;2019/05/15
		// 22:00:00;1;8;0;0;0;1;0;0;0;0;0;0;0;0;128;0;128;;;
		// ES0031300633899013HR1P;11;2019/05/15
		// 21:00:00;1;9;0;0;0;2;0;0;0;0;0;0;0;0;128;0;128;;;

		// ES0021000001955442JW0P;11;2019/02/23
		// 21:00:00;0;5;0;0;0;0;0;0;0;0;0;1;0;0;128;0;128;1;0

		// 29/01/2020 añadir ficheros gzip .gz
		String zip_out = null;
		// 16/02/2022 retornan un Date
		Date fecha_rec = null;
		// los ficheros gz sólo llevan un fichero de texto comprimido gzip
		if (zip.toLowerCase().endsWith(".gz")) {
			String fichero_ugz = gunzip(zip);
			if (fichero_ugz == null) {
				Logger.getLogger(getClass()).error("error al descomprimir " + zip);
				return fecha_rec;
			}
			// procesar
			fecha_rec = procesarP1(fichero_ugz, null, fichero_tipo_f);
			// borrar fichero
			File fichero_ugz_file = new File(fichero_ugz);
			fichero_ugz_file.delete();
		} else if (zip.toLowerCase().endsWith(".bz2")) {
			String fichero_ugz = bz2(zip);
			if (fichero_ugz == null) {
				Logger.getLogger(getClass()).error("error al descomprimir " + zip);
				return fecha_rec;
			}
			// procesar
			fecha_rec = procesarP1(fichero_ugz, null, fichero_tipo_f);
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
				return fecha_rec;
			}

			File dir_zip_out = new File(zip_out);

			String ficheros[] = dir_zip_out.list();

			for (String fichero_procesar : ficheros) {

				fecha_rec = procesarP1(fichero_procesar, zip_out, fichero_tipo_f);

			}
			// al final borrar directorio temporal creado
			this.borrarDir(zip_out);
		} else {
			Logger.getLogger(getClass()).info("formato no contemplado: " + zip);
		}

		return fecha_rec;
	}

	/**
	 * Procesar ficheros P2 comprimidos.
	 * @param zip ruta del fichero comprimido
	 */
	private Date procesarFicheroP2(String zip) {
		Logger.getLogger(getClass()).info("Procesar " + zip);

		// 29/01/2020 añadir ficheros gzip .gz
		String zip_out = null;
		// 16/02/2022 retornan un Date
		Date fecha_rec = null;
		// los ficheros gz sólo llevan un fichero de texto comprimido gzip
		if (zip.toLowerCase().endsWith(".gz")) {
			String fichero_ugz = gunzip(zip);
			if (fichero_ugz == null) {
				Logger.getLogger(getClass()).error("error al descomprimir " + zip);
				return fecha_rec;
			}
			// procesar
			fecha_rec = procesarP2(fichero_ugz, null);
			// borrar fichero
			File fichero_ugz_file = new File(fichero_ugz);
			fichero_ugz_file.delete();
		} else if (zip.toLowerCase().endsWith(".bz2")) {
			String fichero_ugz = bz2(zip);
			if (fichero_ugz == null) {
				Logger.getLogger(getClass()).error("error al descomprimir " + zip);
				return fecha_rec;
			}
			// procesar
			fecha_rec = procesarP2(fichero_ugz, null);
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
				return fecha_rec;
			}

			File dir_zip_out = new File(zip_out);

			String ficheros[] = dir_zip_out.list();

			for (String fichero_procesar : ficheros) {

				fecha_rec = procesarP2(fichero_procesar, zip_out);

			}
			// al final borrar directorio temporal creado
			this.borrarDir(zip_out);
		} else {
			Logger.getLogger(getClass()).info("formato no contemplado: " + zip);
		}

		return fecha_rec;
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
	 * Método encargado de importar los datos de los ficheros de curva horaria tipo P1.
	 * @param fichero_procesar	ruta del fichero
	 * @param dir_zip_out	ruta del directorio temporal descomprimido
	 * @param fichero_tipo_f	fichero tipo F (diferente orden de los campos del fichero)
	 */
	private Date procesarP1(String fichero_procesar, String dir_zip_out, boolean fichero_tipo_f) {

		Logger.getLogger(getClass()).info(fichero_procesar);

		String cups = "";
		Contador cont = null;

		SimpleDateFormat dateFormatGmt = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

		try {
			BufferedReader fich = null;
			BufferedWriter fich_archivo = null;

			if (dir_zip_out == null)
				fich = new BufferedReader(new FileReader(fichero_procesar));
			else
				fich = new BufferedReader(new FileReader(dir_zip_out + File.separator + fichero_procesar));

			// encontrar CUPS y contador asociado
			String linea = fich.readLine();

			Date contadorFecharec = null, contadorFecharec_ultima = null;

			boolean isArchivar = false, ficheroArchivo = false;

			String strFicheroArchivo = new File(fichero_procesar).getName();

			// 01/06/2022 a veces al descomprimir viene con un '.' al final, se quita
			if (strFicheroArchivo.endsWith("."))
				strFicheroArchivo = strFicheroArchivo.substring(0, strFicheroArchivo.length() - 1);

			int num_lines_archivo = 0;

			while (linea != null) {
				String campos[] = linea.split(";");
				if (cups.compareTo(campos[0]) != 0) {
					if (cont != null && contadorFecharec_ultima != null) {
						Logger.getLogger(getClass())
								.debug("cups:fecharec " + cups + ":" + dateFormatGmt.format(contadorFecharec_ultima));
						// 25/09/2020 actualizar Contador fecha rec.
						cont.setContadorFecharec(contadorFecharec_ultima);
						cont.setContadorUltimacomu(new Date());
						cont.setContadorStatus("Actualizado SFTP");
						ServicioCargaDatos.dameIdataBean().saveOrUpdate(cont);
						Logger.getLogger(getClass()).trace(cont);
						contadorFecharec_ultima = null;
					} // nuevo contador
					cups = campos[0];
					Logger.getLogger(getClass()).debug("nuevo cups: " + cups);
					// buscar contador
					cont = buscarContador(cups, "P1");

					isArchivar = hasArchivado(cont);
				}

				// 21/11/2019 si no se ha encontrado contador se siguen leyendo líneas hasta
				// nuevo CUPS
				if (cont == null) {
					linea = fich.readLine();
					continue;
				}

				if (isArchivar) {
					// crear fichero si no se ha hecho antes y escribir línea
					if (!ficheroArchivo) {
						fich_archivo = new BufferedWriter(new FileWriter(DIR_TMP_ARCHVIVAR + strFicheroArchivo));
						ficheroArchivo = true;
					}
					fich_archivo.write(linea);
					fich_archivo.newLine();
					num_lines_archivo++;
				}

				// introducir datos horarios de la señal asociada al contador

				MensajeASDU_CURV_extendido msg_c = new MensajeASDU_CURV_extendido();

				contadorFecharec = null;

				try {
					contadorFecharec = dateFormatGmt.parse(campos[2]);
					if (contadorFecharec_ultima == null || contadorFecharec_ultima.before(contadorFecharec))
						contadorFecharec_ultima = new Date(contadorFecharec.getTime());

					Logger.getLogger(getClass())
							.trace(campos[2] + " " + contadorFecharec + " " + campos[4] + " " + campos.length);

					msg_c.setFechaRespuesta(contadorFecharec);
					for (int medida = 1; medida <= 8; medida++) {
						if (fichero_tipo_f) {
							try {
								Double valor = Double.parseDouble(campos[medida + 3]);
								msg_c.setDato(medida, valor.intValue());
							} catch (Exception e) {
								Logger.getLogger(getClass()).trace(
										linea + ", medida=" + medida + ", Double=" + campos[medida + 3] + " " + e);
								// e.printStackTrace();
							}
						} else {
							try {
								// ficheros tipo P1 valores enteros (37) pero tipo P1D valores decimales
								// (37.000)
								Double valor = Double.parseDouble(campos[2 * (medida + 1)]);
								msg_c.setDato(medida, valor.intValue());
								// TODO valores 128 o superiores
								Integer flag = Integer.parseInt(campos[1 + 2 * (medida + 1)]);
								if (flag < 128)
									msg_c.setFlag(medida, flag.byteValue());
							} catch (Exception e) {
								Logger.getLogger(getClass()).trace(linea + ", medida=" + medida + ", Double="
										+ campos[2 * (medida + 1)] + " " + e);
								// e.printStackTrace();
							}
						}
					}

				} catch (Exception e1) {
					e1.printStackTrace();
					contadorFecharec = null;
				}

				if (contadorFecharec == null) {
					linea = fich.readLine();
					continue;
				}

				com.urbener.postgresql.hibernate.DatosContadores dc_p = new com.urbener.postgresql.hibernate.DatosContadores();

				dc_p.setId(cont.getContadorId());
				dc_p.setFecha(contadorFecharec);
				dc_p.setDatos(msg_c.getTrama());
				dc_p.setOrigen(DatosContadores.P1);
				try {
					ServicioCargaDatos.dameIdataBean().saveOrUpdate(dc_p);
					Logger.getLogger(getClass()).trace(dc_p);
				} catch (EjbConnectionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				linea = fich.readLine();
			}

			fich.close();

			if (ficheroArchivo) {
				fich_archivo.close();
				File f = new File(DIR_TMP_ARCHVIVAR + strFicheroArchivo);
				f.renameTo(new File(DIR_ARCHIVAR + strFicheroArchivo));
				Logger.getLogger(getClass()).debug("num_lines_archivo = " + num_lines_archivo);
			}

			if (cont != null && contadorFecharec_ultima != null) {
				Logger.getLogger(getClass())
						.debug("cups:fecharec " + cups + ":" + dateFormatGmt.format(contadorFecharec_ultima));
				// 25/09/2020 actualizar Contador fecha rec.
				cont.setContadorFecharec(contadorFecharec_ultima);
				cont.setContadorUltimacomu(new Date());
				cont.setContadorStatus("Actualizado SFTP");
				ServicioCargaDatos.dameIdataBean().saveOrUpdate(cont);
				Logger.getLogger(getClass()).trace(cont);
				return contadorFecharec_ultima;
			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;

	}

	/**
	 * Método encargado de importar los datos de los ficheros de curva cuarto horaria tipo P2.
	 * @param fichero_procesar	ruta del fichero
	 */
	private Date procesarP2(String fichero_procesar, String dir_zip_out) {
		Logger.getLogger(getClass()).info(fichero_procesar);

		String cups = "";
		Contador cont = null;

		SimpleDateFormat dateFormatGmt = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

		try {
			BufferedReader fich = null;

			if (dir_zip_out == null)
				fich = new BufferedReader(new FileReader(fichero_procesar));
			else
				fich = new BufferedReader(new FileReader(dir_zip_out + File.separator + fichero_procesar));

			// encontrar CUPS y contador asociado
			String linea = fich.readLine();

			Date contadorFecharec = null, contadorFecharec_ultima = null;

			while (linea != null) {
				String campos[] = linea.split(";");
				if (cups.compareTo(campos[0]) != 0) {
					if (cont != null && contadorFecharec_ultima != null) {
						Logger.getLogger(getClass())
								.debug("cups:fecharec " + cups + ":" + dateFormatGmt.format(contadorFecharec_ultima));
						// 25/09/2020 actualizar Contador fecha rec.
						cont.setContadorFecharec(contadorFecharec_ultima);
						cont.setContadorUltimacomu(new Date());
						cont.setContadorStatus("Actualizado SFTP");
						ServicioCargaDatos.dameIdataBean().saveOrUpdate(cont);
						Logger.getLogger(getClass()).trace(cont);
						contadorFecharec_ultima = null;
					} // nuevo contador
					cups = campos[0];
					Logger.getLogger(getClass()).debug("nuevo cups: " + cups);
					// buscar contador
					cont = buscarContador(cups, "P2");

				}

				// 21/11/2019 si no se ha encontrado contador se siguen leyendo líneas hasta
				// nuevo CUPS
				if (cont == null) {
					linea = fich.readLine();
					continue;
				}

				// introducir datos horarios de la señal asociada al contador

				MensajeASDU_CURV_extendido msg_c = new MensajeASDU_CURV_extendido();

				contadorFecharec = null;

				try {
					contadorFecharec = dateFormatGmt.parse(campos[2]);
					if (contadorFecharec_ultima == null || contadorFecharec_ultima.before(contadorFecharec))
						contadorFecharec_ultima = new Date(contadorFecharec.getTime());

					Logger.getLogger(getClass())
							.trace(campos[2] + " " + contadorFecharec + " " + campos[4] + " " + campos.length);

					msg_c.setFechaRespuesta(contadorFecharec);
					for (int medida = 1; medida <= 8; medida++) {
						try {
							Integer valor = Integer.parseInt(campos[2 * (medida + 1)]);
							msg_c.setDato(medida, valor.intValue());
							// TODO valores 128 o superiores
							Integer flag = Integer.parseInt(campos[1 + 2 * (medida + 1)]);
							if (flag < 128)
								msg_c.setFlag(medida, flag.byteValue());
						} catch (Exception e) {
							Logger.getLogger(getClass()).trace(
									linea + ", medida=" + medida + ", Double=" + campos[2 * (medida + 1)] + " " + e);
							// e.printStackTrace();
						}
					}

				} catch (Exception e1) {
					e1.printStackTrace();
					contadorFecharec = null;
				}

				if (contadorFecharec == null) {
					linea = fich.readLine();
					continue;
				}

				DatosContadores dc_p = new DatosContadores();

				dc_p.setId(cont.getContadorId());
				dc_p.setFecha(contadorFecharec);
				dc_p.setDatos(msg_c.getTrama());
//				dc_p.setOrigen(DatosContadores.P2);
				try {
					ServicioCargaDatos.dameIdataBean().saveOrUpdate(dc_p);
					Logger.getLogger(getClass()).trace(dc_p);
				} catch (EjbConnectionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				linea = fich.readLine();
			}

			fich.close();

			if (cont != null && contadorFecharec_ultima != null) {
				Logger.getLogger(getClass())
						.debug("cups:fecharec " + cups + ":" + dateFormatGmt.format(contadorFecharec_ultima));
				// 25/09/2020 actualizar Contador fecha rec.
				cont.setContadorFecharec(contadorFecharec_ultima);
				cont.setContadorUltimacomu(new Date());
				cont.setContadorStatus("Actualizado SFTP");
				ServicioCargaDatos.dameIdataBean().saveOrUpdate(cont);
				Logger.getLogger(getClass()).trace(cont);
				return contadorFecharec_ultima;
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
	 * Método para descomprimir ficheros tipo GZ
	 * @param gz	ruta del fichero a descomprimir
	 * @return	ruta del directorio con el contenido de gz descomprimido
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
	 * @param origen tipo de fichero que llama al método, se usa para escribir en los ficheros de traza el tipo de dato que se ha cargado del cups concreto (CN, P1, ...)
	 * @return	Contador asociado al que importar los datos de curva o cierres
	 */
	private Contador buscarContador(String cups, String origen) {
		// 06/10/2021 se añade concepto de CUPS_PROCESAR
		if (CUPS_PROCESAR != null && cups.compareTo(CUPS_PROCESAR) != 0)
			return null;

		Contador cont = null;
		Object tmp = null;
		try {
			// buscar CUPS con 20 primeros caracteres
			String cups_ = cups.substring(0, cups.length() - 2);
			List<Object> contadores = ServicioCargaDatos.dameIdataBean().dameObjetoConsultaHQL(
					" from Cups as c left join fetch c.contador where c.cups like '%" + cups_ + "%'");
			if (contadores != null && contadores.size() > 0) {
				cont = Cups.class.cast(contadores.get(0)).getContador();
				tmp = contadores.get(0);
				Logger.getLogger(getClass()).debug("Encontrado Cups " + cont);
			}
			if (cont == null) {
				contadores = ServicioCargaDatos.dameIdataBean().dameObjetoConsultaHQL(
						" from ClientesExternos as c left join fetch c.contador where cups like '%" + cups_ + "%'");
				if (contadores != null && contadores.size() > 0) {
					cont = ClientesExternos.class.cast(contadores.get(0)).getContador();
					tmp = contadores.get(0);
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
			successLog.info(cups + ":" + cont + ":" + origen);
		}
		return cont;
	}

	static final Logger successLog = Logger.getLogger("cupsLogger");

	/**
	 * Método auxiliar para borrar directorios temporales creados a la hora d eimportar los datos de los ficheros comprimidos.
	 * @param dir	String con la ruta del directorio
	 */
	private void borrarDir(String dir) {
		File fich = new File(dir);
		Logger.getLogger(getClass()).info("borrarDir: " + fich.getName());
		if (fich.isDirectory() && fich.list().length == 0)
			fich.delete();
		else
			for (String fichero_dir_borrar : fich.list()) {
				Logger.getLogger(getClass()).info("borrar: " + fichero_dir_borrar);
				File fich_dir_borrar_file = new File(
						fich.getPath() + System.getProperty("file.separator") + fichero_dir_borrar);
				if (fich_dir_borrar_file.isDirectory()) {
					borrarDir(fich_dir_borrar_file.getName());
				} else {
					fich_dir_borrar_file.delete();
				}
			}

		if (fich.isDirectory() && fich.list().length == 0)
			fich.delete();

	}
}
