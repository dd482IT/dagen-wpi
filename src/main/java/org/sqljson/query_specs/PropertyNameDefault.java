package org.sqljson.query_specs;

import java.util.function.Function;
import static java.util.function.Function.identity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.sqljson.util.StringFuns;


public enum PropertyNameDefault
{
   AS_IN_DB,
   CAMELCASE;

   public Function<String,String> toFunctionOfFieldName()
   {
      switch ( this )
      {
         case AS_IN_DB: return identity();
         case CAMELCASE: return StringFuns::lowerCamelCase;
         default: throw new RuntimeException("Unexpected output field name default.");
      }
   }
}
