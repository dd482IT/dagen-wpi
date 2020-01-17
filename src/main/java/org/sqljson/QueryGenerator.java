package org.sqljson;

import java.util.*;
import java.util.function.Function;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static java.util.Optional.empty;
import static java.util.function.Function.identity;

import org.sqljson.dbmd.*;
import org.sqljson.result_types.GeneratedType;
import org.sqljson.specs.FieldParamCondition;
import org.sqljson.specs.queries.*;
import org.sqljson.sql.*;
import org.sqljson.util.StringFuns;
import org.sqljson.sql.dialect.SqlDialect;
import static org.sqljson.specs.mod_stmts.ParametersType.NAMED;
import static org.sqljson.util.DatabaseUtils.verifyTableFieldsExist;
import static org.sqljson.util.Optionals.opt;
import static org.sqljson.util.StringFuns.*;


public class QueryGenerator
{
   private final DatabaseMetadata dbmd;
   private final Optional<String> defaultSchema;
   private final Set<String> generateUnqualifiedNamesForSchemas;
   private final SqlDialect sqlDialect;
   private final int indentSpaces;
   private final QueryTypesGenerator queryTypesGenerator;
   private final Function<String,String> outputFieldNameDefaultFn;

   private static final String HIDDEN_PK_PREFIX = "_";

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

   public QueryGenerator
   (
      DatabaseMetadata dbmd,
      Optional<String> defaultSchema,
      Set<String> generateUnqualifiedNamesForSchemas,
      Function<String,String> outputFieldNameDefaultFn
   )
   {
      this.dbmd = dbmd;
      this.defaultSchema = defaultSchema;
      this.generateUnqualifiedNamesForSchemas =
         generateUnqualifiedNamesForSchemas.stream().map(dbmd::normalizeName).collect(toSet());
      this.indentSpaces = 2;
      this.sqlDialect = SqlDialect.fromDatabaseMetadata(this.dbmd, this.indentSpaces);
      this.queryTypesGenerator = new QueryTypesGenerator(dbmd, defaultSchema);
      this.outputFieldNameDefaultFn = outputFieldNameDefaultFn;
   }

   public List<GeneratedQuery> generateQueries(List<QuerySpec> querySpecs)
   {
      return
         querySpecs.stream()
         .map(this::generateQuery)
         .collect(toList());
   }

   private GeneratedQuery generateQuery(QuerySpec querySpec)
   {
      String queryName = querySpec.getQueryName();

      // This query spec may customize the default output field name making function.
      Function<String,String> outputFieldNameDefaultFn =
         querySpec.getOutputFieldNameDefault().map(OutputFieldNameDefault::toFunctionOfFieldName)
         .orElse(this.outputFieldNameDefaultFn);

      Map<ResultsRepr,String> querySqls =
         querySpec.getResultsRepresentations().stream()
         .collect(toMap(identity(), repr -> makeSqlForResultsRepr(querySpec, repr, outputFieldNameDefaultFn)));

      List<GeneratedType> generatedTypes =
         querySpec.getGenerateResultTypes() ?
            queryTypesGenerator.generateTypes(querySpec.getTableJson(), emptyMap(), outputFieldNameDefaultFn)
            : emptyList();

      return
         new GeneratedQuery(
            queryName,
            querySqls,
            generatedTypes,
            querySpec.getGenerateSource(),
            getAllFieldParamConditionParamNames(querySpec)
         );
   }

   private String makeSqlForResultsRepr
   (
      QuerySpec querySpec,
      ResultsRepr resultsRepr,
      Function<String,String> outputFieldNameDefaultFn
   )
   {
      TableJsonSpec tjs = querySpec.getTableJson();
      switch (resultsRepr)
      {
         case MULTI_COLUMN_ROWS:
            return makeBaseQuery(tjs, empty(), false, outputFieldNameDefaultFn).getSql();
         case JSON_OBJECT_ROWS:
            return makeJsonObjectRowsSql(tjs, empty(), outputFieldNameDefaultFn);
         case JSON_ARRAY_ROW:
            return makeAggregatedJsonResultSql(tjs, empty(), outputFieldNameDefaultFn);
         default:
            throw new RuntimeException("unrecognized results representation: " + resultsRepr);
      }
   }

   /** Generate SQL and column name metadata for the given table output
    *  specification and parent/child condition, with multi-column and multi-row
    *  representation of results.
    * @param tableJsonSpec
    *    The output specification for this table, the subject of the query.
    * @param parentChildCond
    *    A filter condition on this table from a parent or child table whose
    *    alias (accessible from the condition) can be assumed to be in context.
    * @param exportAllPkFieldsAsHidden
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
   private BaseQuery makeBaseQuery
   (
      TableJsonSpec tableJsonSpec,
      Optional<ParentChildCondition> parentChildCond,
      boolean exportAllPkFieldsAsHidden,
      Function<String,String> outputFieldNameDefaultFn
   )
   {
      SqlQueryParts q = new SqlQueryParts();

      // Identify this table and make an alias for it.
      RelId relId = dbmd.identifyTable(tableJsonSpec.getTable(), defaultSchema);
      parentChildCond.ifPresent(pcCond ->
         q.addAliasToScope(pcCond.getOtherTableAlias())
      );
      String alias = q.makeNewAliasFor(relId.getName());
      q.addFromClauseEntry(minimalRelIdentifier(relId) + " " + alias);

      // If exporting pk fields, add any that aren't already in the output fields list to the select clause.
      if ( exportAllPkFieldsAsHidden )
         for ( String pkFieldName : dbmd.getPrimaryKeyFieldNames(relId) )
         {
            String pkFieldDbName = dbmd.quoteIfNeeded(pkFieldName);
            String pkFieldOutputName = dbmd.quoteIfNeeded(HIDDEN_PK_PREFIX + pkFieldName);
            q.addSelectClauseEntry(alias + "." + pkFieldDbName, pkFieldOutputName, SelectClauseEntry.Source.HIDDEN_PK);
         }

      // Add this table's own field expressions to the select clause.
      for ( TableFieldExpr tfe : tableJsonSpec.getFieldExpressions() )
         q.addSelectClauseEntry(
            tfe.getValueExpressionForAlias(alias),
            dbmd.quoteIfNeeded(getJsonPropertyName(tfe, outputFieldNameDefaultFn, tableJsonSpec.getTable())),
            SelectClauseEntry.Source.NATIVE_FIELD,
            tfe.getGenerateTypes()
         );

      // Add child record collections to the select clause.
      for ( ChildCollectionSpec childCollectionSpec : tableJsonSpec.getChildTableCollections() )
         q.addSelectClauseEntry(
            "(\n" + indent(makeChildRecordsQuery(childCollectionSpec, relId, alias, outputFieldNameDefaultFn)) + "\n)",
            dbmd.quoteIfNeeded(childCollectionSpec.getCollectionName()),
            SelectClauseEntry.Source.CHILD_COLLECTION
         );

      // Add query parts for inline parents.
      for ( InlineParentSpec inlineParentTableSpec : tableJsonSpec.getInlineParentTables() )
         q.addParts(
            getInlineParentBaseQueryParts(inlineParentTableSpec, relId, alias, q.getAliasesInScope(), outputFieldNameDefaultFn)
         );

      // Add parts for referenced parents.
      for ( ReferencedParentSpec refdParentSpec : tableJsonSpec.getReferencedParentTables() )
         q.addParts(getReferencedParentBaseQueryParts(refdParentSpec, relId, alias, outputFieldNameDefaultFn));

      // Add parent/child relationship filter condition if any to the where clause.
      parentChildCond.ifPresent(pcCond ->
         q.addWhereClauseEntry(pcCond.asEquationConditionOn(alias, dbmd))
      );

      getWhereCondition(tableJsonSpec, alias).ifPresent(q::addWhereClauseEntry);

      String sql = makeSqlFromParts(q);

      List<ColumnMetadata> columnMetadatas =
         q.getSelectClauseEntries().stream()
         .filter(e -> e.getSource() != SelectClauseEntry.Source.HIDDEN_PK)
         .map(e -> new ColumnMetadata(e.getOutputName(), e.getSource()))
         .collect(toList());

      return new BaseQuery(sql, columnMetadatas);
   }

   /** Make a query having a single row and column result, with the result value
    *  representing the collection of json object representations of all rows
    *  of the table whose output specification is passed.
    * @param tableJsonSpec  The output specification for this table, the subject of the query.
    * @param parentChildLinkCond A filter condition on this table (always) from a parent or child table whose alias
    *                            (accessible from the condition) can be assumed to be in context.
    * @return the generated SQL query
    */
   private String makeAggregatedJsonResultSql
      (
         TableJsonSpec tableJsonSpec,
         Optional<ParentChildCondition> parentChildLinkCond,
         Function<String,String> outputFieldNameDefaultFn
      )
   {
      BaseQuery baseQuery = makeBaseQuery(tableJsonSpec, parentChildLinkCond, false, outputFieldNameDefaultFn);

      String aggExpr = sqlDialect.getAggregatedRowObjectsExpression(baseQuery.getResultColumnMetadatas(), "q");

      String simpleAggregateQuery =
         "select\n" +
            indent(aggExpr) + " json\n" +
         "from (\n" +
            indent(baseQuery.getSql()) + "\n" +
         ") q";

      return sqlDialect.getAggregatedObjectsFinalQuery(simpleAggregateQuery, "json");
   }

   /** Make a query having JSON object result values at the top level of the
    *  result set. The query returns a JSON value in a single column and with
    *  any number of result rows.
    * @param tableJsonSpec  The output specification for this table, the subject of the query.
    * @param parentChildLinkCond A filter condition on this table (always) from a parent or child table whose alias
    *                            (accessible from the condition) can be assumed to be in context.
    * @return the generated SQL query
    */
   private String makeJsonObjectRowsSql
   (
      TableJsonSpec tableJsonSpec,
      Optional<ParentChildCondition> parentChildLinkCond,
      Function<String,String> outputFieldNameDefaultFn
   )
   {
      BaseQuery baseQuery =
         makeBaseQuery(tableJsonSpec, parentChildLinkCond, false, outputFieldNameDefaultFn);

      String rowObjExpr = sqlDialect.getRowObjectExpression(baseQuery.getResultColumnMetadatas(), "q");

      return
         "select\n" +
            indent(rowObjExpr) + " json\n" +
         "from (\n" +
            indent(baseQuery.getSql()) + "\n" +
         ") q";
   }

   private String makeChildRecordsQuery
   (
      ChildCollectionSpec childCollectionSpec,
      RelId parentRelId,
      String parentAlias,
      Function<String,String> outputFieldNameDefaultFn
   )
   {
      TableJsonSpec tjs = childCollectionSpec.getTableJson();

      RelId relId = dbmd.identifyTable(tjs.getTable(), defaultSchema);

      ForeignKey fk = getForeignKey(relId, parentRelId, childCollectionSpec.getForeignKeyFieldsSet());

      ChildFkCondition childFkCond = new ChildFkCondition(parentAlias, fk.getForeignKeyComponents());

      return makeAggregatedJsonResultSql(tjs, opt(childFkCond), outputFieldNameDefaultFn);
   }

   private SqlQueryParts getInlineParentBaseQueryParts
   (
      InlineParentSpec inlineParentTableSpec,
      RelId childRelId,
      String childAlias,
      Set<String> avoidAliases,
      Function<String,String> outputFieldNameDefaultFn
   )
   {
      SqlQueryParts q = new SqlQueryParts();

      TableJsonSpec tjs = inlineParentTableSpec.getTableJson();
      String table = tjs.getTable();
      RelId relId = dbmd.identifyTable(table, defaultSchema);
      ForeignKey fk = getForeignKey(childRelId, relId, inlineParentTableSpec.getChildForeignKeyFieldsSet());

      BaseQuery fromClauseQuery = makeBaseQuery(tjs, empty(), true, outputFieldNameDefaultFn);

      String fromClauseQueryAlias = StringFuns.makeNameNotInSet("q", avoidAliases);
      q.addAliasToScope(fromClauseQueryAlias);

      for ( ColumnMetadata parentCol : fromClauseQuery.getResultColumnMetadatas() )
         q.addSelectClauseEntry(
            fromClauseQueryAlias + "." + parentCol.getOutputName(),
             parentCol.getOutputName(),
            SelectClauseEntry.Source.INLINE_PARENT,
            parentCol.getFieldTypeOverrides()
         );

      ParentPkCondition parentPkCond = new ParentPkCondition(childAlias, fk.getForeignKeyComponents());
      q.addFromClauseEntry(
         "left join (\n" +
            indent(fromClauseQuery.getSql()) + "\n" +
         ") " + fromClauseQueryAlias + " on " +
         parentPkCond.asEquationConditionOn(fromClauseQueryAlias, dbmd, HIDDEN_PK_PREFIX)
      );

      return q;
   }

   private SqlQueryParts getReferencedParentBaseQueryParts
      (
         ReferencedParentSpec referencedParentSpec,
         RelId childRelId,
         String childAlias,
         Function<String,String> outputFieldNameDefaultFn
      )
   {
      // a referenced parent only requires a SELECT clause entry
      String parentRecordQuery =
         makeParentRecordQuery(referencedParentSpec, childRelId, childAlias, outputFieldNameDefaultFn);

      return new SqlQueryParts(
         singletonList(new SelectClauseEntry(
            "(\n" + indent(parentRecordQuery) + "\n)",
            dbmd.quoteIfNeeded(referencedParentSpec.getReferenceName()),
            SelectClauseEntry.Source.PARENT_REFERENCE
         )),
         emptyList(), emptyList(), emptySet()
      );
   }

   private String makeParentRecordQuery
   (
      ReferencedParentSpec refdParentSpec,
      RelId childRelId,
      String childAlias,
      Function<String,String> outputFieldNameDefaultFn
   )
   {
      TableJsonSpec tjs = refdParentSpec.getParentTableJsonSpec();

      RelId relId = dbmd.identifyTable(tjs.getTable(), defaultSchema);

      ForeignKey fk = getForeignKey(childRelId, relId, refdParentSpec.getChildForeignKeyFieldsSet());

      ParentPkCondition parentPkCond = new ParentPkCondition(childAlias, fk.getForeignKeyComponents());

      return makeJsonObjectRowsSql(tjs, opt(parentPkCond), outputFieldNameDefaultFn);
   }

   private String makeSqlFromParts(SqlQueryParts q)
   {
      String selectEntriesStr =
         q.getSelectClauseEntries().stream()
         .map(p -> p.getValueExpression() + (p.getOutputName().startsWith("\"") ? " " : " as ") + p.getOutputName())
         .collect(joining(",\n"));

      String fromEntriesStr = String.join("\n", q.getFromClauseEntries());

      String whereEntriesStr = String.join(" and\n", q.getWhereClauseEntries());

      return
         "select\n" +
            indent(selectEntriesStr) + "\n" +
         "from\n" +
            indent(fromEntriesStr) + "\n" +
         (q.getWhereClauseEntries().isEmpty() ? "":
         "where (\n" +
            indent(whereEntriesStr) + "\n" +
         ")");
   }

   private Optional<String> getWhereCondition
   (
      TableJsonSpec tableJsonSpec,
      String tableAlias
   )
   {
      List<String> conds = new ArrayList<>();

      verifyTableFieldsExist(
         tableJsonSpec.getTable(),
         defaultSchema,
         tableJsonSpec.getFieldParamConditions().stream().map(FieldParamCondition::getField).collect(toList()),
         dbmd
      );

      for ( FieldParamCondition fieldParamCond : tableJsonSpec.getFieldParamConditions() )
      {
         conds.add(
            fieldParamCond.toSql(
               opt(tableAlias),
               NAMED,
               getDefaultParamNameFn(tableJsonSpec.getTable(), fieldParamCond.getOp())
            )
         );
      }

      tableJsonSpec.getCondition().ifPresent(otherCond ->
         conds.add("(" + substituteVarValue(otherCond, tableAlias) + ")")
      );

      return conds.isEmpty() ? empty() : opt(String.join("\nand\n", conds));
   }

   /// The returned function creates a default param name from a field name in the
   /// context of a given table..
   private Function<String,String> getDefaultParamNameFn(String tableName, FieldParamCondition.Operator op)
   {
      return (String fieldName) -> {
         String baseName = lowerCamelCase(tableName) + upperCamelCase(fieldName);
         if ( op.acceptsListParam() )
            return baseName + "List";
         else
            return baseName;
      };
   }

   private List<String> getAllFieldParamConditionParamNames(QuerySpec querySpec)
   {
      return getAllFieldParamConditionParamNames(querySpec.getTableJson());
   }

   private List<String> getAllFieldParamConditionParamNames(TableJsonSpec tableJsonSpec)
   {
      List<String> paramNames = new ArrayList<>();

      tableJsonSpec.getFieldParamConditions().stream()
         .map(fpCond -> fpCond.getFinalParamName(getDefaultParamNameFn(tableJsonSpec.getTable(), fpCond.getOp())))
         .forEach(paramNames::add);

      for ( ChildCollectionSpec childSpec: tableJsonSpec.getChildTableCollections() )
         paramNames.addAll(getAllFieldParamConditionParamNames(childSpec.getTableJson()));

      for ( InlineParentSpec parentSpec : tableJsonSpec.getInlineParentTables() )
         paramNames.addAll(getAllFieldParamConditionParamNames(parentSpec.getParentTableJsonSpec()));

      for ( ReferencedParentSpec parentSpec : tableJsonSpec.getReferencedParentTables() )
         paramNames.addAll(getAllFieldParamConditionParamNames(parentSpec.getParentTableJsonSpec()));

      return paramNames;
   }

   private ForeignKey getForeignKey
   (
      RelId childRelId,
      RelId parentRelId,
      Optional<Set<String>> foreignKeyFields
   )
   {
      return
         dbmd.getForeignKeyFromTo(childRelId, parentRelId, foreignKeyFields, ForeignKeyScope.REGISTERED_TABLES_ONLY)
         .orElseThrow(() -> new RuntimeException(
            "foreign key not found from " + childRelId.getName() + " to " + parentRelId.getName() +
            " via " + (foreignKeyFields.map(fks -> "foreign keys " + fks).orElse(" implicit foreign key field(s)."))
         ));
   }

   private String getJsonPropertyName
   (
      TableFieldExpr tfe,
      Function<String,String> defaultFn,
      String tableName
   )
   {
      if ( tfe.isSimpleField() )
         return tfe.getJsonProperty().orElseGet(() -> defaultFn.apply(tfe.getField()));
      else
         return tfe.getJsonProperty().orElseThrow(() -> // expression fields must have output name specified
            new RuntimeException(
               "Json propery name is required for expression field " + tfe.getExpression() +
               " of table " + tableName + "."
            )
         );
   }

   /// Return a possibly qualified identifier for the given relation, omitting the schema
   /// qualifier if it has a schema for which it's specified to use unqualified names.
   private String minimalRelIdentifier(RelId relId)
   {
      if ( !relId.getSchema().isPresent() ||
           generateUnqualifiedNamesForSchemas.contains(dbmd.normalizeName(relId.getSchema().get())) )
         return relId.getName();
      else
         return relId.getIdString();
   }

   private String indent(String s)
   {
      return StringFuns.indentLines(s, indentSpaces, true);
   }

   private static class BaseQuery
   {
      private final String sql;
      private final List<ColumnMetadata> resultColumnMetadatas;

      BaseQuery(String sql, List<ColumnMetadata> resultColumnMetadatas)
      {
         this.sql = sql;
         this.resultColumnMetadatas = unmodifiableList(new ArrayList<>(resultColumnMetadatas));
      }

      String getSql() { return sql; }

      List<ColumnMetadata> getResultColumnMetadatas() { return resultColumnMetadatas; }
   }
}
