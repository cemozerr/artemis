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

package tech.pegasys.teku.test.acceptance;

import static tech.pegasys.teku.util.config.Constants.MAX_EFFECTIVE_BALANCE;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.BesuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuDepositSender;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuVoluntaryExit;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.KeystoreGenerator;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeyGenerator;

public class ValidatorExitAcceptanceTest extends AcceptanceTestBase {

  @Test
  void blabla() throws Exception {
    final BesuNode eth1Node = createBesuNode();
    eth1Node.start();

    int numberOfValidators = 4;
    final TekuDepositSender depositSender = createTekuDepositSender();
    final List<ValidatorKeyGenerator.ValidatorKeys> validatorKeys =
        depositSender.generateValidatorKeys(numberOfValidators);
    depositSender.sendValidatorDeposits(eth1Node, validatorKeys, MAX_EFFECTIVE_BALANCE);

    final String validatorKeysPassword = "validatorsKeysPassword";
    final Path validatorInfoDirectoryPath = Path.of("./validatorInfo");
    final String keysDirectory = "keys";
    final String passwordsDirectory = "passwords";
    final Path keysOutputPath = validatorInfoDirectoryPath.resolve(keysDirectory);
    final Path passwordsOutputPath = validatorInfoDirectoryPath.resolve(passwordsDirectory);
    final KeystoreGenerator keystoreGenerator =
        new KeystoreGenerator(
            validatorKeysPassword, keysOutputPath, passwordsOutputPath, (__) -> {});

    // create temporary tar file which will be copied to docker containers
    File validatorInfoTar = File.createTempFile("validatorInfo", ".tar");
    validatorInfoTar.deleteOnExit();

    // create keystores using the validator keys generated by deposit sender
    keystoreGenerator.generateKeystoreAndPasswordFiles(
        validatorKeys.stream()
            .map(ValidatorKeyGenerator.ValidatorKeys::getValidatorKey)
            .collect(Collectors.toList()));

    // copy keystores directory to tar file and delete the now redundant directory
    copyDirectoryToTarFile(validatorInfoDirectoryPath, validatorInfoTar.toPath());
    FileUtils.deleteDirectory(validatorInfoDirectoryPath.toFile());

    final TekuNode beaconNode =
        createTekuNode(config -> config.withNetwork("less-swift").withDepositsFrom(eth1Node));

    final TekuValidatorNode validatorClient =
        createValidatorNode(
            config ->
                config
                    .withNetwork("less-swift")
                    .withInteropModeDisabled()
                    .withValidatorKeys("/opt/teku/keys:/opt/teku/passwords")
                    .withBeaconNodeEndpoint(beaconNode.getBeaconRestApiUrl()));
    validatorClient.copyContentsToWorkingDirectory(validatorInfoTar);

    final TekuVoluntaryExit voluntaryExitProcess =
        createVoluntaryExit(
            config ->
                config
                    .withValidatorKeys("/opt/teku/keys:/opt/teku/passwords")
                    .withBeaconNodeEndpoint(beaconNode.getBeaconRestApiUrl())
                    .withExitAtEpoch(0));
    voluntaryExitProcess.copyContentsToWorkingDirectory(validatorInfoTar);

    beaconNode.start();
    validatorClient.start();

    validatorClient.waitForLogMessageContaining("Published block");
    validatorClient.waitForLogMessageContaining("Published attestation");
    validatorClient.waitForLogMessageContaining("Published aggregate");

    voluntaryExitProcess.start();

    validatorClient.waitForLogMessageContaining("Published aggregate");
  }

  public Path copyDirectoryToTarFile(Path inputDirectoryPath, Path outputPath) throws IOException {
    File outputFile = outputPath.toFile();

    try (FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
        TarArchiveOutputStream tarArchiveOutputStream =
            new TarArchiveOutputStream(bufferedOutputStream)) {

      tarArchiveOutputStream.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
      tarArchiveOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

      List<File> files =
          new ArrayList<>(
              FileUtils.listFiles(inputDirectoryPath.toFile(), new String[] {"json", "txt"}, true));

      for (File currentFile : files) {
        String relativeFilePath =
            new File(inputDirectoryPath.toUri())
                .toURI()
                .relativize(new File(currentFile.getAbsolutePath()).toURI())
                .getPath();

        TarArchiveEntry tarEntry = new TarArchiveEntry(currentFile, relativeFilePath);
        tarEntry.setSize(currentFile.length());

        tarArchiveOutputStream.putArchiveEntry(tarEntry);
        tarArchiveOutputStream.write(IOUtils.toByteArray(new FileInputStream(currentFile)));
        tarArchiveOutputStream.closeArchiveEntry();
      }
      tarArchiveOutputStream.close();
      return outputFile.toPath();
    }
  }
}
