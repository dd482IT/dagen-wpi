package org.sqljson.util;

import java.io.*;
import java.nio.file.Path;
import org.checkerframework.checker.nullness.qual.Nullable;


public final class IO
{
   public static BufferedWriter newFileOrStdoutWriter(Path outputFilePath) throws IOException
   {
      return outputFilePath != null ?
         java.nio.file.Files.newBufferedWriter(outputFilePath)
         : new BufferedWriter(new OutputStreamWriter(System.out));
   }

   public static OutputStream outputStream(String pathOrDash) throws IOException
   {
      if ( "-".equals(pathOrDash) )
         return System.out;
      else
         return new FileOutputStream(pathOrDash);
   }

   public static void writeString(BufferedWriter bw, String s)
   {
      try
      {
         bw.write(s);
      }
      catch (IOException e)
      {
         throw new RuntimeException(e);
      }
   }

   public static String readString(Path p)
   {
      try
      {
         return new String(java.nio.file.Files.readAllBytes(p));
      }
      catch (IOException e)
      {
         throw new RuntimeException(e);
      }
   }

   private IO() {}
}

