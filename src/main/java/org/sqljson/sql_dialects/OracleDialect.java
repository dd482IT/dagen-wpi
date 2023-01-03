package org.sqljson.sql_dialects;

import java.util.List;
import static java.util.stream.Collectors.joining;

import org.checkerframework.checker.nullness.qual.Nullable;

import static org.sqljson.util.StringFuns.*;


public class OracleDialect implements SqlDialect
{
   private final int indentSpaces;

   public OracleDialect(int indentSpaces)
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
         .map(colName -> "'" + unDoubleQuote(colName) + "' value " + fromAlias + "." + colName)
         .collect(joining(",\n"));

      return
         "json_object(\n" +
            indentLines(objectFieldDecls, indentSpaces) + "\n" +
            "  returning clob\n" +
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
         "treat(coalesce(json_arrayagg(" +
            getRowObjectExpression(columnNames, fromAlias) +
            (orderBy != null ? " order by " + orderBy.replace("$$", fromAlias) : "") +
            " returning clob" +
         "), to_clob('[]')) as json)";
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
         "treat(coalesce(json_arrayagg(" +
            fromAlias + "." + columnName +
            (orderBy != null ? " order by " + orderBy.replace("$$", fromAlias) : "") +
            " returning clob" +
         "), to_clob('[]')) as json)";
   }
}

