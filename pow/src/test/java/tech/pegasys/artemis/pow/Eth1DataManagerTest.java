/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.artemis.pow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.util.config.Constants.ETH1_FOLLOW_DISTANCE;
import static tech.pegasys.artemis.util.config.Constants.ETH1_REQUEST_BUFFER;
import static tech.pegasys.artemis.util.config.Constants.SECONDS_PER_ETH1_BLOCK;
import static tech.pegasys.artemis.util.config.Constants.SECONDS_PER_SLOT;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_ETH1_VOTING_PERIOD;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthBlock;
import tech.pegasys.artemis.pow.event.CacheEth1BlockEvent;
import tech.pegasys.artemis.util.async.AsyncRunner;
import tech.pegasys.artemis.util.async.AsyncRunnerTest;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.util.time.TimeProvider;

@SuppressWarnings({"rawtypes", "unchecked"})
public class Eth1DataManagerTest {

  private final TimeProvider timeProvider = mock(TimeProvider.class);
  private final Web3j web3j = mock(Web3j.class);
  private final AsyncRunner asyncRunner = new AsyncRunnerTest();
  private final DepositContractListener depositContractListener =
      mock(DepositContractListener.class);

  private EventBus eventBus;
  private Eth1DataManager eth1DataManager;
  private EventCapture eventCapture;

  private static final Bytes32 HEX_STRING = Bytes32.fromHexString("0xdeadbeef");

  static {
    ETH1_FOLLOW_DISTANCE = UnsignedLong.valueOf(4);
    SECONDS_PER_ETH1_BLOCK = UnsignedLong.valueOf(6);
    ETH1_REQUEST_BUFFER = UnsignedLong.valueOf(1);
    SLOTS_PER_ETH1_VOTING_PERIOD = 5;
    SECONDS_PER_SLOT = 2;
  }

  private final UnsignedLong testStartTime = UnsignedLong.valueOf(200);

  // Cache Range:
  //    Lower Bound = 200 - (5 * 2) - (4 * 6 * 2) = 142
  //    Upper Bound = 200 - (4 * 6) + 1 = 177
  //    Mid-Range =  (142 + 177) / 2 = 160 (given that we use Half Up rounding (i.e. normal math
  // rounding))
  //
  // Number of blocks to middle
  //    = (current-time - mid-range) / (seconds_per_eth1_block)
  //    = (200 - 160) / (6)
  //    = 7 (here we use
  //    i.e. Teku will assume the middle-range block has block number current head - 7

  @BeforeEach
  void setUp() {
    eventBus = new EventBus();
    eventCapture = new EventCapture(eventBus);

    when(timeProvider.getTimeInSeconds()).thenReturn(testStartTime);
    when(depositContractListener.getDepositCount(any()))
        .thenReturn(SafeFuture.completedFuture(UnsignedLong.valueOf(1234)));
    when(depositContractListener.getDepositRoot(any()))
        .thenReturn(SafeFuture.completedFuture(HEX_STRING));

    eth1DataManager =
        new Eth1DataManager(web3j, eventBus, depositContractListener, asyncRunner, timeProvider);
  }

  @Test
  void checkTimeValues() {
    assertThat(eth1DataManager.getCacheRangeLowerBound())
        .isEqualByComparingTo(UnsignedLong.valueOf(142));
    assertThat(eth1DataManager.getCacheRangeUpperBound())
        .isEqualByComparingTo(UnsignedLong.valueOf(177));
    assertThat(Eth1DataManager.getCacheMidRangeTimestamp(timeProvider.getTimeInSeconds()))
        .isEqualByComparingTo(UnsignedLong.valueOf(160));
  }

  @Test
  void cacheStartup_blockActuallyInMidRange() {
    List<MockBlock> eth1Blocks =
        Arrays.asList(
            new MockBlock(10, 132),
            new MockBlock(11, 138),
            // Cache Range Lower Bound: 142
            new MockBlock(12, 144),
            new MockBlock(13, 150),
            new MockBlock(14, 156),
            new MockBlock(15, 162),
            new MockBlock(16, 168),
            new MockBlock(17, 174),
            // Cache Range Upper Bound: 177
            new MockBlock(18, 180),
            new MockBlock(19, 186));

    MockBlock latestBlockRequest = new MockBlock(21, 198);

    setupWeb3jMockedBlockResponses(eth1Blocks, latestBlockRequest);

    eth1DataManager.start();

    assertThat(eventCapture.getEth1BlockEvents().size()).isEqualTo(8);

    List<Integer> eth1BlockTimestamps =
        eventCapture.getEth1BlockEvents().stream()
            .filter(event -> event.getClass().equals(CacheEth1BlockEvent.class))
            .map(CacheEth1BlockEvent::getBlockTimestamp)
            .map(UnsignedLong::intValue)
            .collect(Collectors.toList());

    assertThat(eth1BlockTimestamps)
        .containsExactlyInAnyOrder(138, 144, 150, 156, 162, 168, 174, 180);
  }

  @Test
  void cacheStartup_recalculateSecondsToFindMidRangeBlock() {
    // There is ice-age! Average block times are at about 20 seconds! What does Teku do? FUNCTION.
    List<MockBlock> eth1Blocks =
        Arrays.asList(
            new MockBlock(15, 78),
            new MockBlock(16, 98),
            new MockBlock(17, 118),
            new MockBlock(18, 138),
            // Cache Range Lower Bound: 142
            new MockBlock(19, 158),
            // Cache Range Upper Bound: 177
            new MockBlock(20, 178));

    MockBlock latestBlockRequest = new MockBlock(21, 198);

    setupWeb3jMockedBlockResponses(eth1Blocks, latestBlockRequest);

    eth1DataManager.start();

    assertThat(eventCapture.getEth1BlockEvents().size()).isEqualTo(3);

    List<Integer> eth1BlockTimestamps =
        eventCapture.getEth1BlockEvents().stream()
            .filter(event -> event.getClass().equals(CacheEth1BlockEvent.class))
            .map(CacheEth1BlockEvent::getBlockTimestamp)
            .map(UnsignedLong::intValue)
            .collect(Collectors.toList());

    assertThat(eth1BlockTimestamps).containsExactlyInAnyOrder(158, 138, 178);
  }

  @Test
  void cacheStartup_retryStartup() {
    Request mockRequest = mockFailedRequest();
    when(web3j.ethGetBlockByNumber(any(), eq(false))).thenReturn(mockRequest);

    eth1DataManager.start();

    verify(web3j, times(Math.toIntExact(Constants.ETH1_CACHE_STARTUP_RETRY_GIVEUP)))
        .ethGetBlockByNumber(any(), eq(false));
  }

  @Test
  void onTick_startupNotDone() {
    eventBus = spy(new EventBus());
    eth1DataManager =
        spy(
            new Eth1DataManager(
                web3j, eventBus, depositContractListener, asyncRunner, timeProvider));
    eventBus.post(new Date());
    verifyNoInteractions(eth1DataManager);
  }

  @Test
  void onTick_startupDoneGetNewBlocks() {
    eth1DataManager =
        new Eth1DataManager(web3j, eventBus, depositContractListener, asyncRunner, timeProvider);

    List<MockBlock> eth1Blocks =
        Arrays.asList(
            new MockBlock(10, 132),
            new MockBlock(11, 138),
            // Cache Range Lower Bound: 142
            new MockBlock(12, 144),
            new MockBlock(13, 150),
            new MockBlock(14, 156),
            new MockBlock(15, 162),
            new MockBlock(16, 168),
            new MockBlock(17, 174),
            // Cache Range Upper Bound: 177
            new MockBlock(18, 180),
            // Cache Range Updated Upper Bound: 181
            new MockBlock(19, 186),
            new MockBlock(20, 192));

    MockBlock latestBlockRequest = new MockBlock(21, 198);

    setupWeb3jMockedBlockResponses(eth1Blocks, latestBlockRequest);

    eth1DataManager.start();

    when(timeProvider.getTimeInSeconds()).thenReturn(testStartTime.plus(UnsignedLong.valueOf(4)));

    eth1DataManager.onTick(new Date());

    assertThat(eventCapture.getEth1BlockEvents().size()).isEqualTo(9);

    List<Integer> eth1BlockTimestamps =
        eventCapture.getEth1BlockEvents().stream()
            .filter(event -> event.getClass().equals(CacheEth1BlockEvent.class))
            .map(CacheEth1BlockEvent::getBlockTimestamp)
            .map(UnsignedLong::intValue)
            .collect(Collectors.toList());

    assertThat(eth1BlockTimestamps)
        .containsExactlyInAnyOrder(138, 144, 150, 156, 162, 168, 174, 180, 186);
  }

  @Test
  void onTick_startupDone_LatestTimestampStillHigherThanUpperBound() {
    eth1DataManager =
        new Eth1DataManager(web3j, eventBus, depositContractListener, asyncRunner, timeProvider);

    List<MockBlock> eth1Blocks =
        Arrays.asList(
            new MockBlock(10, 132),
            new MockBlock(11, 138),
            // Cache Range Lower Bound: 142
            new MockBlock(12, 144),
            new MockBlock(13, 150),
            new MockBlock(14, 156),
            new MockBlock(15, 162),
            new MockBlock(16, 168),
            new MockBlock(17, 174),
            // Cache Range Upper Bound: 177
            // Cache Range Updated Upper Bound: 181
            new MockBlock(18, 182));

    MockBlock latestBlockRequest = new MockBlock(21, 198);

    setupWeb3jMockedBlockResponses(eth1Blocks, latestBlockRequest);

    eth1DataManager.start();

    when(timeProvider.getTimeInSeconds()).thenReturn(testStartTime.plus(UnsignedLong.valueOf(4)));

    eth1DataManager.onTick(new Date());

    assertThat(eventCapture.getEth1BlockEvents().size()).isEqualTo(8);

    List<Integer> eth1BlockTimestamps =
        eventCapture.getEth1BlockEvents().stream()
            .filter(event -> event.getClass().equals(CacheEth1BlockEvent.class))
            .map(CacheEth1BlockEvent::getBlockTimestamp)
            .map(UnsignedLong::intValue)
            .collect(Collectors.toList());

    assertThat(eth1BlockTimestamps)
        .containsExactlyInAnyOrder(138, 144, 150, 156, 162, 168, 174, 182);
  }

  private Request mockFailedRequest() {
    Request mockRequest = mock(Request.class);
    when(mockRequest.sendAsync())
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Nope")));
    return mockRequest;
  }

  private void setupWeb3jMockedBlockResponses(
      List<MockBlock> cacheRangeBlocks, MockBlock latestBlock) {
    // Setup latest block
    when(web3j.ethGetBlockByNumber(eq(DefaultBlockParameterName.LATEST), eq(false)))
        .thenReturn(latestBlock.getRequest());

    // Setup blocks around cache range
    for (int i = 0; i < cacheRangeBlocks.size(); i++) {
      MockBlock currentBlock = cacheRangeBlocks.get(i);
      Request mockedBlockRequest = currentBlock.getRequest();
      DefaultBlockParameter blockParam =
          DefaultBlockParameter.valueOf(BigInteger.valueOf(currentBlock.getBlockNumber()));
      when(web3j.ethGetBlockByNumber(blockParameterEq(blockParam), eq(false)))
          .thenReturn(mockedBlockRequest);
    }
  }

  private DefaultBlockParameter blockParameterEq(final DefaultBlockParameter expected) {
    return argThat(arg -> arg != null && arg.getValue().equals(expected.getValue()));
  }

  private static class MockBlock {

    private final long blockNumber;
    private final Request request;

    MockBlock(long blockNumber, long blockTimestamp) {
      this.blockNumber = blockNumber;
      this.request = mockBlockRequest(blockNumber, blockTimestamp);
    }

    public long getBlockNumber() {
      return blockNumber;
    }

    public Request getRequest() {
      return request;
    }

    private Request mockBlockRequest(long number, long timestamp) {
      return mockRequest(mockBlock(number, timestamp));
    }

    private EthBlock mockBlock(long number, long timestamp) {
      EthBlock mockBlock = mock(EthBlock.class);
      EthBlock.Block mockBlockBlock = mock(EthBlock.Block.class);
      when(mockBlock.getBlock()).thenReturn(mockBlockBlock);
      when(mockBlockBlock.getNumber()).thenReturn(BigInteger.valueOf(number));
      when(mockBlockBlock.getTimestamp()).thenReturn(BigInteger.valueOf(timestamp));
      when(mockBlockBlock.getHash()).thenReturn(HEX_STRING.toHexString());
      return mockBlock;
    }

    private Request mockRequest(EthBlock block) {
      Request mockRequest = mock(Request.class);
      when(mockRequest.sendAsync()).thenReturn(CompletableFuture.completedFuture(block));
      return mockRequest;
    }
  }

  private static class EventCapture {

    private final List<CacheEth1BlockEvent> eth1BlockEvents = new ArrayList<>();

    public EventCapture(EventBus eventBus) {
      eventBus.register(this);
    }

    @Subscribe
    public void onEth1BlockEvent(final CacheEth1BlockEvent event) {
      eth1BlockEvents.add(event);
    }

    public List<CacheEth1BlockEvent> getEth1BlockEvents() {
      return eth1BlockEvents;
    }
  }
}
