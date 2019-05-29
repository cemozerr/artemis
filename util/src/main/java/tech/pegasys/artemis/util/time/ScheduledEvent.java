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

import com.google.common.eventbus.EventBus;

import java.lang.reflect.InvocationTargetException;
import java.util.Date;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class ScheduledEvent implements Runnable{

  private EventBus eventBus;
  @SuppressWarnings({"rawtypes"})
  private Class eventClass;

  public ScheduledEvent() {}

  @SuppressWarnings({"rawtypes"})
  public ScheduledEvent(EventBus eventBus, Class eventClass) {
    this.eventBus = eventBus;
    this.eventClass = eventClass;
  }

  /**
   * When an object implementing interface <code>Runnable</code> is used to create a thread,
   * starting the thread causes the object's <code>run</code> method to be called in that separately
   * executing thread.
   *
   * <p>The general contract of the method <code>run</code> is that it may take any action
   * whatsoever.
   *
   * @see Thread#run()
   */
  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public void run() {
    try {
      this.eventBus.post(eventClass.getDeclaredConstructor().newInstance());
    } catch (InstantiationException
            | NoSuchMethodException
            | IllegalAccessException
            | InvocationTargetException e){
      System.out.println(e);
    }
  }
}
