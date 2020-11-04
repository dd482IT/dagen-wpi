package org.sqljson.common.sql_dialects;

import java.util.List;

import org.sqljson.dbmd.DatabaseMetadata;
import org.sqljson.queries.sql.ColumnMetadata;


public interface SqlDialect
{
   enum DbmsType { PG, ORA, ISO }

   String getRowObjectExpression
      (
         List<ColumnMetadata> columnMetadatas,
         String fromAlias
      );

   /// Select expression part of a simple aggregate objects query. This expression should be an aggregate function
   /// which builds an array of json objects from a source relation having the given column names and table alias.
   String getAggregatedRowObjectsExpression
      (
         List<ColumnMetadata> columnMetadatas,
         String fromAlias
      );

   String getAggregatedColumnValuesExpression
      (
         ColumnMetadata columnMetadata,
         String fromAlias
      );

   static SqlDialect fromDatabaseMetadata
      (
         DatabaseMetadata dbmd,
         int indentSpaces
      )
   {
      DbmsType dbmsType = getDbmsType(dbmd.getDbmsName());
      switch ( dbmsType )
      {
         case PG: return new PostgresDialect(indentSpaces);
         case ORA: return new OracleDialect(indentSpaces);
         default: throw new RuntimeException("dbms type " + dbmsType + " is currently not supported");
      }
   }

   static DbmsType getDbmsType(String dbmsName)
   {
      String dbmsLower = dbmsName.toLowerCase();
      if ( dbmsLower.contains("postgres") ) return DbmsType.PG;
      else if ( dbmsLower.contains("oracle") ) return DbmsType.ORA;
      else return DbmsType.ISO;
   }
}
