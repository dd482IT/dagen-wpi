package org.sqljson.queries.result_types;

import java.util.*;

import org.checkerframework.checker.nullness.qual.Nullable;

import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;


public class GeneratedType
{
   private final String typeName; // always unqualified by module/package
   private final List<DatabaseField> databaseFields;
   private final List<ExpressionField> expressionFields;
   private final List<ChildCollectionField> childCollectionFields;
   private final List<ParentReferenceField> parentReferenceFields;
   // NOTE: Fields from inline parents are included in the above.
   private final boolean unwrapped;

   GeneratedType
      (
         String typeName,
         List<DatabaseField> databaseFields,
         List<ExpressionField> expressionFields,
         List<ChildCollectionField> childCollectionFields,
         List<ParentReferenceField> parentReferenceFields
      )
   {
       this(
          typeName,
          databaseFields,
          expressionFields,
          childCollectionFields,
          parentReferenceFields,
          false
       );
   }

   GeneratedType
      (
         String typeName,
         List<DatabaseField> databaseFields,
         List<ExpressionField> expressionFields,
         List<ChildCollectionField> childCollectionFields,
         List<ParentReferenceField> parentReferenceFields,
         boolean unwrapped
      )
   {
      this.typeName = typeName;
      this.databaseFields = unmodifiableList(new ArrayList<>(databaseFields));
      this.expressionFields = unmodifiableList(new ArrayList<>(expressionFields));
      this.childCollectionFields = unmodifiableList(new ArrayList<>(childCollectionFields));
      this.parentReferenceFields = unmodifiableList(new ArrayList<>(parentReferenceFields));
      this.unwrapped = unwrapped;
   }

   public String getTypeName() { return typeName; }

   public List<DatabaseField> getDatabaseFields() { return databaseFields; }

   /// Get nullable variants of the database fields.
   public List<DatabaseField> getDatabaseFieldsNullable()
   {
      return databaseFields.stream().map(DatabaseField::toNullable).collect(toList());
   }

   public List<ExpressionField> getExpressionFields() { return expressionFields; }

   public List<ChildCollectionField> getChildCollectionFields() { return childCollectionFields; }

   /// Get nullable variants of the child collection fields.
   public List<ChildCollectionField> getChildCollectionFieldsNullable()
   {
      return childCollectionFields.stream().map(ChildCollectionField::toNullable).collect(toList());
   }

   public List<ParentReferenceField> getParentReferenceFields() { return parentReferenceFields; }

   /// Get nullable variants of the parent reference fields.
   public List<ParentReferenceField> getParentReferenceFieldsNullable()
   {
      return parentReferenceFields.stream().map(ParentReferenceField::toNullable).collect(toList());
   }

   public boolean isUnwrapped() { return unwrapped; }

   public GeneratedType withUnwrapped(boolean unwrap)
   {
      if ( unwrap == this.unwrapped )
         return this;
      else
         return new GeneratedType(typeName, databaseFields, expressionFields, childCollectionFields, parentReferenceFields, unwrap);
   }

   public int getFieldsCount()
   {
      return databaseFields.size() + expressionFields.size() + childCollectionFields.size() + parentReferenceFields.size();
   }

   public boolean equalsIgnoringName(GeneratedType that)
   {
      return
         databaseFields.equals(that.databaseFields) &&
         expressionFields.equals(that.expressionFields) &&
         childCollectionFields.equals(that.childCollectionFields) &&
         parentReferenceFields.equals(that.parentReferenceFields) &&
         unwrapped == that.unwrapped;
   }

   @Override
   public boolean equals(@Nullable Object o)
   {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      GeneratedType that = (GeneratedType) o;
      return
         typeName.equals(that.typeName) &&
         databaseFields.equals(that.databaseFields) &&
         expressionFields.equals(that.expressionFields) &&
         childCollectionFields.equals(that.childCollectionFields) &&
         parentReferenceFields.equals(that.parentReferenceFields) &&
         unwrapped == that.unwrapped;

   }

   @Override
   public int hashCode()
   {
      return Objects.hash(typeName, databaseFields, expressionFields, childCollectionFields, parentReferenceFields, unwrapped);
   }

   @Override
   public String toString()
   {
      return "GeneratedType{" +
         "typeName='" + typeName + '\'' +
         ", databaseFields=" + databaseFields +
         ", expressionFields=" + expressionFields +
         ", childCollectionFields=" + childCollectionFields +
         ", parentReferenceFields=" + parentReferenceFields +
         ", unwrapped=" + unwrapped +
         '}';
   }
}
