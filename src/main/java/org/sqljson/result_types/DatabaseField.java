package org.sqljson.result_types;

import java.util.*;

import org.checkerframework.checker.nullness.qual.Nullable;

import org.sqljson.dbmd.Field;


/// A field to be included as part of a generated type whose data source is a database column.
public class DatabaseField
{
   private final String name;
   private final int jdbcTypeCode;
   private final String databaseType;
   private final @Nullable Integer length;
   private final @Nullable Integer precision;
   private final @Nullable Integer fractionalDigits;
   private final @Nullable Boolean nullable;
   private final @Nullable String generatedFieldType;

   DatabaseField
      (
         String name,
         Field dbField,
         @Nullable String generatedFieldType
      )
   {
      this.name = name;
      this.jdbcTypeCode = dbField.getJdbcTypeCode();
      this.databaseType = dbField.getDatabaseType();
      this.length = dbField.getLength();
      this.precision = dbField.getPrecision();
      this.fractionalDigits = dbField.getFractionalDigits();
      this.nullable = dbField.getNullable();
      this.generatedFieldType = generatedFieldType;
   }

   private DatabaseField
      (
         String name,
         int jdbcTypeCode,
         String databaseType,
         @Nullable Integer length,
         @Nullable Integer precision,
         @Nullable Integer fractionalDigits,
         @Nullable Boolean nullable,
         @Nullable String generatedFieldType
      )
   {
      this.name = name;
      this.jdbcTypeCode = jdbcTypeCode;
      this.databaseType = databaseType;
      this.length = length;
      this.precision = precision;
      this.fractionalDigits = fractionalDigits;
      this.nullable = nullable;
      this.generatedFieldType = generatedFieldType;
   }

   public String getName() { return name; }
   public int getJdbcTypeCode() { return jdbcTypeCode; }
   public String getDatabaseType() { return databaseType; }
   public @Nullable Integer getLength() { return length; }
   public @Nullable Integer getPrecision() { return precision; }
   public @Nullable Integer getFractionalDigits() { return fractionalDigits; }
   public @Nullable Boolean getNullable() { return nullable; }
   public @Nullable String getGeneratedFieldType() { return generatedFieldType; }

   DatabaseField toNullable()
   {
      if ( nullable != null && nullable )
         return this;
      else
         return new DatabaseField(
            name, jdbcTypeCode, databaseType, length, precision, fractionalDigits, true, generatedFieldType
         );
   }

   @Override
   public boolean equals(@Nullable Object o)
   {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      DatabaseField that = (DatabaseField) o;
      return
         jdbcTypeCode == that.jdbcTypeCode &&
         name.equals(that.name) &&
         databaseType.equals(that.databaseType) &&
         Objects.equals(length, that.length) &&
         Objects.equals(precision, that.precision) &&
         Objects.equals(fractionalDigits, that.fractionalDigits) &&
         Objects.equals(nullable, that.nullable) &&
         Objects.equals(generatedFieldType, that.generatedFieldType);
   }

   @Override
   public int hashCode()
   {
      return Objects.hash(name, jdbcTypeCode, databaseType, length, precision, fractionalDigits, nullable, generatedFieldType);
   }

   @Override
   public String toString()
   {
      return "DatabaseField{" +
         "name='" + name + '\'' +
         ", jdbcTypeCode=" + jdbcTypeCode +
         ", databaseType='" + databaseType + '\'' +
         ", length=" + length +
         ", precision=" + precision +
         ", fractionalDigits=" + fractionalDigits +
         ", nullable=" + nullable +
         ", generatedFieldType=" + generatedFieldType +
         '}';
   }
}
