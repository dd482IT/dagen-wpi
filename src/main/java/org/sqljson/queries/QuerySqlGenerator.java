package org.sqljson.queries;

import java.util.*;
import java.util.function.Function;
import static java.util.Collections.*;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.*;

import org.checkerframework.checker.nullness.qual.Nullable;

import org.sqljson.util.StringFuns;
import org.sqljson.dbmd.DatabaseMetadata;
import org.sqljson.dbmd.ForeignKey;
import org.sqljson.dbmd.RelId;
import org.sqljson.queries.specs.*;
import org.sqljson.queries.sql_dialects.SqlDialect;
import static org.sqljson.queries.QuerySqlGenerator.SelectEntry.Source.HIDDEN_PK;
import static org.sqljson.queries.QuerySqlGenerator.SelectEntry.Source.NATIVE_FIELD;
import static org.sqljson.queries.specs.ResultRepr.MULTI_COLUMN_ROWS;
import static org.sqljson.queries.specs.SpecError.specError;
import static org.sqljson.util.Nullables.*;
import static org.sqljson.queries.SpecValidations.*;
import static org.sqljson.util.StringFuns.indentLines;
import static org.sqljson.dbmd.ForeignKeyScope.REGISTERED_TABLES_ONLY;


public class QuerySqlGenerator
{
   private final DatabaseMetadata dbmd;
   private final SqlDialect sqlDialect;
   private final @Nullable String defaultSchema;
   private final Set<String> unqualifiedNamesSchemas; // Use unqualified names for objects in these schemas.
   private final int indentSpaces;
   private final Function<String,String> defaultPropNameFn; // default output property naming function

   private static final String HIDDEN_PK_PREFIX = "_";

   private static final String DEFAULT_TABLE_ALIAS_VAR = "$$";

   /*
   Note: field name quoting
   - A field name from the database metadata (DBMD) is quoted iff its interpretation by the database would change
     with quoting. So DBMD.quoteIfNeeded is applied to all field names from the database metadata when used in queries.
   - Output field names in the queries spec file (QueryGroup contents) always have DBMD.quoteIfNeeded applied before
     use to preserve letter case.
   - Database field names in the queries spec file are always used as-is in queries without quoting or case conversion.
     Users must quote these identifiers within the specifications file where they are not usable in queries directly,
     such as if they contain non-alphabetic characters or match a SQL keyword. For most schemas none of these field
     references would need quoting and the spec files can safely be used across database types.
   Summary: DBMD.quoteIfNeeded is applied to all field names except database field names from the queries spec file.
   */

   public QuerySqlGenerator
      (
         DatabaseMetadata dbmd,
         @Nullable String defaultSchema,
         Set<String> unqualifiedNamesSchemas,
         Function<String,String> defaultPropNameFn
      )
   {
      this.dbmd = dbmd;
      this.indentSpaces = 2;
      this.sqlDialect = SqlDialect.fromDatabaseMetadata(this.dbmd, this.indentSpaces);
      this.defaultSchema = defaultSchema;
      this.unqualifiedNamesSchemas = unqualifiedNamesSchemas.stream().map(dbmd::normalizeName).collect(toSet());
      this.defaultPropNameFn = defaultPropNameFn;
   }

   public Map<ResultRepr,String> generateSqls(QuerySpec querySpec)
   {
      // This query spec may customize the default output field name making function.
      Function<String,String> propNameFn =
         applyOr(querySpec.getOutputFieldNameDefault(), OutputFieldNameDefault::toFunctionOfFieldName,
                 this.defaultPropNameFn);

       return
         querySpec.getResultRepresentationsList().stream()
         .collect(toMap(identity(), repr -> queryResultReprSql(querySpec, repr, propNameFn)));
   }

   private String queryResultReprSql
      (
         QuerySpec querySpec,
         ResultRepr resultRepr,
         Function<String,String> propNameFn
      )
   {
      TableJsonSpec tjs = querySpec.getTableJson();
      SpecLocation specLoc = new SpecLocation(querySpec.getQueryName()); // for error reporting
      if ( querySpec.getForUpdateOrDefault() && resultRepr != MULTI_COLUMN_ROWS )
         throw specError(querySpec, "for update clause", "FOR UPDATE only allowed with MULTI_COLUMN_ROWS");

      switch ( resultRepr )
      {
         case JSON_OBJECT_ROWS:
            return jsonObjectRowsSql(tjs, null, querySpec.getOrderBy(), propNameFn, specLoc);
         case JSON_ARRAY_ROW:
            return jsonArrayRowSql(tjs, null, false, querySpec.getOrderBy(), propNameFn, specLoc);
         case MULTI_COLUMN_ROWS:
            return baseQuery(tjs, null, false, querySpec.getOrderBy(), propNameFn, specLoc).sql
                   + (querySpec.getForUpdateOrDefault() ? "\nfor update" : "");
         default:
            throw specError(querySpec, "resultRepresentations", "Result representation is not valid.");
      }
   }

   /** Generate SQL and column name metadata for the given table output
    *  specification and parent/child condition, with multi-column and multi-row
    *  representation of results.
    * @param tableSpec
    *    The output specification for this table, the subject of the query.
    * @param parentChildCond
    *    A filter condition on this table from a parent or child table whose
    *    alias (accessible from the condition) can be assumed to be in context.
    * @param exportPkFieldsHidden
    *    If enabled then all primary key fields will be added to the SQL select
    *    clause but not are not listed in the result columns list which
    *    is reserved for columns intended for final results. The primary key
    *    columns added for this option have prefixed output names to avoid name
    *    collisions. This is useful for filtering results of this base query
    *    such as for parent child relationship conditions.
    * @return
    *    A BaseQuery structure containing the generated SQL and some metadata
    *    about the query (e.g. column names).
    */
   private BaseQuery baseQuery
      (
         TableJsonSpec tableSpec,
         @Nullable ParentChildCondition parentChildCond,
         boolean exportPkFieldsHidden,
         @Nullable String orderBy,
         Function<String,String> propNameFn,
         SpecLocation specLoc
      )
   {
      SqlParts q = new SqlParts();

      RelId relId = identifyTable(tableSpec.getTable(), specLoc);
      String alias = q.makeNewAliasFor(relId.getName());
      q.fromEntries.add(minimalRelIdentifier(relId) + " " + alias);

      ifPresent(parentChildCond, pcCond ->
         q.aliasesInScope.add(pcCond.getOtherTableAlias())
      );

      if ( exportPkFieldsHidden )
         q.selectEntries.addAll(hiddenPkSelectEntries(relId, alias));

      q.selectEntries.addAll(
         tableFieldExpressionSelectEntries(tableSpec, alias, propNameFn, specLoc)
      );

      q.addParts(
         inlineParentsSqlParts(tableSpec, relId, alias, q.aliasesInScope, propNameFn, specLoc)
      );
      q.addParts(
         referencedParentsSqlParts(tableSpec, relId, alias, propNameFn, specLoc)
      );

      q.selectEntries.addAll(
         childCollectionSelectEntries(tableSpec, relId, alias, propNameFn, specLoc)
      );

      // Add parent/child relationship filter condition if any to the where clause.
      ifPresent(parentChildCond, pcCond ->
         q.whereEntries.add(pcCond.asEquationConditionOn(alias, dbmd))
      );

      ifPresent(recordConditionSql(tableSpec, alias),
         q.whereEntries::add
      );

      if ( orderBy != null )
         q.orderBy = orderBy;

      List<String> columnNames =
         q.selectEntries.stream()
         .filter(e -> e.getSource() != HIDDEN_PK)
         .map(SelectEntry::getName)
         .collect(toList());

      return new BaseQuery(q.toSql(indentSpaces), columnNames);
   }

   private List<SelectEntry> hiddenPkSelectEntries(RelId relId, String alias)
   {
      return
         dbmd.getPrimaryKeyFieldNames(relId).stream()
         .map(pkFieldName -> {
            String pkFieldDbName = dbmd.quoteIfNeeded(pkFieldName);
            String pkFieldOutputName = dbmd.quoteIfNeeded(HIDDEN_PK_PREFIX + pkFieldName);
            return new SelectEntry(alias + "." + pkFieldDbName, pkFieldOutputName, HIDDEN_PK);
         })
         .collect(toList());
   }

   private List<SelectEntry> tableFieldExpressionSelectEntries
      (
         TableJsonSpec tableSpec,
         String alias,
         Function<String,String> propNameFn,
         SpecLocation specLoc
      )
   {
      verifySimpleSelectFieldsExist(tableSpec, defaultSchema, dbmd, specLoc);

      @Nullable List<TableFieldExpr> fieldExprs = tableSpec.getFieldExpressions();
      if (  fieldExprs == null )
         return emptyList();
      else return
         fieldExprs.stream()
         .map(tfe -> {
            SpecLocation loc = specLoc.addPart("field expressions of table " + tableSpec.getTable());
            String propName = dbmd.quoteIfNeeded(this.jsonPropertyName(tfe, propNameFn, loc));
            String sqlExpr = this.tableFieldExpressionSql(tfe, alias, loc);
            return new SelectEntry(sqlExpr, propName, NATIVE_FIELD);
         })
         .collect(toList());
   }

   private String tableFieldExpressionSql
      (
         TableFieldExpr tableFieldExpr,
         String tableAlias,
         SpecLocation specLoc
      )
   {
      if ( tableFieldExpr.getField() != null )
         return tableAlias + "." + requireNonNull(tableFieldExpr.getField());
      else
      {
         String tableAliasVarInExpr = valueOr(tableFieldExpr.getWithTableAliasAs(), DEFAULT_TABLE_ALIAS_VAR);
         if ( tableFieldExpr.getExpression() == null )
            throw new SpecError(specLoc, "'field' or 'expression' must be provided");
         String expr = requireNonNull(tableFieldExpr.getExpression());
         return expr.replace(tableAliasVarInExpr, tableAlias);
      }
   }

   private SqlParts inlineParentsSqlParts
      (
         TableJsonSpec tableSpec,
         RelId relId,
         String alias,
         Set<String> aliasesInScope,
         Function<String,String> propNameFn,
         SpecLocation specLoc
      )
   {
      var sqlParts = new SqlParts(emptyList(), emptyList(), emptyList(), null, aliasesInScope);

      for ( var parentSpec : tableSpec.getInlineParentTablesList() )
      {
         var parentLoc = specLoc.addPart("inline parent '" + parentSpec.getTableJson().getTable() + "'");
         sqlParts.addParts(
            inlineParentSqlParts(parentSpec, relId, alias, sqlParts.aliasesInScope, propNameFn, parentLoc)
         );
      }

      return sqlParts;
   }

   private SqlParts inlineParentSqlParts
      (
         InlineParentSpec inlineParentSpec,
         RelId childRelId,
         String childAlias,
         Set<String> avoidAliases,
         Function<String,String> propNameFn,
         SpecLocation specLoc
      )
   {
      SqlParts q = new SqlParts();

      TableJsonSpec ptjSpec = inlineParentSpec.getTableJson();
      BaseQuery fromClauseQuery = baseQuery(ptjSpec, null, true, null, propNameFn, specLoc);

      String fromClauseQueryAlias = StringFuns.makeNameNotInSet("q", avoidAliases);
      q.aliasesInScope.add(fromClauseQueryAlias);

      for (int i = 0; i < fromClauseQuery.resultColumnNames.size(); ++i )
      {
         String parentColumn = fromClauseQuery.resultColumnNames.get(i);
         q.selectEntries.add(new SelectEntry(
            fromClauseQueryAlias + "." + parentColumn,
            parentColumn,
            SelectEntry.Source.INLINE_PARENT,
            (i == 0 ? lineCommentInlineParentFieldsBegin(inlineParentSpec): null)
         ));
      }

      String joinCond =
         getParentPkCondition(inlineParentSpec, childRelId, childAlias, specLoc)
         .asEquationConditionOn(fromClauseQueryAlias, dbmd, HIDDEN_PK_PREFIX);

      q.fromEntries.add(
         lineCommentJoinToParent(inlineParentSpec) + "\n" +
         "left join (\n" +
            indent(fromClauseQuery.sql) + "\n" +
         ") " + fromClauseQueryAlias + " on " + joinCond
      );

      return q;
   }

   private ParentPkCondition getParentPkCondition
      (
         ParentSpec parentSpec,
         RelId childRelId,
         String childAlias,
         SpecLocation specLoc
      )
   {
      @Nullable CustomJoinCondition customJoinCond = parentSpec.getCustomJoinCondition();

      if ( customJoinCond != null )
      {
         if ( parentSpec.getChildForeignKeyFieldsSet() != null )
            throw new SpecError(specLoc, "Parent with customJoinCondition cannot specify foreignKeyFields.");

         return customJoinParentPkCondition(customJoinCond, childAlias);
      }
      else
      {
         @Nullable Set<String> childForeignKeyFieldsSet = parentSpec.getChildForeignKeyFieldsSet();
         RelId parentRelId = identifyTable(parentSpec.getParentTableJsonSpec().getTable(), specLoc);
         ForeignKey fk = getForeignKey(childRelId, parentRelId, childForeignKeyFieldsSet, specLoc);
         return new ParentPkCondition(childAlias, fk.getForeignKeyComponents());
      }
   }

   private ParentPkCondition customJoinParentPkCondition
      (
         CustomJoinCondition customJoinCond,
         String childAlias
      )
   {
      List<ForeignKey.Component> virtualForeignKeyComponents =
         customJoinCond.getEquatedFields().stream()
         .map(eqfs -> new ForeignKey.Component(
            dbmd.normalizeName(eqfs.getChildField()),
            dbmd.normalizeName(eqfs.getParentPrimaryKeyField())
         ))
         .collect(toList());

      return new QuerySqlGenerator.ParentPkCondition(childAlias, virtualForeignKeyComponents);
   }

   private SqlParts referencedParentsSqlParts
      (
         TableJsonSpec tableSpec,
         RelId relId,
         String alias,
         Function<String,String> propNameFn,
         SpecLocation specLoc
      )
   {
      var sqlParts = new SqlParts();

      for ( var parentSpec : tableSpec.getReferencedParentTablesList() )
      {
         SpecLocation parentLoc = specLoc.addPart("parent '" + parentSpec.getTableJson().getTable() + "'");
         sqlParts.addParts(
            referencedParentSqlParts(parentSpec, relId, alias, propNameFn, parentLoc)
         );
      }

      return sqlParts;
   }

   private SqlParts referencedParentSqlParts
      (
         ReferencedParentSpec parentSpec,
         RelId childRelId,
         String childAlias,
         Function<String,String> propNameFn,
         SpecLocation specLoc
      )
   {
      var parentPkCond = getParentPkCondition(parentSpec, childRelId, childAlias, specLoc);

      var selectEntries = singletonList(new SelectEntry(
         lineCommentReferencedParent(parentSpec) + "\n" +
            "(\n" +
               indent(
                  jsonObjectRowsSql(parentSpec.getTableJson(), parentPkCond, null, propNameFn, specLoc)
               ) + "\n" +
            ")",
         dbmd.quoteIfNeeded(parentSpec.getReferenceName()),
         SelectEntry.Source.PARENT_REFERENCE,
         null
      ));

      return new SqlParts(selectEntries, emptyList(), emptyList(), null, emptySet());
   }

   private List<SelectEntry> childCollectionSelectEntries
      (
         TableJsonSpec tableSpec,
         RelId relId,
         String alias,
         Function<String,String> propNameFn,
         SpecLocation specLoc
      )
   {
      @Nullable List<ChildCollectionSpec> childSpecs = tableSpec.getChildTableCollections();

      if ( childSpecs == null )
         return emptyList();
      return
         childSpecs.stream()
         .map(childSpec -> {
            SpecLocation loc =  specLoc.addPart("child collection '" + childSpec.getCollectionName() + "'");
            return new SelectEntry(
               lineCommentChildCollectionSelectExpression(childSpec) + "\n" +
                  "(" + "\n" +
                     indent(
                        childCollectionQuery(childSpec, relId, alias, propNameFn, loc)
                     ) + "\n" +
                  ")",
               dbmd.quoteIfNeeded(childSpec.getCollectionName()),
               SelectEntry.Source.CHILD_COLLECTION
            );
         })
         .collect(toList());
   }

   private String childCollectionQuery
      (
         ChildCollectionSpec childSpec,
         RelId parentRelId,
         String parentAlias,
         Function<String,String> propNameFn,
         SpecLocation specLoc
      )
   {
      TableJsonSpec tableSpec = childSpec.getTableJson();

      RelId childRelId = identifyTable(tableSpec.getTable(), specLoc);

      var pcCond = getChildFkCondition(childSpec, childRelId, parentRelId, parentAlias, specLoc);

      boolean unwrapChildValues = valueOr(childSpec.getUnwrap(), false);
      if ( unwrapChildValues && childSpec.getTableJson().getJsonPropertiesCount() > 1 )
         throw new SpecError(specLoc, "Unwrapped child collection option is incompatible with multiple field expressions.");

      return jsonArrayRowSql(tableSpec, pcCond, unwrapChildValues, childSpec.getOrderBy(), propNameFn, specLoc);
   }

   private ChildFkCondition getChildFkCondition
      (
         ChildCollectionSpec childCollectionSpec,
         RelId childRelId,
         RelId parentRelId,
         String parentAlias,
         SpecLocation specLoc
      )
   {
      @Nullable CustomJoinCondition customJoinCond = childCollectionSpec.getCustomJoinCondition();

      if ( customJoinCond != null ) // custom join condition specified
      {
         if ( childCollectionSpec.getForeignKeyFields() != null )
            throw new SpecError(specLoc, "Child collection that specifies customJoinCondition cannot specify foreignKeyFields.");
         validateCustomJoinCondition(customJoinCond, childRelId, parentRelId, dbmd, specLoc.addPart("custom join condition"));
         return customJoinChildFkCondition(customJoinCond, parentAlias);
      }
      else // foreign key join condition
      {
         @Nullable Set<String> fkFields = childCollectionSpec.getForeignKeyFieldsSet();
         ForeignKey fk = getForeignKey(childRelId, parentRelId, fkFields, specLoc);
         return new ChildFkCondition(parentAlias, fk.getForeignKeyComponents());
      }
   }

   private ChildFkCondition customJoinChildFkCondition
      (
         CustomJoinCondition customJoinCond,
         String parentAlias
      )
   {
      List<ForeignKey.Component> virtualFkComps =
         customJoinCond.getEquatedFields().stream()
         .map(eqfs -> new ForeignKey.Component(
            dbmd.normalizeName(eqfs.getChildField()),
            dbmd.normalizeName(eqfs.getParentPrimaryKeyField())
         ))
         .collect(toList());

      return new QuerySqlGenerator.ChildFkCondition(parentAlias, virtualFkComps);
   }

   /** Make a query having a single row and column result, with the result value
    *  representing the collection of json object representations of all rows
    *  of the table whose output specification is passed.
    * @param tableSpec  The output specification for this table, the subject of the query.
    * @param parentChildCond A filter condition on this table (always) from a parent or child table whose alias
    *                        (accessible from the condition) can be assumed to be in context.
    * @return the generated SQL query
    */
   private String jsonArrayRowSql
      (
         TableJsonSpec tableSpec,
         @Nullable ParentChildCondition parentChildCond,
         boolean unwrap,
         @Nullable String orderBy,
         Function<String, String> propNameFn,
         SpecLocation specLoc
      )
   {
      BaseQuery baseQuery = baseQuery(tableSpec, parentChildCond, false, null, propNameFn, specLoc);

      if ( unwrap && baseQuery.resultColumnNames.size() != 1 )
         throw new SpecError(specLoc, "Unwrapped child collections cannot have multiple field expressions.");

      return
         "select\n" +
            indent(lineCommentAggregatedRowObjects(tableSpec)) + "\n" +
            indent(
               (unwrap ? sqlDialect.getAggregatedColumnValuesExpression(baseQuery.resultColumnNames.get(0), orderBy, "q")
                       : sqlDialect.getAggregatedRowObjectsExpression(baseQuery.resultColumnNames, orderBy, "q"))
            ) + " json\n" +
         "from (\n" +
            indent(lineCommentBaseTableQuery(tableSpec)) + "\n" +
            indent(baseQuery.sql) + "\n" +
         ") q";
   }

   /** Make a query having JSON object result values at the top level of the
    *  result set. The query returns a JSON value in a single column and with
    *  any number of result rows.
    * @param tjSpec  The output specification for this table, the subject of the query.
    * @param parentChildCond A filter condition on this table (always) from a parent or child table whose alias
    *                        (accessible from the condition) can be assumed to be in context.
    * @return the generated SQL query
    */
   private String jsonObjectRowsSql
      (
         TableJsonSpec tjSpec,
         @Nullable ParentChildCondition parentChildCond,
         @Nullable String orderBy,
         Function<String, String> propNameFn,
         SpecLocation specLoc
      )
   {
      BaseQuery baseQuery = baseQuery(tjSpec, parentChildCond, false, null, propNameFn, specLoc);

      return
         "select\n" +
            indent(lineCommentTableRowObject(tjSpec)) + "\n" +
            indent(
               sqlDialect.getRowObjectExpression(baseQuery.resultColumnNames, "q")
            ) + " json\n" +
         "from (\n" +
            indent(lineCommentBaseTableQuery(tjSpec)) + "\n" +
            indent(baseQuery.sql) + "\n" +
         ") q" +
         (orderBy != null ? "\norder by " + orderBy.replace("$$", "q") : "");
   }

   private @Nullable String recordConditionSql
      (
         TableJsonSpec tableSpec,
         String tableAlias
      )
   {
      @Nullable RecordCondition cond = tableSpec.getRecordCondition();
      if ( cond != null )
      {
         String tableAliasVar = valueOr(cond.getWithTableAliasAs(), DEFAULT_TABLE_ALIAS_VAR);
         return "(" + cond.getSql().replace(tableAliasVar, tableAlias) + ")";
      }
      else
         return null;
   }

   private ForeignKey getForeignKey
      (
         RelId childRelId,
         RelId parentRelId,
         @Nullable Set<String> foreignKeyFields,
         SpecLocation specLoc
      )
   {
      @Nullable ForeignKey fk = dbmd.getForeignKeyFromTo(childRelId, parentRelId, foreignKeyFields, REGISTERED_TABLES_ONLY);

      if ( fk == null )
      {
         throw new SpecError(specLoc,
            "No foreign key found from " + childRelId.getName() + " to " + parentRelId.getName() + " via " +
            (foreignKeyFields != null ? "foreign keys " + foreignKeyFields : "implicit foreign key fields") + "."
         );
      }

      return fk;
   }

   private String jsonPropertyName
      (
         TableFieldExpr tfe,
         Function<String,String> defaultFn,
         SpecLocation specLoc
      )
   {
      if ( tfe.getField() != null )
         return valueOrGet(tfe.getJsonProperty(), () -> defaultFn.apply(requireNonNull(tfe.getField())));
      else
      {
         if ( tfe.getExpression() == null )
            throw new SpecError(specLoc, "'field' or 'expression' must be provided");
         return valueOrThrow(tfe.getJsonProperty(), () -> // expression fields must have output name specified
            new SpecError(specLoc, "Json property required for expression field " + tfe.getExpression() + ".")
         );
      }
   }

   /// Return a possibly qualified identifier for the given relation, omitting the schema
   /// qualifier if it has a schema for which it's specified to use unqualified names.
   private String minimalRelIdentifier(RelId relId)
   {
      @Nullable String schema = relId.getSchema();
      if ( schema == null || unqualifiedNamesSchemas.contains(dbmd.normalizeName(schema)) )
         return relId.getName();
      else
         return relId.getIdString();
   }

   private RelId identifyTable(String table, SpecLocation loc)
   {
      return SpecValidations.identifyTable(table, defaultSchema, dbmd, loc);
   }

   private static String lineCommentTableRowObject(TableJsonSpec tableJsonSpec)
   {
      return "-- row object builder for table '" + tableJsonSpec.getTable() + "'";
   }

   private static String lineCommentBaseTableQuery(TableJsonSpec tableSpec)
   {
      return "-- base query for table '" + tableSpec.getTable() + "'";
   }

   private static String lineCommentAggregatedRowObjects(TableJsonSpec tableSpec)
   {
      return "-- aggregated row objects builder for table '" + tableSpec.getTable() + "'";
   }

   private static String lineCommentChildCollectionSelectExpression(ChildCollectionSpec childSpec)
   {
      return "-- records from child table '" + childSpec.getTableJson().getTable() + "'" +
         " as collection '" + childSpec.getCollectionName() + "'";
   }

   private static String lineCommentJoinToParent(ParentSpec parentSpec)
   {
      return
         "-- parent table '" + parentSpec.getTableJson().getTable() + "'" +
         ", joined for inlined fields";
   }

   private static String lineCommentInlineParentFieldsBegin(ParentSpec parentSpec)
   {
      return "-- field(s) inlined from parent table '" + parentSpec.getTableJson().getTable() + "'";
   }

   private static String lineCommentReferencedParent(ReferencedParentSpec parentSpec)
   {
      return
         "-- parent table '" + parentSpec.getTableJson().getTable() + "'" +
         " referenced as '" + parentSpec.getReferenceName() + "'";
   }

   private String indent(String s)
   {
      return indentLines(s, indentSpaces, true);
   }


   ///////////////////////////////////////////////////
   // utility types
   ///////////////////////////////////////////////////

   private static class BaseQuery
   {
      final String sql;
      final List<String> resultColumnNames;

      BaseQuery(String sql, List<String> resultColumnNames)
      {
         this.sql = sql;
         this.resultColumnNames = List.copyOf(resultColumnNames);
      }
   }

   /// Represents some condition on a table in context with reference to another
   /// table which is identified by its alias.
   private interface ParentChildCondition
   {
      String asEquationConditionOn
         (
            String tableAlias,
            DatabaseMetadata dbmd
         );

      String getOtherTableAlias();
   }

   private static class ParentPkCondition implements ParentChildCondition
   {
      private final String childAlias;
      private final List<ForeignKey.Component> matchedFields;

      ParentPkCondition
         (
            String childAlias,
            List<ForeignKey.Component> matchedFields
         )
      {
         this.childAlias = childAlias;
         this.matchedFields = List.copyOf(matchedFields);
      }

      public String getOtherTableAlias() { return childAlias; }

      public String asEquationConditionOn
         (
            String parentAlias,
            DatabaseMetadata dbmd
         )
      {
         return asEquationConditionOn(parentAlias, dbmd, "");
      }

      public String asEquationConditionOn
         (
            String parentAlias,
            DatabaseMetadata dbmd,
            String parentPkPrefix
         )
      {
         return
            matchedFields.stream()
            .map(mf ->
                childAlias + "." + dbmd.quoteIfNeeded(mf.getForeignKeyFieldName()) +
                " = " +
                parentAlias + "." + dbmd.quoteIfNeeded(parentPkPrefix + mf.getPrimaryKeyFieldName())
            )
            .collect(joining(" and "));
      }
   }

   private static class ChildFkCondition implements ParentChildCondition
   {
      private final String parentAlias;
      private final List<ForeignKey.Component> matchedFields;

      ChildFkCondition
         (
            String parentAlias,
            List<ForeignKey.Component> matchedFields
         )
      {
         this.parentAlias = parentAlias;
         this.matchedFields = List.copyOf(matchedFields);
      }

      public String getOtherTableAlias() { return parentAlias; }

      public String asEquationConditionOn
         (
            String childAlias,
            DatabaseMetadata dbmd
         )
      {
         return
            matchedFields.stream()
               .map(mf -> childAlias + "." + dbmd.quoteIfNeeded(mf.getForeignKeyFieldName()) + " = " +
                  parentAlias + "." + dbmd.quoteIfNeeded(mf.getPrimaryKeyFieldName()))
               .collect(joining(" and "));
      }
   }

   static class SelectEntry
   {
      enum Source { NATIVE_FIELD, INLINE_PARENT, PARENT_REFERENCE, CHILD_COLLECTION, HIDDEN_PK }

      private final String valueExpression;
      private final String name;
      private final Source source;
      private final @Nullable String comment;

      public SelectEntry(String valueExpression, String name, Source source) { this(valueExpression, name, source, null); }
      public SelectEntry
         (
            String valueExpression,
            String name,
            Source source,
            @Nullable String comment
         )
      {
         this.valueExpression = valueExpression;
         this.name = name;
         this.source = source;
         this.comment = comment;
      }

      String getValueExpression() { return valueExpression; }

      String getName() { return name; }

      Source getSource() { return source; }

      @Nullable String getComment() { return comment; }
   }

   private static class SqlParts
   {
      private final List<SelectEntry> selectEntries;
      private final List<String> fromEntries;
      private final List<String> whereEntries;
      private @Nullable String orderBy;
      private final Set<String> aliasesInScope;

      SqlParts()
      {
         this.selectEntries = new ArrayList<>();
         this.fromEntries = new ArrayList<>();
         this.whereEntries = new ArrayList<>();
         this.orderBy = null;
         this.aliasesInScope = new HashSet<>();
      }

      SqlParts
         (
            List<SelectEntry> selectEntries,
            List<String> fromEntries,
            List<String> whereEntries,
            @Nullable String orderBy,
            Set<String> aliasesInScope
         )
      {
         this.selectEntries = new ArrayList<>(selectEntries);
         this.fromEntries = new ArrayList<>(fromEntries);
         this.whereEntries = new ArrayList<>(whereEntries);
         this.orderBy = orderBy;
         this.aliasesInScope = new HashSet<>(aliasesInScope);
      }

      void addParts(SqlParts otherParts)
      {
         selectEntries.addAll(otherParts.selectEntries);
         fromEntries.addAll(otherParts.fromEntries);
         whereEntries.addAll(otherParts.whereEntries);
         aliasesInScope.addAll(otherParts.aliasesInScope);
      }

      String makeNewAliasFor(String dbObjectName)
      {
         String alias = StringFuns.makeNameNotInSet(StringFuns.lowercaseInitials(dbObjectName, "_"), aliasesInScope);
         aliasesInScope.add(alias);
         return alias;
      }

      String toSql(int indentSpaces)
      {
         String selectEntriesStr =
            selectEntries.stream()
            .map(SqlParts::makeSelectClauseEntrySql)
            .collect(joining(",\n"));

         String fromEntriesStr = String.join("\n", fromEntries);

         String whereEntriesStr = String.join(" and\n", whereEntries);

         return
            "select\n" +
               indentLines(selectEntriesStr, indentSpaces) + "\n" +
            "from\n" +
               indentLines(fromEntriesStr, indentSpaces) + "\n" +
            (whereEntries.isEmpty() ? "":
               "where (\n" +
                  indentLines(whereEntriesStr, indentSpaces) + "\n" +
               ")") +
            (orderBy != null ? "\norder by " + orderBy: "");
      }

      // Make sql string for a select clause entry.
      private static String makeSelectClauseEntrySql(SelectEntry sce)
      {
         String exprNameSep = sce.getName().startsWith("\"") ? " " : " as ";
         @Nullable String comment = sce.getComment();
         return
            (comment != null ? comment + "\n" : "") +
            sce.getValueExpression() + exprNameSep + sce.getName();
      }
   }
}
