package org.sqljson.dbmd;

import java.sql.Types;
import static java.util.Objects.requireNonNull;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

public class Field {

   private String name;

   private int jdbcTypeCode;

   private String databaseType;

   private Integer length;

   private Integer precision;

   private Integer precisionRadix;

   private Integer fractionalDigits;

   private Boolean nullable;

   private Integer primaryKeyPartNumber;


   public Field
      (
         String name,
         int jdbcTypeCode,
         String databaseType,
         Integer length,
         Integer precision,
         Integer precisionRadix,
         Integer fractionalDigits,
         Boolean nullable,
         Integer primaryKeyPartNumber
      )
   {
      this.name = requireNonNull(name);
      this.jdbcTypeCode = jdbcTypeCode;
      this.databaseType = requireNonNull(databaseType);
      this.length = length;
      this.precision = precision;
      this.precisionRadix = precisionRadix;
      this.fractionalDigits = fractionalDigits;
      this.nullable = nullable;
      this.primaryKeyPartNumber = primaryKeyPartNumber;
   }

   Field()
   {
      this.name = "";
      this.databaseType = "";
   }

   public String getName() { return name; }

   public int getJdbcTypeCode() { return jdbcTypeCode; }

   public String getDatabaseType() { return databaseType; }

   public Integer getLength() { return length; }

   public Integer getFractionalDigits() { return fractionalDigits; }

   public Integer getPrecision() { return precision; }

   public Integer getPrecisionRadix() { return precisionRadix; }

   public Boolean getNullable() { return nullable; }

   public Integer getPrimaryKeyPartNumber() { return primaryKeyPartNumber; }

   public boolean isNumericType() { return isJdbcTypeNumeric(jdbcTypeCode); }

   public boolean isCharacterType() { return isJdbcTypeChar(jdbcTypeCode); }

   public static boolean isJdbcTypeNumeric(int jdbcType)
   {
      switch ( jdbcType )
      {
         case Types.TINYINT:
         case Types.SMALLINT:
         case Types.INTEGER:
         case Types.BIGINT:
         case Types.FLOAT:
         case Types.REAL:
         case Types.DOUBLE:
         case Types.DECIMAL:
         case Types.NUMERIC:
            return true;
         default:
            return false;
      }
   }

   public static boolean isJdbcTypeChar(int jdbcType)
   {
      switch (jdbcType)
      {
         case Types.CHAR:
         case Types.VARCHAR:
         case Types.LONGVARCHAR:
            return true;
         default:
            return false;
      }
   }
}
