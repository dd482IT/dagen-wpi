// ---------------------------------------------------------------------------
// [ THIS SOURCE CODE WAS AUTO-GENERATED, ANY CHANGES MADE HERE MAY BE LOST. ]
// ---------------------------------------------------------------------------
package org.relmds;

import org.checkerframework.checker.nullness.qual.Nullable;



public class Field
{
   public String name;
   public int jdbcTypeCode;
   public String databaseType;
   public Integer length;
   public Integer precision;
   public Integer fractionalDigits;
   public Boolean nullable;
   public Integer primaryKeyPartNumber;
   public Field
      (
         String name,
         int jdbcTypeCode,
         String databaseType,
         Integer length,
         Integer precision,
         Integer fractionalDigits,
         Boolean nullable,
         Integer primaryKeyPartNumber
      )
   {
      this.name = name;
      this.jdbcTypeCode = jdbcTypeCode;
      this.databaseType = databaseType;
      this.length = length;
      this.precision = precision;
      this.fractionalDigits = fractionalDigits;
      this.nullable = nullable;
      this.primaryKeyPartNumber = primaryKeyPartNumber;
   }

}
