package org.sqljson.result_types;

import java.util.*;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;

import org.checkerframework.checker.nullness.qual.Nullable;


public class ResultType
{
   private final String typeName; // always unqualified by module/package
   private final List<SimpleTableFieldProperty> simpleTableFieldProperties;
   private final List<TableExpressionProperty> tableExpressionProperties;
   private final List<ChildCollectionProperty> childCollectionProperties;
   private final List<ParentReferenceProperty> parentReferenceProperties;
   // NOTE: Fields from inline parents are included in the above.
   private final boolean unwrapped;

   ResultType
      (
         String typeName,
         List<SimpleTableFieldProperty> simpleTableFieldProperties,
         List<TableExpressionProperty> tableExpressionProperties,
         List<ChildCollectionProperty> childCollectionProperties,
         List<ParentReferenceProperty> parentReferenceProperties
      )
   {
       this(
          typeName,
          simpleTableFieldProperties,
          tableExpressionProperties,
          childCollectionProperties,
          parentReferenceProperties,
          false
       );
   }

   ResultType
      (
         String typeName,
         List<SimpleTableFieldProperty> simpleTableFieldProperties,
         List<TableExpressionProperty> tableExpressionProperties,
         List<ChildCollectionProperty> childCollectionProperties,
         List<ParentReferenceProperty> parentReferenceProperties,
         boolean unwrapped
      )
   {
      this.typeName = typeName;
      this.simpleTableFieldProperties = unmodifiableList(new ArrayList<>(simpleTableFieldProperties));
      this.tableExpressionProperties = unmodifiableList(new ArrayList<>(tableExpressionProperties));
      this.childCollectionProperties = unmodifiableList(new ArrayList<>(childCollectionProperties));
      this.parentReferenceProperties = unmodifiableList(new ArrayList<>(parentReferenceProperties));
      this.unwrapped = unwrapped;
   }

   public String getTypeName() { return typeName; }

   public List<SimpleTableFieldProperty> getSimpleTableFieldProperties() { return simpleTableFieldProperties; }

   /// Get nullable variants of the database fields.
   public List<SimpleTableFieldProperty> getSimpleTableFieldPropertiesNullable()
   {
      return simpleTableFieldProperties.stream().map(SimpleTableFieldProperty::toNullable).collect(toList());
   }

   public List<TableExpressionProperty> getTableExpressionProperties() { return tableExpressionProperties; }

   public List<ChildCollectionProperty> getChildCollectionProperties() { return childCollectionProperties; }

   /// Get nullable variants of the child collection fields.
   public List<ChildCollectionProperty> getChildCollectionPropertiesNullable()
   {
      return childCollectionProperties.stream().map(ChildCollectionProperty::toNullable).collect(toList());
   }

   public List<ParentReferenceProperty> getParentReferenceProperties() { return parentReferenceProperties; }

   /// Get nullable variants of the parent reference fields.
   public List<ParentReferenceProperty> getParentReferencePropertiesNullable()
   {
      return parentReferenceProperties.stream().map(ParentReferenceProperty::toNullable).collect(toList());
   }

   public boolean isUnwrapped() { return unwrapped; }

   public ResultType withUnwrapped(boolean unwrap)
   {
      if ( unwrap == this.unwrapped )
         return this;
      else
         return new ResultType(typeName, simpleTableFieldProperties, tableExpressionProperties, childCollectionProperties, parentReferenceProperties, unwrap);
   }

   public ResultType withTypeName(String newTypeName)
   {
      return new ResultType(newTypeName, simpleTableFieldProperties, tableExpressionProperties, childCollectionProperties, parentReferenceProperties, unwrapped);
   }

   public int getFieldsCount()
   {
      return simpleTableFieldProperties.size() + tableExpressionProperties.size() + childCollectionProperties.size() + parentReferenceProperties.size();
   }

   public boolean equalsIgnoringName(ResultType that)
   {
      return
         simpleTableFieldProperties.equals(that.simpleTableFieldProperties) &&
         tableExpressionProperties.equals(that.tableExpressionProperties) &&
         childCollectionProperties.equals(that.childCollectionProperties) &&
         parentReferenceProperties.equals(that.parentReferenceProperties) &&
         unwrapped == that.unwrapped;
   }

   @Override
   public boolean equals(Object o)
   {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ResultType that = (ResultType) o;
      return
         typeName.equals(that.typeName) &&
         simpleTableFieldProperties.equals(that.simpleTableFieldProperties) &&
         tableExpressionProperties.equals(that.tableExpressionProperties) &&
         childCollectionProperties.equals(that.childCollectionProperties) &&
         parentReferenceProperties.equals(that.parentReferenceProperties) &&
         unwrapped == that.unwrapped;

   }

   @Override
   public int hashCode()
   {
      return Objects.hash(typeName, simpleTableFieldProperties, tableExpressionProperties, childCollectionProperties, parentReferenceProperties, unwrapped);
   }

   @Override
   public String toString()
   {
      return "ResultType{" +
         "typeName='" + typeName + '\'' +
         ", simpleTableFieldProperties=" + simpleTableFieldProperties +
         ", tableExpressionProperties=" + tableExpressionProperties +
         ", childCollectionProperties=" + childCollectionProperties +
         ", parentReferenceProperties=" + parentReferenceProperties +
         ", unwrapped=" + unwrapped +
         '}';
   }
}

