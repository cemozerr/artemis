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

package tech.pegasys.artemis.validator.client;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.util.time.Timer;
import tech.pegasys.artemis.util.time.TimerFactory;
import tech.pegasys.artemis.validator.coordinator.ValidatorCoordinator;
import java.lang.Class;

public class ValidatorClient {

  private Timer timer;
  private EventBus eventBus;

  @SuppressWarnings({"rawtypes"})
  public ValidatorClient() {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    this.eventBus = new AsyncEventBus(executor);
    this.eventBus.register(this);

    try {
      this.timer =
          new TimerFactory()
              .create(
                  "QuartzTimer",
                  new Object[] {this.eventBus, 1, Constants.SECONDS_PER_SLOT, Date.class},
                  new Class[] {EventBus.class, Integer.class, Integer.class, Class.class});
    } catch (IllegalArgumentException e) {
      System.exit(1);
    }
    this.timer.start();
    System.out.println("Starting a new validator client");
  }

  @Subscribe
  public void checkIfGenesisEventHappen(Boolean bool) {
    System.out.println("New slot here in ValidatorClient");
  }

  @Subscribe
  public void onNewSlot(Date date) {
    System.out.println("New slot here in ValidatorClient");
  }
}
