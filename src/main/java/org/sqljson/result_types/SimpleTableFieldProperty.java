package org.sqljson.result_types;

import java.util.*;

import org.checkerframework.checker.nullness.qual.Nullable;

import org.sqljson.dbmd.Field;


/// A field to be included as part of a result type whose data source is a database column.
public class SimpleTableFieldProperty
{
   private final String name;
   private final int jdbcTypeCode;
   private final String databaseType;
   private final Integer length;
   private final Integer precision;
   private final Integer fractionalDigits;
   private final Boolean nullable;
   private final String specifiedSourceCodeFieldType;

   SimpleTableFieldProperty
      (
         String name,
         Field dbField,
         String specifiedSourceCodeFieldType
      )
   {
      this.name = name;
      this.jdbcTypeCode = dbField.getJdbcTypeCode();
      this.databaseType = dbField.getDatabaseType();
      this.length = dbField.getLength();
      this.precision = dbField.getPrecision();
      this.fractionalDigits = dbField.getFractionalDigits();
      this.nullable = dbField.getNullable();
      this.specifiedSourceCodeFieldType = specifiedSourceCodeFieldType;
   }

   private SimpleTableFieldProperty
      (
         String name,
         int jdbcTypeCode,
         String databaseType,
         Integer length,
         Integer precision,
         Integer fractionalDigits,
         Boolean nullable,
         String specifiedSourceCodeFieldType
      )
   {
      this.name = name;
      this.jdbcTypeCode = jdbcTypeCode;
      this.databaseType = databaseType;
      this.length = length;
      this.precision = precision;
      this.fractionalDigits = fractionalDigits;
      this.nullable = nullable;
      this.specifiedSourceCodeFieldType = specifiedSourceCodeFieldType;
   }

   public String getName() { return name; }
   public int getJdbcTypeCode() { return jdbcTypeCode; }
   public String getDatabaseType() { return databaseType; }
   public Integer getLength() { return length; }
   public Integer getPrecision() { return precision; }
   public Integer getFractionalDigits() { return fractionalDigits; }
   public Boolean getNullable() { return nullable; }
   public String getSpecifiedSourceCodeFieldType() { return specifiedSourceCodeFieldType; }

   SimpleTableFieldProperty toNullable()
   {
      if ( nullable != null && nullable )
         return this;
      else
         return new SimpleTableFieldProperty(
            name, jdbcTypeCode, databaseType, length, precision, fractionalDigits, true, specifiedSourceCodeFieldType
         );
   }

   @Override
   public boolean equals(Object o)
   {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      var that = (SimpleTableFieldProperty) o;
      return
         jdbcTypeCode == that.jdbcTypeCode &&
         name.equals(that.name) &&
         databaseType.equals(that.databaseType) &&
         Objects.equals(length, that.length) &&
         Objects.equals(precision, that.precision) &&
         Objects.equals(fractionalDigits, that.fractionalDigits) &&
         Objects.equals(nullable, that.nullable) &&
         Objects.equals(specifiedSourceCodeFieldType, that.specifiedSourceCodeFieldType);
   }

   @Override
   public int hashCode()
   {
      return Objects.hash(name, jdbcTypeCode, databaseType, length, precision, fractionalDigits, nullable, specifiedSourceCodeFieldType);
   }

   @Override
   public String toString()
   {
      return "SimpleTableFieldProperty{" +
         "name='" + name + '\'' +
         ", jdbcTypeCode=" + jdbcTypeCode +
         ", databaseType='" + databaseType + '\'' +
         ", length=" + length +
         ", precision=" + precision +
         ", fractionalDigits=" + fractionalDigits +
         ", nullable=" + nullable +
         ", specifiedSourceCodeFieldType=" + specifiedSourceCodeFieldType +
         '}';
   }
}
