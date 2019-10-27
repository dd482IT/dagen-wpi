package org.sqljsonquery.util;

import java.util.Optional;
import java.util.Properties;
import static java.util.Optional.empty;

import static org.sqljsonquery.util.Optionals.opt;


public class Props
{
   // Get the property value for the first contained key if any.
   public static Optional<String> getProperty
   (
      Properties p,
      String... keys
   )
   {
      for ( String key: keys )
      {
         if ( p.containsKey(key) )
            return opt(p.getProperty(key));
      }
      return empty();
   }

   public static String requireProperty
   (
      Properties p,
      String... keys
   )
   {
      return getProperty(p, keys).orElseThrow(() ->
         new RuntimeException("Property " + keys[0] + " is required.")
      );
    }

   private Props() {}
}
