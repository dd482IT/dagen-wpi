package org.sqljson;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import static java.util.Collections.emptyList;
import static java.util.Optional.empty;
import static java.util.stream.Collectors.*;

import org.sqljson.dbmd.DatabaseMetadata;
import org.sqljson.dbmd.RelId;
import org.sqljson.dbmd.RelMetadata;
import org.sqljson.specs.FieldParamCondition;
import org.sqljson.specs.mod_stmts.ParametersType;
import org.sqljson.util.AppUtils;
import org.sqljson.specs.mod_stmts.ModSpec;
import org.sqljson.specs.mod_stmts.TableInputField;
import static org.sqljson.specs.mod_stmts.ParametersType.NAMED;
import static org.sqljson.util.DatabaseUtils.verifyTableFieldsExist;
import static org.sqljson.util.Optionals.opt;
import static org.sqljson.util.StringFuns.*;


public class ModStatementGenerator
{
   private final DatabaseMetadata dbmd;
   private final Optional<String> defaultSchema;
   private final Set<String> unqualifiedNamesSchemas;
   private final int indentSpaces;
   private static final Pattern simpleParamNameRegex = Pattern.compile(":[a-zA-Z0-9_]+");

   public ModStatementGenerator
   (
      DatabaseMetadata dbmd,
      Optional<String> defaultSchema,
      Set<String> unqualifiedNamesSchemas
   )
   {
      this.dbmd = dbmd;
      this.defaultSchema = defaultSchema;
      this.unqualifiedNamesSchemas = unqualifiedNamesSchemas.stream().map(dbmd::normalizeName).collect(toSet());
      this.indentSpaces = 2;
   }

   public List<GeneratedModStatement> generateModStatements(List<ModSpec> modSpecs)
   {
      return modSpecs.stream().map(this::generateModStatement).collect(toList());
   }

   private GeneratedModStatement generateModStatement(ModSpec mod)
   {
      validateInputFieldValues(mod);

      switch ( mod.getCommand() )
      {
         case INSERT: return generateInsertStatement(mod);
         case UPDATE: return generateUpdateStatement(mod);
         case DELETE: return generateDeleteStatement(mod);
         default: throw new RuntimeException("Unexpected mod command " + mod.getCommand());
      }
   }

   private void validateInputFieldValues(ModSpec modSpec)
   {
      boolean namedParams = modSpec.getParametersType() == NAMED;

      for ( TableInputField inputField : modSpec.getInputFields() )
      {
         String value = getInputFieldValue(inputField, modSpec);

         boolean notParenthesized = !value.startsWith("(") || !value.endsWith(")");

         if ( namedParams )
         {
            if ( notParenthesized && !simpleParamNameRegex.matcher(value).matches() )
               throw new RuntimeException(
                  "Invalid parameter value for field \"" + inputField.getField() + "\" " +
                  "of statement \"" + modSpec.getStatementName() + "\". General expressions must be parenthesized."
               );
         }
         else // numbered params
         {
            if ( notParenthesized && !value.equals("?") )
                  throw new RuntimeException(
                     "Numbered parameter \"" + inputField.getField() + "\" value must be either \"?\" " +
                        "(the default) or else a parenthesized expression."
                  );
         }
      }
   }

   private GeneratedModStatement generateInsertStatement(ModSpec modSpec)
   {
      if ( modSpec.getTableAlias().isPresent() )
         AppUtils.throwError("A table alias is not allowed in an INSERT command.");
      if ( !modSpec.getFieldParamConditions().isEmpty() || modSpec.getCondition().isPresent() )
         AppUtils.throwError("Conditions are not allowed for INSERT commands.");

      String sql = makeInsertSql(modSpec);

      List<String> inputFieldParams = getInputFieldParamNames(modSpec);

      return new GeneratedModStatement(modSpec, sql, inputFieldParams, emptyList());
   }

   private String makeInsertSql(ModSpec modSpec)
   {
      RelId relId = dbmd.identifyTable(modSpec.getTable(), defaultSchema);

      verifyAllReferencedFieldsExist(modSpec, relId);

      String fieldNames =
         modSpec.getInputFields().stream()
         .map(TableInputField::getField)
         .collect(joining(",\n"));

      String fieldValues =
         modSpec.getInputFields().stream()
         .map(f -> getInputFieldValue(f, modSpec))
         .collect(joining(",\n"));

      return
         "insert into " + minimalRelIdentifier(relId) + "\n" +
            "  (\n" +
               indentLines(fieldNames, 2 + indentSpaces) + "\n" +
            "  )\n" +
            "values\n" +
            "  (\n" +
               indentLines(fieldValues, 2 + indentSpaces) + "\n" +
            "  )";
   }

   private GeneratedModStatement generateUpdateStatement(ModSpec modSpec)
   {
      String sql = makeUpdateSql(modSpec);

      List<String> inputFieldParams = getInputFieldParamNames(modSpec);

      List<String> conditionParams = getConditionParamNames(modSpec);

      return new GeneratedModStatement(modSpec, sql, inputFieldParams, conditionParams);
   }

   private String makeUpdateSql(ModSpec modSpec)
   {
      RelId relId = dbmd.identifyTable(modSpec.getTable(), defaultSchema);

      verifyAllReferencedFieldsExist(modSpec, relId);

      String fieldAssignments =
         modSpec.getInputFields().stream()
         .map(f -> f.getField() + " = " + getInputFieldValue(f, modSpec))
         .collect(joining(",\n"));

      if ( modSpec.getInputFields().isEmpty() )
         throw new RuntimeException("At least one field is required in an update modification command.");

      Optional<String> whereCond = getCondition(modSpec);

      return
         "update " + minimalRelIdentifier(relId) +
            modSpec.getTableAlias().map(a -> " " + a).orElse("") + "\n" +
            "set\n" +
            indentLines(fieldAssignments, indentSpaces) +
            whereCond.map(cond ->
               "\nwhere (\n" +
                  indentLines(cond, indentSpaces) + "\n" +
               ")"
            ).orElse("");
   }

   private GeneratedModStatement generateDeleteStatement(ModSpec modSpec)
   {
      if ( !modSpec.getInputFields().isEmpty() )
         AppUtils.throwError("Fields are not allowed in a delete command.");

      String sql = makeDeleteSql(modSpec);

      List<String> conditionParams = getConditionParamNames(modSpec);

      return new GeneratedModStatement(modSpec, sql, emptyList(), conditionParams);
   }

   private String makeDeleteSql(ModSpec modSpec)
   {
      RelId relId = dbmd.identifyTable(modSpec.getTable(), defaultSchema);

      verifyAllReferencedFieldsExist(modSpec, relId);

      Optional<String> whereCond = getCondition(modSpec);

      return
         "delete from " + minimalRelIdentifier(relId) +
            modSpec.getTableAlias().map(a -> " " + a).orElse("") +
         whereCond.map(cond ->
            "\nwhere (\n"
               + indentLines(cond, indentSpaces) + "\n" +
            ")"
         ).orElse("");
   }

   private String getInputFieldValue
   (
      TableInputField f,
      ModSpec modSpec
   )
   {
      return f.getValue().orElseGet(() ->
         getDefaultParamValueExpression(f, modSpec)
      );
   }

   private String getDefaultParamValueExpression(TableInputField inputField, ModSpec modSpec)
   {
      switch ( modSpec.getParametersType() )
      {
         case NAMED: return  ":" + getDefaultInputFieldParamName(inputField.getField());
         case NUMBERED: return "?";
         default: throw new RuntimeException("Unexpected parameter name default enumeration value.");
      }
   }

   private String getDefaultInputFieldParamName(String inputFieldName)
   {
      return lowerCamelCase(unDoubleQuote(inputFieldName));
   }

   /// Return names for parameters used in the table input fields of the passed mod statement specification.
   /// If params are of numbered type ("?" params), then the parameter names are merely descriptive and are
   /// used to determine source code member names which store the parameter numbers.
   private List<String> getInputFieldParamNames(ModSpec modSpec)
   {
      List<String> res = new ArrayList<>();

      for ( TableInputField inputField : modSpec.getInputFields() )
      {
         if ( inputField.hasSimpleParamValue() )
         {
            switch ( modSpec.getParametersType() )
            {
               case NAMED:
               {
                  String paramValueExpr = getInputFieldValue(inputField, modSpec);

                  if ( !paramValueExpr.startsWith(":") )
                     throw new RuntimeException(
                        "Failed to determine parameter name for field \"" + inputField.getField() + "\" " +
                        "in statement specification \"" + modSpec.getStatementName() + "\" (expected leading  ':')."
                     );

                  res.add(paramValueExpr.substring(1));
                  break;
               }
               case NUMBERED:
                  res.add(getDefaultInputFieldParamName(inputField.getField()));
                  break;
               default:
                  throw new RuntimeException("Unrecognized parameters type " + modSpec.getParametersType());
            }
         }
         else // Input field spec has custom expression value (parenthesized), it must list any involved param names.
         {
            String valueExpression = inputField.getValue().orElseThrow(() ->
               new RuntimeException("Programming error: input value should be present when not a simple param value.")
            );
            // Check that the declared parameters actually occur in the value expression string.
            if ( modSpec.getParametersType() == NAMED )
            {
               for ( String exprValParam : inputField.getExpressionValueParamNames() )
                  if ( !valueExpression.contains(":" + exprValParam) )
                     throw new RuntimeException(
                        "Param \"" + exprValParam + "\" not detected in value expresion for input field " +
                           "\"" + inputField.getField() + "\" of statement \"" + modSpec.getStatementName() + "\"."
                     );
            }

            res.addAll(inputField.getExpressionValueParamNames());
         }
      }

      return res;
   }

   private String getDefaultCondParamName(String inputFieldName)
   {
      return lowerCamelCase(unDoubleQuote(inputFieldName)) + "Cond";
   }


   private List<String> getConditionParamNames(ModSpec modSpec)
   {
      return
        modSpec.getFieldParamConditions().stream()
        .map(eq -> eq.getParamName().orElse(getDefaultCondParamName(eq.getField())))
        .collect(toList());
   }

   private Optional<String> getCondition(ModSpec modSpec)
   {
      List<String> conds = new ArrayList<>();

      for ( FieldParamCondition fieldParamCond : modSpec.getFieldParamConditions() )
      {
         conds.add(
            fieldParamCond.toSql(
               modSpec.getTableAlias(),
               modSpec.getParametersType(),
               this::getDefaultCondParamName
            )
         );
      }

      // Other condition goes last so it will not interfere with parameter numbering in case it introduces its own params.
      modSpec.getCondition().ifPresent(otherCond -> conds.add("(" + otherCond + ")"));

      return conds.isEmpty() ? empty() : opt(String.join("\nand\n", conds));
   }

   public String getDefaultInputFieldValue(String inputFieldName, ParametersType parametersType)
   {
      switch ( parametersType )
      {
         case NAMED: return  ":" + getDefaultInputFieldParamName(inputFieldName);
         case NUMBERED: return "?";
         default: throw new RuntimeException("Unexpected parameter name default enumeration value.");
      }
   }

   /// Return a possibly qualified identifier for the given table, omitting the schema
   /// qualifier if it has a schema for which it's specified to use unqualified names.
   private String minimalRelIdentifier(RelId relId)
   {
      if ( !relId.getSchema().isPresent() ||
         unqualifiedNamesSchemas.contains(dbmd.normalizeName(relId.getSchema().get())) )
         return relId.getName();
      else
         return relId.getIdString();
   }

   private void verifyAllReferencedFieldsExist
   (
      ModSpec modSpec,
      RelId relId
   )
      throws DatabaseObjectsNotFoundException
   {
      RelMetadata relMetadata = dbmd.getRelationMetadata(relId).orElseThrow(() ->
         new DatabaseObjectsNotFoundException("Table " + relId.toString() + " not found.")
      );

      List<String> inputFieldNames = modSpec.getInputFields().stream().map(TableInputField::getField).collect(toList());
      verifyTableFieldsExist(inputFieldNames, relMetadata, dbmd);

      List<String> whereCondFieldNames =
         modSpec.getFieldParamConditions().stream().map(FieldParamCondition::getField).collect(toList());
      verifyTableFieldsExist(whereCondFieldNames, relMetadata, dbmd);
   }

   private String commaJoinFieldNames(List<TableInputField> fields)
   {
      return fields.stream().map(TableInputField::getField).collect(joining(","));
   }
}
