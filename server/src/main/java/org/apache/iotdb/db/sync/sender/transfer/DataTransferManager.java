
package org.apache.iotdb.db.sync.sender.transfer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileLock;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.iotdb.db.concurrent.IoTDBThreadPoolFactory;
import org.apache.iotdb.db.concurrent.ThreadName;
import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.exception.SyncConnectionException;
import org.apache.iotdb.db.metadata.MetadataConstant;
import org.apache.iotdb.db.sync.sender.conf.Constans;
import org.apache.iotdb.db.sync.sender.conf.SyncSenderConfig;
import org.apache.iotdb.db.sync.sender.conf.SyncSenderDescriptor;
import org.apache.iotdb.db.sync.sender.manage.SyncFileManager;
import org.apache.iotdb.db.sync.sender.recover.SyncSenderLogAnalyzer;
import org.apache.iotdb.db.sync.sender.recover.SyncSenderLogger;
import org.apache.iotdb.db.utils.SyncUtils;
import org.apache.iotdb.service.sync.thrift.ResultStatus;
import org.apache.iotdb.service.sync.thrift.SyncService;
import org.apache.iotdb.tsfile.utils.BytesUtils;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SyncSenderImpl is used to transfer tsfiles that needs to sync to receiver.
 */
public class DataTransferManager implements IDataTransferManager {

  private static final Logger logger = LoggerFactory.getLogger(DataTransferManager.class);

  private static SyncSenderConfig config = SyncSenderDescriptor.getInstance().getConfig();

  private static final int BATCH_LINE = 1000;

  private int schemaFileLinePos;

  private TTransport transport;

  private SyncService.Client serviceClient;

  /**
   * Files that need to be synchronized
   */
  private Map<String, Set<File>> toBeSyncedFilesMap;

  private Map<String, Set<File>> deletedFilesMap;

  private Map<String, Set<File>> sucessSyncedFilesMap;

  private Map<String, Set<File>> successDeletedFilesMap;

  private Map<String, Set<File>> lastLocalFilesMap;

  /**
   * If true, sync is in execution.
   **/
  private volatile boolean syncStatus = false;

  private SyncSenderLogger syncLog;

  private SyncFileManager syncFileManager = SyncFileManager.getInstance();

  private ScheduledExecutorService executorService;

  private DataTransferManager() {
    init();
  }

  public static DataTransferManager getInstance() {
    return InstanceHolder.INSTANCE;
  }

  /**
   * Create a sender and sync files to the receiver.
   */
  public static void main(String[] args) throws IOException {
    Thread.currentThread().setName(ThreadName.SYNC_CLIENT.getName());
    DataTransferManager fileSenderImpl = new DataTransferManager();
    fileSenderImpl.verifySingleton();
    fileSenderImpl.startMonitor();
    fileSenderImpl.startTimedTask();
  }


  /**
   * The method is to verify whether the client lock file is locked or not, ensuring that only one
   * client is running.
   */
  private void verifySingleton() throws IOException {
    File lockFile = new File(config.getLockFilePath());
    if (!lockFile.getParentFile().exists()) {
      lockFile.getParentFile().mkdirs();
    }
    if (!lockFile.exists()) {
      lockFile.createNewFile();
    }
    if (!lockInstance(config.getLockFilePath())) {
      logger.error("Sync client is already running.");
      System.exit(1);
    }
  }

  /**
   * Try to lock lockfile. if failed, it means that sync client has benn started.
   *
   * @param lockFile path of lock file
   */
  private boolean lockInstance(final String lockFile) {
    try {
      final File file = new File(lockFile);
      final RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
      final FileLock fileLock = randomAccessFile.getChannel().tryLock();
      if (fileLock != null) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
          try {
            fileLock.release();
            randomAccessFile.close();
          } catch (Exception e) {
            logger.error("Unable to remove lock file: {}", lockFile, e);
          }
        }));
        return true;
      }
    } catch (Exception e) {
      logger.error("Unable to create and/or lock file: {}", lockFile, e);
    }
    return false;
  }


  @Override
  public void init() {
    if (executorService == null) {
      executorService = IoTDBThreadPoolFactory.newScheduledThreadPool(2,
          "sync-client-timer");
    }
  }

  /**
   * Start Monitor Thread, monitor sync status
   */
  private void startMonitor() {
    executorService.scheduleWithFixedDelay(() -> {
      if (syncStatus) {
        logger.info("Sync process for receiver {} is in execution!", config.getSyncReceiverName());
      }
    }, Constans.SYNC_MONITOR_DELAY, Constans.SYNC_MONITOR_PERIOD, TimeUnit.SECONDS);
  }

  /**
   * Start sync task in a certain time.
   */
  private void startTimedTask() {
    executorService.scheduleWithFixedDelay(() -> {
      try {
        syncAll();
      } catch (SyncConnectionException | IOException | TException e) {
        logger.error("Sync failed", e);
        stop();
      }
    }, Constans.SYNC_PROCESS_DELAY, Constans.SYNC_PROCESS_PERIOD, TimeUnit.SECONDS);
  }

  @Override
  public void stop() {
    executorService.shutdownNow();
    executorService = null;
  }

  public void syncAll() throws SyncConnectionException, IOException, TException {

    // 1. Connect to sync receiver and confirm identity
    establishConnection(config.getServerIp(), config.getServerPort());
    if (!confirmIdentity(config.getSenderPath())) {
      logger.error("Sorry, you do not have the permission to connect to sync receiver.");
      System.exit(1);
    }

    // 2. Sync Schema
    syncSchema();

    // 3. Sync all data
    String[] dataDirs = IoTDBDescriptor.getInstance().getConfig().getDataDirs();
    for (String dataDir : dataDirs) {
      logger.info("Start to sync data in data dir {}", dataDir);
      config.update(dataDir);
      SyncFileManager.getInstance().getValidFiles(dataDir);
      lastLocalFilesMap = SyncFileManager.getInstance().getLastLocalFilesMap();
      deletedFilesMap = SyncFileManager.getInstance().getDeletedFilesMap();
      toBeSyncedFilesMap = SyncFileManager.getInstance().getToBeSyncedFilesMap();
      checkRecovery();
      if (SyncUtils.isEmpty(deletedFilesMap) && SyncUtils.isEmpty(toBeSyncedFilesMap)) {
        logger.info("There has no data to sync in data dir {}", dataDir);
        continue;
      }
      sync();
      logger.info("Finish to sync data in data dir {}", dataDir);
    }

    // 4. notify receiver that synchronization finish
    // At this point the synchronization has finished even if connection fails
    try {
      serviceClient.endSync();
      transport.close();
      logger.info("Sync process has finished.");
    } catch (TException e) {
      logger.error("Unable to connect to receiver.", e);
    }
  }

  /**
   * Execute a sync task.
   */
  @Override
  public void sync() throws IOException {
    try {
      syncStatus = true;
      syncLog = new SyncSenderLogger(getSchemaLogFile());

      // 1. Sync data
      for (Entry<String, Set<File>> entry : deletedFilesMap.entrySet()) {
        checkRecovery();
        syncLog = new SyncSenderLogger(getSyncLogFile());
        // TODO deal with the situation
        try {
          if (serviceClient.init(entry.getKey()) == ResultStatus.FAILURE) {
            throw new SyncConnectionException("unable init receiver");
          }
        } catch (TException | SyncConnectionException e) {
          throw new SyncConnectionException("Unable to connect to receiver", e);
        }
        logger.info("Sync process starts to transfer data of storage group {}", entry.getKey());
        syncDeletedFilesName(entry.getKey(), entry.getValue());
        syncDataFilesInOneGroup(entry.getKey(), entry.getValue());
        clearSyncLog();
      }

    } catch (SyncConnectionException e) {
      logger.error("cannot finish sync process", e);
    } finally {
      if (syncLog != null) {
        syncLog.close();
      }
      syncStatus = false;
    }
  }

  private void checkRecovery() {
    new SyncSenderLogAnalyzer(config.getSenderPath()).recover();
  }

  @Override
  public void syncDeletedFilesName(String sgName, Set<File> deletedFilesName) throws IOException {
    if (deletedFilesName.isEmpty()) {
      logger.info("There has no deleted files to be synced in storage group {}", sgName);
      return;
    }
    syncLog.startSyncDeletedFilesName();
    logger.info("Start to sync names of deleted files in storage group {}", sgName);
    for (File file : deletedFilesName) {
      try {
        serviceClient.syncDeletedFileName(file.getName());
        successDeletedFilesMap.get(sgName).add(file);
        syncLog.finishSyncDeletedFileName(file);
      } catch (TException e) {
        logger.error("Can not sync deleted file name {}, skip it.", file);
      }
    }
    logger.info("Finish to sync names of deleted files in storage group {}", sgName);
  }

  @Override
  public void syncDataFilesInOneGroup(String sgName, Set<File> toBeSyncFiles)
      throws SyncConnectionException, IOException {
    if (toBeSyncFiles.isEmpty()) {
      logger.info("There has no new tsfiles to be synced in storage group {}", sgName);
      return;
    }
    syncLog.startSyncTsFiles();
    logger.info("Sync process starts to transfer data of storage group {}", sgName);
    int cnt = 0;
    for (File tsfile : toBeSyncFiles) {
      cnt++;
      File snapshotFile = null;
      try {
        snapshotFile = makeFileSnapshot(tsfile);
        syncSingleFile(snapshotFile);
        sucessSyncedFilesMap.get(sgName).add(tsfile);
        syncLog.finishSyncTsfile(tsfile);
        logger.info("Task of synchronization has completed {}/{}.", cnt, toBeSyncFiles.size());
      } catch (IOException e) {
        logger.info(
            "Tsfile {} can not make snapshot, so skip the tsfile and continue to sync other tsfiles",
            tsfile, e);
      } finally {
        if (snapshotFile != null) {
          snapshotFile.deleteOnExit();
        }
      }
    }
    logger.info("Sync process has finished storage group {}.", sgName);
  }

  private File makeFileSnapshot(File file) throws IOException {
    File snapshotFile = SyncUtils.getSnapshotFile(file);
    if (!snapshotFile.getParentFile().exists()) {
      snapshotFile.getParentFile().mkdirs();
    }
    Path link = FileSystems.getDefault().getPath(snapshotFile.getAbsolutePath());
    Path target = FileSystems.getDefault().getPath(snapshotFile.getAbsolutePath());
    Files.createLink(link, target);
    return snapshotFile;
  }

  /**
   * Transfer data of a storage group to receiver.
   */
  private void syncSingleFile(File snapshotFile) throws SyncConnectionException {
    try {
      int retryCount = 0;
      MessageDigest md = MessageDigest.getInstance(Constans.MESSAGE_DIGIT_NAME);
      serviceClient.initSyncData(snapshotFile.getName());
      outer:
      while (true) {
        retryCount++;
        if (retryCount > Constans.MAX_SYNC_FILE_TRY) {
          throw new SyncConnectionException(String
              .format("Can not sync file %s after %s tries.", snapshotFile.getAbsoluteFile(),
                  Constans.MAX_SYNC_FILE_TRY));
        }
        md.reset();
        byte[] buffer = new byte[Constans.DATA_CHUNK_SIZE];
        int dataLength;
        try (FileInputStream fis = new FileInputStream(snapshotFile);
            ByteArrayOutputStream bos = new ByteArrayOutputStream(Constans.DATA_CHUNK_SIZE)) {
          while ((dataLength = fis.read(buffer)) != -1) { // cut the file into pieces to send
            bos.write(buffer, 0, dataLength);
            md.update(buffer, 0, dataLength);
            ByteBuffer buffToSend = ByteBuffer.wrap(bos.toByteArray());
            bos.reset();
            if (serviceClient.syncData(buffToSend) == ResultStatus.FAILURE) {
              logger.info("Receiver failed to receive data from {}, retry.",
                  snapshotFile.getAbsoluteFile());
              continue outer;
            }
          }
        }

        // the file is sent successfully
        String md5OfSender = (new BigInteger(1, md.digest())).toString(16);
        String md5OfReceiver = serviceClient.checkDataMD5(md5OfSender);
        if (md5OfSender.equals(md5OfReceiver)) {
          logger.info("Receiver has received {} successfully.", snapshotFile.getAbsoluteFile());
          break;
        }
      }
    } catch (IOException | TException | NoSuchAlgorithmException e) {
      throw new SyncConnectionException("Cannot sync data with receiver.", e);
    }
  }


  /**
   * Establish a connection between the sender and the receiver.
   *
   * @param serverIp the ip address of the receiver
   * @param serverPort must be same with port receiver set.
   */
  @Override
  public void establishConnection(String serverIp, int serverPort) throws SyncConnectionException {
    transport = new TSocket(serverIp, serverPort);
    TProtocol protocol = new TBinaryProtocol(transport);
    serviceClient = new SyncService.Client(protocol);
    try {
      transport.open();
    } catch (TTransportException e) {
      logger.error("Cannot connect to server");
      throw new SyncConnectionException(e);
    }
  }

  /**
   * UUID marks the identity of sender for receiver.
   */
  @Override
  public boolean confirmIdentity(String uuidPath) throws SyncConnectionException {
    try {
      return serviceClient.checkIdentity(InetAddress.getLocalHost().getHostAddress())
          == ResultStatus.SUCCESS;
    } catch (Exception e) {
      logger.error("Cannot confirm identity with receiver");
      throw new SyncConnectionException(e);
    }
  }

  /**
   * Sync schema with receiver.
   */
  @Override
  public void syncSchema() throws SyncConnectionException, TException {
    int retryCount = 0;
    serviceClient.initSyncData(MetadataConstant.METADATA_LOG);
    while (true) {
      if (retryCount > Constans.MAX_SYNC_FILE_TRY) {
        throw new SyncConnectionException(String
            .format("Can not sync schema after %s tries.", Constans.MAX_SYNC_FILE_TRY));
      }
      try {
        if (tryToSyncSchema()) {
          writeSyncSchemaPos(getSchemaPosFile());
          break;
        }
      } finally {
        retryCount++;
      }
    }
  }

  private boolean tryToSyncSchema() {
    int schemaPos = readSyncSchemaPos(getSchemaPosFile());

    // start to sync file data and get md5 of this file.
    try (BufferedReader br = new BufferedReader(new FileReader(getSchemaLogFile()));
        ByteArrayOutputStream bos = new ByteArrayOutputStream(Constans.DATA_CHUNK_SIZE)) {
      schemaFileLinePos = 0;
      while (schemaFileLinePos++ <= schemaPos) {
        br.readLine();
      }
      MessageDigest md = MessageDigest.getInstance(Constans.MESSAGE_DIGIT_NAME);
      String line;
      int cntLine = 0;
      while ((line = br.readLine()) != null) {
        schemaFileLinePos++;
        byte[] singleLineData = BytesUtils.stringToBytes(line);
        bos.write(singleLineData);
        md.update(singleLineData);
        if (cntLine++ == BATCH_LINE) {
          ByteBuffer buffToSend = ByteBuffer.wrap(bos.toByteArray());
          bos.reset();
          // PROCESSING_STATUS represents there is still schema buffer to send.
          if (serviceClient.syncData(buffToSend) == ResultStatus.FAILURE) {
            logger.error("Receiver failed to receive metadata, retry.");
            return false;
          }
          cntLine = 0;
        }
      }
      if (bos.size() != 0) {
        ByteBuffer buffToSend = ByteBuffer.wrap(bos.toByteArray());
        bos.reset();
        if (serviceClient.syncData(buffToSend) == ResultStatus.FAILURE) {
          logger.error("Receiver failed to receive metadata, retry.");
          return false;
        }
      }

      // check md5
      return checkMD5ForSchema((new BigInteger(1, md.digest())).toString(16));
    } catch (NoSuchAlgorithmException | IOException | TException e) {
      logger.error("Can not finish transfer schema to receiver", e);
      return false;
    }
  }


  private boolean checkMD5ForSchema(String md5OfSender) throws TException {
    String md5OfReceiver = serviceClient.checkDataMD5(md5OfSender);
    if (md5OfSender.equals(md5OfReceiver)) {
      logger.info("Receiver has received schema successfully, retry.");
      return true;
    } else {
      logger
          .error("MD5 check of schema file {} failed, retry", getSchemaLogFile().getAbsoluteFile());
      return false;
    }
  }

  private int readSyncSchemaPos(File syncSchemaLogFile) {
    try {
      if (syncSchemaLogFile.exists()) {
        try (BufferedReader br = new BufferedReader(new FileReader(syncSchemaLogFile))) {
          return Integer.parseInt(br.readLine());
        }
      }
    } catch (IOException e) {
      logger.error("Can not find file {}", syncSchemaLogFile.getAbsoluteFile(), e);
    }
    return 0;
  }

  private void writeSyncSchemaPos(File syncSchemaLogFile) {
    try {
      if (syncSchemaLogFile.exists()) {
        try (BufferedWriter br = new BufferedWriter(new FileWriter(syncSchemaLogFile))) {
          br.write(Integer.toString(schemaFileLinePos));
        }
      }
    } catch (IOException e) {
      logger.error("Can not find file {}", syncSchemaLogFile.getAbsoluteFile(), e);
    }
  }

  private void clearSyncLog() {
    for (Entry<String, Set<File>> entry : lastLocalFilesMap.entrySet()) {
      entry.getValue()
          .removeAll(successDeletedFilesMap.getOrDefault(entry.getKey(), new HashSet<>()));
      entry.getValue()
          .removeAll(sucessSyncedFilesMap.getOrDefault(entry.getKey(), new HashSet<>()));
    }
    File currentLocalFile = getCurrentLogFile();
    File lastLocalFile = new File(config.getLastFileInfo());
    try (BufferedWriter bw = new BufferedWriter(new FileWriter(currentLocalFile))) {
      for (Set<File> currentLocalFiles : lastLocalFilesMap.values()) {
        for (File file : currentLocalFiles) {
          bw.write(file.getAbsolutePath());
          bw.newLine();
        }
        bw.flush();
      }
    } catch (IOException e) {
      logger.error("Can not clear sync log {}", lastLocalFile.getAbsoluteFile(), e);
    }
    currentLocalFile.renameTo(lastLocalFile);
  }


  private File getSchemaPosFile() {
    return new File(config.getSenderPath(), Constans.SCHEMA_POS_FILE_NAME);
  }

  private File getSchemaLogFile() {
    return new File(IoTDBDescriptor.getInstance().getConfig().getSchemaDir(),
        MetadataConstant.METADATA_LOG);
  }

  private static class InstanceHolder {

    private static final DataTransferManager INSTANCE = new DataTransferManager();
  }

  private File getSyncLogFile() {
    return new File(config.getSenderPath(), Constans.SYNC_LOG_NAME);
  }

  private File getCurrentLogFile() {
    return new File(config.getSenderPath(), Constans.CURRENT_LOCAL_FILE_NAME);
  }

  public void setConfig(SyncSenderConfig config) {
    DataTransferManager.config = config;
  }
}
