/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netease.arctic.scan;

import com.netease.arctic.data.ChangeAction;
import com.netease.arctic.data.IcebergContentFile;
import com.netease.arctic.io.TableDataTestBase;
import com.netease.arctic.utils.ArcticDataFiles;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.util.StructLikeMap;
import org.junit.Assert;
import org.junit.Test;

public class TestChangeTableBasicIncrementalScan extends TableDataTestBase {

  @Test
  public void testIncrementalScan() {
    ChangeTableIncrementalScan changeTableIncrementalScan =
        getArcticTable().asKeyedTable().changeTable().newScan();
    CloseableIterable<IcebergContentFile<?>> files = changeTableIncrementalScan.planFilesWithSequence();

    assertFiles(files, 3, 1, 2);
  }

  @Test
  public void testIncrementalScanUseSnapshot() {
    Snapshot snapshot = getArcticTable().asKeyedTable().changeTable().currentSnapshot();
    writeChangeStore(4L, ChangeAction.INSERT, changeInsertRecords(allRecords));
    Assert.assertNotEquals(snapshot.snapshotId(),
        getArcticTable().asKeyedTable().changeTable().currentSnapshot().snapshotId());

    ChangeTableIncrementalScan changeTableIncrementalScan =
        getArcticTable().asKeyedTable().changeTable().newScan();
    changeTableIncrementalScan = changeTableIncrementalScan.useSnapshot(snapshot.snapshotId());
    CloseableIterable<IcebergContentFile<?>> files = changeTableIncrementalScan.planFilesWithSequence();

    assertFiles(files, 3, 1, 2);
  }

  @Test
  public void testIncrementalScanUseRef() {
    Snapshot snapshot = getArcticTable().asKeyedTable().changeTable().currentSnapshot();
    String branchName = "test_branch";
    getArcticTable().asKeyedTable().changeTable()
        .manageSnapshots()
        .createBranch(branchName, snapshot.snapshotId())
        .commit();
    writeChangeStore(4L, ChangeAction.INSERT, changeInsertRecords(allRecords));
    Assert.assertNotEquals(snapshot.snapshotId(),
        getArcticTable().asKeyedTable().changeTable().currentSnapshot().snapshotId());

    ChangeTableIncrementalScan changeTableIncrementalScan =
        getArcticTable().asKeyedTable().changeTable().newScan();
    changeTableIncrementalScan = changeTableIncrementalScan.useRef(branchName);
    CloseableIterable<IcebergContentFile<?>> files = changeTableIncrementalScan.planFilesWithSequence();

    assertFiles(files, 3, 1, 2);
  }

  @Test
  public void testIncrementalScanFrom() {
    StructLikeMap<Long> fromSequence = StructLikeMap.create(getArcticTable().spec().partitionType());
    StructLike partitionData = ArcticDataFiles.data(getArcticTable().spec(), "op_time_day=2022-01-01");
    fromSequence.put(partitionData, 1L);
    ChangeTableIncrementalScan changeTableIncrementalScan =
        getArcticTable().asKeyedTable().changeTable().newScan().fromSequence(fromSequence);
    CloseableIterable<IcebergContentFile<?>> files = changeTableIncrementalScan.planFilesWithSequence();

    assertFiles(files, 1, 2, 2);
  }

  @Test
  public void testIncrementalScanTo() {
    ChangeTableIncrementalScan changeTableIncrementalScan =
        getArcticTable().asKeyedTable().changeTable().newScan().toSequence(1);
    CloseableIterable<IcebergContentFile<?>> files = changeTableIncrementalScan.planFilesWithSequence();

    assertFiles(files, 2, 1, 1);
  }

  @Test
  public void testIncrementalScanFromTo() {
    StructLikeMap<Long> fromSequence = StructLikeMap.create(getArcticTable().spec().partitionType());
    StructLike partitionData = ArcticDataFiles.data(getArcticTable().spec(), "op_time_day=2022-01-01");
    fromSequence.put(partitionData, 1L);
    ChangeTableIncrementalScan changeTableIncrementalScan =
        getArcticTable().asKeyedTable().changeTable().newScan().fromSequence(fromSequence).toSequence(1);
    CloseableIterable<IcebergContentFile<?>> files = changeTableIncrementalScan.planFilesWithSequence();

    assertFiles(files, 0, 0, 0);
  }

  private void assertFiles(CloseableIterable<IcebergContentFile<?>> files, int fileCnt,
                           long minSequence, long maxSequence) {
    int cnt = 0;
    for (IcebergContentFile<?> file : files) {
      cnt++;
      Assert.assertTrue(file.getSequenceNumber() >= minSequence);
      Assert.assertTrue(file.getSequenceNumber() <= maxSequence);
    }
    Assert.assertEquals(fileCnt, cnt);
  }
}
