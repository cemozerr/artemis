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

package tech.pegasys.artemis.validator.coordinator;

import com.google.common.primitives.UnsignedLong;
import java.util.List;

public class CommitteeAssignmentTuple {

  private List<Integer> validators;
  private UnsignedLong shard;
  private UnsignedLong slot;
  private boolean isProposer;

  CommitteeAssignmentTuple(
      List<Integer> validators, UnsignedLong shard, UnsignedLong slot, boolean isProposer) {
    this.validators = validators;
    this.shard = shard;
    this.slot = slot;
    this.isProposer = isProposer;
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public List<Integer> getValidators() {
    return validators;
  }

  public UnsignedLong getShard() {
    return shard;
  }

  public UnsignedLong getSlot() {
    return slot;
  }

  public boolean isProposer() {
    return isProposer;
  }
}
