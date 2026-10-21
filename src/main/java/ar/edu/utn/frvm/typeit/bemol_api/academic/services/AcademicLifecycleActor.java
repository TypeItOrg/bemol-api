package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.AccountType;
import java.util.UUID;

record AcademicLifecycleActor(AccountType type, UUID id) {}
