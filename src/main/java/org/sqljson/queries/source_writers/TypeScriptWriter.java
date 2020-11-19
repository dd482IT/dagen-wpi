package org.sqljson.queries.source_writers;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Types;
import java.time.Instant;
import java.util.*;

import org.checkerframework.checker.nullness.qual.Nullable;

import org.sqljson.queries.QueryReprSqlPath;
import org.sqljson.queries.result_types.*;
import org.sqljson.util.IO;
import static org.sqljson.util.IO.writeString;
import static org.sqljson.util.Nullables.*;
import static org.sqljson.util.StringFuns.upperCamelCase;


public class TypeScriptWriter implements SourceCodeWriter
{
   private final @Nullable Path srcOutputDir;
   private final @Nullable String filesHeader;
   private final String sqlResourceNamePrefix;

   public TypeScriptWriter
      (
         @Nullable Path srcOutputDir,
         @Nullable String filesHeader,
         String sqlResourceNamePrefix
      )
   {
      this.srcOutputDir = srcOutputDir;
      this.filesHeader = filesHeader;
      this.sqlResourceNamePrefix = sqlResourceNamePrefix;
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
      String moduleName = makeModuleName(queryName);

      @Nullable Path outputPath = getOutputFilePath(moduleName);

      BufferedWriter bw = IO.newFileOrStdoutWriter(outputPath);

      try
      {
         writeCommonSourceFileHeader(bw, includeTimestamp);

         writeQueryModuleFileHeaders(bw, queryFileHeader);

         bw.write("\n\n");

         writeQueryModuleMembers(bw, resultTypes, paramNames, sqlPaths);
      }
      finally
      {
         if ( outputPath != null ) bw.close();
         else bw.flush();
      }
   }

   private void writeCommonSourceFileHeader
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
   }

   private void writeQueryModuleFileHeaders
      (
         BufferedWriter bw,
         @Nullable String queryFileHeader
      )
      throws IOException
   {
      // Write common header for query files if specified.
      if ( filesHeader != null )
         bw.write(filesHeader + "\n");

      // Write any additional headers specified in the query.
      ifPresent(queryFileHeader, hdr -> writeString(bw, hdr + "\n"));
   }

   private void writeQueryModuleMembers
      (
         BufferedWriter bw,
         List<ResultType> resultTypes,
         List<String> paramNames,
         List<QueryReprSqlPath> sqlPaths
      )
      throws IOException
   {
      writeQuerySqlFileReferenceMembers(bw, sqlPaths);

      writeParamMembers(paramNames, bw);

      if ( !resultTypes.isEmpty() )
      {
         Set<String> writtenTypeNames = new HashSet<>();

         for ( ResultType resultType : resultTypes )
         {
            if ( !writtenTypeNames.contains(resultType.getTypeName()) &&
                 !resultType.isUnwrapped() )
            {
               bw.write('\n');
               bw.write(getTypeDeclaration(resultType));

               writtenTypeNames.add(resultType.getTypeName());
            }
         }
      }
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
         bw.write("export const " + memberName + " = \"" + resourceName + "\";\n");
      }
      bw.write("\n");
   }

   private void writeParamMembers
      (
         List<String> paramNames,
         BufferedWriter bw
      )
      throws IOException
   {
      for ( String paramName: paramNames )
      {
         bw.write("export const ");
         bw.write(paramName);
         bw.write("Param");
         bw.write(" = '");
         bw.write(paramName);
         bw.write("';\n\n");
      }
   }

   public String getTypeDeclaration
      (
         ResultType genType
      )
   {
      StringBuilder sb = new StringBuilder();

      String typeName = genType.getTypeName();

      sb.append("export interface ");
      sb.append(typeName);
      sb.append("\n{\n");

      List<FieldInfo> fields = new ArrayList<>();
      genType.getSimpleTableFieldProperties().forEach(f ->
         fields.add(new FieldInfo(f.getName(), getTSTypeNameForSimpleTableField(f)))
      );
      genType.getTableExpressionProperties().forEach(f ->
         fields.add(new FieldInfo(f.getName(), getTSTypeNameForTableExpressionProperty(f)))
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
         sb.append("   ");
         sb.append(f.name);
         sb.append(": ");
         sb.append(f.typeDeclaration);
         sb.append(";\n");
      }

      sb.append("}\n");

      return sb.toString();
   }

   private String getTSTypeNameForSimpleTableField(SimpleTableFieldProperty f)
   {
      boolean notNull = !valueOr(f.getNullable(), true);

      @Nullable String typeOverride = f.getSpecifiedSourceCodeFieldType();
      if ( typeOverride != null )
         return typeOverride;

      switch ( f.getJdbcTypeCode() )
      {
         case Types.TINYINT:
         case Types.SMALLINT:
         case Types.INTEGER:
         case Types.BIGINT:
         case Types.DECIMAL:
         case Types.NUMERIC:
         case Types.FLOAT:
         case Types.REAL:
         case Types.DOUBLE:
            return notNull ? "number" : "number | null";
         case Types.CHAR:
         case Types.VARCHAR:
         case Types.LONGVARCHAR:
         case Types.CLOB:
         case Types.DATE:
         case Types.TIME:
         case Types.TIMESTAMP:
         case Types.TIMESTAMP_WITH_TIMEZONE:
            return notNull ? "string" : "string | null";
         case Types.BIT:
         case Types.BOOLEAN:
            return notNull ? "boolean" : "boolean | null";
         case Types.OTHER:
            if ( f.getDatabaseType().toLowerCase().startsWith("json") )
               return notNull ? "any" : "any | null";
            else
               throw new RuntimeException("unsupported type for database field " + f);
         default:
            throw new RuntimeException("unsupported type for database field " + f);
      }
   }

   private String getTSTypeNameForTableExpressionProperty(TableExpressionProperty f)
   {
      return valueOrThrow(f.getSpecifiedSourceCodeFieldType(), () ->
          new RuntimeException("Field type override is required for expression field " + f.getTableExpression())
      );
   }


   private String getParentRefDeclaredType(ParentReferenceProperty parentRefField)
   {
      return
         !parentRefField.isNullable() ?
            parentRefField.getGeneratedType().getTypeName()
            : parentRefField.getGeneratedType().getTypeName() + " | null";
   }

   private String getChildCollectionDeclaredType(ChildCollectionProperty childCollField)
   {
      ResultType genType = childCollField.getGeneratedType();
      String elType = !genType.isUnwrapped() ? genType.getTypeName() : getSoleFieldDeclaredType(genType);
      String bareChildCollType = elType + "[]";
      return !childCollField.isNullable() ? bareChildCollType : bareChildCollType + " | null";
   }

   private String getSoleFieldDeclaredType(ResultType genType)
   {
      if ( genType.getFieldsCount() != 1 )
         throw new RuntimeException("Expected single field when unwrapping " + genType.getTypeName() + ".");

      if ( genType.getSimpleTableFieldProperties().size() == 1 )
         return getTSTypeNameForSimpleTableField(genType.getSimpleTableFieldProperties().get(0));
      else if ( genType.getTableExpressionProperties().size() == 1 )
         return getTSTypeNameForTableExpressionProperty(genType.getTableExpressionProperties().get(0));
      else if ( genType.getChildCollectionProperties().size() == 1 )
         return getChildCollectionDeclaredType(genType.getChildCollectionProperties().get(0));
      else if ( genType.getParentReferenceProperties().size() == 1 )
         return getParentRefDeclaredType(genType.getParentReferenceProperties().get(0));
      throw
          new RuntimeException("Unhandled field category when unwrapping " + genType.getTypeName() + ".");
   }

   private @Nullable Path getOutputFilePath(String moduleName)
   {
      return applyIfPresent(srcOutputDir, d -> d.resolve(moduleName + ".ts"));
   }

   private static String makeModuleName(String statementName)
   {
      return statementName.replace(' ', '-').toLowerCase();
   }
}

