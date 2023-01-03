package org.sqljson.query_specs;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import org.checkerframework.checker.nullness.qual.Nullable;

import org.sqljson.dbmd.DatabaseMetadata;
import org.sqljson.dbmd.Field;
import org.sqljson.dbmd.RelId;
import org.sqljson.dbmd.RelMetadata;


public final class QuerySpecValidations
{
   public static RelId identifyTable
      (
         String table, // as from input, possibly qualified
         String defaultSchema,
         DatabaseMetadata dbmd,
         SpecLocation specLocation
      )
      throws SpecError
   {
      RelMetadata relMd = dbmd.getRelationMetadata(dbmd.toRelId(table, defaultSchema));

      if ( relMd == null )
         throw new SpecError(
            specLocation,
            "Table '" + table + "' was not found in database metadata."
         );

      return relMd.getRelationId();
   }

   public static void verifyTableFieldExpressionsValid
      (
         TableJsonSpec tableSpec,
         String defaultSchema,
         DatabaseMetadata dbmd,
         SpecLocation specLoc
      )
   {
      if ( tableSpec.getFieldExpressions() == null )
         return;

      var simpleSelectFields = new ArrayList<String>();

      var fieldExprs = tableSpec.getFieldExpressionsList();
      for ( int ix=0; ix < fieldExprs.size(); ++ix )
      {
         var fieldExpr = fieldExprs.get(ix);
         String field = fieldExpr.getField();
         String expr = fieldExpr.getExpression();

         if ( expr != null && fieldExpr.getFieldTypeInGeneratedSource() == null )
            throw new SpecError(specLoc,
               "fieldExpressions entry #" + (ix+1) + " is invalid: " +
                  "fieldTypeInGeneratedSource must be specified with the 'expression' property."
            );

         if ( (field == null) == (expr == null) )
            throw new SpecError(specLoc,
               "fieldExpressions entry #" + (ix+1) + " is invalid: " +
               "exactly one of 'field' or 'expression' properties must be provided."
            );

         if (  field != null )
            simpleSelectFields.add(field);
      }

      RelMetadata relMd = dbmd.getRelationMetadata(dbmd.toRelId(tableSpec.getTable(), defaultSchema));
      if ( relMd == null )
         throw new SpecError(specLoc, "Table '" + tableSpec.getTable() + "' was not found in database metadata.");

      verifyTableFieldsExist(relMd, simpleSelectFields, dbmd, specLoc);
   }

   public static void validateCustomJoinCondition
      (
         CustomJoinCondition customJoinCond,
         RelId childRelId,
         RelId parentRelId,
         DatabaseMetadata dbmd,
         SpecLocation stmtLoc
      )
      throws SpecError
   {
      RelMetadata parentMd = dbmd.getRelationMetadata(parentRelId);
      RelMetadata childMd = dbmd.getRelationMetadata(childRelId);

      if ( parentMd == null )
         throw new SpecError(stmtLoc.addPart("custom join condition"), "Parent table not found.");
      if ( childMd == null )
         throw new SpecError(stmtLoc.addPart("custom join condition"), "Child table not found.");

      List<String> parentMatchFields =
         customJoinCond.getEquatedFields().stream()
         .map(CustomJoinCondition.FieldPair::getParentPrimaryKeyField)
         .collect(toList());

      verifyTableFieldsExist(parentMd, parentMatchFields, dbmd, stmtLoc);

      List<String> childMatchFields =
         customJoinCond.getEquatedFields().stream()
         .map(CustomJoinCondition.FieldPair::getChildField)
         .collect(toList());

      verifyTableFieldsExist(childMd, childMatchFields, dbmd, stmtLoc);
   }

   private static void verifyTableFieldsExist
      (
         RelMetadata relMd,
         List<String> fieldNames,
         DatabaseMetadata dbmd,
         SpecLocation specLocation
      )
      throws SpecError
   {
      Set<String> dbmdTableFields = relMd.getFields().stream().map(Field::getName).collect(toSet());

      List<String> missingFields =
         fieldNames.stream()
            .filter(fieldName -> !dbmdTableFields.contains(dbmd.normalizeName(fieldName)))
            .collect(toList());

      if ( !missingFields.isEmpty() )
         throw new SpecError(
            specLocation,
            "Field(s) not found in table " + relMd.getRelationId() + ": " + missingFields + "."
         );
   }

   private QuerySpecValidations() {}
}

