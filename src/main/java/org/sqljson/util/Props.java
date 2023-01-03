package org.sqljson.util;

import java.util.Properties;

import static java.util.Objects.requireNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;


public class Props
{
   // Get the property value for the first contained key if any.
   public static String getProperty
      (
         Properties p,
         String... keys
      )
   {
      for ( String key: keys )
      {
         if ( p.containsKey(key) )
            return p.getProperty(key);
      }
      return null;
   }

   public static String requireProperty
      (
         Properties p,
         String... keys
      )
   {
      return requireNonNull(getProperty(p, keys), "Property " + keys[0] + " is required.");
   }

   private Props() {}
}

