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
package org.apache.iotdb.jdbc;

import java.sql.*;
import java.util.List;
import java.util.Set;
import org.apache.iotdb.rpc.IoTDBRPCException;
import org.apache.iotdb.rpc.RpcUtils;
import org.apache.iotdb.service.rpc.thrift.TSFetchMetadataReq;
import org.apache.iotdb.service.rpc.thrift.TSFetchMetadataResp;
import org.apache.iotdb.service.rpc.thrift.TSIService;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IoTDBDatabaseMetadata implements DatabaseMetaData {

  private IoTDBConnection connection;
  private TSIService.Iface client;
  private static final Logger logger = LoggerFactory
          .getLogger(IoTDBDatabaseMetadata.class);
  private static final String METHOD_NOT_SUPPORTED_STRING = "Method not supported";

  public IoTDBDatabaseMetadata(IoTDBConnection connection, TSIService.Iface client) {
    this.connection = connection;
    this.client = client;
  }

  @Override
  public ResultSet getColumns(String catalog, String schemaPattern, String columnPattern,
      String devicePattern)
      throws SQLException {
    try {
      return getColumnsFunc(catalog, schemaPattern);
    } catch (TException e) {
      boolean flag = connection.reconnect();
      this.client = connection.client;
      if (flag) {
        try {
          return getColumnsFunc(catalog, schemaPattern);
        } catch (TException e2) {
          throw new SQLException(String.format("Fail to get columns catalog=%s, schemaPattern=%s,"
                  + " columnPattern=%s, devicePattern=%s after reconnecting."
                  + " please check server status",
              catalog, schemaPattern, columnPattern, devicePattern));
        }
      } else {
        throw new SQLException(String.format(
            "Fail to reconnect to server when getting columns catalog=%s, schemaPattern=%s,"
                + " columnPattern=%s, devicePattern=%s after reconnecting. "
                + "please check server status",
            catalog, schemaPattern, columnPattern, devicePattern));
      }
    }
  }

  private ResultSet getColumnsFunc(String catalog, String schemaPattern)
      throws TException, SQLException {
    TSFetchMetadataReq req;
    switch (catalog) {
      case Constant.CATALOG_COLUMN:
        req = new TSFetchMetadataReq(Constant.GLOBAL_COLUMNS_REQ);
        req.setColumnPath(schemaPattern);
        try {
          TSFetchMetadataResp resp = client.fetchMetadata(req);
          try {
            RpcUtils.verifySuccess(resp.getStatus());
          } catch (IoTDBRPCException e) {
            throw new IoTDBSQLException(e.getMessage());
          }
          return new IoTDBMetadataResultSet(resp.getColumnsList(), IoTDBMetadataResultSet.MetadataType.COLUMN);
        } catch (TException e) {
          throw new TException("Conncetion error when fetching column metadata", e);
        }
      case Constant.CATALOG_DEVICE:
        req = new TSFetchMetadataReq(Constant.GLOBAL_DELTA_OBJECT_REQ);
        req.setColumnPath(schemaPattern);
        try {
          TSFetchMetadataResp resp = client.fetchMetadata(req);
          try {
            RpcUtils.verifySuccess(resp.getStatus());
          } catch (IoTDBRPCException e) {
            throw new IoTDBSQLException(e.getMessage());
          }
          return new IoTDBMetadataResultSet(resp.getColumnsList(), IoTDBMetadataResultSet.MetadataType.COLUMN);
        } catch (TException e) {
          throw new TException("Conncetion error when fetching delta object metadata", e);
        }
      case Constant.CATALOG_STORAGE_GROUP:
        req = new TSFetchMetadataReq(Constant.GLOBAL_SHOW_STORAGE_GROUP_REQ);
        try {
          TSFetchMetadataResp resp = client.fetchMetadata(req);
          try {
            RpcUtils.verifySuccess(resp.getStatus());
          } catch (IoTDBRPCException e) {
            throw new IoTDBSQLException(e.getMessage());
          }
          Set<String> showStorageGroup = resp.getShowStorageGroups();
          return new IoTDBMetadataResultSet(showStorageGroup, IoTDBMetadataResultSet.MetadataType.STORAGE_GROUP);
        } catch (TException e) {
          throw new TException("Conncetion error when fetching storage group metadata", e);
        }
      case Constant.CATALOG_TIMESERIES:
        req = new TSFetchMetadataReq(Constant.GLOBAL_SHOW_TIMESERIES_REQ);
        req.setColumnPath(schemaPattern);
        try {
          TSFetchMetadataResp resp = client.fetchMetadata(req);
          try {
            RpcUtils.verifySuccess(resp.getStatus());
          } catch (IoTDBRPCException e) {
            throw new IoTDBSQLException(e.getMessage());
          }
          List<List<String>> showTimeseriesList = resp.getShowTimeseriesList();
          return new IoTDBMetadataResultSet(showTimeseriesList, IoTDBMetadataResultSet.MetadataType.TIMESERIES);
        } catch (TException e) {
          throw new TException("Conncetion error when fetching timeseries metadata", e);
        }
      case Constant.COUNT_TIMESERIES:
        req = new TSFetchMetadataReq(Constant.GLOBAL_COUNT_TIMESERIES_REQ);
        req.setColumnPath(schemaPattern);
        try {
          TSFetchMetadataResp resp = client.fetchMetadata(req);
          try {
            RpcUtils.verifySuccess(resp.getStatus());
          } catch (IoTDBRPCException e) {
            throw new IoTDBSQLException(e.getMessage());
          }
          return new IoTDBMetadataResultSet(resp.getColumnsList().size(), IoTDBMetadataResultSet.MetadataType.COUNT_TIMESERIES);
        } catch (TException e) {
          throw new TException("Connection error when fetching timeseries metadata", e);
        }
      default:
        throw new SQLException(catalog + " is not supported. Please refer to the user guide"
            + " for more details.");
    }
  }

  public ResultSet getNodes(String catalog, String schemaPattern, String columnPattern,
      String devicePattern, String nodeLevel) throws SQLException {
    try {
      return getNodesFunc(catalog, nodeLevel);
    } catch (TException e) {
      boolean flag = connection.reconnect();
      this.client = connection.client;
      if (flag) {
        try {
          return getNodesFunc(catalog, nodeLevel);
        } catch (TException e2) {
          throw new SQLException(String.format("Fail to get columns catalog=%s, schemaPattern=%s,"
                  + " columnPattern=%s, devicePattern=%s, nodeLevel=%s after reconnecting."
                  + " please check server status",
              catalog, schemaPattern, columnPattern, devicePattern, nodeLevel));
        }
      } else {
        throw new SQLException(String.format(
            "Fail to reconnect to server when getting columns catalog=%s, schemaPattern=%s,"
                + " columnPattern=%s, devicePattern=%s, nodeLevel=%s after reconnecting. "
                + "please check server status",
            catalog, schemaPattern, columnPattern, devicePattern, nodeLevel));
      }
    }
  }

  private ResultSet getNodesFunc(String catalog, String nodeLevel) throws TException, SQLException {
    TSFetchMetadataReq req;
    switch (catalog) {
      case Constant.COUNT_NODES:
        req = new TSFetchMetadataReq(Constant.GLOBAL_COUNT_NODES_REQ);
        req.setNodeLevel(nodeLevel);
        try {
          TSFetchMetadataResp resp = client.fetchMetadata(req);
          try {
            RpcUtils.verifySuccess(resp.getStatus());
          } catch (IoTDBRPCException e) {
            throw new IoTDBSQLException(e.getMessage());
          }
          return new IoTDBMetadataResultSet(resp.getNodesList().size(), IoTDBMetadataResultSet.MetadataType.COUNT_NODES);
        } catch (TException e) {
          throw new TException("Conncetion error when fetching node metadata", e);
        }
      case Constant.COUNT_NODE_TIMESERIES:
        req = new TSFetchMetadataReq(Constant.GLOBAL_COUNT_NODE_TIMESERIES_REQ);
        req.setNodeLevel(nodeLevel);
        try {
          TSFetchMetadataResp resp = client.fetchMetadata(req);
          try {
            RpcUtils.verifySuccess(resp.getStatus());
          } catch (IoTDBRPCException e) {
            throw new IoTDBSQLException(e.getMessage());
          }
          return new IoTDBMetadataResultSet(resp.getNodeTimeseriesNum(), IoTDBMetadataResultSet.MetadataType.COUNT_NODE_TIMESERIES);
        } catch (TException e) {
          throw new TException("Conncetion error when fetching node metadata", e);
        }
      default:
        throw new SQLException(catalog + " is not supported. Please refer to the user guide"
            + " for more details.");
    }
  }

  @Override
  public boolean isWrapperFor(Class<?> arg0) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean allProceduresAreCallable() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean allTablesAreSelectable() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean autoCommitFailureClosesAllResultSets() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean dataDefinitionCausesTransactionCommit() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean dataDefinitionIgnoredInTransactions() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean deletesAreDetected(int arg0) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean doesMaxRowSizeIncludeBlobs() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean generatedKeyAlwaysReturned() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getAttributes(String arg0, String arg1, String arg2, String arg3)
      throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getBestRowIdentifier(String arg0, String arg1, String arg2, int arg3,
      boolean arg4)
      throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public String getCatalogSeparator() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public String getCatalogTerm() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getCatalogs() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getClientInfoProperties() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getColumnPrivileges(String arg0, String arg1, String arg2,
      String arg3) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public Connection getConnection() throws SQLException {
    return connection;
  }

  @Override
  public ResultSet getCrossReference(String arg0, String arg1, String arg2, String arg3,
      String arg4, String arg5)
      throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public int getDatabaseMajorVersion() throws SQLException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getDatabaseMinorVersion() throws SQLException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public String getDatabaseProductName() throws SQLException {
    return Constant.GLOBAL_DB_NAME;
  }

  @Override
  public String getDatabaseProductVersion() throws SQLException {
    return Constant.GLOBAL_DB_VERSION;
  }

  @Override
  public int getDefaultTransactionIsolation() throws SQLException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getDriverMajorVersion() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getDriverMinorVersion() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public String getDriverName() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public String getDriverVersion() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getExportedKeys(String arg0, String arg1, String arg2) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public String getExtraNameCharacters() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getFunctionColumns(String arg0, String arg1, String arg2, String arg3)
      throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getFunctions(String arg0, String arg1, String arg2) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public String getIdentifierQuoteString() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getImportedKeys(String arg0, String arg1, String arg2) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getIndexInfo(String arg0, String arg1, String arg2, boolean arg3, boolean arg4)
      throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public int getJDBCMajorVersion() throws SQLException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getJDBCMinorVersion() throws SQLException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getMaxBinaryLiteralLength() throws SQLException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getMaxCatalogNameLength() throws SQLException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getMaxCharLiteralLength() throws SQLException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getMaxColumnNameLength() throws SQLException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getMaxColumnsInGroupBy() throws SQLException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getMaxColumnsInIndex() throws SQLException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getMaxColumnsInOrderBy() throws SQLException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getMaxColumnsInSelect() throws SQLException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getMaxColumnsInTable() throws SQLException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getMaxConnections() throws SQLException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getMaxCursorNameLength() throws SQLException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getMaxIndexLength() throws SQLException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getMaxProcedureNameLength() throws SQLException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getMaxRowSize() throws SQLException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getMaxSchemaNameLength() throws SQLException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getMaxStatementLength() throws SQLException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getMaxStatements() throws SQLException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getMaxTableNameLength() throws SQLException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getMaxTablesInSelect() throws SQLException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getMaxUserNameLength() throws SQLException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public String getNumericFunctions() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getPrimaryKeys(String arg0, String arg1, String arg2) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getProcedureColumns(String arg0, String arg1, String arg2, String arg3)
      throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public String getProcedureTerm() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getProcedures(String arg0, String arg1, String arg2) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getPseudoColumns(String catalog, String schemaPattern, String tableNamePattern,
      String columnNamePattern) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public int getResultSetHoldability() throws SQLException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public RowIdLifetime getRowIdLifetime() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public String getSQLKeywords() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public int getSQLStateType() throws SQLException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public String getSchemaTerm() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getSchemas() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getSchemas(String catalog, String schemaPattern) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public String getSearchStringEscape() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public String getStringFunctions() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getSuperTables(String catalog, String schemaPattern, String tableNamePattern)
      throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getSuperTypes(String catalog, String schemaPattern, String typeNamePattern)
      throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public String getSystemFunctions() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getTablePrivileges(String catalog, String schemaPattern, String tableNamePattern)
      throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getTableTypes() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern,
      String[] types)
      throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public String getTimeDateFunctions() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getTypeInfo() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getUDTs(String catalog, String schemaPattern, String typeNamePattern,
      int[] types)
      throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public String getURL() throws SQLException {
    // TODO: Return the URL for this DBMS or null if it cannot be generated
    return null;
  }

  @Override
  public String getUserName() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getVersionColumns(String catalog, String schema, String table)
      throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean insertsAreDetected(int type) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean isCatalogAtStart() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean isReadOnly() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean locatorsUpdateCopy() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean nullPlusNonNullIsNull() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean nullsAreSortedAtEnd() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean nullsAreSortedAtStart() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean nullsAreSortedHigh() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean nullsAreSortedLow() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean othersDeletesAreVisible(int type) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean othersInsertsAreVisible(int type) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean othersUpdatesAreVisible(int type) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean ownDeletesAreVisible(int type) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean ownInsertsAreVisible(int type) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean ownUpdatesAreVisible(int type) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean storesLowerCaseIdentifiers() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean storesLowerCaseQuotedIdentifiers() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean storesMixedCaseIdentifiers() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean storesMixedCaseQuotedIdentifiers() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean storesUpperCaseIdentifiers() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean storesUpperCaseQuotedIdentifiers() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsANSI92EntryLevelSQL() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsANSI92FullSQL() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsANSI92IntermediateSQL() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsAlterTableWithAddColumn() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsAlterTableWithDropColumn() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsBatchUpdates() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsCatalogsInDataManipulation() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsCatalogsInIndexDefinitions() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsCatalogsInPrivilegeDefinitions() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsCatalogsInProcedureCalls() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsCatalogsInTableDefinitions() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsColumnAliasing() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsConvert() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsConvert(int fromType, int toType) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsCoreSQLGrammar() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsCorrelatedSubqueries() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsDataDefinitionAndDataManipulationTransactions() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsDataManipulationTransactionsOnly() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsDifferentTableCorrelationNames() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsExpressionsInOrderBy() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsExtendedSQLGrammar() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsFullOuterJoins() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsGetGeneratedKeys() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsGroupBy() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsGroupByBeyondSelect() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsGroupByUnrelated() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsIntegrityEnhancementFacility() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsLikeEscapeClause() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsLimitedOuterJoins() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsMinimumSQLGrammar() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsMixedCaseIdentifiers() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsMixedCaseQuotedIdentifiers() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsMultipleOpenResults() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsMultipleResultSets() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsMultipleTransactions() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsNamedParameters() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsNonNullableColumns() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsOpenCursorsAcrossCommit() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsOpenCursorsAcrossRollback() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsOpenStatementsAcrossCommit() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsOpenStatementsAcrossRollback() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsOrderByUnrelated() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsOuterJoins() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsPositionedDelete() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsPositionedUpdate() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsResultSetConcurrency(int type, int concurrency) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsResultSetHoldability(int holdability) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsResultSetType(int type) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsSavepoints() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsSchemasInDataManipulation() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsSchemasInIndexDefinitions() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsSchemasInPrivilegeDefinitions() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsSchemasInProcedureCalls() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsSchemasInTableDefinitions() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsSelectForUpdate() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsStatementPooling() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsStoredFunctionsUsingCallSyntax() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsStoredProcedures() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsSubqueriesInComparisons() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsSubqueriesInExists() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsSubqueriesInIns() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsSubqueriesInQuantifieds() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsTableCorrelationNames() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsTransactionIsolationLevel(int level) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsTransactions() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsUnion() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsUnionAll() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean updatesAreDetected(int type) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean usesLocalFilePerTable() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean usesLocalFiles() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  /**
   * @deprecated
   * recommend using getMetadataInJson() instead of toString()
   */
  @Deprecated
  @Override
  public String toString() {
    try {
      return getMetadataInJsonFunc();
    } catch (IoTDBSQLException e) {
      logger.error("Failed to fetch metadata in json because: ", e);
    } catch (TException e) {
      boolean flag = connection.reconnect();
      this.client = connection.client;
      if (flag) {
        try {
          return getMetadataInJsonFunc();
        } catch (TException e2) {
          logger.error("Fail to get all timeseries " + "info after reconnecting."
                  + " please check server status", e2);
        } catch (IoTDBSQLException e1) {
          // ignored
        }
      } else {
        logger.error("Fail to reconnect to server "
                + "when getting all timeseries info. please check server status");
      }
    }
    return null;
  }

  /*
   * recommend using getMetadataInJson() instead of toString()
   */
  public String getMetadataInJson() throws SQLException {
    try {
      return getMetadataInJsonFunc();
    } catch (TException e) {
      boolean flag = connection.reconnect();
      this.client = connection.client;
      if (flag) {
        try {
          return getMetadataInJsonFunc();
        } catch (TException e2) {
          throw new SQLException("Failed to fetch all metadata in json "
              + "after reconnecting. Please check the server status.");
        }
      } else {
        throw new SQLException("Failed to reconnect to the server "
            + "when fetching all metadata in json. Please check the server status.");
      }
    }
  }

  private String getMetadataInJsonFunc() throws TException, IoTDBSQLException {
    TSFetchMetadataReq req = new TSFetchMetadataReq("METADATA_IN_JSON");
    TSFetchMetadataResp resp;
    resp = client.fetchMetadata(req);
    try {
      RpcUtils.verifySuccess(resp.getStatus());
    } catch (IoTDBRPCException e) {
      throw new IoTDBSQLException(e.getMessage());
    }
    return resp.getMetadataInJson();
  }
}
