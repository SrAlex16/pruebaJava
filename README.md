
# Java Test

Este proyecto se centra en el procesamiento de archivos de medidas (contadores) y su carga en una base de datos. Ha sido refactorizado significativamente desde su versión original para mejorar la modularidad, la testabilidad y la mantenibilidad.

# 1. Refactoring of *CargaDatosCUPSsftp*

*CargaDatosCUPSsftp* now injects dependencies for file decompression (*ArchivoDescompresor*) and measurement parsing (*FicheroMedidasParser*), delegating these responsibilities to their respective implementations.

<ins>Before:</ins> The decompression and parsing logic was directly embedded in private methods.

<ins>Now:</ins> *CargaDatosCUPSsftp.java* uses instances of *ArchivoDescompresor* and *FicheroMedidasParser* to perform these operations, making the class cleaner.

# 2. Introduction of *ArchivoDescompresor*

A new interface *ArchivoDescompresor* and its implementation *ArchivoDescompresorImpl* have been created to centrally manage file decompression logic.

- *ArchivoDescompresor.java:* Defines the contract for file decompression. It includes methods like *descomprimirArchivo*, *gunzip*, and *bz2* to handle different compression formats (.gz, .bz2, .zip).

- *ArchivoDescompresorImpl.java:* Provides the concrete implementation of the *ArchivoDescompresor* interface, using libraries like Apache Commons Compress and Zip4j to perform decompression operations.

- <ins>Advantage</ins>: The decompression logic is now reusable and separated from the main data loading process, improving modularity and facilitating future extensions for new compression formats.

# 3. Introduction of *FicheroMedidasParser*

A new interface *FicheroMedidasParser* and its implementation *FicheroMedidasParserImpl* have been introduced to encapsulate the specific parsing logic for measurement files.

- *FicheroMedidasParser.java:* Defines the interface for parsing measurement files and converting them into objects.

- *FicheroMedidasParserImpl.java:* Implements the actual parsing logic for the measurement file. This class now receives *ArchivadoPolicy* and *ContadorRepository* as dependencies, which allows it to decide if a file should be archived and to update counters in the database in a decoupled manner.

<ins>Advantage:</ins> The parsing logic, which previously resided directly in *CargaDatosCUPSsftp*, now has its own responsibility, which facilitates its maintenance.

# 4. Introduction of *ArchivadoPolicy*

An *ArchivadoPolicy* interface and an *ArchivadoPolicyPorRed* implementation have been created to manage the counter archiving policy.

- *ArchivadoPolicy.java:* Defines the interface for deciding if a counter should be archived.

- *ArchivadoPolicyPorRed.java:* Implements the archiving policy, deciding whether to archive a counter based on its network number (numRed).

- <ins>Advantage:</ins> The business logic for archiving is now a separate concern, allowing for changes or additions of new archiving policies without affecting the processing or persistence classes.

# 5. Introduction of ContadorRepository

An interface *ContadorRepository* and its implementation *ContadorRepositoryImpl* have been defined to manage counter access and update operations in the database.

- *ContadorRepository.java:* Interface that defines CRUD (Create, Read, Update, Delete) operations.

- *ContadorRepositoryImpl.java:* Provides the implementation of the operations defined in *ContadorRepository*, interacting with *ServicioCargaDatos* for database access.

- <ins>Advantage:</ins> We abstract the database from the persistence layer of the rest of our application without impacting the classes that use the repository.