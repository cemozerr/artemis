package tech.pegasys.artemis.util.time;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;

public class SimpleJob implements Job {
  @Override
  @SuppressWarnings({"rawtypes"})
  public void execute(JobExecutionContext context) {
    JobDataMap dataMap = context.getJobDetail().getJobDataMap();
    ScheduledEvent scheduledEvent = (ScheduledEvent) dataMap.get("task");
    scheduledEvent.run();
  }
}
