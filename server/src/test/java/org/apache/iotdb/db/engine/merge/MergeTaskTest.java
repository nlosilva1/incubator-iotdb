/**
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
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.db.engine.merge;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import org.apache.commons.io.FileUtils;
import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.engine.merge.manage.MergeResource;
import org.apache.iotdb.db.engine.merge.task.MergeTask;
import org.apache.iotdb.db.engine.modification.Deletion;
import org.apache.iotdb.db.exception.MetadataErrorException;
import org.apache.iotdb.db.exception.StorageEngineException;
import org.apache.iotdb.db.query.context.QueryContext;
import org.apache.iotdb.db.query.reader.resourceRelated.SeqResourceIterateReader;
import org.apache.iotdb.tsfile.exception.write.WriteProcessException;
import org.apache.iotdb.tsfile.read.common.BatchData;
import org.apache.iotdb.tsfile.read.common.Path;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MergeTaskTest extends MergeTest {

  private File tempSGDir;

  @Before
  public void setUp() throws IOException, WriteProcessException, MetadataErrorException {
    super.setUp();
    tempSGDir = new File("tempSG");
    tempSGDir.mkdirs();
  }

  @After
  public void tearDown() throws IOException, StorageEngineException {
    super.tearDown();
    FileUtils.deleteDirectory(tempSGDir);
  }

  @Test
  public void testMerge() throws Exception {
    MergeTask mergeTask =
        new MergeTask(new MergeResource(seqResources, unseqResources), tempSGDir.getPath(), (k, v
            , l) -> {}, "test", false, 1, MERGE_TEST_SG);
    mergeTask.call();

    QueryContext context = new QueryContext();
    Path path = new Path(deviceIds[0], measurementSchemas[0].getMeasurementId());
    SeqResourceIterateReader tsFilesReader = new SeqResourceIterateReader(path,
        Collections.singletonList(seqResources.get(0)),
        null, context);
    while (tsFilesReader.hasNext()) {
      BatchData batchData = tsFilesReader.nextBatch();
      for (int i = 0; i < batchData.length(); i++) {
        assertEquals(batchData.getTimeByIndex(i) + 20000.0, batchData.getDoubleByIndex(i), 0.001);
      }
    }
    tsFilesReader.close();
  }

  @Test
  public void testFullMerge() throws Exception {
    MergeTask mergeTask =
        new MergeTask(new MergeResource(seqResources, unseqResources), tempSGDir.getPath(), (k, v, l) -> {}, "test",
            true, 1, MERGE_TEST_SG);
    mergeTask.call();

    QueryContext context = new QueryContext();
    Path path = new Path(deviceIds[0], measurementSchemas[0].getMeasurementId());
    SeqResourceIterateReader tsFilesReader = new SeqResourceIterateReader(path,
        Collections.singletonList(seqResources.get(0)),
        null, context);
    while (tsFilesReader.hasNext()) {
      BatchData batchData = tsFilesReader.nextBatch();
      for (int i = 0; i < batchData.length(); i++) {
        assertEquals(batchData.getTimeByIndex(i) + 20000.0, batchData.getDoubleByIndex(i), 0.001);
      }
    }
    tsFilesReader.close();
  }

  @Test
  public void testChunkNumThreshold() throws Exception {
    IoTDBDescriptor.getInstance().getConfig().setChunkMergePointThreshold(Integer.MAX_VALUE);
    MergeTask mergeTask =
        new MergeTask(new MergeResource(seqResources, unseqResources), tempSGDir.getPath(), (k, v, l) -> {}, "test",
            false, 1, MERGE_TEST_SG);
    mergeTask.call();

    QueryContext context = new QueryContext();
    Path path = new Path(deviceIds[0], measurementSchemas[0].getMeasurementId());
    SeqResourceIterateReader tsFilesReader = new SeqResourceIterateReader(path,
        Collections.singletonList(seqResources.get(0)),
        null, context);
    while (tsFilesReader.hasNext()) {
      BatchData batchData = tsFilesReader.nextBatch();
      for (int i = 0; i < batchData.length(); i++) {
        assertEquals(batchData.getTimeByIndex(i) + 20000.0, batchData.getDoubleByIndex(i), 0.001);
      }
    }
    tsFilesReader.close();
  }

  @Test
  public void testPartialMerge1() throws Exception {
    MergeTask mergeTask =
        new MergeTask(new MergeResource(seqResources, unseqResources.subList(0, 1)), tempSGDir.getPath(),
            (k, v, l) -> {}, "test", false, 1, MERGE_TEST_SG);
    mergeTask.call();

    QueryContext context = new QueryContext();
    Path path = new Path(deviceIds[0], measurementSchemas[0].getMeasurementId());
    SeqResourceIterateReader tsFilesReader = new SeqResourceIterateReader(path,
        Collections.singletonList(seqResources.get(0)),
        null, context);
    while (tsFilesReader.hasNext()) {
      BatchData batchData = tsFilesReader.nextBatch();
      for (int i = 0; i < batchData.length(); i++) {
        if (batchData.getTimeByIndex(i) < 20) {
          assertEquals(batchData.getTimeByIndex(i) + 10000.0, batchData.getDoubleByIndex(i), 0.001);
        } else {
          assertEquals(batchData.getTimeByIndex(i) + 0.0, batchData.getDoubleByIndex(i), 0.001);
        }
      }
    }
    tsFilesReader.close();
  }

  @Test
  public void testPartialMerge2() throws Exception {
    MergeTask mergeTask =
        new MergeTask(new MergeResource(seqResources, unseqResources.subList(5, 6)), tempSGDir.getPath(),
            (k, v, l) -> {}, "test", false, 1, MERGE_TEST_SG);
    mergeTask.call();

    QueryContext context = new QueryContext();
    Path path = new Path(deviceIds[0], measurementSchemas[0].getMeasurementId());
    SeqResourceIterateReader tsFilesReader = new SeqResourceIterateReader(path,
        Collections.singletonList(seqResources.get(0)),
        null, context);
    while (tsFilesReader.hasNext()) {
      BatchData batchData = tsFilesReader.nextBatch();
      for (int i = 0; i < batchData.length(); i++) {
        assertEquals(batchData.getTimeByIndex(i) + 20000.0, batchData.getDoubleByIndex(i), 0.001);
      }
    }
    tsFilesReader.close();
  }

  @Test
  public void testPartialMerge3() throws Exception {
    MergeTask mergeTask =
        new MergeTask(new MergeResource(seqResources, unseqResources.subList(0, 5)), tempSGDir.getPath(),
            (k, v, l) -> {}, "test", false, 1, MERGE_TEST_SG);
    mergeTask.call();

    QueryContext context = new QueryContext();
    Path path = new Path(deviceIds[0], measurementSchemas[0].getMeasurementId());
    SeqResourceIterateReader tsFilesReader = new SeqResourceIterateReader(path,
        Collections.singletonList(seqResources.get(2)),
        null, context);
    while (tsFilesReader.hasNext()) {
      BatchData batchData = tsFilesReader.nextBatch();
      for (int i = 0; i < batchData.length(); i++) {
        if (batchData.getTimeByIndex(i) < 260) {
          assertEquals(batchData.getTimeByIndex(i) + 10000.0, batchData.getDoubleByIndex(i), 0.001);
        } else {
          assertEquals(batchData.getTimeByIndex(i) + 0.0, batchData.getDoubleByIndex(i), 0.001);
        }
      }
    }
    tsFilesReader.close();
  }

  @Test
  public void mergeWithDeletionTest() throws Exception {
    seqResources.get(0).getModFile().write(new Deletion(new Path(deviceIds[0],
        measurementSchemas[0].getMeasurementId()), 10000, 49));
    seqResources.get(0).getModFile().close();


    MergeTask mergeTask =
        new MergeTask(new MergeResource(seqResources, unseqResources.subList(0, 1)), tempSGDir.getPath(),
            (k, v, l) -> {
              try {
                seqResources.get(0).removeModFile();
              } catch (IOException e) {
                e.printStackTrace();
              }
            }, "test", false, 1, MERGE_TEST_SG);
    mergeTask.call();

    QueryContext context = new QueryContext();
    Path path = new Path(deviceIds[0], measurementSchemas[0].getMeasurementId());
    SeqResourceIterateReader tsFilesReader = new SeqResourceIterateReader(path,
        Collections.singletonList(seqResources.get(0)),
        null, context);
    int count = 0;
    while (tsFilesReader.hasNext()) {
      BatchData batchData = tsFilesReader.nextBatch();
      for (int i = 0; i < batchData.length(); i++) {
        if (batchData.getTimeByIndex(i) <= 20) {
          assertEquals(batchData.getTimeByIndex(i) + 10000.0, batchData.getDoubleByIndex(i), 0.001);
        } else {
          assertEquals(batchData.getTimeByIndex(i), batchData.getDoubleByIndex(i), 0.001);
        }
        count ++;
      }
    }
    assertEquals(70, count);
    tsFilesReader.close();
  }
}
