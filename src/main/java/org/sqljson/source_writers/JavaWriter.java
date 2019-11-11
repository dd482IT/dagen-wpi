package org.sqljson.source_writers;

import java.nio.file.Files;
import java.util.*;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.sql.Types;
import static java.util.Optional.empty;
import static java.util.stream.Collectors.toList;

import org.sqljson.GeneratedModStatement;
import org.sqljson.result_types.*;
import org.sqljson.specs.queries.FieldTypeOverride;
import org.sqljson.specs.queries.ResultsRepr;
import org.sqljson.specs.queries.TableOutputField;
import org.sqljson.GeneratedQuery;
import org.sqljson.WrittenQueryReprPath;
import static org.sqljson.util.Files.newFileOrStdoutWriter;
import static org.sqljson.util.Optionals.opt;
import static org.sqljson.util.StringFuns.*;


public class JavaWriter implements SourceCodeWriter
{
   private String targetPackage;
   private Optional<Path> srcOutputBaseDir;
   private NullableFieldRepr nullableFieldRepr;
   private Optional<String> filesHeader;

   public enum NullableFieldRepr { OPTWRAPPED, ANNOTATED, BARETYPE }

   public JavaWriter
   (
      String targetPackage,
      Optional<Path> srcOutputBaseDir
   )
   {
      this.targetPackage = targetPackage;
      this.srcOutputBaseDir = srcOutputBaseDir;
      this.nullableFieldRepr = NullableFieldRepr.OPTWRAPPED;
      this.filesHeader = empty();
   }

   public JavaWriter
   (
      String targetPackage,
      Optional<Path> srcOutputBaseDir,
      NullableFieldRepr nullableFieldRepr,
      Optional<String> filesHeader
   )
   {
      this.targetPackage = targetPackage;
      this.srcOutputBaseDir = srcOutputBaseDir;
      this.nullableFieldRepr = nullableFieldRepr;
      this.filesHeader = filesHeader;
   }

   @Override
   public void writeQueries
   (
      List<GeneratedQuery> generatedQueries,
      List<WrittenQueryReprPath> writtenQueryPaths,
      boolean includeTimestamp
   )
      throws IOException
   {
      Optional<Path> outputDir = !targetPackage.isEmpty() ?
         srcOutputBaseDir.map(d -> d.resolve(targetPackage.replace('.','/')))
         : srcOutputBaseDir;

      if ( outputDir.isPresent() )
         Files.createDirectories(outputDir.get());

      for ( GeneratedQuery q : generatedQueries )
      {
         String queryClassName = upperCamelCase(q.getName());
         Optional<Path> outputFilePath = outputDir.map(d -> d.resolve(queryClassName + ".java"));

         BufferedWriter bw = newFileOrStdoutWriter(outputFilePath);

         Map<ResultsRepr,Path> writtenQueryPathsByRepr =
            WrittenQueryReprPath.writtenPathsForQuery(q.getName(), writtenQueryPaths);

         try
         {
            bw.write("// --------------------------------------------------------------------------\n");
            bw.write("// [ THIS SOURCE CODE WAS AUTO-GENERATED, ANY CHANGES MADE HERE MAY BE LOST. ]\n");
            if ( includeTimestamp )
               bw.write("//   " + Instant.now().toString().replace('T',' ') + "\n");
            bw.write("// --------------------------------------------------------------------------\n");
            bw.write("package " + targetPackage + ";\n\n");
            bw.write("import java.util.*;\n");
            bw.write("import java.math.*;\n");
            bw.write("import java.time.*;\n");
            bw.write("import com.fasterxml.jackson.databind.JsonNode;\n");

            if ( filesHeader.isPresent() ) bw.write(filesHeader.get());

            bw.write("\n\n");
            bw.write("public class " + queryClassName + "\n");
            bw.write("{\n");

            // Write members holding resource/file names for the result representations that were written for this query.
            for ( ResultsRepr resultsRepr : sorted(writtenQueryPathsByRepr.keySet()) )
            {
               String memberName = writtenQueryPathsByRepr.size() == 1 ? "sqlResourceName" :
                  "sqlResourceName" + upperCamelCase(resultsRepr.toString());
               String resourceName = writtenQueryPathsByRepr.get(resultsRepr).getFileName().toString();
               bw.write("   public static final String " + memberName + " = \"" + resourceName + "\";\n");
            }
            bw.write("\n");

            if ( !q.getGeneratedResultTypes().isEmpty() )
            {
               String topClass = q.getGeneratedResultTypes().get(0).getTypeName();

               bw.write("   public static final Class<" + topClass + "> principalResultClass = " +
                        topClass + ".class;\n\n");

               Set<String> writtenTypeNames = new HashSet<>();

               for ( GeneratedType generatedType: q.getGeneratedResultTypes() )
               {
                  if ( !writtenTypeNames.contains(generatedType.getTypeName()) )
                  {
                     String srcCode = makeGeneratedTypeSource(generatedType);

                     bw.write('\n');
                     bw.write(indentLines(srcCode, 3));
                     bw.write('\n');

                     writtenTypeNames.add(generatedType.getTypeName());
                  }
               }
            }

            bw.write("}\n");
         }
         finally
         {
            if ( outputFilePath.isPresent() ) bw.close();
            else bw.flush();
         }
      }
   }

   @Override
   public void writeModStatements
      (
         List<GeneratedModStatement> generatedModStatements,
         Map<String,Path> writtenPathsByModName,
         boolean includeTimestamp
      )
      throws IOException
   {
      Optional<Path> outputDir = !targetPackage.isEmpty() ?
         srcOutputBaseDir.map(d -> d.resolve(targetPackage.replace('.','/')))
         : srcOutputBaseDir;

      if ( outputDir.isPresent() )
         Files.createDirectories(outputDir.get());

      for ( GeneratedModStatement modStmt : generatedModStatements )
      {
         if ( !modStmt.getGenerateSource() ) continue;

         String className = upperCamelCase(modStmt.getStatementName());
         Optional<Path> outputFilePath = outputDir.map(d -> d.resolve(className + ".java"));

         BufferedWriter bw = newFileOrStdoutWriter(outputFilePath);

         try
         {
            bw.write("// --------------------------------------------------------------------------\n");
            bw.write("// [ THIS SOURCE CODE WAS AUTO-GENERATED, ANY CHANGES MADE HERE MAY BE LOST. ]\n");
            if ( includeTimestamp )
               bw.write("//   " + Instant.now().toString().replace('T',' ') + "\n");
            bw.write("// --------------------------------------------------------------------------\n");
            bw.write("package " + targetPackage + ";\n\n");

            bw.write("\n\n");
            bw.write("public class " + className + "\n");
            bw.write("{\n");

            Path writtenPath = writtenPathsByModName.get(modStmt.getStatementName());

            if ( writtenPath != null )
            {
               String resourceName = writtenPath.getFileName().toString();
               bw.write("   public static final String sqlResourceName = \"" + resourceName + "\";\n");
            }

            bw.write("\n");

            List<String> paramNames = modStmt.getAllParameterNames();

            for ( int paramIx = 0; paramIx < paramNames.size(); ++paramIx )
            {
               String inputFieldParam = paramNames.get(paramIx);

               bw.write("   public static final ");

               if ( modStmt.hasNamedParameters() )
               {
                  bw.write("String ");
                  bw.write(inputFieldParam);
                  bw.write("Param");
                  bw.write(" = \"");
                  bw.write(inputFieldParam);
                  bw.write("\";\n\n");
               }
               else
               {
                  bw.write("int ");
                  bw.write(inputFieldParam);
                  bw.write("ParamNum");
                  bw.write(" = ");
                  bw.write(String.valueOf(paramIx + 1));
                  bw.write(";\n\n");
               }
            }

            bw.write("}\n");
         }
         finally
         {
            if ( outputFilePath.isPresent() ) bw.close();
            else bw.flush();
         }
      }
   }

   private String makeGeneratedTypeSource(GeneratedType generatedType)
   {
      StringBuilder sb = new StringBuilder();

      String typeName = generatedType.getTypeName();

      sb.append("public static class ");
      sb.append(typeName);
      sb.append("\n{\n");

      for ( DatabaseField f : generatedType.getDatabaseFields() )
      {
         sb.append("   public ");
         sb.append(getJavaTypeNameForDatabaseField(f));
         sb.append(" ");
         sb.append(f.getName());
         sb.append(";\n");
      }

      for ( TableOutputField tof : generatedType.getExpressionFields() )
      {
         String fieldName = tof.getOutputName().orElseThrow(() ->
            new RuntimeException("Output name is required for expression field " + tof.getFieldExpression())
         );
         FieldTypeOverride typeOverride = tof.getTypeOverride("Java").orElseThrow(() ->
            new RuntimeException("Field type override is required for expression field " + tof.getFieldExpression())
         );
         sb.append("   public ");
         sb.append(typeOverride.getTypeDeclaration());
         sb.append(" ");
         sb.append(fieldName);
         sb.append(";\n");
      }

      for ( ChildCollectionField childCollField : generatedType.getChildCollectionFields() )
      {
         sb.append("   public ");
         sb.append(getChildCollectionDeclaredType(childCollField));
         sb.append(" ");
         sb.append(childCollField.getName());
         sb.append(";\n");
      }

      for ( ParentReferenceField parentRefField : generatedType.getParentReferenceFields() )
      {
         sb.append("   public ");
         sb.append(getParentRefDeclaredType(parentRefField));
         sb.append(" ");
         sb.append(parentRefField.getName());
         sb.append(";\n");
      }

      sb.append("}\n");

      return sb.toString();
   }

   private String getJavaTypeNameForDatabaseField(DatabaseField f)
   {
      boolean notNull = !(f.getNullable().orElse(true));

      Optional<FieldTypeOverride> typeOverride = f.getTypeOverride("Java");
      if ( typeOverride.isPresent() )
         return typeOverride.get().getTypeDeclaration();

      switch ( f.getJdbcTypeCode() )
      {
         case Types.TINYINT:
         case Types.SMALLINT:
            return notNull ? "int" : nullableType("Integer");
         case Types.INTEGER:
         case Types.BIGINT:
         {
            boolean needsLong = !f.getPrecision().isPresent() || f.getPrecision().get() > 9;
            if ( notNull ) return needsLong ? "long": "int";
            else return needsLong ? nullableType("Long"): nullableType("Integer");
         }
         case Types.DECIMAL:
         case Types.NUMERIC:
            if ( f.getFractionalDigits().equals(opt(0) ) )
            {
               boolean needsLong = !f.getPrecision().isPresent() || f.getPrecision().get() > 9;
               if ( notNull ) return needsLong ? "long": "int";
               else return needsLong ? nullableType("Long"): nullableType("Integer");
            }
            else
               return notNull ? "BigDecimal" : nullableType("BigDecimal");
         case Types.FLOAT:
         case Types.REAL:
         case Types.DOUBLE:
            return notNull ? "double" : nullableType("Double");
         case Types.CHAR:
         case Types.VARCHAR:
         case Types.LONGVARCHAR:
         case Types.CLOB:
            return notNull ? "String" : nullableType("String");
         case Types.BIT:
         case Types.BOOLEAN:
            return notNull ? "boolean" : nullableType("Boolean");
         case Types.DATE:
            return notNull ? "LocalDate" : nullableType("LocalDate");
         case Types.TIME:
            return notNull ? "LocalTime" : nullableType("LocalTime");
         case Types.TIMESTAMP:
            return notNull ? "Instant" : nullableType("Instant");
         case Types.OTHER:
            if ( f.getDatabaseType().toLowerCase().startsWith("json") )
               return notNull ? "JsonNode" : nullableType("JsonNode");
            else
               throw new RuntimeException("unsupported type for database field " + f);
         default:
            throw new RuntimeException("unsupported type for database field " + f);
      }
   }

   private String getParentRefDeclaredType(ParentReferenceField parentRefField)
   {
      return
         !parentRefField.isNullable() ?
            parentRefField.getGeneratedType().getTypeName()
            : nullableType(parentRefField.getGeneratedType().getTypeName());
   }

   private String getChildCollectionDeclaredType(ChildCollectionField childCollField)
   {
      String bareChildCollType = "List<" + childCollField.getGeneratedType().getTypeName() + ">";
      return !childCollField.isNullable() ? bareChildCollType : nullableType(bareChildCollType);
   }

   private String nullableType(String baseType)
   {
      StringBuilder sb = new StringBuilder();

      if ( nullableFieldRepr == NullableFieldRepr.ANNOTATED)
         sb.append("@Null ");
      else if ( nullableFieldRepr == NullableFieldRepr.OPTWRAPPED  )
         sb.append("Optional<");
      else if ( nullableFieldRepr != NullableFieldRepr.BARETYPE )
         throw new RuntimeException("unexpected nullable field repr: " + nullableFieldRepr);

      sb.append(baseType);

      if ( nullableFieldRepr == NullableFieldRepr.OPTWRAPPED  )
         sb.append(">");

      return sb.toString();
   }

   private List<ResultsRepr> sorted(Collection<ResultsRepr> xs)
   {
      return xs.stream().sorted().collect(toList());
   }
}
