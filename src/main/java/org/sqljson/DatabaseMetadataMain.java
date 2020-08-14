package org.sqljson;

import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import org.sqljson.util.IO;
import org.sqljson.dbmd.DatabaseMetadata;
import org.sqljson.dbmd.DatabaseMetadataFetcher;
import static org.sqljson.util.Nullables.*;
import static org.sqljson.util.Props.getProperty;
import static org.sqljson.util.Props.requireProperty;


public class DatabaseMetadataMain
{
   public static void printUsage(PrintStream ps)
   {
      ps.println("Expected arguments: jdbc-properties-file [dbmd-properties-file] output-file|-");

      ps.println(
         "jdbc properties file properties:\n  " +
            "  jdbc-driver-class\n" +
            "  jdbc-connect-url\n" +
            "  user\n" +
            "  password\n"
      );

      ps.println(
         "dbmd properties file properties:\n  " +
            "  date-mapping (DATES_AS_DRIVER_REPORTED | DATES_AS_TIMESTAMPS | DATES_AS_DATES)\n" +
            "  schema (schema name | *any-owners*)\n" +
            "  exclude-relations-fqname-regex\n"
      );
   }

   public static void main(String[] args)
   {
      try
      {
         execCommandLine(args);
      }
      catch(Exception e)
      {
         System.err.println(e.getMessage());
         System.exit(1);
      }
   }

   public static void execCommandLine(String[] args) throws Exception
   {
      if ( args.length == 1 && args[0].equals("-h") || args[0].equals("--help") )
      {
         printUsage(System.out);
         return;
      }
      else if ( args.length < 2 )
      {
         throw new RuntimeException("Expected 2 or more arguments");
      }

      int argIx = 0;

      String jdbcPropsFilePath = args[argIx++];
      @Nullable String dbmdPropsFilePath = args.length == 3 ? args[argIx++] : null;
      String outputFilePath = args[argIx];

      Properties props = new Properties();

      try (OutputStream os = IO.outputStream(outputFilePath);
           InputStream propsIS = new FileInputStream(jdbcPropsFilePath) )
      {
         props.load(propsIS);

         String driverClassname = requireProperty(props, "jdbc-driver-class", "jdbc.driverClassName");
         String connStr = requireProperty(props, "jdbc-connect-url", "jdbc.url");
         String user = requireProperty(props, "user", "jdbc.username");
         String password = requireProperty(props, "password", "jdbc.password");

         Class.forName(driverClassname);

         try ( Connection conn = DriverManager.getConnection(connStr, user, password) )
         {
            ifPresent(dbmdPropsFilePath, path -> {
               try
               {
                  if ( !jdbcPropsFilePath.equals(path) )
                     props.load(new FileInputStream(path));
               }
               catch (IOException ioe) { throw new RuntimeException(ioe); }
            });

            @Nullable String dateMappingStr = getProperty(props, "date-mapping");
            DatabaseMetadataFetcher.DateMapping dateMapping =
               dateMappingStr != null ? DatabaseMetadataFetcher.DateMapping.valueOf(dateMappingStr)
                  : DatabaseMetadataFetcher.DateMapping.DATES_AS_DRIVER_REPORTED;

            @Nullable String relsOwner = getProperty(props, "schema", "relations-owner");
            if ( Objects.equals(relsOwner, "*any-owners*") )
               relsOwner = null;

            @Nullable Pattern excludeRelsPat =
                applyIfPresent(getProperty(props, "exclude-relations-fqname-regex"), Pattern::compile);

            DatabaseMetadata dbmd =
               new DatabaseMetadataFetcher(dateMapping)
               .fetchMetadata(
                  conn.getMetaData(),
                  relsOwner,
                  true,
                  true,
                  true,
                  excludeRelsPat
               );

            String outputFormat = valueOr(props.getProperty("output-format"), "json");

            switch ( outputFormat )
            {
               case "json":
               {
                  ObjectMapper mapper = new ObjectMapper();
                  mapper.registerModule(new Jdk8Module());
                  mapper.enable(SerializationFeature.INDENT_OUTPUT);
                  mapper.writeValue(os, dbmd);
                  break;
               }
               case "yaml":
               {
                  ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
                  mapper.registerModule(new Jdk8Module());
                  mapper.writeValue(os, dbmd);
                  break;
               }
               default:
                  throw new RuntimeException("output format in property must be json or yaml");
                }
            }
        }
    }
}

