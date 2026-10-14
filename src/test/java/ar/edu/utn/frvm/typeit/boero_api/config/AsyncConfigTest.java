package ar.edu.utn.frvm.typeit.boero_api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AsyncConfigTest {

  @Test
  @DisplayName("mailExecutor usa un pool acotado con prefijo mail-")
  void mailExecutorUsesBoundedPoolWithMailPrefix() {
    final ThreadPoolTaskExecutor executor = new AsyncConfig().mailExecutor();
    executor.initialize();
    try {
      assertThat(executor.getCorePoolSize()).isEqualTo(2);
      assertThat(executor.getMaxPoolSize()).isEqualTo(4);
      assertThat(executor.getThreadNamePrefix()).isEqualTo("mail-");
      assertThat(executor.getQueueCapacity()).isEqualTo(100);
      assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
          .isInstanceOf(java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy.class);
    } finally {
      executor.shutdown();
    }
  }
}
