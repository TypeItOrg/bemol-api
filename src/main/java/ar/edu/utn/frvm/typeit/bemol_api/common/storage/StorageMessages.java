package ar.edu.utn.frvm.typeit.bemol_api.common.storage;

public final class StorageMessages {
  public static final String KEY_INVALID = "La ruta del archivo no es válida.";
  public static final String FILE_NOT_FOUND = "El archivo no está disponible.";
  public static final String BUCKET_REQUIRED =
      "Debe configurar el bucket de almacenamiento de la aplicación.";
  public static final String UNAVAILABLE = "El almacenamiento de archivos no está disponible.";
  public static final String DELETE_FAILED = "No se pudo eliminar el archivo del almacenamiento.";

  private StorageMessages() {}
}
