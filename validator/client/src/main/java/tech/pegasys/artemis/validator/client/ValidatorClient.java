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
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.common.primitives.UnsignedLong;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.util.time.Timer;
import tech.pegasys.artemis.util.time.TimerFactory;
import tech.pegasys.artemis.validator.coordinator.CommitteeAssignmentTuple;
import tech.pegasys.artemis.validator.coordinator.ValidatorCoordinator;
import java.lang.Class;

public class ValidatorClient {

  private Timer timer;
  private UnsignedLong slot;
  private EventBus eventBus;
  private Integer GENESIS_CHECK_FREQUENCY = 10000; // in milliseconds
  private Integer DELTA = 800; // in milliseconds

  @SuppressWarnings({"rawtypes"})
  public ValidatorClient() {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    this.eventBus = new AsyncEventBus(executor);
    this.eventBus.register(this);

    setTimer("beforeGenesis", 0);
    System.out.println("Starting a new validator client");
  }

  @Subscribe
  public void checkIfGenesisEventHappened(GenesisCheckEvent event) {
    System.out.println("Checking if Genesis Event happened");
    Date genesisTime = ValidatorCoordinator.getGenesisTime();
    if (genesisTime != null) {
      this.timer.stop();
      Date currentTime = new Date();
      int durationSinceGenesis = Math.toIntExact(currentTime.getTime() - genesisTime.getTime());
      slot = UnsignedLong.valueOf(Constants.GENESIS_SLOT + (durationSinceGenesis / Constants.SECONDS_PER_SLOT));
      int durationSinceLastSlot = durationSinceGenesis % (Constants.SECONDS_PER_SLOT * 1000);
      int durationUntilNextSlot = (Constants.SECONDS_PER_SLOT * 1000) - durationSinceLastSlot;
      setTimer("afterGenesis", durationUntilNextSlot - DELTA);
    }
  }

  @Subscribe
  public void onNewSlot(DateEvent date) {
    slot =
    System.out.println("New slot here in ValidatorClient: " + date.getDate());
    int validator_index = 2;
    boolean registry_change = false;
    Optional<CommitteeAssignmentTuple> committeeAssignment
            = ValidatorCoordinator.get_committee_assignment(epoch, validator_index, registry_change);
    System.out.println("shard: " + committeeAssignment.get().getShard());
    System.out.println("slot: " + committeeAssignment.get().getSlot());
    System.out.println("validators: " + committeeAssignment.get().getValidators());
    System.out.println("isProposer: " + committeeAssignment.get().isProposer());
  }

  @SuppressWarnings({"rawtypes"})
  private void setTimer(String state, Integer startDelay) {
    try {
      switch (state) {
        case "beforeGenesis":
          this.timer =
                  new TimerFactory()
                          .create(
                                  "QuartzTimer",
                                  new Object[]{this.eventBus, startDelay, GENESIS_CHECK_FREQUENCY, GenesisCheckEvent.class},
                                  new Class[]{EventBus.class, Integer.class, Integer.class, Class.class});
          break;
        case "afterGenesis":
          this.timer.stop();
          this.timer =
                  new TimerFactory()
                          .create(
                                  "QuartzTimer",
                                  new Object[]{this.eventBus, startDelay, Constants.SECONDS_PER_SLOT * 1000, DateEvent.class},
                                  new Class[]{EventBus.class, Integer.class, Integer.class, Class.class});
          break;
      }
    } catch (IllegalArgumentException e) {
      System.out.println("Error when setting timer");
    }
    System.out.println("starting timer in validator client");
    this.timer.start();
  }
}
