package org.sqljson;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import org.sqljson.source_code_writers.SourceCodeLanguage;
import org.sqljson.util.AppUtils.SplitArgs;
import static org.sqljson.util.AppUtils.splitOptionsAndRequiredArgs;
import static org.sqljson.util.AppUtils.throwError;
import static org.sqljson.util.Nullables.ifPresent;
import org.sqljson.dbmd.DatabaseMetadata;
import org.sqljson.dbmd.source_writers.JavaWriter;
import org.sqljson.dbmd.source_writers.SourceCodeWriter;
import org.sqljson.dbmd.source_writers.TypeScriptWriter;


public class RelationMetadataSourceGeneratorMain
{
   private static final String langOptPrefix = "--types-language:";
   private static final String pkgOptPrefix = "--package:";
   private static final String javaNullabilityOptPrefix = "--java-nullability:";
   private static final String includeSourceGenerationTimestamp = "--include-source-gen-timestamp";
   private static final String javaGenerateGetters = "--java-generate-getters";
   private static final String javaGenerateSetters = "--java-generate-setters";

   private static void printUsage()
   {
      PrintStream ps = System.out;
      ps.println("Expected arguments: [options] <db-metadata-file> [<src-output-base-dir>");
      ps.println("If output directory is not provided, then all output is written to standard out.");
      ps.println("Options:");
      ps.println("   " + langOptPrefix + "<language>  Output language, \"Java\"|\"TypeScript\".");
      ps.println("   " + pkgOptPrefix + "<java-package>  The Java package for the generated source class(es).");
      ps.println("   " + javaNullabilityOptPrefix + "<nullable-fields-option>  How nullable fields should be" +
                 "represented in Java.");
      ps.println("       Valid options are:");
      ps.println("         annotated  : annotate nullable fields with with @Nullable.");
      ps.println("         optwrapped : wrap the type with Optional<>");
      ps.println("         baretype   : leave as bare type (Object variant for native types)");
      ps.println("   " + javaGenerateGetters + "  Include getters in generated Java types.");
      ps.println("   " + javaGenerateSetters + "  Include setters in generated Java types.");
   }

   public static void main(String[] allArgs)
   {
      if ( allArgs.length == 1 && allArgs[0].equals("-h") || allArgs[0].equals("--help") )
      {
         printUsage();
         return;
      }

      SplitArgs args = splitOptionsAndRequiredArgs(allArgs);

      if ( args.required.size() != 1 && args.required.size() != 2 )
         throw new RuntimeException("expected 1 or 2 non-option arguments");

      Path dbmdPath = Paths.get(args.required.get(0));
      if ( !Files.isRegularFile(dbmdPath) )
         throwError("Database metadata file not found.");

      Path outputDir = args.required.size() > 1 ? Paths.get(args.required.get(1)) : null;

      try ( var dbmdIS = Files.newInputStream(dbmdPath) )
      {
         ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
         yamlMapper.registerModule(new Jdk8Module());

         DatabaseMetadata dbmd = yamlMapper.readValue(dbmdIS, DatabaseMetadata.class);

         ifPresent(outputDir, path ->  {
            if ( !Files.isDirectory(path) ) throwError("Source output directory not found.");
         });

         SourceCodeWriter srcWriter = getSourceCodeWriter(args, outputDir);
         boolean includeTimestamp = args.optional.contains(includeSourceGenerationTimestamp);
         srcWriter.writeRelationDefinitions(dbmd, includeTimestamp);
      }
      catch( Exception e )
      {
         e.printStackTrace();
         System.err.println(e.getMessage());
         System.exit(1);
      }
   }

   private static SourceCodeWriter getSourceCodeWriter
      (
         SplitArgs args,
         Path srcOutputBaseDir
      )
   {
      String langStr = "";
      String targetPackage = "";

      for ( String opt : args.optional )
      {
         if ( opt.startsWith(langOptPrefix) )
            langStr = opt.substring(langOptPrefix.length());
         else if ( opt.startsWith(pkgOptPrefix) )
            targetPackage = opt.substring(pkgOptPrefix.length());
         else
            throw new RuntimeException("Unrecognized option \"" + opt + "\".");
      }

      var sourcesLanguage = SourceCodeLanguage.valueOf(langStr);

      switch ( sourcesLanguage )
      {
         case Java:
            return new JavaWriter(targetPackage, srcOutputBaseDir);
         case TypeScript:
            return new TypeScriptWriter(srcOutputBaseDir);
         default:
            throw new RuntimeException("target language not supported");
      }
   }
}
