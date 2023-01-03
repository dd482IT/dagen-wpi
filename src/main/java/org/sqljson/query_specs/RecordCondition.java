package org.sqljson.query_specs;

import java.util.List;

import org.checkerframework.checker.nullness.qual.Nullable;


public class RecordCondition
{
   private final String sql;
   private final List<String> paramNames;
   private final String withTableAliasAs; // table alias variable used in sql

   private RecordCondition()
   {
      this.sql =  "";
      this.paramNames = null;
      this.withTableAliasAs = null;
   }

   public RecordCondition
      (
         String sql,
         List<String> paramNames,
         String withTableAliasAs
      )
   {
      this.sql = sql;
      this.paramNames = paramNames;
      this.withTableAliasAs = withTableAliasAs;
   }

   public String getSql() { return sql; }

   public List<String> getParamNames() { return paramNames; }

   public String getWithTableAliasAs() { return withTableAliasAs; }
}

