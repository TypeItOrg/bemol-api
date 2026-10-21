package ar.edu.utn.frvm.typeit.bemol_api.common.persistence;

import static org.hibernate.generator.EventTypeSets.INSERT_ONLY;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import java.io.Serial;
import java.util.EnumSet;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;

public class GeneratedUUIDv7Generator implements BeforeExecutionGenerator {

  @Serial private static final long serialVersionUID = 1L;

  private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();

  @Override
  public EnumSet<EventType> getEventTypes() {
    return INSERT_ONLY;
  }

  @Override
  public Object generate(
      SharedSessionContractImplementor session,
      Object owner,
      Object currentValue,
      EventType eventType) {
    return currentValue != null ? currentValue : GENERATOR.generate();
  }
}
