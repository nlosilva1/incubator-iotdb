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
package org.apache.iotdb.db.engine;

import java.util.Collections;
import org.apache.iotdb.db.metadata.MManager;
import org.apache.iotdb.tsfile.common.conf.TSFileDescriptor;
import org.apache.iotdb.tsfile.file.metadata.enums.CompressionType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
import org.apache.iotdb.tsfile.read.common.Path;

public class MetadataManagerHelper {

  private static MManager mmanager = null;

  public static void initMetadata() {
    mmanager = MManager.getInstance();
    mmanager.init();
    mmanager.clear();
    try {
      mmanager.setStorageGroupToMTree("root.vehicle.d0");
      mmanager.setStorageGroupToMTree("root.vehicle.d1");
      mmanager.setStorageGroupToMTree("root.vehicle.d2");

      CompressionType compressionType =CompressionType.valueOf(
          TSFileDescriptor.getInstance().getConfig().getCompressor());

      mmanager.addPathToMTree(new Path("root.vehicle.d0.s0"), TSDataType.valueOf("INT32"),
          TSEncoding.valueOf("RLE"), compressionType,
          Collections.emptyMap());
      mmanager.addPathToMTree(new Path("root.vehicle.d0.s1"), TSDataType.valueOf("INT64"),
          TSEncoding.valueOf("RLE"), compressionType,
          Collections.emptyMap());
      mmanager.addPathToMTree(new Path("root.vehicle.d0.s2"), TSDataType.valueOf("FLOAT"),
          TSEncoding.valueOf("RLE"), compressionType,
          Collections.emptyMap());
      mmanager.addPathToMTree(new Path("root.vehicle.d0.s3"), TSDataType.valueOf("DOUBLE"),
          TSEncoding.valueOf("RLE"), compressionType,
          Collections.emptyMap());
      mmanager.addPathToMTree(new Path("root.vehicle.d0.s4"), TSDataType.valueOf("BOOLEAN"),
          TSEncoding.valueOf("PLAIN"), compressionType,
          Collections.emptyMap());
      mmanager.addPathToMTree(new Path("root.vehicle.d0.s5"), TSDataType.valueOf("TEXT"),
          TSEncoding.valueOf("PLAIN"), compressionType,
          Collections.emptyMap());

      mmanager.addPathToMTree(new Path("root.vehicle.d1.s0"), TSDataType.valueOf("INT32"),
          TSEncoding.valueOf("RLE"), compressionType, Collections.emptyMap());
      mmanager.addPathToMTree(new Path("root.vehicle.d1.s1"), TSDataType.valueOf("INT64"),
          TSEncoding.valueOf("RLE"), compressionType, Collections.emptyMap());
      mmanager.addPathToMTree(new Path("root.vehicle.d1.s2"), TSDataType.valueOf("FLOAT"),
          TSEncoding.valueOf("RLE"), compressionType, Collections.emptyMap());
      mmanager.addPathToMTree(new Path("root.vehicle.d1.s3"), TSDataType.valueOf("DOUBLE"),
          TSEncoding.valueOf("RLE"), compressionType, Collections.emptyMap());
      mmanager.addPathToMTree(new Path("root.vehicle.d1.s4"), TSDataType.valueOf("BOOLEAN"),
          TSEncoding.valueOf("PLAIN"), compressionType, Collections.emptyMap());
      mmanager.addPathToMTree(new Path("root.vehicle.d1.s5"), TSDataType.valueOf("TEXT"),
          TSEncoding.valueOf("PLAIN"), compressionType, Collections.emptyMap());

      mmanager.addPathToMTree(new Path("root.vehicle.d2.s0"), TSDataType.valueOf("INT32"),
          TSEncoding.valueOf("RLE"), compressionType, Collections.emptyMap());
      mmanager.addPathToMTree(new Path("root.vehicle.d2.s1"), TSDataType.valueOf("INT64"),
          TSEncoding.valueOf("RLE"), compressionType, Collections.emptyMap());
      mmanager.addPathToMTree(new Path("root.vehicle.d2.s2"), TSDataType.valueOf("FLOAT"),
          TSEncoding.valueOf("RLE"), compressionType, Collections.emptyMap());
      mmanager.addPathToMTree(new Path("root.vehicle.d2.s3"), TSDataType.valueOf("DOUBLE"),
          TSEncoding.valueOf("RLE"), compressionType, Collections.emptyMap());
      mmanager.addPathToMTree(new Path("root.vehicle.d2.s4"), TSDataType.valueOf("BOOLEAN"),
          TSEncoding.valueOf("PLAIN"), compressionType, Collections.emptyMap());
      mmanager.addPathToMTree(new Path("root.vehicle.d2.s5"), TSDataType.valueOf("TEXT"),
          TSEncoding.valueOf("PLAIN"), compressionType, Collections.emptyMap());

    } catch (Exception e) {
      throw new RuntimeException("Initialize the metadata manager failed", e);
    }
  }

  public static void initMetadata2() {

    mmanager = MManager.getInstance();
    mmanager.clear();
    try {
      mmanager.setStorageGroupToMTree("root.vehicle");
      CompressionType compressionType =CompressionType.valueOf(TSFileDescriptor.getInstance().getConfig().getCompressor());

      mmanager.addPathToMTree(new Path("root.vehicle.d0.s0"), TSDataType.valueOf("INT32"), TSEncoding.valueOf("RLE"), compressionType, Collections.emptyMap());
      mmanager.addPathToMTree(new Path("root.vehicle.d0.s1"), TSDataType.valueOf("INT64"), TSEncoding.valueOf("RLE"), compressionType, Collections.emptyMap());
      mmanager.addPathToMTree(new Path("root.vehicle.d0.s2"), TSDataType.valueOf("FLOAT"), TSEncoding.valueOf("RLE"), compressionType, Collections.emptyMap());
      mmanager.addPathToMTree(new Path("root.vehicle.d0.s3"), TSDataType.valueOf("DOUBLE"), TSEncoding.valueOf("RLE"), compressionType, Collections.emptyMap());
      mmanager.addPathToMTree(new Path("root.vehicle.d0.s4"), TSDataType.valueOf("BOOLEAN"), TSEncoding.valueOf("PLAIN"), compressionType, Collections.emptyMap());
      mmanager.addPathToMTree(new Path("root.vehicle.d0.s5"), TSDataType.valueOf("TEXT"), TSEncoding.valueOf("PLAIN"), compressionType, Collections.emptyMap());

      mmanager.addPathToMTree(new Path("root.vehicle.d1.s0"), TSDataType.valueOf("INT32"), TSEncoding.valueOf("RLE"), compressionType, Collections.emptyMap());
      mmanager.addPathToMTree(new Path("root.vehicle.d1.s1"), TSDataType.valueOf("INT64"), TSEncoding.valueOf("RLE"), compressionType, Collections.emptyMap());
      mmanager.addPathToMTree(new Path("root.vehicle.d1.s2"), TSDataType.valueOf("FLOAT"), TSEncoding.valueOf("RLE"), compressionType, Collections.emptyMap());
      mmanager.addPathToMTree(new Path("root.vehicle.d1.s3"), TSDataType.valueOf("DOUBLE"), TSEncoding.valueOf("RLE"), compressionType, Collections.emptyMap());
      mmanager.addPathToMTree(new Path("root.vehicle.d1.s4"), TSDataType.valueOf("BOOLEAN"), TSEncoding.valueOf("PLAIN"), compressionType, Collections.emptyMap());
      mmanager.addPathToMTree(new Path("root.vehicle.d1.s5"), TSDataType.valueOf("TEXT"), TSEncoding.valueOf("PLAIN"), compressionType, Collections.emptyMap());

      mmanager.addPathToMTree(new Path("root.vehicle.d2.s0"), TSDataType.valueOf("INT32"), TSEncoding.valueOf("RLE"), compressionType, Collections.emptyMap());
      mmanager.addPathToMTree(new Path("root.vehicle.d2.s1"), TSDataType.valueOf("INT64"), TSEncoding.valueOf("RLE"), compressionType, Collections.emptyMap());
      mmanager.addPathToMTree(new Path("root.vehicle.d2.s2"), TSDataType.valueOf("FLOAT"), TSEncoding.valueOf("RLE"), compressionType, Collections.emptyMap());
      mmanager.addPathToMTree(new Path("root.vehicle.d2.s3"), TSDataType.valueOf("DOUBLE"), TSEncoding.valueOf("RLE"), compressionType, Collections.emptyMap());
      mmanager.addPathToMTree(new Path("root.vehicle.d2.s4"), TSDataType.valueOf("BOOLEAN"), TSEncoding.valueOf("PLAIN"), compressionType, Collections.emptyMap());
      mmanager.addPathToMTree(new Path("root.vehicle.d2.s5"), TSDataType.valueOf("TEXT"), TSEncoding.valueOf("PLAIN"), compressionType, Collections.emptyMap());

    } catch (Exception e) {

      throw new RuntimeException("Initialize the metadata manager failed", e);
    }
  }

}
