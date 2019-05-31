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

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.StrictMath.toIntExact;
import static tech.pegasys.artemis.datastructures.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_beacon_proposer_index;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_crosslink_committees_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_epoch_start_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_previous_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.slot_to_epoch;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.PriorityBlockingQueue;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1;
import org.apache.tuweni.ssz.SSZ;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.CrosslinkCommittee;
import tech.pegasys.artemis.datastructures.util.AttestationUtil;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.services.ServiceConfig;
import tech.pegasys.artemis.statetransition.GenesisHeadStateEvent;
import tech.pegasys.artemis.statetransition.HeadStateEvent;
import tech.pegasys.artemis.statetransition.StateTransition;
import tech.pegasys.artemis.statetransition.StateTransitionException;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;

/** This class coordinates the activity between the validator clients and the the beacon chain */
public class ValidatorCoordinator {
  private static final ALogger LOG = new ALogger(ValidatorCoordinator.class.getName());
  private final EventBus eventBus;

  private static BeaconStateWithCache headState;
  private static Date genesisTime = null;
  private StateTransition stateTransition;
  private final Boolean printEnabled = false;
  private SECP256K1.SecretKey nodeIdentity;
  private int numValidators;
  private int numNodes;
  private BeaconBlock validatorBlock;
  private ArrayList<Deposit> newDeposits = new ArrayList<>();
  private final HashMap<BLSPublicKey, BLSKeyPair> validatorSet = new HashMap<>();
  static final Integer UNPROCESSED_BLOCKS_LENGTH = 100;
  private final PriorityBlockingQueue<Attestation> attestationsQueue =
      new PriorityBlockingQueue<>(
          UNPROCESSED_BLOCKS_LENGTH, Comparator.comparing(Attestation::getSlot));

  public ValidatorCoordinator(ServiceConfig config) {
    this.eventBus = config.getEventBus();
    this.eventBus.register(this);
    this.nodeIdentity =
        SECP256K1.SecretKey.fromBytes(Bytes32.fromHexString(config.getConfig().getIdentity()));
    this.numValidators = config.getConfig().getNumValidators();
    this.numNodes = config.getConfig().getNumNodes();

    initializeValidators();

    stateTransition = new StateTransition(printEnabled);
  }

  public static Date getGenesisTime() {
    return genesisTime;
  }

  public static Optional<CommitteeAssignmentTuple> get_committee_assignment(UnsignedLong epoch,
                                                                            int validator_index,
                                                                            boolean registry_change) {
    return get_committee_assignment(
            headState,
            slot_to_epoch(headState.getSlot()),
            validator_index,
            registry_change
    );

  }

  @Subscribe
  public void onNewSlot(Date date) {
    if (validatorBlock != null) {
      this.eventBus.post(validatorBlock);
      validatorBlock = null;
    }
  }

  @Subscribe
  public void onGenesisHeadStateEvent(GenesisHeadStateEvent genesisHeadStateEvent) {
    onNewHeadStateEvent(
        new HeadStateEvent(
            genesisHeadStateEvent.getHeadState(), genesisHeadStateEvent.getHeadBlock()));
    this.eventBus.post(true);
    genesisTime = new Date();
  }

  @Subscribe
  public void onNewHeadStateEvent(HeadStateEvent headStateEvent) {
    // Retrieve headState and headBlock from event
    headState = headStateEvent.getHeadState();
    BeaconBlock headBlock = headStateEvent.getHeadBlock();

    List<Attestation> attestations =
        AttestationUtil.createAttestations(headState, headBlock, validatorSet);

    for (Attestation attestation : attestations) {
      this.eventBus.post(attestation);
    }

    // Copy state so that state transition during block creation does not manipulate headState in
    // storage
    BeaconStateWithCache newHeadState = BeaconStateWithCache.deepCopy(headState);
    createBlockIfNecessary(newHeadState, headBlock);
  }

  @Subscribe
  public void onNewAttestation(Attestation attestation) {
    // Store attestations in a priority queue
    if (!attestationsQueue.contains(attestation)) {
      attestationsQueue.add(attestation);
    }
  }

  private void initializeValidators() {
    // Add all validators to validatorSet hashMap
    int nodeCounter = UInt256.fromBytes(nodeIdentity.bytes()).mod(numNodes).intValue();
    // LOG.log(Level.DEBUG, "nodeCounter: " + nodeCounter);
    // if (nodeCounter == 0) {

    int startIndex = nodeCounter * (numValidators / numNodes);
    int endIndex =
        startIndex
            + (numValidators / numNodes - 1)
            + toIntExact(Math.round((double) nodeCounter / Math.max(1, numNodes - 1)));
    endIndex = Math.min(endIndex, numValidators - 1);
    // int startIndex = 0;
    // int endIndex = numValidators-1;
    LOG.log(Level.DEBUG, "startIndex: " + startIndex + " endIndex: " + endIndex);
    for (int i = startIndex; i <= endIndex; i++) {
      BLSKeyPair keypair = BLSKeyPair.random(i);
      LOG.log(Level.DEBUG, "i = " + i + ": " + keypair.getPublicKey().toString());
      validatorSet.put(keypair.getPublicKey(), keypair);
    }
    // }
  }

  private void createBlockIfNecessary(BeaconStateWithCache headState, BeaconBlock headBlock) {
    // Calculate the block proposer index, and if we have the
    // block proposer in our set of validators, produce the block
    Integer proposerIndex;
    BLSPublicKey proposerPubkey;
    // Implements change from 6.1 for validator client, quoting from spec:
    // "To see if a validator is assigned to proposer during the slot,
    // the validator must run an empty slot transition from the previous
    // state to the current slot."
    // However, this is only required on epoch changes, because otherwise
    // validator registry doesn't change anyway.
    if (headState
        .getSlot()
        .plus(UnsignedLong.ONE)
        .mod(UnsignedLong.valueOf(SLOTS_PER_EPOCH))
        .equals(UnsignedLong.ZERO)) {
      BeaconStateWithCache newState = BeaconStateWithCache.deepCopy(headState);
      try {
        stateTransition.initiate(newState, null);
      } catch (StateTransitionException e) {
        LOG.log(Level.WARN, e.toString(), printEnabled);
      }
      proposerIndex = get_beacon_proposer_index(newState, newState.getSlot());
      proposerPubkey = newState.getValidator_registry().get(proposerIndex).getPubkey();
    } else {
      proposerIndex =
          get_beacon_proposer_index(
              headState, headState.getSlot().plus(UnsignedLong.ONE));
      proposerPubkey = headState.getValidator_registry().get(proposerIndex).getPubkey();
    }
    System.out.println("Proposer index in coordinator: " + proposerIndex);
    if (validatorSet.containsKey(proposerPubkey)) {
      Bytes32 blockRoot = headBlock.signed_root("signature");
      createNewBlock(headState, blockRoot, validatorSet.get(proposerPubkey));
    }
  }

  private void createNewBlock(
      BeaconStateWithCache headState, Bytes32 blockRoot, BLSKeyPair keypair) {
    try {
      List<Attestation> current_attestations;
      final Bytes32 MockStateRoot = Bytes32.ZERO;
      BeaconBlock block;
      if (headState
              .getSlot()
              .compareTo(
                  UnsignedLong.valueOf(
                      Constants.GENESIS_SLOT + Constants.MIN_ATTESTATION_INCLUSION_DELAY))
          >= 0) {
        UnsignedLong attestation_slot =
            headState
                .getSlot()
                .minus(UnsignedLong.valueOf(Constants.MIN_ATTESTATION_INCLUSION_DELAY));

        current_attestations =
            AttestationUtil.getAttestationsUntilSlot(attestationsQueue, attestation_slot);

        block =
            DataStructureUtil.newBeaconBlock(
                headState.getSlot().plus(UnsignedLong.ONE),
                blockRoot,
                MockStateRoot,
                newDeposits,
                current_attestations);
      } else {
        block =
            DataStructureUtil.newBeaconBlock(
                headState.getSlot().plus(UnsignedLong.ONE),
                blockRoot,
                MockStateRoot,
                newDeposits,
                new ArrayList<>());
      }

      BLSSignature epoch_signature = setEpochSignature(headState, keypair);
      block.getBody().setRandao_reveal(epoch_signature);
      stateTransition.initiate(headState, block);
      Bytes32 stateRoot = headState.hash_tree_root();
      block.setState_root(stateRoot);
      BLSSignature signed_proposal = signProposalData(headState, block, keypair);
      block.setSignature(signed_proposal);
      validatorBlock = block;

      LOG.log(Level.INFO, "ValidatorCoordinator - NEWLY PRODUCED BLOCK", printEnabled);
      LOG.log(Level.INFO, "ValidatorCoordinator - block.slot: " + block.getSlot(), printEnabled);
      LOG.log(
          Level.INFO,
          "ValidatorCoordinator - block.parent_root: " + block.getPrevious_block_root(),
          printEnabled);
      LOG.log(
          Level.INFO,
          "ValidatorCoordinator - block.state_root: " + block.getState_root(),
          printEnabled);

      LOG.log(Level.INFO, "End ValidatorCoordinator", printEnabled);
    } catch (StateTransitionException e) {
      LOG.log(Level.WARN, e.toString(), printEnabled);
    }
  }

  private BLSSignature setEpochSignature(BeaconState state, BLSKeyPair keypair) {
    UnsignedLong slot = state.getSlot().plus(UnsignedLong.ONE);
    UnsignedLong epoch = BeaconStateUtil.slot_to_epoch(slot);

    Bytes32 messageHash =
        HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(epoch.longValue()));
    UnsignedLong domain =
        BeaconStateUtil.get_domain(state.getFork(), epoch, Constants.DOMAIN_RANDAO);
    LOG.log(Level.INFO, "Sign Epoch", printEnabled);
    LOG.log(Level.INFO, "Proposer pubkey: " + keypair.getPublicKey(), printEnabled);
    LOG.log(Level.INFO, "state: " + state.hash_tree_root(), printEnabled);
    LOG.log(Level.INFO, "slot: " + slot, printEnabled);
    LOG.log(Level.INFO, "domain: " + domain, printEnabled);
    return BLSSignature.sign(keypair, messageHash, domain.longValue());
  }

  private BLSSignature signProposalData(BeaconState state, BeaconBlock block, BLSKeyPair keypair) {
    // Let proposal = Proposal(block.slot, BEACON_CHAIN_SHARD_NUMBER,
    //   signed_root(block, "signature"), block.signature).

    UnsignedLong domain =
        BeaconStateUtil.get_domain(
            state.getFork(),
            BeaconStateUtil.slot_to_epoch(UnsignedLong.valueOf(block.getSlot())),
            Constants.DOMAIN_BEACON_BLOCK);
    BLSSignature signature =
        BLSSignature.sign(keypair, block.signed_root("signature"), domain.longValue());
    LOG.log(Level.INFO, "Sign Proposal", printEnabled);
    LOG.log(Level.INFO, "Proposer pubkey: " + keypair.getPublicKey(), printEnabled);
    LOG.log(Level.INFO, "state: " + state.hash_tree_root(), printEnabled);
    LOG.log(Level.INFO, "block signature: " + signature.toString(), printEnabled);
    LOG.log(Level.INFO, "slot: " + state.getSlot().longValue(), printEnabled);
    LOG.log(Level.INFO, "domain: " + domain, printEnabled);
    return signature;
  }

  /**
   * Return the committee assignment in the ``epoch`` for ``validator_index`` and
   * ``registry_change``. ``assignment`` returned is a tuple of the following form: *
   * ``assignment[0]`` is the list of validators in the committee * ``assignment[1]`` is the shard
   * to which the committee is assigned * ``assignment[2]`` is the slot at which the committee is
   * assigned * ``assignment[3]`` is a bool signalling if the validator is expected to propose a
   * beacon block at the assigned slot.
   *
   * @param state the BeaconState.
   * @param epoch either on or between previous or current epoch.
   * @param validator_index the validator that is calling this function.
   * @param registry_change whether there has been a validator registry change.
   * @return Optional.of(CommitteeAssignmentTuple) or Optional.empty.
   */
  private static Optional<CommitteeAssignmentTuple> get_committee_assignment(
      BeaconState state, UnsignedLong epoch, int validator_index, boolean registry_change) {
    UnsignedLong previous_epoch = get_previous_epoch(state);
    UnsignedLong next_epoch = get_current_epoch(state).plus(UnsignedLong.ONE);
    checkArgument(previous_epoch.compareTo(epoch) <= 0);
    checkArgument(epoch.compareTo(next_epoch) <= 0);

    UnsignedLong epoch_start_slot = get_epoch_start_slot(epoch);

    for (UnsignedLong slot = epoch_start_slot;
        slot.compareTo(epoch_start_slot.plus(UnsignedLong.valueOf(SLOTS_PER_EPOCH))) <= 0;
        slot = slot.plus(UnsignedLong.ONE)) {

      ArrayList<CrosslinkCommittee> crosslink_committees =
          get_crosslink_committees_at_slot(state, slot, registry_change);

      ArrayList<CrosslinkCommittee> selected_committees = new ArrayList<>();
      for (CrosslinkCommittee committee : crosslink_committees) {
        if (committee.getCommittee().contains(validator_index)) {
          selected_committees.add(committee);
        }
      }

      if (selected_committees.size() > 0) {
        List<Integer> validators = selected_committees.get(0).getCommittee();
        UnsignedLong shard = selected_committees.get(0).getShard();
        boolean is_proposer =
            validator_index == get_beacon_proposer_index(state, slot, registry_change);

        return Optional.of(new CommitteeAssignmentTuple(validators, shard, slot, is_proposer));
      }
    }
    return Optional.empty();
  }
}
