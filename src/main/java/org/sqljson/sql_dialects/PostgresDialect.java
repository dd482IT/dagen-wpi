package org.sqljson.sql_dialects;

import java.util.List;
import static java.util.stream.Collectors.joining;

import org.checkerframework.checker.nullness.qual.Nullable;

import org.sqljson.util.StringFuns;


public class PostgresDialect implements SqlDialect
{
   private final int indentSpaces;

   public PostgresDialect(int indentSpaces)
   {
      this.indentSpaces = indentSpaces;
   }

   @Override
   public String getRowObjectExpression
      (
         List<String> columnNames,
         String fromAlias
      )
   {
      String objectFieldDecls =
         columnNames.stream()
         .map(colName -> "'" + StringFuns.unDoubleQuote(colName) + "', " + fromAlias + "." + colName)
         .collect(joining(",\n"));

         return
            "jsonb_build_object(\n" +
               StringFuns.indentLines(objectFieldDecls, indentSpaces) + "\n" +
            ")";
   }

   @Override
   public String getAggregatedRowObjectsExpression
      (
         List<String> columnNames,
         String orderBy,
         String fromAlias
      )
   {

      return
         "coalesce(jsonb_agg(" +
            getRowObjectExpression(columnNames, fromAlias) +
            (orderBy != null ? " order by " + orderBy.replace("$$", fromAlias) : "") +
         "),'[]'::jsonb)";
   }

   @Override
   public String getAggregatedColumnValuesExpression
      (
          String columnName,
          String orderBy,
          String fromAlias
      )
   {
      return
         "coalesce(jsonb_agg(" +
            fromAlias + "." + columnName +
            (orderBy != null ? " order by " + orderBy.replace("$$", fromAlias) : "") +
         "))";
   }
}

