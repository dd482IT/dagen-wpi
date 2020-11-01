package org.sqljson.common.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static java.util.Collections.unmodifiableList;


public class AppUtils
{
   public static void throwError(String message)
   {
      System.err.println(message);
      throw new RuntimeException(message);
   }

   public static SplitArgs splitOptionsAndRequiredArgs(String[] args)
   {
      List<String> optArgs = new ArrayList<>();
      for (String arg : args)
         if (arg.startsWith("-")) optArgs.add(arg);
      return new SplitArgs(optArgs, Arrays.asList(Arrays.copyOfRange(args, optArgs.size(), args.length)));
   }

   public static class SplitArgs
   {
      public List<String> optional;
      public List<String> required;

      SplitArgs(List<String> optional, List<String> required)
      {
         this.optional = unmodifiableList(new ArrayList<>(optional));
         this.required = unmodifiableList(new ArrayList<>(required));
      }
   }

   private AppUtils() {}
}

