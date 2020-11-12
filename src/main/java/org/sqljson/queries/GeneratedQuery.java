package org.sqljson.queries;

import java.util.*;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;

import org.checkerframework.checker.nullness.qual.Nullable;

import org.sqljson.queries.result_types.ResultType;
import org.sqljson.queries.specs.ResultsRepr;


public class GeneratedQuery
{
   private final String queryName;
   private final Map<ResultsRepr,String> generatedSqls;
   private final List<ResultType> generatedResultTypes;
   private final @Nullable String typesFileHeader;
   private final List<String> paramNames;

   public GeneratedQuery
      (
         String queryName,
         Map<ResultsRepr,String> generatedSqls,
         List<ResultType> generatedResultTypes,
         @Nullable String typesFileHeader,
         List<String> paramNames
      )
   {
      this.queryName = queryName;
      this.generatedSqls = unmodifiableMap(new HashMap<>(generatedSqls));
      this.generatedResultTypes = unmodifiableList(new ArrayList<>(generatedResultTypes));
      this.typesFileHeader = typesFileHeader;
      this.paramNames = unmodifiableList(new ArrayList<>(paramNames));
   }

   public String getQueryName() { return queryName; }

   public Set<ResultsRepr> getResultRepresentations() { return new HashSet<>(generatedSqls.keySet()); }

   public String getSql(ResultsRepr resultsRepr) { return requireNonNull(generatedSqls.get(resultsRepr)); }

   public Map<ResultsRepr,String> getGeneratedSqls() { return generatedSqls; }

   public List<ResultType> getGeneratedResultTypes() { return generatedResultTypes; }

   public @Nullable String getTypesFileHeader() { return typesFileHeader; }

   public List<String> getParamNames() { return paramNames; }
}

