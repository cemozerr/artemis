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

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.artemis.datastructures.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_beacon_proposer_index;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_crosslink_committees_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_epoch_start_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_previous_epoch;

import com.google.common.primitives.UnsignedLong;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.tx.gas.DefaultGasProvider;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.CrosslinkCommittee;
import tech.pegasys.artemis.pow.contract.DepositContract;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.mikuli.BLS12381;
import tech.pegasys.artemis.util.mikuli.KeyPair;
import tech.pegasys.artemis.util.mikuli.PublicKey;

public class ValidatorClientUtil {

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
  public static Optional<CommitteeAssignmentTuple> get_committee_assignment(
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

  /*
  // Imported from ValidatorClient (was not being used)
  public static void registerValidatorEth1(
      Validator validator, long amount, String address, Web3j web3j, DefaultGasProvider gasProvider)
      throws Exception {
    Credentials credentials =
        Credentials.create(validator.getSecpKeys().secretKey().bytes().toHexString());
    DepositContract contract = DepositContract.load(address, web3j, credentials, gasProvider);
    Bytes deposit_data =
        Bytes.wrap(
            validator.getPubkey().getPublicKey().toBytesCompressed(),
            validator.getWithdrawal_credentials(),
            Bytes.ofUnsignedLong(amount));
    deposit_data =
        Bytes.wrap(
            deposit_data,
            BLS12381
                .sign(validator.getBlsKeys(), deposit_data, Constants.DOMAIN_DEPOSIT)
                .signature()
                .toBytesCompressed());
    contract.deposit(deposit_data.toArray(), new BigInteger(amount + "000000000")).send();
  }
  */

  public static Bytes generateDepositData(
      KeyPair blsKeys, Bytes32 withdrawal_credentials, long amount) {
    Bytes deposit_data =
        Bytes.wrap(
            Bytes.ofUnsignedLong(amount),
            withdrawal_credentials,
            getPublicKeyFromKeyPair(blsKeys).toBytesCompressed());
    return Bytes.wrap(generateProofOfPossession(blsKeys, deposit_data), deposit_data).reverse();
  }

  public static Bytes generateProofOfPossession(KeyPair blsKeys, Bytes deposit_data) {
    return BLS12381
        .sign(blsKeys, deposit_data, Constants.DOMAIN_DEPOSIT)
        .signature()
        .toBytesCompressed();
  }

  public static PublicKey getPublicKeyFromKeyPair(KeyPair blsKeys) {
    return BLSPublicKey.fromBytesCompressed(blsKeys.publicKey().toBytesCompressed()).getPublicKey();
  }

  public static void registerValidatorEth1(
      Validator validator, long amount, String address, Web3j web3j, DefaultGasProvider gasProvider)
      throws Exception {
    Credentials credentials =
        Credentials.create(validator.getSecpKeys().secretKey().bytes().toHexString());
    DepositContract contract = null;
    Bytes deposit_data =
        generateDepositData(validator.getBlsKeys(), validator.getWithdrawal_credentials(), amount);
    contract = DepositContract.load(address, web3j, credentials, gasProvider);
    contract.deposit(deposit_data.toArray(), new BigInteger(amount + "000000000")).send();
  }
}
