package org.sqljson.result_types;

import java.util.*;
import java.util.function.Function;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.*;
import static java.util.function.Function.identity;

import org.checkerframework.checker.nullness.qual.Nullable;

import org.sqljson.util.StringFuns;
import org.sqljson.dbmd.*;
import org.sqljson.query_specs.*;
import static org.sqljson.util.Nullables.*;


public class ResultTypesGenerator
{
   private final DatabaseMetadata dbmd;
   private final String defaultSchema;
   private final Function<String,String> defaultPropertyNameFn;

   public ResultTypesGenerator
      (
         DatabaseMetadata dbmd,
         String defaultSchema,
         Function<String,String> defaultPropertyNameFn
      )
   {
      this.dbmd = dbmd;
      this.defaultSchema = defaultSchema;
      this.defaultPropertyNameFn = defaultPropertyNameFn;
   }

   public List<ResultType> generateResultTypes(TableJsonSpec tjs)
   {
      return this.generateResultTypesWithTypesInScope(tjs, emptyMap());
   }

   @SuppressWarnings("keyfor")
   private List<ResultType> generateResultTypesWithTypesInScope
      (
         TableJsonSpec tjs,
         Map<String,ResultType> envTypesInScope // types by type name
      )
   {
      Map<String,ResultType> typesInScope = new HashMap<>(envTypesInScope);

      var typeBuilder = new ResultTypeBuilder();
      var resultTypes = new ArrayList<ResultType>();

      RelId relId = dbmd.toRelId(tjs.getTable(), defaultSchema);

      // Add the table's own fields and expressions involving those fields.
      typeBuilder.addSimpleTableFieldProperties(getSimpleTableFieldProperties(relId, tjs.getFieldExpressionsList()));
      typeBuilder.addTableExpressionProperties(getTableExpressionProperties(relId, tjs.getFieldExpressionsList()));

      // Inline parents can contribute fields to any primary field category (table field,
      // expression, parent ref, child collection). Get the inline parent fields, and the result
      // types from the tables themselves and recursively from their specified related tables.
      var inlineParentsContr = getInlineParentContrs(relId, tjs.getInlineParentTablesList(), typesInScope);
      typeBuilder.addAllFieldsFrom(inlineParentsContr.typeBuilder);
      resultTypes.addAll(inlineParentsContr.resultTypes);
      inlineParentsContr.resultTypes.forEach(t -> typesInScope.put(t.getTypeName(), t));

      // Get referenced parent fields and result types, with result types from related tables.
      var refdParentsContr = getRefdParentContrs(relId, tjs.getReferencedParentTablesList(), typesInScope);
      typeBuilder.addParentReferenceProperties(refdParentsContr.parentReferenceProperties);
      resultTypes.addAll(refdParentsContr.resultTypes);
      refdParentsContr.resultTypes.forEach(t -> typesInScope.put(t.getTypeName(), t));

      // Get the child collection fields and result types, with result types from related tables.
      var childCollsContr = getChildCollectionContrs(tjs.getChildTableCollectionsList(), typesInScope);
      typeBuilder.addChildCollectionProperties(childCollsContr.childCollectionProperties);
      resultTypes.addAll(childCollsContr.resultTypes);
      childCollsContr.resultTypes.forEach(t -> typesInScope.put(t.getTypeName(), t));

      // The top table's type must be added at leading position in the returned list.
      // If the type is identical to one already in scope when ignoring only any name
      // extension added to make the name unique, then add the previously generated
      // instance instead.
      String baseTypeName = StringFuns.upperCamelCase(tjs.getTable()); // Base type name is the desired name, without any trailing digits.
      ResultType bnResType = typeBuilder.build(baseTypeName);
      if ( !typesInScope.containsKey(baseTypeName) ) // No previously generated type of same base name.
         resultTypes.add(0, bnResType);
      else
      {
         // Search the previous scope (prior to this type-building) for a type which is identical
         // except for any additions to the name to make it unique. Note: Only the original
         // scope (prior to this type building) is searched because the additions made here
         // to typesInScope are proper parts of the type and so not identical to the whole type.
         ResultType existingIdenticalType = findTypeIgnoringNameExtensions(bnResType, envTypesInScope);
         if ( existingIdenticalType != null ) // Identical previously generated type found, use it as top type.
            resultTypes.add(0, existingIdenticalType);
         else // This type does not match any previously generated, but needs a new name.
         {
            String uniqueName = StringFuns.makeNameNotInSet(baseTypeName, typesInScope.keySet(), "_");
            resultTypes.add(0, bnResType.withTypeName(uniqueName));
         }
      }

      return resultTypes;
   }

   private List<SimpleTableFieldProperty> getSimpleTableFieldProperties
      (
         RelId relId,
         List<TableFieldExpr> tableFieldExpressions
      )
   {
      var fields = new ArrayList<SimpleTableFieldProperty>();

      Map<String,Field> dbFieldsByName = getTableFieldsByName(relId);

      for ( TableFieldExpr tfe : tableFieldExpressions )
      {
         if ( tfe.getField() != null )
         {
            Field dbField = requireNonNull(dbFieldsByName.get(dbmd.normalizeName(requireNonNull(tfe.getField()))),
               "no metadata for field " + relId + "." + tfe.getField());
            fields.add(new SimpleTableFieldProperty(getOutputFieldName(tfe, dbField), dbField, tfe.getFieldTypeInGeneratedSource()));
         }
      }

      return fields;
   }

   private List<TableExpressionProperty> getTableExpressionProperties
      (
         RelId relId,
         List<TableFieldExpr> tableFieldExpressions
      )
   {
      var fields = new ArrayList<TableExpressionProperty>();

      for ( TableFieldExpr tfe : tableFieldExpressions )
      {
         if ( tfe.getExpression() != null )
         {
            String jsonProperty = valueOrThrow(tfe.getJsonProperty(), () ->
                new RuntimeException("Expression field " + relId + "." + tfe + " requires a json property.")
            );
            fields.add(new TableExpressionProperty(jsonProperty, tfe.getExpression(), tfe.getFieldTypeInGeneratedSource()));
         }
      }

      return fields;
   }

   /// Get the inline parent contributions of properties and result types for the type to be generated.
   private InlineParentContrs getInlineParentContrs
      (
         RelId relId,
         List<ParentSpec> inlineParentSpecs,
         Map<String,ResultType> envTypesInScope
      )
   {
      var typeBuilder = new ResultTypeBuilder();
      var resultTypes = new ArrayList<ResultType>();

      var typesInScope = new HashMap<>(envTypesInScope);

      for ( var parentSpec :  inlineParentSpecs )
      {
         // Generate types for the parent table and any related tables it includes recursively.
         List<ResultType> parentResultTypes = generateResultTypesWithTypesInScope(parentSpec.getParentTableJsonSpec(), typesInScope);
         ResultType parentType = parentResultTypes.get(0); // will not be generated

         // If the parent record might be absent, then all inline fields must be nullable.
         boolean forceNullable =
            parentSpec.getParentTableJsonSpec().hasCondition() ||
            !someFkFieldKnownNotNullable(parentSpec, relId);

         typeBuilder.addAllFieldsFrom(parentType, forceNullable);

         var actuallyGeneratedParentTypes = parentResultTypes.subList(1, parentResultTypes.size());
         resultTypes.addAll(actuallyGeneratedParentTypes);
         actuallyGeneratedParentTypes.forEach(t -> typesInScope.put(t.getTypeName(), t));
      }

      return new InlineParentContrs(typeBuilder, resultTypes);
   }

   /// Get fields and types from the given referenced parents.
   private RefdParentContrs getRefdParentContrs
      (
         RelId relId,
         List<ParentSpec> referencedParentSpecs,
         Map<String, ResultType> envTypesInScope
      )
   {
      var parentRefFields = new ArrayList<ParentReferenceProperty>();
      var resultTypes = new ArrayList<ResultType>();

      var typesInScope = new HashMap<>(envTypesInScope);

      for ( var parentSpec : referencedParentSpecs )
      {
         String refName = requireNonNull(parentSpec.getReferenceName());

         // Generate types by traversing the parent table and its parents and children.
         List<ResultType> parentResultTypes = generateResultTypesWithTypesInScope(parentSpec.getParentTableJsonSpec(), typesInScope);
         ResultType parentType = parentResultTypes.get(0);

         boolean forceNullable =
            parentSpec.getParentTableJsonSpec().hasCondition() ||
            !someFkFieldKnownNotNullable(parentSpec, relId);

         parentRefFields.add(new ParentReferenceProperty(refName, parentType, forceNullable));

         resultTypes.addAll(parentResultTypes);
         parentResultTypes.forEach(t -> typesInScope.put(t.getTypeName(), t));
      }

      return new RefdParentContrs(parentRefFields, resultTypes);
   }

   private ChildCollectionContrs getChildCollectionContrs
      (
         List<ChildCollectionSpec> childCollectionSpecs,
         Map<String, ResultType> envTypesInScope
      )
   {
      var childCollectionProperties = new ArrayList<ChildCollectionProperty>();
      var resultTypes = new ArrayList<ResultType>();

      var typesInScope = new HashMap<>(envTypesInScope);

      for ( var childCollSpec : childCollectionSpecs )
      {
         // Generate types by traversing the child table and its parents and children recursively.
         List<ResultType> childResultTypes = generateResultTypesWithTypesInScope(childCollSpec.getTableJson(), typesInScope);

         // Mark the top-level child type as unwrapped if specified.
         ResultType childType = childResultTypes.get(0).withUnwrapped(valueOr(childCollSpec.getUnwrap(), false));
         childResultTypes.set(0, childType);

         childCollectionProperties.add(new ChildCollectionProperty(childCollSpec.getCollectionName(), childType, false));

         resultTypes.addAll(childResultTypes);
         childResultTypes.forEach(t -> typesInScope.put(t.getTypeName(), t));
      }

      return new ChildCollectionContrs(childCollectionProperties, resultTypes);
   }

   private String getOutputFieldName
      (
         TableFieldExpr tableFieldExpr,
         Field dbField
      )
   {
      return valueOrGet(tableFieldExpr.getJsonProperty(), () -> defaultPropertyNameFn.apply(dbField.getName()));
   }

   private Map<String,Field> getTableFieldsByName(RelId relId)
   {
      RelMetadata relMd = valueOrThrow(dbmd.getRelationMetadata(relId), () ->
         new RuntimeException("Metadata for table " + relId + " not found.")
      );

      return relMd.getFields().stream().collect(toMap(Field::getName, identity()));
   }

   private ResultType findTypeIgnoringNameExtensions
      (
         ResultType typeToFind,
         Map<String, ResultType> inMap
      )
   {
      String baseName = typeToFind.getTypeName();

      for ( var entry: inMap.entrySet() )
      {
         boolean baseNamesMatch =
            entry.getKey().startsWith(baseName) &&
            (entry.getKey().equals(baseName) ||
             entry.getKey().charAt(baseName.length()) == '_'); // underscore used as suffix separator for making unique names

         if ( baseNamesMatch && typeToFind.equalsIgnoringName(entry.getValue()) )
            return entry.getValue();
      }

      return null;
   }

   private boolean someFkFieldKnownNotNullable(ParentSpec parentSpec, RelId childRelId)
   {
      RelId parentRelId = dbmd.toRelId(parentSpec.getParentTableJsonSpec().getTable(), defaultSchema);
      Set<String> specFkFields = parentSpec.getChildForeignKeyFieldsSet();
      ForeignKey fk = valueOrThrow(dbmd.getForeignKeyFromTo(childRelId, parentRelId, specFkFields, ForeignKeyScope.REGISTERED_TABLES_ONLY), () ->
         new RuntimeException("foreign key to parent not found")
      );

      Map<String,Field> childFieldsByName = getTableFieldsByName(childRelId);

      for ( String fkFieldName : fk.getChildFieldNames() )
      {
         Field fkField = valueOrThrow(childFieldsByName.get(fkFieldName), () ->
            new RuntimeException("foreign key not found")
         );

         if ( !valueOr(fkField.getNullable(), true) )
            return true;
      }

      return false;
   }
}

class InlineParentContrs
{
   ResultTypeBuilder typeBuilder;
   List<ResultType> resultTypes;

   InlineParentContrs
      (
         ResultTypeBuilder typeBuilder,
         List<ResultType> resultTypes
      )
   {
      this.typeBuilder = typeBuilder;
      this.resultTypes = resultTypes;
   }
}

class RefdParentContrs
{
   List<ParentReferenceProperty> parentReferenceProperties;
   List<ResultType> resultTypes;

   RefdParentContrs
      (
         List<ParentReferenceProperty> parentReferenceProperties,
         List<ResultType> resultTypes
      )
   {
      this.parentReferenceProperties = parentReferenceProperties;
      this.resultTypes = resultTypes;
   }
}

class ChildCollectionContrs
{
   List<ChildCollectionProperty> childCollectionProperties;
   List<ResultType> resultTypes;

   ChildCollectionContrs
      (
         List<ChildCollectionProperty> childCollectionProperties,
         List<ResultType> resultTypes
      )
   {
      this.childCollectionProperties = childCollectionProperties;
      this.resultTypes = resultTypes;
   }
}

