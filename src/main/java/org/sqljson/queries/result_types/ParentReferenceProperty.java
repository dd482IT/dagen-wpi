package org.sqljson.queries.result_types;

import java.util.Objects;

import org.checkerframework.checker.nullness.qual.Nullable;


public class ParentReferenceProperty
{
   String name;
   ResultType resultType;
   boolean nullable;

   public ParentReferenceProperty
      (
         String name,
         ResultType resultType,
         boolean nullable
      )
   {
      this.name = name;
      this.resultType = resultType;
      this.nullable = nullable;
   }

   public String getName() { return name; }

   public ResultType getGeneratedType() { return resultType; }

   public boolean isNullable() { return nullable; }

   public ParentReferenceProperty toNullable()
   {
      if ( nullable ) return this;
      else return new ParentReferenceProperty(name, resultType, true);
   }

   @Override
   public boolean equals(@Nullable Object o)
   {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ParentReferenceProperty that = (ParentReferenceProperty) o;
      return
         nullable == that.nullable &&
         name.equals(that.name) &&
         resultType.equals(that.resultType);
   }

   @Override
   public int hashCode()
   {
      return Objects.hash(name, resultType, nullable);
   }

   @Override
   public String toString()
   {
      return "ParentReferenceProperty{" +
         "name='" + name + '\'' +
         ", resultType=" + resultType +
         ", isNullable=" + nullable +
         '}';
   }
}

