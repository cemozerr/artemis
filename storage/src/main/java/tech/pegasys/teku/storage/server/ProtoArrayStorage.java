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

package tech.pegasys.teku.storage.server;

import java.util.Optional;
import tech.pegasys.teku.protoarray.ProtoArray;
import tech.pegasys.teku.protoarray.ProtoArrayStorageChannel;
import tech.pegasys.teku.util.async.SafeFuture;

public class ProtoArrayStorage implements ProtoArrayStorageChannel {
  private final Database database;

  public ProtoArrayStorage(Database database) {
    this.database = database;
  }

  @Override
  public void onProtoArrayUpdate(ProtoArray protoArray) {
    database.updateProtoArrayOnDisk(protoArray);
  }

  @Override
  public SafeFuture<Optional<ProtoArray>> getProtoArrayFromDisk() {
    return SafeFuture.completedFuture(database.getProtoArrayFromDisk());
  }
}
