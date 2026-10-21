package ar.edu.utn.frvm.typeit.bemol_api.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

  public static final String MAIL_EXECUTOR = "mailExecutor";

  private static final int MAIL_CORE_POOL_SIZE = 2;
  private static final int MAIL_MAX_POOL_SIZE = 4;
  private static final int MAIL_QUEUE_CAPACITY = 100;
  private static final String MAIL_THREAD_NAME_PREFIX = "mail-";

  @Bean(name = AsyncConfig.MAIL_EXECUTOR)
  public ThreadPoolTaskExecutor mailExecutor() {
    final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(MAIL_CORE_POOL_SIZE);
    executor.setMaxPoolSize(MAIL_MAX_POOL_SIZE);
    executor.setQueueCapacity(MAIL_QUEUE_CAPACITY);
    executor.setThreadNamePrefix(MAIL_THREAD_NAME_PREFIX);
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    return executor;
  }
}
