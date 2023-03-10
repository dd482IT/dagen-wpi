package org.sqljson;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import static java.util.Objects.requireNonNull;

import org.checkerframework.checker.nullness.qual.Nullable;

import org.sqljson.result_types.ResultType;
import org.sqljson.result_types.ResultTypesGenerator;
import org.sqljson.source_code_writers.SourceCodeWriter;
import org.sqljson.source_code_writers.JavaWriter;
import org.sqljson.source_code_writers.SourceCodeLanguage;
import org.sqljson.source_code_writers.TypeScriptWriter;
import org.sqljson.query_specs.*;
import org.sqljson.query_specs.SpecError;
import org.sqljson.util.AppUtils.SplitArgs;
import org.sqljson.dbmd.DatabaseMetadata;
import static org.sqljson.util.AppUtils.splitOptionsAndRequiredArgs;
import static org.sqljson.util.AppUtils.throwError;
import static org.sqljson.util.IO.newFileOrStdoutWriter;
import static org.sqljson.util.IO.readString;
import static org.sqljson.util.Serialization.getObjectMapper;
import static org.sqljson.util.Serialization.writeJsonSchema;
import static org.sqljson.util.StringFuns.fileExtension;


public class QueryGeneratorMain
{
   private static final String sqlResourcePathInGeneratedSourceOptPrefix = "--sql-resource-path-in-generated-src:";
   private static final String langOptPrefix = "--types-language:";
   private static final String pkgOptPrefix = "--package:";
   private static final String javaNullabilityOptPrefix = "--java-nullability:";
   private static final String generatedTypesHeaderFileOptPrefix = "--types-file-header:";
   private static final String includeSourceGenerationTimestamp = "--include-source-gen-timestamp";
   private static final String javaGenerateGetters = "--java-generate-getters";
   private static final String javaGenerateSetters = "--java-generate-setters";

   private static void printUsage()
   {
      PrintStream ps = System.out;
      ps.println("Expected arguments: [options] <db-metadata-file> <queries-spec-file> <types-output-base-dir> <sql-output-dir>");
      ps.println("Options:");
      ps.println("   " + sqlResourcePathInGeneratedSourceOptPrefix + "<path>: a prefix to the SQL file name written into source code.");
      ps.println("   " + langOptPrefix + "<language>  Output language, \"Java\"|\"TypeScript\".");
      ps.println("   " + pkgOptPrefix + "<java-package>  The Java package for the generated query classes.");
      ps.println("   " + javaNullabilityOptPrefix + "<nullable-fields-option>  How nullable fields should be" +
                 "represented in Java.");
      ps.println("       Valid options are:");
      ps.println("         annotated  : annotate nullable fields with with @Nullable.");
      ps.println("         optwrapped : wrap the type with Optional<>");
      ps.println("         baretype   : leave as bare type (Object variant for native types)");
      ps.println("   " + javaGenerateGetters + "  Include getters in generated Java types.");
      ps.println("   " + javaGenerateSetters + "  Include setters in generated Java types.");
      ps.println("   " + generatedTypesHeaderFileOptPrefix + "<file>  Contents of this file will be included at the " +
         "top of each generated type's source file (e.g. additional imports for overridden field types).");
      ps.println("   " + includeSourceGenerationTimestamp + "  Whether to include a generation timestamp " +
         "in generated source code.");
      ps.println("    --print-spec-json-schema: Print a json schema for the query group spec, to " +
         "facilitate editing.");
   }

   public static void main(String[] allArgs)
   {
      if ( allArgs.length == 1 && (allArgs[0].equals("-h") || allArgs[0].equals("--help")) )
      {
         printUsage();
         return;
      }
      if ( allArgs.length == 1 && allArgs[0].equals("--print-spec-json-schema") )
      {
         writeJsonSchema(QueryGroupSpec.class, System.out);
         return;
      }

      SplitArgs args = splitOptionsAndRequiredArgs(allArgs);
      if ( args.required.size() != 4 )
         throw new RuntimeException("expected 4 non-option arguments, got: "+ args.required.size());

      Path dbmdPath = Paths.get(args.required.get(0));
      if ( !Files.isRegularFile(dbmdPath) ) throwError("Database metadata file not found.");

      Path queriesSpecFilePath = Paths.get(args.required.get(1));
      if ( !Files.isRegularFile(queriesSpecFilePath) ) throwError("Queries specification file not found.");

      Path srcOutputBaseDirPath = Paths.get(args.required.get(2));
      if ( !Files.isDirectory(srcOutputBaseDirPath) ) throwError("Source output base directory not found.");

      Path queriesOutputDirPath = Paths.get(args.required.get(3));
      if ( !Files.isDirectory(queriesOutputDirPath) ) throwError("Queries output directory not found.");

      boolean includeSrcGenTimestamp = args.optional.contains(includeSourceGenerationTimestamp);

      SourceCodeWriter srcWriter = getSourceCodeWriter(args.optional, srcOutputBaseDirPath);

      try ( var dbmdIS = Files.newInputStream(dbmdPath);
            var queriesSpecIS = Files.newInputStream(queriesSpecFilePath) )
      {
         DatabaseMetadata dbmd = getObjectMapper(fileExtension(dbmdPath)).readValue(dbmdIS, DatabaseMetadata.class);

         QueryGroupSpec queryGroupSpec = getObjectMapper(fileExtension(queriesSpecFilePath)).readValue(queriesSpecIS, QueryGroupSpec.class);

         generateQueries(queryGroupSpec, queriesOutputDirPath, dbmd, srcWriter, includeSrcGenTimestamp);
      }
      catch( SpecError sse )
      {
         System.err.println("\n\n" +
            "----------------------------------------------------------------------\n" +
            "Error in query specification:\n" +
            "  in query: " + sse.getSpecLocation().getQueryName() + "\n" +
            (sse.getSpecLocation().getQueryPart().length() > 0 ?
               "At part: " + sse.getSpecLocation().getQueryPart() + "\n": "") +
            "  problem: " + sse.getProblem() + "\n" +
            "----------------------------------------------------------------------\n\n\n"
         );
      }
      catch( Exception e )
      {
         e.printStackTrace();
         System.err.println(e.getMessage());
         System.exit(1);
      }
   }

   private static void generateQueries
      (
         QueryGroupSpec queryGroupSpec,
         Path queriesOutputDirPath,
         DatabaseMetadata dbmd,
         SourceCodeWriter srcWriter,
         boolean includeSrcGenTimestamp
      )
      throws IOException
   {
      var sqlGenerator =
         new QuerySqlGenerator(
            dbmd,
            queryGroupSpec.getDefaultSchema(),
            new HashSet<>(queryGroupSpec.getGenerateUnqualifiedNamesForSchemas()),
            getPropertyNamer(queryGroupSpec)
         );

      var resultTypesGenerator =
         new ResultTypesGenerator(
            dbmd,
            queryGroupSpec.getDefaultSchema(),
            getPropertyNamer(queryGroupSpec)
         );

      for ( QuerySpec querySpec : queryGroupSpec.getQuerySpecs() )
      {
         // Generate SQL for each of the query's specified result representations.
         Map<ResultRepr,String> queryReprSqls = sqlGenerator.generateSqls(querySpec);

         // Write query SQLs.
         List<QueryReprSqlPath> sqlPaths = writeQuerySqls(querySpec.getQueryName(), queryReprSqls, queriesOutputDirPath);

         if ( querySpec.getGenerateResultTypesOrDefault() )
         {
            List<ResultType> resultTypes = resultTypesGenerator.generateResultTypes(querySpec.getTableJson());

            srcWriter.writeQuerySourceCode(
               querySpec.getQueryName(),
               resultTypes,
               getParamNames(querySpec),
               sqlPaths,
               querySpec.getTypesFileHeader(),
               includeSrcGenTimestamp
            );
         }
      }
   }

   private static SourceCodeWriter getSourceCodeWriter
      (
         List<String> optionalArgs,
         @Nullable Path srcOutputBaseDir
      )
   {
      String langStr = "";
      String targetPackage = "";
      String sqlResourceNamePrefix = "";
      boolean generateJavaGetters = false;
      boolean generateJavaSetters = false;

      var nullableFieldRepr = JavaWriter.NullableFieldRepr.ANNOTATED;
      @Nullable String typeFilesHeader = null;
      for ( String opt : optionalArgs )
      {
         if ( opt.startsWith(sqlResourcePathInGeneratedSourceOptPrefix) )
            sqlResourceNamePrefix = opt.substring(sqlResourcePathInGeneratedSourceOptPrefix.length());
         else if ( opt.startsWith(langOptPrefix) )
            langStr = opt.substring(langOptPrefix.length());
         else if ( opt.startsWith(pkgOptPrefix) )
            targetPackage = opt.substring(pkgOptPrefix.length());
         else if ( opt.startsWith(javaNullabilityOptPrefix) )
            nullableFieldRepr = JavaWriter.NullableFieldRepr.valueOf(opt.substring(javaNullabilityOptPrefix.length()).toUpperCase());
         else if ( opt.startsWith(generatedTypesHeaderFileOptPrefix) )
            typeFilesHeader = readString(Paths.get(opt.substring(generatedTypesHeaderFileOptPrefix.length())));
         else if ( opt.equals(javaGenerateGetters) )
            generateJavaGetters = true;
         else if ( opt.equals(javaGenerateSetters) )
            generateJavaSetters = true;
         else
            throw new RuntimeException("Unrecognized option \"" + opt + "\".");
      }

      var sourcesLanguage = SourceCodeLanguage.valueOf(langStr);

      switch ( sourcesLanguage )
      {
         case Java:
            return new JavaWriter(
               targetPackage,
               srcOutputBaseDir,
               nullableFieldRepr,
               typeFilesHeader,
               sqlResourceNamePrefix,
               generateJavaGetters,
               generateJavaSetters
            );
         case TypeScript:
            return new TypeScriptWriter(
               srcOutputBaseDir,
               typeFilesHeader,
               sqlResourceNamePrefix
            );
         default:
            throw new RuntimeException("target language not supported");
      }
   }

   private static List<QueryReprSqlPath> writeQuerySqls
      (
         String queryName,
         Map<ResultRepr,String> resultReprToSqlMap,
         Path outputDir
      )
      throws IOException
   {
      var res = new ArrayList<QueryReprSqlPath>();

      for ( var entry : resultReprToSqlMap.entrySet() )
      {
         ResultRepr repr = entry.getKey();
         String sql = entry.getValue();
         String fileName = queryName + "(" + repr.toString().toLowerCase().replace('_',' ') + ").sql";
         Path outputFilePath = outputDir.resolve(fileName);

         try ( BufferedWriter bw = newFileOrStdoutWriter(outputFilePath) )
         {
            bw.write(
               "-- [ THIS QUERY WAS AUTO-GENERATED, ANY CHANGES MADE HERE MAY BE LOST. ]\n" +
                  "-- " + repr + " results representation for " + queryName + "\n" +
                  sql + "\n"
            );

            res.add(new QueryReprSqlPath(queryName, repr, outputFilePath));
         }
      }

      return res;
   }

   private static List<String> getParamNames(QuerySpec querySpec)
   {
      return getParamNames(querySpec.getTableJson());
   }

   private static List<String> getParamNames(TableJsonSpec tableSpec)
   {
      List<String> paramNames = new ArrayList<>();

      for ( var childSpec: tableSpec.getChildTableCollectionsList() )
         paramNames.addAll(getParamNames(childSpec.getTableJson()));

      for ( var parentSpec : tableSpec.getParentTablesList() )
         paramNames.addAll(getParamNames(parentSpec.getParentTableJsonSpec()));

      @Nullable RecordCondition recCond = tableSpec.getRecordCondition();
      if ( recCond != null && recCond.getParamNames() != null )
         paramNames.addAll(requireNonNull(recCond.getParamNames()));

      return paramNames;
   }

   private static Function<String,String> getPropertyNamer(QueryGroupSpec queryGroupSpec)
   {
      return queryGroupSpec.getPropertyNameDefault().toFunctionOfFieldName();
   }
}
