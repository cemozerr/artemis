/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.artemis.util.time;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

import com.google.common.eventbus.EventBus;
import java.util.Date;
import org.quartz.DateBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.SimpleTrigger;
import org.quartz.impl.StdSchedulerFactory;

public class QuartzTimer implements Timer {
  final Scheduler sched;
  final SimpleTrigger trigger;
  final JobDetail job;

  @SuppressWarnings({"rawtypes"})
  public QuartzTimer(EventBus eventBus, Date startTime, Integer interval, Class objectClass)
      throws IllegalArgumentException {
    SchedulerFactory sf = new StdSchedulerFactory();
    try {
      sched =  sf.getScheduler();
      sched.start();
      ScheduledEvent task = new ScheduledEvent(eventBus, objectClass);
      job = newJob(SimpleJob.class).storeDurably(false).build();
      job.getJobDataMap().put("task", task);
      trigger = newTrigger()
              .startAt(startTime)
              .withSchedule(simpleSchedule().withIntervalInMilliseconds(interval).repeatForever())
              .build();
    } catch (SchedulerException e) {
      throw new IllegalArgumentException(
          "In QuartzTimer a SchedulerException was thrown: " + e.toString());
    }
  }

  @SuppressWarnings({"rawtypes"})
  public QuartzTimer(EventBus eventBus, Integer startDelay, Integer interval, Class objectClass) {
    this(eventBus, DateBuilder.futureDate(startDelay, DateBuilder.IntervalUnit.MILLISECOND), interval, objectClass);
  }

  @Override
  public void start() throws IllegalArgumentException {
    try {
      sched.scheduleJob(job, trigger);
    } catch (SchedulerException e) {
      throw new IllegalArgumentException(
          "In QuartzTimer a SchedulerException was thrown: " + e.toString());
    }
  }

  @Override
  public void stop() {
    try {
      sched.unscheduleJob(trigger.getKey());
    } catch (SchedulerException e) {
      throw new IllegalArgumentException(
          "In QuartzTimer a SchedulerException was thrown: " + e.toString());
    }
  }
}
