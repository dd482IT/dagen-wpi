package org.sqljson.result_types;

import java.util.Objects;

import org.checkerframework.checker.nullness.qual.Nullable;


public class TableExpressionProperty
{
   private final String name;
   private final @Nullable String tableExpression;
   private final @Nullable String specifiedSourceCodeFieldType;

   public TableExpressionProperty
      (
         String name,
         @Nullable String tableExpression,
         @Nullable String specifiedSourceCodeFieldType
      )
   {
      this.name = name;
      this.tableExpression = tableExpression;
      this.specifiedSourceCodeFieldType = specifiedSourceCodeFieldType;
   }

   public String getName() { return name; }

   public @Nullable String getTableExpression() { return tableExpression; }

   public @Nullable String getSpecifiedSourceCodeFieldType() { return specifiedSourceCodeFieldType; }

   @Override
   public boolean equals(@Nullable Object o)
   {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      var that = (TableExpressionProperty) o;
      return
         Objects.equals(tableExpression, that.tableExpression) &&
         Objects.equals(name, that.name) &&
         Objects.equals(specifiedSourceCodeFieldType, that.specifiedSourceCodeFieldType);
   }

   @Override
   public int hashCode()
   {
      return Objects.hash(tableExpression, name, specifiedSourceCodeFieldType);
   }

   @Override
   public String toString()
   {
      return "TableExpressionProperty{" +
         "fieldExpression=" + tableExpression +
         ", name=" + name +
         ", specifiedSourceCodeFieldType=" + specifiedSourceCodeFieldType +
         '}';
   }
}

