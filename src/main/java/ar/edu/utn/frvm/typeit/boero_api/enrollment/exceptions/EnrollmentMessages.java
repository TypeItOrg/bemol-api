package ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions;

public final class EnrollmentMessages {
  public static final String APPLICATION_ACADEMIC_YEAR_INVALID =
      "La solicitud debe pertenecer al ciclo lectivo activo de la institución.";
  public static final String COURSE_OUTSIDE_ACADEMIC_YEAR =
      "El curso no pertenece al ciclo lectivo de la solicitud.";
  public static final String DOCUMENT_REQUEST_CLOSED =
      "Solo se puede solicitar documentación en inscripciones enviadas o aprobadas provisionalmente.";
  public static final String DOCUMENT_REQUEST_DUPLICATE =
      "El documento ya está requerido en esta inscripción. Consultá su requisito existente.";
  public static final String DOCUMENT_REQUEST_INACTIVE =
      "El documento no está activo en el catálogo de esta institución.";
  public static final String DOCUMENT_REQUEST_IMMUTABLE =
      "El pedido y sus condiciones no se pueden modificar.";
  public static final String DOCUMENT_REQUIREMENT_RETIRED =
      "Este requisito fue retirado. Su documentación se conserva como historial.";
  public static final String DOCUMENT_FORMAT_CHANGED =
      "El formato admitido cambió. Reemplazá el archivo o retiralo si es opcional.";
  public static final String DOCUMENT_REQUIREMENT_NOT_FOUND =
      "El requisito documental no pertenece a esta solicitud o trayecto.";
  public static final String DOCUMENT_SUBMISSION_REQUIRED =
      "Adjuntá todos los documentos obligatorios para enviar la solicitud.";
  public static final String DOCUMENT_PROVISIONAL_REQUIRED =
      "Para admitir provisoriamente, aceptá todos los documentos exigidos antes de aprobar. Debe quedar documentación obligatoria para confirmar pendiente.";
  public static final String DOCUMENT_CONFIRMATION_REQUIRED =
      "Todos los documentos obligatorios deben estar aceptados para confirmar la inscripción.";
  public static final String DOCUMENT_VERSION_LOCKED =
      "Esta entrega fue reemplazada, retirada o aceptada y no puede modificarse.";
  public static final String DOCUMENT_REVIEW_INVALID =
      "Solo se puede aceptar u observar una entrega pendiente de revisión.";
  public static final String DOCUMENT_OBSERVATION_REQUIRED = "Indicá el motivo de la observación.";
  public static final String DOCUMENT_WITHDRAW_DENIED =
      "El documento inicial es obligatorio; podés reemplazarlo, pero no retirarlo.";
  public static final String DOCUMENT_FORMAT_INVALID =
      "El formato no está admitido para este requisito documental.";
  public static final String FILE_CONTENT_INVALID =
      "El archivo no es un PDF, JPEG o PNG válido, o su contenido no coincide con el tipo indicado.";
  public static final String FILE_IMAGE_TOO_LARGE =
      "La imagen no puede superar los 25 millones de píxeles.";
  public static final String FILE_PDF_ENCRYPTED =
      "El PDF debe poder leerse sin contraseña y no estar cifrado.";
  public static final String STORAGE_DESTINATION_PENDING =
      "Hay documentos o trabajos pendientes asociados a otro destino de almacenamiento. Revisá el procedimiento de migración.";
  public static final String STORAGE_JOB_UNAVAILABLE =
      "La operación de almacenamiento ya no está disponible.";
  public static final String STORAGE_DELETE_FAILED =
      "No se pudo eliminar el archivo del almacenamiento.";
  public static final String PERIOD_SCOPE_INVALID =
      "Seleccioná planes publicados y al menos un nivel o espacios sin nivel de la misma institución.";
  public static final String PERIOD_SCOPE_REQUIRED =
      "El período todavía no tiene una oferta configurada.";
  public static final String PERIOD_SCOPE_OVERLAP =
      "Ya existe un período planificado o abierto para ese plan y nivel en las fechas seleccionadas.";
  public static final String PERIOD_SCOPE_SHRINK =
      "Un período con solicitudes enviadas solo permite ampliar su oferta.";
  public static final String COURSE_OUTSIDE_PERIOD =
      "El curso no está habilitado por el período de esta solicitud.";
  public static final String ACADEMIC_REQUIREMENTS_PENDING =
      "No se puede solicitar ni matricular este curso sin cumplir sus correlatividades.";

  public static final String SCHEDULE_WEEK_INVALID =
      "La fecha debe estar entre los años 1900 y 2100.";

  public static final String PERIOD_NOT_FOUND = "Período de inscripción no encontrado.";
  public static final String S3_BUCKET_REQUIRED =
      "Debe configurar el bucket de almacenamiento de inscripciones.";

  public static final String PERIOD_NOT_OPEN =
      "No existe un período de inscripción habilitado actualmente.";
  public static final String APPLICATION_ID_NOT_FOUND =
      "No se encontró la solicitud de inscripción con ID ";
  public static final String PERIOD_DATES_INVALID =
      "La fecha de inicio del período de inscripción debe ser anterior o igual a la fecha de fin.";
  public static final String ACTIVE_PATH_APPLICATION_EXISTS =
      "Ya existe una solicitud de inscripción activa para ese trayecto formativo.";
  public static final String ATTACHMENT_ID_NOT_FOUND = "No se encontró el archivo adjunto con ID ";
  public static final String ACADEMIC_YEAR_ID_NOT_FOUND = "No se encontró el ciclo lectivo con ID ";
  public static final String ACADEMIC_YEAR_AMBIGUOUS =
      "La institución tiene más de un ciclo lectivo activo. Contactá a la administración.";
  public static final String INSTRUMENT_ID_NOT_FOUND = "Instrumento no encontrado: ";
  public static final String SPACE_ID_NOT_FOUND = "Espacio de plan de estudio no encontrado: ";
  public static final String APPLICANT_REQUIRED = "Los datos del aspirante son obligatorios";
  public static final String NAME_REQUIRED = "El nombre es obligatorio";
  public static final String LAST_NAME_REQUIRED = "El apellido es obligatorio";
  public static final String DOCUMENT_REQUIRED = "El número de documento es obligatorio";
  public static final String EMAIL_REQUIRED = "El correo electrónico es obligatorio";
  public static final String BIRTH_DATE_REQUIRED = "La fecha de nacimiento es obligatoria";
  public static final String APPLICANT_MUST_BE_ADULT =
      "Quien se inscribe por sí mismo debe ser mayor de 18 años";
  public static final String RESPONSIBLE_REQUIRED =
      "Los postulantes menores de 18 años deben incluir los datos del tutor o responsable legal";
  public static final String RESPONSIBLE_NAME_REQUIRED = "El nombre del responsable es obligatorio";
  public static final String RESPONSIBLE_DOCUMENT_REQUIRED =
      "El documento del responsable es obligatorio";
  public static final String RESPONSIBLE_PHONE_REQUIRED =
      "El teléfono del responsable es obligatorio";
  public static final String EDUCATION_REQUIRED = "Los antecedentes educativos son obligatorios";
  public static final String CURRENTLY_STUDYING_REQUIRED =
      "Indicá si actualmente asistís a una institución educativa";
  public static final String EDUCATION_LEVEL_REQUIRED = "El nivel educativo es obligatorio";
  public static final String CURRENT_EDUCATION_LEVEL_INVALID =
      "Seleccioná un nivel educativo para indicar que estudiás actualmente";
  public static final String EDUCATION_INSTITUTION_REQUIRED =
      "La institución educativa actual es obligatoria";
  public static final String EDUCATION_COMPLETION_REQUIRED =
      "Indicá si completaste el máximo nivel alcanzado";
  public static final String SECONDARY_COMPLETION_REQUIRED = "Indicá si completaste el secundario";
  public static final String SHIFT_REQUIRED = "El turno preferido es obligatorio";
  public static final String PREVIOUS_TEACHER_REQUIRED =
      "El docente previo es obligatorio para aspirantes reingresantes";
  public static final String SUBMISSION_INCOMPLETE =
      "Existen campos obligatorios sin completar para enviar la inscripción";
  public static final String STUDY_PLAN_REQUIRED = "El plan de estudio es obligatorio";
  public static final String ACADEMIC_YEAR_REQUIRED = "El ciclo lectivo es obligatorio";
  public static final String STATUS_REQUIRED = "El estado es obligatorio";
  public static final String NAME_TOO_LONG = "El nombre no debe superar los 150 caracteres";
  public static final String START_DATE_REQUIRED = "La fecha de inicio es obligatoria";
  public static final String END_DATE_REQUIRED = "La fecha de fin es obligatoria";
  public static final String FILE_EMPTY = "El archivo no puede estar vacío.";
  public static final String FILE_TOO_LARGE = "El tamaño del archivo no puede superar los 10MB.";
  public static final String FILE_TYPE_REQUIRED = "Tipo de archivo no especificado.";
  public static final String FILE_TYPE_INVALID =
      "Tipo de archivo no permitido. Solo se permiten formatos PDF, JPG y PNG.";
  public static final String FILE_PATH_TRAVERSAL = "Intento de path traversal detectado.";
  public static final String FILE_NOT_FOUND = "No se encontró el archivo físico en almacenamiento.";
  public static final String FILE_UNREADABLE = "No se pudo leer el archivo físico.";
  public static final String FILE_PATH_INVALID = "Ruta de archivo inválida.";
  public static final String FILE_PATH_REQUIRED = "Ruta de archivo no especificada.";
  public static final String FILE_OUTSIDE_STORAGE =
      "Acceso no permitido fuera del directorio de almacenamiento.";
  public static final String FILE_EXTENSION_INVALID = "Extensión de archivo no permitida.";
  public static final String ATTACHMENT_TYPE_REQUIRED = "El tipo de adjunto es obligatorio.";
  public static final String ATTACHMENT_TYPE_INVALID =
      "Tipo de adjunto no válido. Tipos aceptados: ";
  public static final String ATTACHMENT_MODIFY_DENIED =
      "No tiene permisos para modificar adjuntos de esta solicitud.";

  public static final String ATTACHMENT_TYPE_CONFLICT =
      "Ya existe un adjunto activo de este tipo. Volvé a intentar.";
  public static final String ATTACHMENT_ACCESS_DENIED =
      "No tiene permisos para acceder a los adjuntos de esta solicitud.";
  public static final String ATTACHMENT_NAME_TOO_LONG =
      "El nombre del archivo no puede superar los 255 caracteres.";

  public static final String ENROLLMENT_STORAGE_UNAVAILABLE =
      "No se pudo inicializar el almacenamiento de adjuntos de inscripción: ";
  public static final String ENROLLMENT_APPLICATION_INSTRUMENT_REQUIRED =
      "Debe seleccionar un instrumento para cada espacio que lo requiere.";

  public static final String ENROLLMENT_APPLICATION_NOT_FOUND =
      "La solicitud de inscripción especificada no existe.";
  public static final String ENROLLMENT_APPLICATION_NOT_EDITABLE =
      "La solicitud de inscripción ya no puede editarse.";
  public static final String ENROLLMENT_APPLICATION_DATA_REQUIRED =
      "Los datos del borrador son requeridos.";
  public static final String ENROLLMENT_APPLICATION_DATA_INVALID =
      "Los datos del borrador deben ser un objeto JSON válido.";
  public static final String ENROLLMENT_APPLICATION_TRAINING_PATH_INVALID =
      "El trayecto formativo seleccionado no está habilitado para inscripción.";
  public static final String ENROLLMENT_APPLICATION_STUDY_PLAN_SPACES_INVALID =
      "Los espacios academicos seleccionados no son validos.";
  public static final String ENROLLMENT_APPLICATION_STUDY_PLAN_SPACE_INVALID =
      "Uno o mas espacios academicos seleccionados no estan habilitados para inscripcion.";
  public static final String ENROLLMENT_APPLICATION_INSTRUMENT_SELECTION_INVALID =
      "La seleccion de instrumentos no es valida.";
  public static final String ENROLLMENT_APPLICATION_INSTRUMENT_INVALID =
      "Uno o mas instrumentos seleccionados no estan habilitados para inscripcion.";
  public static final String ENROLLMENT_APPLICATION_APPLICANT_REQUIRED =
      "Solo un postulante puede operar sobre solicitudes de inscripción.";
  public static final String ENROLLMENT_APPLICATION_SPACES_REQUIRED =
      "Debe seleccionar al menos un curso para enviar la inscripción.";
  public static final String ENROLLMENT_APPLICATION_SPACES_DUPLICATED =
      "No puede seleccionar el mismo curso más de una vez.";
  public static final String PERSON_ID_NOT_FOUND = "No se encontró la persona con ID ";
  public static final String ENROLLMENT_APPLICATION_TEACHER_INVALID =
      "El docente preferido no dicta clases en el curso seleccionado.";
  public static final String COURSE_ENROLLMENT_NOT_FOUND = "La cursada especificada no existe.";
  public static final String COURSE_NOT_ACTIVE = "El curso seleccionado no está activo.";
  public static final String COURSE_CLASS_INVALID =
      "La clase seleccionada no pertenece al curso indicado.";
  public static final String COURSE_ASSIGNMENT_REQUIRED =
      "Debe seleccionar al menos un día u horario.";
  public static final String COURSE_ASSIGNMENT_INVALID =
      "La asignación horaria no pertenece a la clase seleccionada.";
  public static final String COURSE_ASSIGNMENT_CONFLICT =
      "La asignación horaria entra en conflicto con otra cursada vigente.";
  public static final String COURSE_CAPACITY_EXCEEDED =
      "No existe capacidad disponible para la asignación seleccionada.";
  public static final String COURSE_ALREADY_ENROLLED =
      "El estudiante ya tiene una cursada vigente para este curso.";
  public static final String COURSE_ALREADY_REQUESTED =
      "El curso ya está solicitado en otra inscripción vigente.";
  public static final String COURSE_ENROLLMENT_REASON_REQUIRED =
      "Debe indicar un motivo para modificar la cursada.";
  public static final String COURSE_ENROLLMENT_VERSION_STALE =
      "La cursada fue modificada por otra operación. Actualizá los datos e intentá nuevamente.";
  public static final String PARENT_NOT_APPROVED =
      "La solicitud documental todavía no está aprobada.";
  public static final String COURSE_APPLICATION_NOT_FOUND =
      "La solicitud de cursada especificada no existe.";
  public static final String COURSE_APPLICATION_ALREADY_RESOLVED =
      "La solicitud de cursada ya fue resuelta y no puede modificarse.";

  public static final String APPLICATION_NOT_FOUND =
      "La solicitud de inscripción no existe o no pertenece a la institución.";
  public static final String APPLICATION_NOT_OWNED =
      "La solicitud de inscripción no pertenece al postulante.";
  public static final String APPLICATION_ALREADY_RESOLVED =
      "La solicitud de inscripción ya fue resuelta y no puede modificarse.";
  public static final String APPLICATION_NOT_PENDING_EVALUATION =
      "Solo las solicitudes con estado 'Pendiente de evaluación' pueden resolverse.";
  public static final String APPLICATION_NOT_EDITABLE =
      "La solicitud de inscripción no se encuentra en estado editable.";
  public static final String APPLICATION_CANNOT_SUBMIT =
      "La solicitud de inscripción no puede enviarse en su estado actual.";
  public static final String PERIOD_CLOSED =
      "No existe un período de inscripción habilitado para el ciclo lectivo seleccionado.";
  public static final String REJECTION_REASON_REQUIRED =
      "Debe indicar un motivo para rechazar la solicitud.";

  public static final String ACADEMIC_TRANSITION_INVALID =
      "El resultado no es válido para el estado actual de la cursada.";
  public static final String COURSE_CLASSES_HAVE_ENROLLMENTS =
      "No se pueden reemplazar las clases de un curso con cursadas registradas.";
  public static final String COURSE_SELECTION_REQUIRED =
      "Seleccioná un trayecto y cursos para iniciar la solicitud.";

  private EnrollmentMessages() {}
}
