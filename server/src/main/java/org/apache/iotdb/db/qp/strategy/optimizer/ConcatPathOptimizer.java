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
package org.apache.iotdb.db.qp.strategy.optimizer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import org.apache.iotdb.db.exception.MetadataErrorException;
import org.apache.iotdb.db.exception.qp.LogicalOperatorException;
import org.apache.iotdb.db.exception.qp.LogicalOptimizeException;
import org.apache.iotdb.db.qp.constant.SQLConstant;
import org.apache.iotdb.db.qp.executor.IQueryProcessExecutor;
import org.apache.iotdb.db.qp.logical.Operator;
import org.apache.iotdb.db.qp.logical.crud.BasicFunctionOperator;
import org.apache.iotdb.db.qp.logical.crud.FilterOperator;
import org.apache.iotdb.db.qp.logical.crud.FromOperator;
import org.apache.iotdb.db.qp.logical.crud.QueryOperator;
import org.apache.iotdb.db.qp.logical.crud.SFWOperator;
import org.apache.iotdb.db.qp.logical.crud.SelectOperator;
import org.apache.iotdb.tsfile.read.common.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * concat paths in select and from clause.
 */
public class ConcatPathOptimizer implements ILogicalOptimizer {

  private static final Logger LOG = LoggerFactory.getLogger(ConcatPathOptimizer.class);
  private static final String WARNING_NO_SUFFIX_PATHS = "given SFWOperator doesn't have suffix paths, cannot concat seriesPath";

  private IQueryProcessExecutor executor;

  public ConcatPathOptimizer(IQueryProcessExecutor executor) {
    this.executor = executor;
  }

  @Override
  public Operator transform(Operator operator) throws LogicalOptimizeException {
    if (!(operator instanceof SFWOperator)) {
      LOG.warn("given operator isn't SFWOperator, cannot concat seriesPath");
      return operator;
    }
    SFWOperator sfwOperator = (SFWOperator) operator;
    FromOperator from = sfwOperator.getFromOperator();
    List<Path> prefixPaths;
    if (from == null) {
      LOG.warn("given SFWOperator doesn't have prefix paths, cannot concat seriesPath");
      return operator;
    } else {
      prefixPaths = from.getPrefixPaths();
      if (prefixPaths.isEmpty()) {
        LOG.warn("given SFWOperator doesn't have prefix paths, cannot concat seriesPath");
        return operator;
      }
    }
    SelectOperator select = sfwOperator.getSelectOperator();
    List<Path> initialSuffixPaths;
    if (select == null) {
      LOG.warn(WARNING_NO_SUFFIX_PATHS);
      return operator;
    } else {
      initialSuffixPaths = select.getSuffixPaths();
      if (initialSuffixPaths.isEmpty()) {
        LOG.warn(WARNING_NO_SUFFIX_PATHS);
        return operator;
      }
    }

    concatSelect(prefixPaths, select); // concat select paths

    if (operator instanceof QueryOperator && ((QueryOperator) operator).hasSlimit()) {
      checkSlimitUsageConstraint(select, initialSuffixPaths, prefixPaths);

      // Make 'SLIMIT&SOFFSET' take effect by trimming the suffixList
      // and aggregations of the selectOperator
      int seriesLimit = ((QueryOperator) operator).getSeriesLimit();
      int seriesOffset = ((QueryOperator) operator).getSeriesOffset();
      slimitTrim(select, seriesLimit, seriesOffset);
    }

    // concat filter
    FilterOperator filter = sfwOperator.getFilterOperator();
    if (filter == null) {
      return operator;
    }
    sfwOperator.setFilterOperator(concatFilter(prefixPaths, filter));
    return sfwOperator;
  }

  private List<Path> judgeSelectOperator(SelectOperator selectOperator)
      throws LogicalOptimizeException {
    List<Path> suffixPaths;
    if (selectOperator == null) {
      throw new LogicalOptimizeException(WARNING_NO_SUFFIX_PATHS);
    } else {
      suffixPaths = selectOperator.getSuffixPaths();
      if (suffixPaths.isEmpty()) {
        throw new LogicalOptimizeException(WARNING_NO_SUFFIX_PATHS);
      }
    }
    return suffixPaths;
  }

  private void checkAggrOfSelectOperator(SelectOperator selectOperator)
      throws LogicalOptimizeException {
    if (!selectOperator.getAggregations().isEmpty()
        && selectOperator.getSuffixPaths().size() != selectOperator.getAggregations().size()) {
      throw new LogicalOptimizeException(
          "Common queries and aggregated queries are not allowed to appear at the same time");
    }
  }

  private void extendListSafely(List<String> source, int index, List<String> target) {
    if (source != null && !source.isEmpty()) {
      target.add(source.get(index));
    }
  }

  /**
   * Extract paths from select&from cql, expand them into complete versions, and reassign them to
   * selectOperator's suffixPathList. Treat aggregations similarly.
   */
  private void concatSelect(List<Path> fromPaths, SelectOperator selectOperator)
      throws LogicalOptimizeException {
    List<Path> suffixPaths = judgeSelectOperator(selectOperator);
    checkAggrOfSelectOperator(selectOperator);

    List<Path> allPaths = new ArrayList<>();
    List<String> originAggregations = selectOperator.getAggregations();
    List<String> afterConcatAggregations = new ArrayList<>();

    for (int i = 0; i < suffixPaths.size(); i++) {
      // selectPath cannot start with ROOT, which is guaranteed by TSParser
      Path selectPath = suffixPaths.get(i);
      for (Path fromPath : fromPaths) {
        if (!fromPath.startWith(SQLConstant.ROOT)) {
          throw new LogicalOptimizeException("illegal from clause : " + fromPath.getFullPath());
        }
        allPaths.add(Path.addPrefixPath(selectPath, fromPath));
        extendListSafely(originAggregations, i, afterConcatAggregations);
      }
    }

    removeStarsInPath(allPaths, afterConcatAggregations, selectOperator);
  }

  private boolean isWithStar(List<Path> suffixPaths, List<Path> prefixPaths, List<Path> fakePaths) {
    boolean isWithStar = false;
    for (Iterator<Path> iterSuffix = suffixPaths.iterator();
        iterSuffix.hasNext() && !isWithStar; ) {
      // NOTE the traversal order should keep consistent with that of the `transformedPaths`
      Path suffixPath = iterSuffix.next();
      if (suffixPath.getFullPath().contains("*")) {
        isWithStar = true; // the case of 3)seriesPath with stars
        break;
      }
      for (Iterator<Path> iterPrefix = prefixPaths.iterator(); iterPrefix.hasNext(); ) {
        Path fakePath = new Path(iterPrefix.next() + "." + suffixPath);
        if (fakePath.getFullPath().contains("*")) {
          isWithStar = true; // the case of 3)seriesPath with stars
        } else {
          fakePaths.add(fakePath);
        }
      }
    }
    return isWithStar;
  }

  /**
   * Check whether SLIMIT is wrongly used with complete paths and throw an exception if it is.
   * Considering a query seriesPath, there are three types: 1)complete seriesPath, 2)prefix
   * seriesPath, 3)seriesPath with stars. And SLIMIT is designed to be used with 2) or 3). In
   * another word, SLIMIT is not allowed to be used with 1).
   */
  private void checkSlimitUsageConstraint(SelectOperator selectOperator,
      List<Path> initialSuffixPaths,
      List<Path> prefixPaths) throws LogicalOptimizeException {
    List<Path> transformedPaths = selectOperator.getSuffixPaths();

    List<Path> fakePaths = new ArrayList<>();
    boolean isWithStar = isWithStar(initialSuffixPaths, prefixPaths, fakePaths);
    if (!isWithStar) {
      int sz = fakePaths.size();
      if (sz == transformedPaths.size()) {
        int i = 0;
        for (; i < sz; i++) {
          if (!fakePaths.get(i).getFullPath().equals(transformedPaths.get(i).getFullPath())) {
            break; // the case of 2)prefix seriesPath
          }
        }

        if (i
            >= sz) { // the case of 1)complete seriesPath, i.e.,
          // SLIMIT is wrongly used with complete paths
          throw new LogicalOptimizeException(
              "Wrong use of SLIMIT: SLIMIT is not allowed to be used with complete paths.");
        }
      }
    }
  }

  /**
   * Make 'SLIMIT&SOFFSET' take effect by trimming the suffixList and aggregations of the
   * selectOperator.
   *
   * @param seriesLimit is ensured to be positive integer
   * @param seriesOffset is ensured to be non-negative integer
   */
  public void slimitTrim(SelectOperator select, int seriesLimit, int seriesOffset)
      throws LogicalOptimizeException {
    List<Path> suffixList = select.getSuffixPaths();
    List<String> aggregations = select.getAggregations();
    int size = suffixList.size();

    // check parameter range
    if (seriesOffset >= size) {
      throw new LogicalOptimizeException("SOFFSET <SOFFSETValue>: SOFFSETValue exceeds the range.");
    }
    int endPosition = seriesOffset + seriesLimit;
    if (endPosition > size) {
      endPosition = size;
    }

    // trim seriesPath list
    List<Path> trimedSuffixList = new ArrayList<>(suffixList.subList(seriesOffset, endPosition));
    select.setSuffixPathList(trimedSuffixList);

    // trim aggregations if exists
    if (aggregations != null && !aggregations.isEmpty()) {
      List<String> trimedAggregations = new ArrayList<>(
          aggregations.subList(seriesOffset, endPosition));
      select.setAggregations(trimedAggregations);
    }
  }

  private FilterOperator concatFilter(List<Path> fromPaths, FilterOperator operator)
      throws LogicalOptimizeException {
    if (!operator.isLeaf()) {
      List<FilterOperator> newFilterList = new ArrayList<>();
      for (FilterOperator child : operator.getChildren()) {
        newFilterList.add(concatFilter(fromPaths, child));
      }
      operator.setChildren(newFilterList);
      return operator;
    }
    BasicFunctionOperator basicOperator = (BasicFunctionOperator) operator;
    Path filterPath = basicOperator.getSinglePath();
    // do nothing in the cases of "where time > 5" or "where root.d1.s1 > 5"
    if (SQLConstant.isReservedPath(filterPath) || filterPath.startWith(SQLConstant.ROOT)) {
      return operator;
    }
    List<Path> concatPaths = new ArrayList<>();
    fromPaths.forEach(fromPath -> concatPaths.add(Path.addPrefixPath(filterPath, fromPath)));
    List<Path> noStarPaths = removeStarsInPathWithUnique(concatPaths);
    if (noStarPaths.size() == 1) {
      // Transform "select s1 from root.car.* where s1 > 10" to
      // "select s1 from root.car.* where root.car.*.s1 > 10"
      basicOperator.setSinglePath(noStarPaths.get(0));
      return operator;
    } else {
      // Transform "select s1 from root.car.d1, root.car.d2 where s1 > 10" to
      // "select s1 from root.car.d1, root.car.d2 where root.car.d1.s1 > 10 and root.car.d2.s1 > 10"
      // Note that,
      // two fork tree has to be maintained while removing stars in paths for DnfFilterOptimizer
      // requirement.
      return constructTwoForkFilterTreeWithAnd(noStarPaths, operator);
    }
  }

  private FilterOperator constructTwoForkFilterTreeWithAnd(List<Path> noStarPaths,
      FilterOperator operator)
      throws LogicalOptimizeException {
    FilterOperator filterTwoFolkTree = new FilterOperator(SQLConstant.KW_AND);
    FilterOperator currentNode = filterTwoFolkTree;
    for (int i = 0; i < noStarPaths.size(); i++) {
      if (i > 0 && i < noStarPaths.size() - 1) {
        FilterOperator newInnerNode = new FilterOperator(SQLConstant.KW_AND);
        currentNode.addChildOperator(newInnerNode);
        currentNode = newInnerNode;
      }
      try {
        currentNode.addChildOperator(
            new BasicFunctionOperator(operator.getTokenIntType(), noStarPaths.get(i),
                ((BasicFunctionOperator) operator).getValue()));
      } catch (LogicalOperatorException e) {
        throw new LogicalOptimizeException(e);
      }
    }
    return filterTwoFolkTree;
  }

  /**
   * replace "*" by actual paths.
   *
   * @param paths list of paths which may contain stars
   * @return a unique seriesPath list
   */
  private List<Path> removeStarsInPathWithUnique(List<Path> paths) throws LogicalOptimizeException {
    List<Path> retPaths = new ArrayList<>();
    LinkedHashMap<String, Integer> pathMap = new LinkedHashMap<>();
    try {
      for (Path path : paths) {
        List<String> all;
        all = executor.getAllPaths(path.getFullPath());
        for (String subPath : all) {
          if (!pathMap.containsKey(subPath)) {
            pathMap.put(subPath, 1);
          }
        }
      }
      for (String pathStr : pathMap.keySet()) {
        retPaths.add(new Path(pathStr));
      }
    } catch (MetadataErrorException e) {
      throw new LogicalOptimizeException("error when remove star: ", e);
    }
    return retPaths;
  }

  private void removeStarsInPath(List<Path> paths, List<String> afterConcatAggregations,
      SelectOperator selectOperator) throws LogicalOptimizeException {
    List<Path> retPaths = new ArrayList<>();
    List<String> newAggregations = new ArrayList<>();
    for (int i = 0; i < paths.size(); i++) {
      try {
        List<String> actualPaths = executor.getAllPaths(paths.get(i).getFullPath());
        for (String actualPath : actualPaths) {
          retPaths.add(new Path(actualPath));
          if (afterConcatAggregations != null && !afterConcatAggregations.isEmpty()) {
            newAggregations.add(afterConcatAggregations.get(i));
          }
        }
      } catch (MetadataErrorException e) {
        throw new LogicalOptimizeException("error when remove star: ", e);
      }
    }
    if (retPaths.isEmpty()) {
      throw new LogicalOptimizeException("do not select any existing series");
    }
    selectOperator.setSuffixPathList(retPaths);
    selectOperator.setAggregations(newAggregations);
  }
}
