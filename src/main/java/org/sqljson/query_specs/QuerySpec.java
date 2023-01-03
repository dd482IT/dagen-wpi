package org.sqljson.query_specs;

import java.util.ArrayList;
import java.util.List;
import static java.util.Collections.*;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import static org.sqljson.util.Nullables.valueOr;
import static org.sqljson.query_specs.ResultRepr.JSON_OBJECT_ROWS;


public final class QuerySpec
{
   private final String queryName;
   private final TableJsonSpec tableJson;
   private final List<ResultRepr> resultRepresentations;
   private final Boolean generateResultTypes;
   private final Boolean generateSource; // Contains at least the resource name for generated SQL, if not result types.
   private final PropertyNameDefault propertyNameDefault; // inherited from query group spec if empty
   private final String orderBy;
   private final Boolean forUpdate;
   private final String typesFileHeader;

   private QuerySpec()
   {
      this.queryName = "";
      this.tableJson = new TableJsonSpec();
      this.resultRepresentations = singletonList(JSON_OBJECT_ROWS);
      this.generateResultTypes = true;
      this.generateSource = true;
      this.propertyNameDefault = null;
      this.orderBy = null;
      this.forUpdate = false;
      this.typesFileHeader = null;
   }

   public QuerySpec
      (
         String queryName,
         TableJsonSpec tableJson,
         List<ResultRepr> resultRepresentations,
         Boolean generateResultTypes,
         Boolean generateSource,
         PropertyNameDefault propertyNameDefault,
         String orderBy,
         Boolean forUpdate,
         String typesFileHeader
      )
   {
      this.queryName = queryName;
      this.resultRepresentations = resultRepresentations != null ?
         unmodifiableList(new ArrayList<>(resultRepresentations))
         : singletonList(JSON_OBJECT_ROWS);
      this.generateResultTypes = generateResultTypes;
      this.generateSource = generateSource;
      this.propertyNameDefault = propertyNameDefault;
      this.tableJson = tableJson;
      this.orderBy = orderBy;
      this.forUpdate = forUpdate;
      this.typesFileHeader = typesFileHeader;
      if ( valueOr(generateResultTypes, true) && !valueOr(generateSource, true) )
         throw new RuntimeException(
            "In query \"" + queryName + "\", cannot generate result types without " +
            "generateSource option enabled."
         );
   }

   public String getQueryName() { return queryName; }

   /// Generates a SQL query for each of the specified result representations.
   public List<ResultRepr> getResultRepresentations() { return resultRepresentations; }

   public List<ResultRepr> getResultRepresentationsList()
   {
      return resultRepresentations != null ? resultRepresentations : singletonList(JSON_OBJECT_ROWS);
   }

   public Boolean getGenerateResultTypes() { return generateResultTypes; }

   public boolean getGenerateResultTypesOrDefault()
   {
      return generateResultTypes != null ? generateResultTypes: true;
   }

   public Boolean getGenerateSource() { return generateSource; }

   public boolean getGenerateSourceOrDefault()
   {
      return generateSource != null ? generateSource: true;
   }

   public PropertyNameDefault getPropertyNameDefault() { return propertyNameDefault; }

   public TableJsonSpec getTableJson() { return tableJson; }

   public String getOrderBy() { return orderBy; }

   public Boolean getForUpdate() { return forUpdate; }

   public boolean getForUpdateOrDefault()
   {
      return forUpdate != null ? forUpdate : false;
   }

   public String getTypesFileHeader() { return typesFileHeader; }
}
