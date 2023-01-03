package org.sqljson.result_types;

import java.util.Objects;

import org.checkerframework.checker.nullness.qual.Nullable;


public class ChildCollectionProperty
{
   String name;
   ResultType resultType;
   boolean nullable;

   public ChildCollectionProperty
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

   public ChildCollectionProperty toNullable()
   {
      if ( nullable ) return this;
      else return new ChildCollectionProperty(name, resultType, true);
   }

   @Override
   public boolean equals(Object o)
   {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      var that = (ChildCollectionProperty) o;
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
      return "ChildCollectionProperty{" +
         "name='" + name + '\'' +
         ", resultType=" + resultType +
         ", nullable=" + nullable +
         '}';
   }
}
