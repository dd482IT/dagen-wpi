package org.sqljson.source_code_writers;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Types;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

import org.checkerframework.checker.nullness.qual.Nullable;

import org.sqljson.QueryReprSqlPath;
import org.sqljson.result_types.*;
import static org.sqljson.util.IO.newFileOrStdoutWriter;
import static org.sqljson.util.IO.writeString;
import static org.sqljson.util.Nullables.*;
import static org.sqljson.util.StringFuns.*;


public class JavaWriter implements SourceCodeWriter
{
   private final String targetPackage;
   private final @Nullable Path packageOutputDir;
   private final NullableFieldRepr nullableFieldRepr;
   private final @Nullable String filesHeader;
   private final String sqlResourceNamePrefix;
   private final boolean generateGetters;
   private final boolean generateSetters;

   private final Pattern TIMESTAMPTZ_REGEX = Pattern.compile("^timestamp(\\([0-9]+\\))? with time zone$");
   public enum NullableFieldRepr { OPTWRAPPED, ANNOTATED, BARETYPE }

   public JavaWriter
      (
         String targetPackage,
         @Nullable Path srcOutputBaseDir,
         NullableFieldRepr nullableFieldRepr,
         @Nullable String filesHeader,
         @Nullable String sqlResourceNamePrefix,
         boolean generateGetters,
         boolean generateSetters
      )
   {
      this.packageOutputDir = !targetPackage.isEmpty() ?
         applyIfPresent(srcOutputBaseDir, d -> d.resolve(targetPackage.replace('.','/')))
         : srcOutputBaseDir;
      this.targetPackage = targetPackage;
      this.nullableFieldRepr = nullableFieldRepr;
      this.filesHeader = filesHeader;
      this.sqlResourceNamePrefix = valueOr(sqlResourceNamePrefix, "");
      this.generateGetters = generateGetters;
      this.generateSetters = generateSetters;

   }

   @Override
   public void writeQuerySourceCode
      (
         String queryName,
         List<ResultType> resultTypes,
         List<String> paramNames,
         List<QueryReprSqlPath> sqlPaths,
         @Nullable String queryFileHeader,
         boolean includeTimestamp
      )
      throws IOException
   {
      String queryClassName = upperCamelCase(queryName);

      @Nullable Path outputPath = getOutputFilePath(queryClassName);

      BufferedWriter bw = newFileOrStdoutWriter(outputPath);

      try
      {
         writeCommonHeaderAndPackageDeclaration(bw, includeTimestamp);

         writeQueryFileImportsAndHeaders(bw, queryFileHeader);

         bw.write("\n\n");

         writeQueryClass(bw, queryClassName, paramNames, resultTypes, sqlPaths);
      }
      finally
      {
         if ( outputPath != null ) bw.close();
         else bw.flush();
      }
   }

   private void writeQueryFileImportsAndHeaders
      (
         BufferedWriter bw,
         @Nullable String queryFileHeader
      )
      throws IOException
   {
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
      bw.write("import com.fasterxml.jackson.databind.node.*;\n");

      // Write common headers if any.
      if ( filesHeader != null )
         bw.write(filesHeader + "\n");

      // Write any additional headers specified in the query.
      ifPresent(queryFileHeader, hdr -> writeString(bw, hdr + "\n"));
   }

   private void writeQueryClass
      (
         BufferedWriter bw,
         String queryClassName,
         List<String> paramNames,
         List<ResultType> resultTypes,
         List<QueryReprSqlPath> sqlPaths
      )
      throws IOException
   {
      bw.write("public class " + queryClassName + "\n");
      bw.write("{\n");

      writeQuerySqlFileReferenceMembers(bw, sqlPaths);

      writeParamMembers(paramNames, bw);

      if ( !resultTypes.isEmpty() )
      {
         String topClass = resultTypes.get(0).getTypeName();

         bw.write("   public static final Class<" + topClass + "> principalResultClass = " +
                  topClass + ".class;\n\n");

         Set<String> writtenTypeNames = new HashSet<>();

         for ( ResultType resultType : resultTypes )
         {
            if ( !writtenTypeNames.contains(resultType.getTypeName()) &&
                 !resultType.isUnwrapped() )
            {
               String srcCode = makeGeneratedTypeSource(resultType);

               bw.write('\n');
               bw.write(indentLines(srcCode, 3));
               bw.write('\n');

               writtenTypeNames.add(resultType.getTypeName());
            }
         }
      }

      bw.write("}\n");
   }

   @SuppressWarnings("keyfor")
   private void writeQuerySqlFileReferenceMembers
      (
         BufferedWriter bw,
         List<QueryReprSqlPath> sqlPaths
      )
      throws IOException
   {
      // Write members holding resource/file names for the result representations that were written for this query.
      for ( QueryReprSqlPath queryReprSqlPath: sqlPaths )
      {
         String memberName = sqlPaths.size() == 1 ? "sqlResource" :
            "sqlResource" + upperCamelCase(queryReprSqlPath.getResultRepr().toString());
         String resourceName = sqlResourceNamePrefix + queryReprSqlPath.getSqlPath().getFileName();
         bw.write("   public static final String " + memberName + " = \"" + resourceName + "\";\n");
      }
      bw.write("\n");
   }

   private void writeCommonHeaderAndPackageDeclaration
      (
         BufferedWriter bw,
         boolean includeTimestamp
      )
      throws IOException
   {
      bw.write("// ---------------------------------------------------------------------------\n");
      bw.write("// [ THIS SOURCE CODE WAS AUTO-GENERATED, ANY CHANGES MADE HERE MAY BE LOST. ]\n");
      if (includeTimestamp)
         bw.write("//   " + Instant.now().toString().replace('T', ' ') + "\n");
      bw.write("// ---------------------------------------------------------------------------\n");
      if ( !targetPackage.isEmpty() )
         bw.write("package " + targetPackage + ";\n\n");
   }

   private void writeParamMembers
      (
         List<String> paramNames,
         BufferedWriter bw
      )
      throws IOException
   {
      for ( String paramName : paramNames )
      {
         bw.write("   public static final ");

         bw.write("String ");
         bw.write(paramName);
         bw.write("Param");
         bw.write(" = \"");
         bw.write(paramName);
         bw.write("\";\n\n");
      }
   }

   private @Nullable Path getOutputFilePath(String className)
   {
      return applyIfPresent(packageOutputDir, d -> d.resolve(className + ".java"));
   }

   private String makeGeneratedTypeSource(ResultType genType)
   {
      StringBuilder sb = new StringBuilder();

      String typeName = genType.getTypeName();

      if ( nullableFieldRepr == NullableFieldRepr.ANNOTATED ) sb.append(
         "@DefaultQualifier(value=NonNull.class)\n" +
         "@SuppressWarnings(\"nullness\") // because fields will be set directly by the deserializer not by constructor\n"
      );
      sb.append("public static class ");
      sb.append(typeName);
      sb.append("\n{\n");

      List<FieldInfo> fields = new ArrayList<>();
      genType.getSimpleTableFieldProperties().forEach(f ->
         fields.add(new FieldInfo(f.getName(), getJavaTypeNameForSimpleTableField(f)))
      );
      genType.getTableExpressionProperties().forEach(f ->
         fields.add(new FieldInfo(f.getName(), getJavaTypeNameForTableExpressionProperty(f)))
      );
      genType.getChildCollectionProperties().forEach(f ->
         fields.add(new FieldInfo(f.getName(), getChildCollectionDeclaredType(f)))
      );
      genType.getParentReferenceProperties().forEach(f ->
         fields.add(new FieldInfo(f.getName(), getParentRefDeclaredType(f)))
      );

      // field declarations
      for ( FieldInfo f : fields )
      {
         sb.append("   public ");
         sb.append(f.typeDeclaration);
         sb.append(" ");
         sb.append(f.name);
         sb.append(";\n");
      }

      if ( generateGetters || generateSetters )
      {
         sb.append("\n");

         // getters and setters
         for ( FieldInfo f : fields )
         {
            if ( generateGetters )
            {
               sb.append("   public ");
               sb.append(f.typeDeclaration);
               sb.append(" get");
               sb.append(capitalize(f.name));
               sb.append("() { return ");
               sb.append(f.name);
               sb.append("; }\n");
            }

            if ( generateSetters )
            {
               sb.append("   public void ");
               sb.append(" set");
               sb.append(capitalize(f.name));
               sb.append("(");
               sb.append(f.typeDeclaration);
               sb.append(" v) { ");
               sb.append(f.name);
               sb.append(" = v; }\n");
            }
         }
      }

      sb.append("}\n");

      return sb.toString();
   }

   private String getJavaTypeNameForTableExpressionProperty(TableExpressionProperty f)
   {
      return valueOrThrow(f.getSpecifiedSourceCodeFieldType(), () ->
          new RuntimeException("Field type override is required for expression field " + f.getTableExpression())
      );
   }

   private String getJavaTypeNameForSimpleTableField(SimpleTableFieldProperty f)
   {
      return getJavaTypeNameForSimpleTableField(f, false);
   }

   private String getJavaTypeNameForSimpleTableField
      (
          SimpleTableFieldProperty f,
          boolean box
      )
   {
      boolean notNull = !valueOr(f.getNullable(), true);

      @Nullable String typeDecl = f.getSpecifiedSourceCodeFieldType();
      if ( typeDecl != null )
         return typeDecl;

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
         case Types.TIMESTAMP_WITH_TIMEZONE:
            return notNull ? "OffsetDateTime" : nullableType("OffsetDateTime");
         default:
            if ( f.getDatabaseType().toLowerCase().startsWith("json") )
               return notNull ? "JsonNode" : nullableType("JsonNode");
            // Another entry for timezones, this entry is for Oracle drivers that don't
            // declare timestamps with timezone with the proper java.sql.Types constant.
            else if ( TIMESTAMPTZ_REGEX.matcher(f.getDatabaseType().toLowerCase()).matches() )
               return notNull ? "OffsetDateTime" : nullableType("OffsetDateTime");
            throw new RuntimeException("unsupported type for database field " + f);
      }
   }

   private String getParentRefDeclaredType(ParentReferenceProperty parentRefField)
   {
      return
         !parentRefField.isNullable() ?
            parentRefField.getGeneratedType().getTypeName()
            : nullableType(parentRefField.getGeneratedType().getTypeName());
   }

   private String getChildCollectionDeclaredType(ChildCollectionProperty childCollField)
   {
      ResultType genType = childCollField.getGeneratedType();
      String elType = !genType.isUnwrapped() ? genType.getTypeName() : getSoleFieldDeclaredBoxedType(genType);
      String bareChildCollType = "List<" + elType + ">";
      return !childCollField.isNullable() ? bareChildCollType : nullableType(bareChildCollType);
   }

   private String getSoleFieldDeclaredBoxedType(ResultType genType)
   {
      if ( genType.getFieldsCount() != 1 )
         throw new RuntimeException("Expected single field when unwrapping " + genType.getTypeName() + ".");

      if ( genType.getSimpleTableFieldProperties().size() == 1 )
         return getJavaTypeNameForSimpleTableField(genType.getSimpleTableFieldProperties().get(0), true);
      else if ( genType.getTableExpressionProperties().size() == 1 )
         return getJavaTypeNameForTableExpressionProperty(genType.getTableExpressionProperties().get(0));
      else if ( genType.getChildCollectionProperties().size() == 1 )
         return getChildCollectionDeclaredType(genType.getChildCollectionProperties().get(0));
      else if ( genType.getParentReferenceProperties().size() == 1 )
         return getParentRefDeclaredType(genType.getParentReferenceProperties().get(0));
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
}

class FieldInfo
{
   String name;
   String typeDeclaration;

   public FieldInfo(String name, String typeDeclaration)
   {
      this.name = name;
      this.typeDeclaration = typeDeclaration;
   }
}
