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

package tech.pegasys.artemis.networking.eth2.gossip.topics;

import static java.lang.StrictMath.toIntExact;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZException;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.ssz.SSZTypes.Bytes4;
import tech.pegasys.artemis.storage.client.RecentChainData;
import tech.pegasys.artemis.networking.eth2.gossip.topics.validation.AttestationValidator;
import tech.pegasys.artemis.networking.eth2.gossip.topics.validation.ValidationResult;

public class AttestationTopicHandler extends Eth2TopicHandler<Attestation> {

  private final RecentChainData recentChainData;
  private final UnsignedLong subnetId;

  public AttestationTopicHandler(
      final EventBus eventBus,
      final RecentChainData recentChainData,
      final UnsignedLong subnetId,
      final Bytes4 forkDigest) {
    super(eventBus, forkDigest);
    this.recentChainData = recentChainData;
    this.subnetId = subnetId;
  }

  @Override
  public String getTopicName() {
    return "committee_index" + toIntExact(subnetId.longValue()) + "_beacon_attestation";
  }

  @Override
  protected Attestation deserialize(final Bytes bytes) throws SSZException {
    return SimpleOffsetSerializer.deserialize(bytes, Attestation.class);
  }

  @Override
  protected boolean validateData(final Attestation attestation) {
    final ValidationResult validationResult = attestationValidator.validate(attestation, subnetId);
    switch (validationResult) {
      case INVALID:
        return false;
      case SAVED_FOR_FUTURE:
        eventBus.post(createEvent(attestation));
        return false;
      case VALID:
        return true;
      default:
        throw new UnsupportedOperationException(
            "Unexpected attestation validation result: " + validationResult);
    }
  }
}
