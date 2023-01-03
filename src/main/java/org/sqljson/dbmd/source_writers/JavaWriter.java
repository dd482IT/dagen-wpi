package org.sqljson.dbmd.source_writers;

import java.io.StringWriter;
import java.nio.file.Files;
import java.util.*;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import static java.util.stream.Collectors.groupingBy;

import org.checkerframework.checker.nullness.qual.Nullable;

import org.sqljson.dbmd.DatabaseMetadata;
import org.sqljson.dbmd.Field;
import org.sqljson.dbmd.RelMetadata;
import org.sqljson.dbmd.RelId;
import static org.sqljson.util.IO.newFileOrStdoutWriter;
import static org.sqljson.util.Nullables.*;
import static org.sqljson.util.StringFuns.*;


public class JavaWriter implements SourceCodeWriter
{
   private final String targetPackage;
   private final Path packageOutputDir;

   public JavaWriter
      (
         String targetPackage,
         Path srcOutputBaseDir
      )
   {
      this.packageOutputDir = !targetPackage.isEmpty() ?
         applyIfPresent(srcOutputBaseDir, d -> d.resolve(targetPackage.replace('.','/')))
         : srcOutputBaseDir;
      this.targetPackage = targetPackage;
   }

   @Override
   public void writeRelationDefinitions
      (
         DatabaseMetadata dbmd,
         boolean includeTimestamp
      )
      throws IOException
   {
      if ( packageOutputDir != null )
         Files.createDirectories(packageOutputDir);

      writeCommonFieldMetadataClass(includeTimestamp);

      String topClassName = "Relations";

      Path outputPath = getOutputFilePath(topClassName);

      BufferedWriter bw = newFileOrStdoutWriter(outputPath);

      try
      {
         writeCommonHeaderAndPackageDeclaration(bw, includeTimestamp);

         writeRelationsClass(bw, topClassName, dbmd);
      }
      finally
      {
         if ( outputPath != null ) bw.close();
         else bw.flush();
      }
   }

   private void writeRelationsClass
      (
         BufferedWriter bw,
         String relationsClassName,
         DatabaseMetadata dbmd
      )
      throws IOException
   {
      bw.write("public class " + relationsClassName + "\n{\n\n");

      Map<String, List<RelMetadata>> relMdsBySchema =
         dbmd.getRelationMetadatas().stream()
         .collect(groupingBy(rmd -> valueOr(rmd.getRelationId().getSchema(), "DEFAULT")));

      for ( String schema : relMdsBySchema.keySet() )
      {
         bw.write("   public static class " + schema + "\n");
         bw.write("   {\n\n");

         for ( RelMetadata relMd : relMdsBySchema.get(schema) )
         {
            bw.write(indentLines(getRelationClassSource(relMd), 6));
            bw.write("\n\n");
         }

         bw.write("   }\n\n"); // close schema class
      }

      bw.write("}\n"); // close top relations class
   }

   private String getRelationClassSource(RelMetadata relMd)
   {
      StringWriter sw = new StringWriter();

      RelId relId = relMd.getRelationId();

      sw.write("public static class ");
      sw.write(relId.getName() + "\n");
      sw.write("{\n");
      sw.write("   public static String id() { return " + asStringLiteral(relId.getIdString()) + "; }\n");

      for ( Field f : relMd.getFields() )
      {
         sw.write("   public static final Field ");
         sw.write(f.getName());
         sw.write(" = new Field(");
         sw.write(asStringLiteral(f.getName()) + ",");
         sw.write(f.getJdbcTypeCode() + ",");
         sw.write(asStringLiteral(f.getDatabaseType()) + ",");
         sw.write(f.getLength() + ",");
         sw.write(f.getPrecision() + ",");
         sw.write(f.getFractionalDigits() + ",");
         sw.write(f.getNullable() + ",");
         Integer pkPartNum = f.getPrimaryKeyPartNumber();
         sw.write(pkPartNum != null ? pkPartNum.toString() : "null");
         sw.write(");\n");
      }

      sw.write("}\n"); // close relation class

      return sw.toString();
   }

   private void writeCommonFieldMetadataClass(boolean includeTimestamp)
      throws IOException
   {
      String className = "Field";
      Path outputFilePath = getOutputFilePath(className);

      BufferedWriter bw = newFileOrStdoutWriter(outputFilePath);

      try
      {
         writeCommonHeaderAndPackageDeclaration(bw, includeTimestamp);

         bw.write("import org.checkerframework.checker.nullness.qual.Nullable;\n\n");

         bw.write("\n\n");
         bw.write("public class " + className + "\n");
         bw.write("{\n");

         bw.write(
            "   public String name;\n" +
            "   public int jdbcTypeCode;\n" +
            "   public String databaseType;\n" +
            "   public @Nullable Integer length;\n" +
            "   public @Nullable Integer precision;\n" +
            "   public @Nullable Integer fractionalDigits;\n" +
            "   public @Nullable Boolean nullable;\n" +
            "   public @Nullable Integer primaryKeyPartNumber;\n" +
            "   public Field\n" +
            "      (\n" +
            "         String name,\n" +
            "         int jdbcTypeCode,\n" +
            "         String databaseType,\n" +
            "         @Nullable Integer length,\n" +
            "         @Nullable Integer precision,\n" +
            "         @Nullable Integer fractionalDigits,\n" +
            "         @Nullable Boolean nullable,\n" +
            "         @Nullable Integer primaryKeyPartNumber\n" +
            "      )\n" +
            "   {\n" +
            "      this.name = name;\n" +
            "      this.jdbcTypeCode = jdbcTypeCode;\n" +
            "      this.databaseType = databaseType;\n" +
            "      this.length = length;\n" +
            "      this.precision = precision;\n" +
            "      this.fractionalDigits = fractionalDigits;\n" +
            "      this.nullable = nullable;\n" +
            "      this.primaryKeyPartNumber = primaryKeyPartNumber;\n" +
            "   }\n" +
            "\n");

         bw.write("}\n");
      }
      finally
      {
         if ( outputFilePath != null ) bw.close();
         else bw.flush();
      }
   }

   private void writeCommonHeaderAndPackageDeclaration(BufferedWriter bw, boolean includeTimestamp) throws IOException
   {
      bw.write("// ---------------------------------------------------------------------------\n");
      bw.write("// [ THIS SOURCE CODE WAS AUTO-GENERATED, ANY CHANGES MADE HERE MAY BE LOST. ]\n");
      if (includeTimestamp)
         bw.write("//   " + Instant.now().toString().replace('T', ' ') + "\n");
      bw.write("// ---------------------------------------------------------------------------\n");
      if ( !targetPackage.isEmpty() )
         bw.write("package " + targetPackage + ";\n\n");
   }

   private Path getOutputFilePath(String className)
   {
      return applyIfPresent(packageOutputDir, d -> d.resolve(className + ".java"));
   }

   private static String asStringLiteral(String s)
   {
      return "\"" + s.replace("\"", "\\\"") + "\"";
   }
}
