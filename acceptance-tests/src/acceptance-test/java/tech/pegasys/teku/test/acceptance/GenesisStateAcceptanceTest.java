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

package tech.pegasys.teku.test.acceptance;

import static tech.pegasys.teku.util.config.Constants.MAX_EFFECTIVE_BALANCE;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.BesuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;

public class GenesisStateAcceptanceTest extends AcceptanceTestBase {

  @Test
  public void shouldCreateTheSameGenesisState() throws Exception {
    final BesuNode eth1Node = createBesuNode();
    eth1Node.start();

    createTekuDepositSender().sendValidatorDeposits(eth1Node, 4);

    final TekuNode firstTeku = createTekuNode(config -> config.withDepositsFrom(eth1Node));
    firstTeku.start();
    firstTeku.waitForGenesis();

    final TekuNode lateJoinTeku = createTekuNode(config -> config.withDepositsFrom(eth1Node));
    lateJoinTeku.start();
    lateJoinTeku.waitForGenesis();

    // Even though the nodes aren't connected to each other they should generate the same genesis
    // state because they processed the same deposits from the same ETH1 chain.
    lateJoinTeku.waitUntilInSyncWith(firstTeku);
  }

  @Test
  public void shouldCreateGenesisFromPartialDeposits() throws Exception {
    final BesuNode eth1Node = createBesuNode();
    eth1Node.start();

    createTekuDepositSender()
        .partiallySendValidatorDeposits(eth1Node, 4, MAX_EFFECTIVE_BALANCE / 2);

    final TekuNode firstTeku = createTekuNode(config -> config.withDepositsFrom(eth1Node));
    firstTeku.start();
    firstTeku.waitForGenesis();

    firstTeku.waitForValidators(4);
  }
}
