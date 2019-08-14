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

package tech.pegasys.artemis.datastructures.state;

import static tech.pegasys.artemis.datastructures.Constants.ZERO_HASH;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.int_to_bytes;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.util.SSZTypes.SSZContainer;
import tech.pegasys.artemis.util.SSZTypes.SSZVector;
import tech.pegasys.artemis.util.reflectionInformation.ReflectionInformation;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class BeaconState implements SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 19;
  public static final ReflectionInformation reflectionInfo =
      new ReflectionInformation(BeaconState.class);

  // Versioning
  protected UnsignedLong genesis_time;
  protected UnsignedLong slot;
  protected Fork fork; // For versioning hard forks

  // History
  protected BeaconBlockHeader latest_block_header;
  protected SSZVector<Bytes32> block_roots; // Vector Bounded by SLOTS_PER_HISTORICAL_ROOT
  protected SSZVector<Bytes32> state_roots; // Vector Bounded by SLOTS_PER_HISTORICAL_ROOT
  protected List<Bytes32> historical_roots; // Bounded by HISTORICAL_ROOTS_LIMIT

  // Ethereum 1.0 chain data
  protected Eth1Data eth1_data;
  protected List<Eth1Data> eth1_data_votes; // List Bounded by SLOTS_PER_ETH1_VOTING_PERIOD
  protected UnsignedLong eth1_deposit_index;

  // Validator registry
  protected List<Validator> validators; // List Bounded by VALIDATOR_REGISTRY_LIMIT
  protected List<UnsignedLong> balances; // List Bounded by VALIDATOR_REGISTRY_LIMIT

  // Shuffling
  protected UnsignedLong start_shard;
  protected SSZVector<Bytes32> randao_mixes; // Vector Bounded by EPOCHS_PER_HISTORICAL_VECTOR
  protected SSZVector<Bytes32> active_index_roots; // Vector Bounded by EPOCHS_PER_HISTORICAL_VECTOR
  protected SSZVector<Bytes32>
      compact_committees_roots; // Vector Bounded by EPOCHS_PER_HISTORICAL_VECTOR

  // Slashings
  protected SSZVector<UnsignedLong> slashings; // Vector Bounded by EPOCHS_PER_SLASHINGS_VECTOR

  // Attestations
  protected List<PendingAttestation>
      previous_epoch_attestations; // List bounded by MAX_ATTESTATIONS * SLOTS_PER_EPOCH
  protected List<PendingAttestation>
      current_epoch_attestations; // List bounded by MAX_ATTESTATIONS * SLOTS_PER_EPOCH

  // Crosslinks
  protected SSZVector<Crosslink> previous_crosslinks; // Vector Bounded by SHARD_COUNT
  protected SSZVector<Crosslink> current_crosslinks; // Vector Bounded by SHARD_COUNT

  // Finality
  protected Bytes justification_bits; // Bitvector bounded by JUSTIFICATION_BITS_LENGTH
  protected Checkpoint previous_justified_checkpoint;
  protected Checkpoint current_justified_checkpoint;
  protected Checkpoint finalized_checkpoint;

  public BeaconState() {

    // Versioning
    this.genesis_time = UnsignedLong.ZERO;
    this.slot = UnsignedLong.valueOf(Constants.GENESIS_SLOT);
    this.fork =
        new Fork(
            int_to_bytes(0, 4), int_to_bytes(0, 4), UnsignedLong.valueOf(Constants.GENESIS_EPOCH));

    // History
    this.latest_block_header = new BeaconBlockHeader();
    this.block_roots = new SSZVector<>(Constants.SLOTS_PER_HISTORICAL_ROOT, Constants.ZERO_HASH);
    this.state_roots = new SSZVector<>(Constants.SLOTS_PER_HISTORICAL_ROOT, Constants.ZERO_HASH);
    this.historical_roots = new ArrayList<>();

    // Eth1
    // TODO gotta change this with genesis eth1DATA because deposit count is dependent on the
    // number of validators
    this.eth1_data = new Eth1Data(ZERO_HASH, UnsignedLong.ZERO, ZERO_HASH);
    this.eth1_data_votes = new ArrayList<>();
    this.eth1_deposit_index = UnsignedLong.ZERO;

    // Registry
    this.validators = new ArrayList<>();
    this.balances = new ArrayList<>();

    // Shuffling
    this.start_shard = UnsignedLong.ZERO;
    this.randao_mixes =
        new SSZVector<>(Constants.EPOCHS_PER_HISTORICAL_VECTOR, Constants.ZERO_HASH);
    this.active_index_roots =
        new SSZVector<>(Constants.EPOCHS_PER_HISTORICAL_VECTOR, Constants.ZERO_HASH);
    this.compact_committees_roots =
        new SSZVector<>(Constants.EPOCHS_PER_HISTORICAL_VECTOR, Constants.ZERO_HASH);

    // Slashings
    this.slashings = new SSZVector<>(Constants.EPOCHS_PER_SLASHINGS_VECTOR, UnsignedLong.ZERO);

    // Attestations
    this.previous_epoch_attestations = new ArrayList<>();
    this.current_epoch_attestations = new ArrayList<>();

    // Crosslinks
    this.previous_crosslinks = new SSZVector<>(Constants.SHARD_COUNT, new Crosslink());
    this.current_crosslinks = new SSZVector<>(Constants.SHARD_COUNT, new Crosslink());

    // Finality
    this.justification_bits = Bytes.wrap(new byte[1]); // TODO change to bitvector with 4 bits
    this.previous_justified_checkpoint = new Checkpoint();
    this.current_justified_checkpoint = new Checkpoint();
    this.finalized_checkpoint = new Checkpoint();
  }

  public BeaconState(
      // Versioning
      UnsignedLong genesis_time,
      UnsignedLong slot,
      Fork fork,

      // History
      BeaconBlockHeader latest_block_header,
      SSZVector<Bytes32> block_roots,
      SSZVector<Bytes32> state_roots,
      List<Bytes32> historical_roots,

      // Eth1
      Eth1Data eth1_data,
      List<Eth1Data> eth1_data_votes,
      UnsignedLong eth1_deposit_index,

      // Registry
      List<Validator> validators,
      List<UnsignedLong> balances,

      // Shuffling
      UnsignedLong start_shard,
      SSZVector<Bytes32> randao_mixes,
      SSZVector<Bytes32> active_index_roots,
      SSZVector<Bytes32> compact_committees_roots,

      // Slashings
      SSZVector<UnsignedLong> slashings,

      // Attestations
      List<PendingAttestation> previous_epoch_attestations,
      List<PendingAttestation> current_epoch_attestations,

      // Crosslinks
      SSZVector<Crosslink> previous_crosslinks,
      SSZVector<Crosslink> current_crosslinks,

      // Finality
      Bytes justification_bits,
      Checkpoint previous_justified_checkpoint,
      Checkpoint current_justified_checkpoint,
      Checkpoint finalized_checkpoint) {
    // Versioning
    this.genesis_time = genesis_time;
    this.slot = slot;
    this.fork = fork;

    // History
    this.latest_block_header = latest_block_header;
    this.block_roots = block_roots;
    this.state_roots = state_roots;
    this.historical_roots = historical_roots;

    // Eth1
    this.eth1_data = eth1_data;
    this.eth1_data_votes = eth1_data_votes;
    this.eth1_deposit_index = eth1_deposit_index;

    // Registry
    this.validators = validators;
    this.balances = balances;

    // Shuffling
    this.start_shard = start_shard;
    this.randao_mixes = randao_mixes;
    this.active_index_roots = active_index_roots;
    this.compact_committees_roots = compact_committees_roots;

    // Slashings
    this.slashings = slashings;

    // Attestations
    this.previous_epoch_attestations = previous_epoch_attestations;
    this.current_epoch_attestations = current_epoch_attestations;

    // Crosslinks
    this.previous_crosslinks = previous_crosslinks;
    this.current_crosslinks = current_crosslinks;

    // Finality
    this.justification_bits = justification_bits;
    this.previous_justified_checkpoint = previous_justified_checkpoint;
    this.current_justified_checkpoint = current_justified_checkpoint;
    this.finalized_checkpoint = finalized_checkpoint;
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT
        + fork.getSSZFieldCount()
        + latest_block_header.getSSZFieldCount()
        + eth1_data.getSSZFieldCount()
        + previous_justified_checkpoint.getSSZFieldCount()
        + current_justified_checkpoint.getSSZFieldCount()
        + finalized_checkpoint.getSSZFieldCount();
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    // TODO Implement this stub.
    return Collections.nCopies(getSSZFieldCount(), Bytes.EMPTY);
  }

  @Override
  public List<Bytes> get_variable_parts() {
    // TODO Implement this stub.
    return Collections.nCopies(getSSZFieldCount(), Bytes.EMPTY);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        // Versioning
        genesis_time,
        slot,
        fork,

        // History
        latest_block_header,
        block_roots,
        state_roots,
        historical_roots,

        // Eth1
        eth1_data,
        eth1_data_votes,
        eth1_deposit_index,

        // Registry
        validators,
        balances,

        // Shuffling
        start_shard,
        randao_mixes,
        active_index_roots,
        compact_committees_roots,

        // Slashings
        slashings,

        // Attestations
        previous_epoch_attestations,
        current_epoch_attestations,

        // Crosslinks
        current_crosslinks,
        previous_crosslinks,

        // Finality
        justification_bits,
        previous_justified_checkpoint,
        current_justified_checkpoint,
        finalized_checkpoint);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof BeaconState)) {
      return false;
    }

    BeaconState other = (BeaconState) obj;
    return Objects.equals(this.getGenesis_time(), other.getGenesis_time())
        && Objects.equals(slot, other.getSlot())
        && Objects.equals(this.getFork(), other.getFork())
        && Objects.equals(this.getLatest_block_header(), other.getLatest_block_header())
        && Objects.equals(this.getBlock_roots(), other.getBlock_roots())
        && Objects.equals(this.getState_roots(), other.getState_roots())
        && Objects.equals(this.getHistorical_roots(), other.getHistorical_roots())
        && Objects.equals(this.getEth1_data(), other.getEth1_data())
        && Objects.equals(this.getEth1_data_votes(), other.getEth1_data_votes())
        && Objects.equals(this.getEth1_deposit_index(), other.getEth1_deposit_index())
        && Objects.equals(this.getValidators(), other.getValidators())
        && Objects.equals(this.getBalances(), other.getBalances())
        && Objects.equals(this.getStart_shard(), other.getStart_shard())
        && Objects.equals(this.getRandao_mixes(), other.getRandao_mixes())
        && Objects.equals(this.getActive_index_roots(), other.getActive_index_roots())
        && Objects.equals(this.getCompact_committees_roots(), other.getCompact_committees_roots())
        && Objects.equals(this.getSlashings(), other.getSlashings())
        && Objects.equals(
            this.getPrevious_epoch_attestations(), other.getPrevious_epoch_attestations())
        && Objects.equals(
            this.getCurrent_epoch_attestations(), other.getCurrent_epoch_attestations())
        && Objects.equals(this.getPrevious_crosslinks(), other.getPrevious_crosslinks())
        && Objects.equals(this.getCurrent_crosslinks(), other.getCurrent_crosslinks())
        && Objects.equals(this.getJustification_bits(), other.getJustification_bits())
        && Objects.equals(
            this.getPrevious_justified_checkpoint(), other.getPrevious_justified_checkpoint())
        && Objects.equals(
            this.getCurrent_justified_checkpoint(), other.getCurrent_justified_checkpoint())
        && Objects.equals(this.getFinalized_checkpoint(), other.getFinalized_checkpoint());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */

  // Versioning
  public UnsignedLong getGenesis_time() {
    return genesis_time;
  }

  public void setGenesis_time(UnsignedLong genesis_time) {
    this.genesis_time = genesis_time;
  }

  public UnsignedLong getSlot() {
    return slot;
  }

  public void setSlot(UnsignedLong slot) {
    this.slot = slot;
  }

  public Fork getFork() {
    return fork;
  }

  public void setFork(Fork fork) {
    this.fork = fork;
  }

  // History
  public BeaconBlockHeader getLatest_block_header() {
    return latest_block_header;
  }

  public void setLatest_block_header(BeaconBlockHeader latest_block_header) {
    this.latest_block_header = latest_block_header;
  }

  public SSZVector<Bytes32> getBlock_roots() {
    return block_roots;
  }

  public void setBlock_roots(SSZVector<Bytes32> block_roots) {
    this.block_roots = block_roots;
  }

  public SSZVector<Bytes32> getState_roots() {
    return state_roots;
  }

  public void setState_roots(SSZVector<Bytes32> state_roots) {
    this.state_roots = state_roots;
  }

  public List<Bytes32> getHistorical_roots() {
    return historical_roots;
  }

  public void setHistorical_roots(List<Bytes32> historical_roots) {
    this.historical_roots = historical_roots;
  }

  // Eth1
  public Eth1Data getEth1_data() {
    return eth1_data;
  }

  public void setEth1_data(Eth1Data eth1_data) {
    this.eth1_data = eth1_data;
  }

  public List<Eth1Data> getEth1_data_votes() {
    return eth1_data_votes;
  }

  public void setEth1_data_votes(List<Eth1Data> eth1_data_votes) {
    this.eth1_data_votes = eth1_data_votes;
  }

  public UnsignedLong getEth1_deposit_index() {
    return eth1_deposit_index;
  }

  public void setEth1_deposit_index(UnsignedLong eth1_deposit_index) {
    this.eth1_deposit_index = eth1_deposit_index;
  }

  // Registry
  public List<Validator> getValidators() {
    return validators;
  }

  public void setValidators(List<Validator> validators) {
    this.validators = validators;
  }

  public List<UnsignedLong> getBalances() {
    return balances;
  }

  public void setBalances(List<UnsignedLong> balances) {
    this.balances = balances;
  }

  // Shuffling
  public UnsignedLong getStart_shard() {
    return start_shard;
  }

  public void setStart_shard(UnsignedLong start_shard) {
    this.start_shard = start_shard;
  }

  public SSZVector<Bytes32> getRandao_mixes() {
    return randao_mixes;
  }

  public void setRandao_mixes(SSZVector<Bytes32> randao_mixes) {
    this.randao_mixes = randao_mixes;
  }

  public SSZVector<Bytes32> getActive_index_roots() {
    return active_index_roots;
  }

  public void setActive_index_roots(SSZVector<Bytes32> active_index_roots) {
    this.active_index_roots = active_index_roots;
  }

  public SSZVector<Bytes32> getCompact_committees_roots() {
    return compact_committees_roots;
  }

  public void setCompact_committees_roots(SSZVector<Bytes32> compact_committees_roots) {
    this.compact_committees_roots = compact_committees_roots;
  }

  // Slashings
  public SSZVector<UnsignedLong> getSlashings() {
    return slashings;
  }

  public void setSlashings(SSZVector<UnsignedLong> slashings) {
    this.slashings = slashings;
  }

  // Attestations
  public List<PendingAttestation> getPrevious_epoch_attestations() {
    return previous_epoch_attestations;
  }

  public void setPrevious_epoch_attestations(List<PendingAttestation> previous_epoch_attestations) {
    this.previous_epoch_attestations = previous_epoch_attestations;
  }

  public List<PendingAttestation> getCurrent_epoch_attestations() {
    return current_epoch_attestations;
  }

  public void setCurrent_epoch_attestations(List<PendingAttestation> current_epoch_attestations) {
    this.current_epoch_attestations = current_epoch_attestations;
  }

  // Crosslinks
  public SSZVector<Crosslink> getPrevious_crosslinks() {
    return previous_crosslinks;
  }

  public void setPrevious_crosslinks(SSZVector<Crosslink> previous_crosslinks) {
    this.previous_crosslinks = previous_crosslinks;
  }

  public SSZVector<Crosslink> getCurrent_crosslinks() {
    return current_crosslinks;
  }

  public void setCurrent_crosslinks(SSZVector<Crosslink> current_crosslinks) {
    this.current_crosslinks = current_crosslinks;
  }

  // Finality
  public Bytes getJustification_bits() {
    return justification_bits;
  }

  public void setJustification_bits(Bytes justification_bits) {
    this.justification_bits = justification_bits;
  }

  public Checkpoint getPrevious_justified_checkpoint() {
    return previous_justified_checkpoint;
  }

  public void setPrevious_justified_checkpoint(Checkpoint previous_justified_checkpoint) {
    this.previous_justified_checkpoint = previous_justified_checkpoint;
  }

  public Checkpoint getCurrent_justified_checkpoint() {
    return current_justified_checkpoint;
  }

  public void setCurrent_justified_checkpoint(Checkpoint current_justified_checkpoint) {
    this.current_justified_checkpoint = current_justified_checkpoint;
  }

  public Checkpoint getFinalized_checkpoint() {
    return finalized_checkpoint;
  }

  public void setFinalized_checkpoint(Checkpoint finalized_checkpoint) {
    this.finalized_checkpoint = finalized_checkpoint;
  }

  public void incrementSlot() {
    this.slot = slot.plus(UnsignedLong.ONE);
  }

  public Bytes32 hash_tree_root() {
    return Bytes32.ZERO;
  }
}
