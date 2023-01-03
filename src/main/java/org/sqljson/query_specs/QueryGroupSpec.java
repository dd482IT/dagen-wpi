package org.sqljson.query_specs;

import java.util.ArrayList;
import java.util.List;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;

import org.checkerframework.checker.nullness.qual.Nullable;

import static org.sqljson.query_specs.PropertyNameDefault.CAMELCASE;


public final class QueryGroupSpec
{
   private String defaultSchema = null;
   private PropertyNameDefault propertyNameDefault = CAMELCASE;
   private List<String> generateUnqualifiedNamesForSchemas = emptyList();
   private List<QuerySpec> querySpecs = emptyList();

   private QueryGroupSpec() {}

   public QueryGroupSpec
      (
         String defaultSchema,
         PropertyNameDefault propertyNameDefault,
         List<String> generateUnqualifiedNamesForSchemas,
         List<QuerySpec> querySpecs
      )
   {
      this.defaultSchema = defaultSchema;
      this.propertyNameDefault = propertyNameDefault;
      this.generateUnqualifiedNamesForSchemas = generateUnqualifiedNamesForSchemas;
      this.querySpecs = unmodifiableList(new ArrayList<>(querySpecs));
   }

   public String getDefaultSchema() { return defaultSchema; }

   public PropertyNameDefault getPropertyNameDefault() { return propertyNameDefault; }

   public List<String> getGenerateUnqualifiedNamesForSchemas() { return generateUnqualifiedNamesForSchemas; }

   public List<QuerySpec> getQuerySpecs() { return querySpecs; }
}

