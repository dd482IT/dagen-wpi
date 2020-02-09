package org.sqljson.source_writers;

import java.nio.file.Files;
import java.util.*;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.sql.Types;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import org.checkerframework.checker.nullness.qual.Nullable;

import org.sqljson.GeneratedModStatement;
import org.sqljson.result_types.*;
import org.sqljson.specs.queries.FieldTypeOverride;
import org.sqljson.specs.queries.ResultsRepr;
import org.sqljson.GeneratedQuery;
import org.sqljson.WrittenQueryReprPath;
import static org.sqljson.WrittenQueryReprPath.writtenPathsForQuery;
import static org.sqljson.util.Files.newFileOrStdoutWriter;
import static org.sqljson.util.Nullables.*;
import static org.sqljson.util.StringFuns.*;


public class JavaWriter implements SourceCodeWriter
{
   private String targetPackage;
   private @Nullable Path srcOutputBaseDir;
   private NullableFieldRepr nullableFieldRepr;
   private @Nullable String filesHeader;
   private String sqlResourceNamePrefix;

   public enum NullableFieldRepr { OPTWRAPPED, ANNOTATED, BARETYPE }

   public JavaWriter
   (
      String targetPackage,
      @Nullable Path srcOutputBaseDir,
      String sqlResourceNamePrefix
   )
   {
      this.targetPackage = targetPackage;
      this.srcOutputBaseDir = srcOutputBaseDir;
      this.nullableFieldRepr = NullableFieldRepr.OPTWRAPPED;
      this.filesHeader = null;
      this.sqlResourceNamePrefix = sqlResourceNamePrefix;
   }

   public JavaWriter
   (
      String targetPackage,
      @Nullable Path srcOutputBaseDir,
      NullableFieldRepr nullableFieldRepr,
      @Nullable String filesHeader,
      String sqlResourceNamePrefix
   )
   {
      this.targetPackage = targetPackage;
      this.srcOutputBaseDir = srcOutputBaseDir;
      this.nullableFieldRepr = nullableFieldRepr;
      this.filesHeader = filesHeader;
      this.sqlResourceNamePrefix = sqlResourceNamePrefix;
   }

   @Override
   @SuppressWarnings("keyfor")
   public void writeQueries
   (
      List<GeneratedQuery> generatedQueries,
      List<WrittenQueryReprPath> writtenQueryPaths,
      boolean includeTimestamp
   )
      throws IOException
   {
      @Nullable Path outputDir = !targetPackage.isEmpty() ?
         applyIfPresent(srcOutputBaseDir, d -> d.resolve(targetPackage.replace('.','/')))
         : srcOutputBaseDir;

      if ( outputDir != null )
         Files.createDirectories(outputDir);

      for ( GeneratedQuery q : generatedQueries )
      {
         String queryClassName = upperCamelCase(q.getQueryName());
         @Nullable Path outputFilePath = applyIfPresent(outputDir, d -> d.resolve(queryClassName + ".java"));

         BufferedWriter bw = newFileOrStdoutWriter(outputFilePath);

         Map<ResultsRepr,Path> writtenQueryPathsByRepr = writtenPathsForQuery(q.getQueryName(), writtenQueryPaths);

         try
         {
            bw.write("// --------------------------------------------------------------------------\n");
            bw.write("// [ THIS SOURCE CODE WAS AUTO-GENERATED, ANY CHANGES MADE HERE MAY BE LOST. ]\n");
            if ( includeTimestamp )
               bw.write("//   " + Instant.now().toString().replace('T',' ') + "\n");
            bw.write("// --------------------------------------------------------------------------\n");
            if ( !targetPackage.isEmpty() )
               bw.write("package " + targetPackage + ";\n\n");
            bw.write("import java.util.*;\n");
            bw.write("import java.math.*;\n");
            bw.write("import java.time.*;\n");
            if ( nullableFieldRepr == NullableFieldRepr.ANNOTATED ) bw.write(
                "import org.checkerframework.checker.nullness.qual.Nullable;\n" +
               "import org.checkerframework.checker.nullness.qual.NonNull;\n" +
               "import org.checkerframework.framework.qual.DefaultQualifier;\n" +
               "import org.checkerframework.framework.qual.TypeUseLocation;\n"
            );
            bw.write("import com.fasterxml.jackson.databind.JsonNode;\n");

            if ( filesHeader != null ) bw.write(filesHeader);

            bw.write("\n\n");
            bw.write("public class " + queryClassName + "\n");
            bw.write("{\n");

            // Write members holding resource/file names for the result representations that were written for this query.
            for ( ResultsRepr resultsRepr : sorted(writtenQueryPathsByRepr.keySet()) )
            {
               String memberName = writtenQueryPathsByRepr.size() == 1 ? "sqlResource" :
                  "sqlResource" + upperCamelCase(resultsRepr.toString());
               String resourceName = sqlResourceNamePrefix + requireNonNull(writtenQueryPathsByRepr.get(resultsRepr)).getFileName();
               bw.write("   public static final String " + memberName + " = \"" + resourceName + "\";\n");
            }
            bw.write("\n");

            writeParamMembers(q.getParamNames(), bw, true);

            if ( !q.getGeneratedResultTypes().isEmpty() )
            {
               String topClass = q.getGeneratedResultTypes().get(0).getTypeName();

               bw.write("   public static final Class<" + topClass + "> principalResultClass = " +
                        topClass + ".class;\n\n");

               Set<String> writtenTypeNames = new HashSet<>();

               for ( GeneratedType generatedType: q.getGeneratedResultTypes() )
               {
                  if ( !writtenTypeNames.contains(generatedType.getTypeName()) &&
                       !generatedType.isUnwrapped() )
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
            if ( outputFilePath != null ) bw.close();
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
      @Nullable Path outputDir = !targetPackage.isEmpty() ?
         applyIfPresent(srcOutputBaseDir, d -> d.resolve(targetPackage.replace('.','/')))
         : srcOutputBaseDir;

      if ( outputDir != null )
         Files.createDirectories(outputDir);

      for ( GeneratedModStatement modStmt : generatedModStatements )
      {
         if ( !modStmt.getGenerateSource() ) continue;

         String className = upperCamelCase(modStmt.getStatementName());
         @Nullable Path outputFilePath = applyIfPresent(outputDir, d -> d.resolve(className + ".java"));

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

            @Nullable Path writtenPath = writtenPathsByModName.get(modStmt.getStatementName());

            if ( writtenPath != null )
            {
               String resourceName = sqlResourceNamePrefix + writtenPath.getFileName().toString();
               bw.write("   public static final String sqlResource = \"" + resourceName + "\";\n");
            }

            bw.write("\n");

            writeParamMembers(modStmt.getAllParameterNames(), bw, modStmt.hasNamedParameters());

            bw.write("}\n");
         }
         finally
         {
            if ( outputFilePath != null ) bw.close();
            else bw.flush();
         }
      }
   }

   private String makeGeneratedTypeSource(GeneratedType generatedType)
   {
      StringBuilder sb = new StringBuilder();

      String typeName = generatedType.getTypeName();

      if ( nullableFieldRepr == NullableFieldRepr.ANNOTATED ) sb.append(
         "@DefaultQualifier(value=NonNull.class)\n" +
         "@SuppressWarnings(\"nullness\") // because fields will be set directly by the deserializer not by constructor\n"
      );
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

      for ( ExpressionField f : generatedType.getExpressionFields() )
      {
         sb.append("   public ");
         sb.append(getJavaTypeNameForExpressionField(f));
         sb.append(" ");
         sb.append(f.getName());
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

   private void writeParamMembers
   (
      List<String> paramNames,
      BufferedWriter bw,
      boolean hasNamedParams
   )
      throws IOException
   {
      for ( int paramIx = 0; paramIx < paramNames.size(); ++paramIx )
      {
         String paramName = paramNames.get(paramIx);

         bw.write("   public static final ");

         if ( hasNamedParams )
         {
            bw.write("String ");
            bw.write(paramName);
            bw.write("Param");
            bw.write(" = \"");
            bw.write(paramName);
            bw.write("\";\n\n");
         }
         else
         {
            bw.write("int ");
            bw.write(paramName);
            bw.write("ParamNum");
            bw.write(" = ");
            bw.write(String.valueOf(paramIx + 1));
            bw.write(";\n\n");
         }
      }
   }

   private String getJavaTypeNameForExpressionField(ExpressionField f)
   {
      FieldTypeOverride typeOverride = valueOrThrow(f.getTypeOverride("Java"), () ->
          new RuntimeException("Field type override is required for expression field " + f.getFieldExpression())
      );
      return typeOverride.getTypeDeclaration();
   }

   private String getJavaTypeNameForDatabaseField(DatabaseField f)
   {
      return getJavaTypeNameForDatabaseField(f, false);
   }

   private String getJavaTypeNameForDatabaseField
   (
       DatabaseField f,
       boolean box
   )
   {
      boolean notNull = !valueOr(f.getNullable(), true);

      @Nullable FieldTypeOverride typeOverride = f.getTypeOverride("Java");
      if ( typeOverride != null )
         return typeOverride.getTypeDeclaration();

      switch ( f.getJdbcTypeCode() )
      {
         case Types.TINYINT:
         case Types.SMALLINT:
            return notNull ? (box ? "Integer" : "int") : nullableType("Integer");
         case Types.INTEGER:
         case Types.BIGINT:
         {
            boolean needsLong = applyOr(f.getPrecision(), prec -> prec > 9, true);
            if ( notNull ) return needsLong ? (box ? "Long" : "long") : (box ? "Integer" : "int");
            else return needsLong ? nullableType("Long"): nullableType("Integer");
         }
         case Types.DECIMAL:
         case Types.NUMERIC:
            if ( Objects.equals(f.getFractionalDigits(), 0) )
            {
               boolean needsLong = applyOr(f.getPrecision(), prec -> prec > 9, true);
               if ( notNull ) return needsLong ? (box ? "Long" : "long") : (box ? "Integer" : "int");
               else return needsLong ? nullableType("Long"): nullableType("Integer");
            }
            else
               return notNull ? "BigDecimal" : nullableType("BigDecimal");
         case Types.FLOAT:
         case Types.REAL:
         case Types.DOUBLE:
            return notNull ? (box ? "Double": "double") : nullableType("Double");
         case Types.CHAR:
         case Types.VARCHAR:
         case Types.LONGVARCHAR:
         case Types.CLOB:
            return notNull ? "String" : nullableType("String");
         case Types.BIT:
         case Types.BOOLEAN:
            return notNull ? (box ? "Boolean" : "boolean") : nullableType("Boolean");
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
      GeneratedType genType = childCollField.getGeneratedType();
      String elType = !genType.isUnwrapped() ? genType.getTypeName() : getSoleFieldDeclaredBoxedType(genType);
      String bareChildCollType = "List<" + elType + ">";
      return !childCollField.isNullable() ? bareChildCollType : nullableType(bareChildCollType);
   }

   private String getSoleFieldDeclaredBoxedType(GeneratedType genType)
   {
      if ( genType.getFieldsCount() != 1 )
         throw new RuntimeException("Expected single field when unwrapping " + genType.getTypeName() + ".");

      if ( genType.getDatabaseFields().size() == 1 )
         return getJavaTypeNameForDatabaseField(genType.getDatabaseFields().get(0), true);
      else if ( genType.getExpressionFields().size() == 1 )
         return getJavaTypeNameForExpressionField(genType.getExpressionFields().get(0));
      else if ( genType.getChildCollectionFields().size() == 1 )
         return getChildCollectionDeclaredType(genType.getChildCollectionFields().get(0));
      else if ( genType.getParentReferenceFields().size() == 1 )
         return getParentRefDeclaredType(genType.getParentReferenceFields().get(0));
      throw
          new RuntimeException("Unhandled field category when unwrapping " + genType.getTypeName() + ".");
   }

   private String nullableType(String baseType)
   {
      StringBuilder sb = new StringBuilder();

      if ( nullableFieldRepr == NullableFieldRepr.ANNOTATED )
         sb.append("@Nullable ");
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
