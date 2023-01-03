package org.sqljson;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static java.util.stream.Collectors.toSet;

import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import static org.sqljson.TestsBase.Params.params;
import org.sqljson.dbmd.DatabaseMetadata;
import org.sqljson.query_specs.QueryGroupSpec;

import generated.query.*;


@SuppressWarnings("nullness")
class QueryGeneratorTests extends TestsBase
{
   final DatabaseMetadata dbmd;

   QueryGeneratorTests() throws IOException
   {
      this.dbmd = getDatabaseMetadata("dbmd-pg.yaml");
   }

   static void checkDatabaseConnection()
   {
      assertTestDatabaseAvailable();
   }


   void readDrugNativeFields() throws Exception
   {
      String sql = getGeneratedQuerySql("drug fields query with param(multi column rows).sql");

      SqlParameterSource params = params(DrugFieldsQueryWithParam.idParam, 2L);

      doQuery(sql, params, rs -> {
         assertEquals(2, rs.getLong(1));
         assertEquals("Test Drug 2", rs.getString(2));
         assertEquals("MESH2", rs.getString(3));
      });
   }

   void readDrugNativeFieldsViaOtherCondition() throws Exception
   {
      String sql = getGeneratedQuerySql("drug fields query with other cond(multi column rows).sql");

      SqlParameterSource params = params("idMinusOne", 1L);

      doQuery(sql, params, rs -> {
         assertEquals(2, rs.getLong(1));
         assertEquals("Test Drug 2", rs.getString(2));
         assertEquals("MESH2", rs.getString(3));
      });
   }


   void readDrugNativeFieldsAsGeneratedType() throws Exception
   {
      String sql = getGeneratedQuerySql("drug fields query with param(json object rows).sql");

      SqlParameterSource params = params(DrugFieldsQueryWithParam.idParam, 2L);

      doQuery(sql, params, rs -> {
         DrugFieldsQueryWithParam.Drug res = readJson(rs.getString(1), DrugFieldsQueryWithParam.Drug.class);
         assertEquals(2, res.id);
         assertEquals( "Test Drug 2", res.name);
         assertEquals("MESH2", res.meshId);
      });
   }

   void readDrugNativeFieldsWithOneCustomizedAsGeneratedType() throws Exception
   {
      String sql = getGeneratedQuerySql("drug fields customized type query(json object rows).sql");

      SqlParameterSource params = params(DrugFieldsCustomizedTypeQuery.idParam, 2L);

      doQuery(sql, params, rs -> {
         DrugFieldsCustomizedTypeQuery.Drug res = readJson(rs.getString(1), DrugFieldsCustomizedTypeQuery.Drug.class);
         assertEquals(2, 2, res.id);
         assertTrue((res.cid instanceof java.math.BigDecimal));
      });
   }

   void readDrugWithFieldExpression() throws Exception
   {
      String sql = getGeneratedQuerySql("drug with field expression query(json object rows).sql");

      SqlParameterSource params = params(DrugWithFieldExpressionQuery.idParam, 2L);

      doQuery(sql, params, rs -> {
         DrugWithFieldExpressionQuery.Drug res = readJson(rs.getString(1), DrugWithFieldExpressionQuery.Drug.class);
         assertEquals(2, res.id);
         assertEquals(198 + 1000, res.cidPlus1000);
      });
   }

   void readDrugWithBrandsChildCollection() throws Exception
   {
      String sql = getGeneratedQuerySql("drug with brands query(json object rows).sql");

      SqlParameterSource params = params(DrugWithBrandsQuery.idParam, 2L);

      doQuery(sql, params, rs -> {
         DrugWithBrandsQuery.Drug res = readJson(rs.getString(1), DrugWithBrandsQuery.Drug.class);
         assertEquals(2, res.id);
         List<DrugWithBrandsQuery.Brand> brands = res.brands;
         assertEquals(1, brands.size());
         assertEquals("Brand2(TM)", brands.get(0).brandName);
         assertEquals(3L, brands.get(0).manufacturerId);
      });
   }

   void readDrugWithBrandsChildCollectionViaCustomJoin() throws Exception
   {
      String sql = getGeneratedQuerySql("drug with brands custom join query(json object rows).sql");

      SqlParameterSource params = params(DrugWithBrandsCustomJoinQuery.idParam, 2L);

      doQuery(sql, params, rs -> {
         DrugWithBrandsCustomJoinQuery.Drug res = readJson(rs.getString(1), DrugWithBrandsCustomJoinQuery.Drug.class);
         assertEquals(2, res.id);
         List<DrugWithBrandsCustomJoinQuery.Brand> brands = res.brands;
         assertEquals(1, brands.size());
         assertEquals("Brand2(TM)", brands.get(0).brandName);
         assertEquals(3L, brands.get(0).manufacturerId);
      });
   }


   void readDrugWithBrandsAndAdvisoriesChildCollections() throws Exception
   {
      String sql = getGeneratedQuerySql("drug with brands and advisories query(json object rows).sql");

      SqlParameterSource params = params(DrugWithBrandsAndAdvisoriesQuery.idParam, 2L);

      doQuery(sql, params, rs -> {
         DrugWithBrandsAndAdvisoriesQuery.Drug res = readJson(rs.getString(1), DrugWithBrandsAndAdvisoriesQuery.Drug.class);
         assertEquals(2, res.id);

         List<DrugWithBrandsAndAdvisoriesQuery.Brand> brands = res.brands;
         assertEquals(1, brands.size());
         assertEquals("Brand2(TM)", brands.get(0).brandName);
         assertEquals(3L, brands.get(0).manufacturerId);

         List<DrugWithBrandsAndAdvisoriesQuery.Advisory> advisories = res.advisories;
         assertEquals(3, advisories.size());
         Set<String> expectedAdvisories = new HashSet<>();
         expectedAdvisories.add("Advisory concerning drug 2");
         expectedAdvisories.add("Caution concerning drug 2");
         expectedAdvisories.add("Heard this might be bad -anon2");
         assertEquals(expectedAdvisories, advisories.stream().map(a -> a.advisoryText).collect(toSet()));
      });
   }

   void readDrugWithUnwrappedAdvisoryIds() throws Exception
   {
      String sql = getGeneratedQuerySql("drug with unwrapped advisory ids query(json object rows).sql");

      SqlParameterSource params = params(DrugWithUnwrappedAdvisoryIdsQuery.idParam, 2L);

      doQuery(sql, params, rs -> {
         DrugWithUnwrappedAdvisoryIdsQuery.Drug res = readJson(rs.getString(1), DrugWithUnwrappedAdvisoryIdsQuery.Drug.class);
         assertEquals(2, res.id);
         assertEquals(new HashSet<>(Arrays.asList(201L, 202L, 246L)), new HashSet<>(res.advisoryIds));
      });
   }

   void readDrugWithUnwrappedAdvisoryTexts() throws Exception
   {
      String sql = getGeneratedQuerySql("drug with unwrapped advisory texts query(json object rows).sql");

      SqlParameterSource params = params(DrugWithUnwrappedAdvisoryTextsQuery.idParam, 2L);

      doQuery(sql, params, rs -> {
         DrugWithUnwrappedAdvisoryTextsQuery.Drug res = readJson(rs.getString(1), DrugWithUnwrappedAdvisoryTextsQuery.Drug.class);
         assertEquals(2, res.id);
         assertEquals(
            new HashSet<>(Arrays.asList("Advisory concerning drug 2", "Heard this might be bad -anon2", "Caution concerning drug 2")),
            new HashSet<>(res.advisoryTexts)
         );
      });
   }

   void readDrugWithUnwrappedAdvisoryTypeNames() throws Exception
   {
      String sql = getGeneratedQuerySql("drug with unwrapped advisory type names query(json object rows).sql");

      SqlParameterSource params = params(DrugWithUnwrappedAdvisoryTypeNamesQuery.idParam, 2L);

      doQuery(sql, params, rs -> {
         DrugWithUnwrappedAdvisoryTypeNamesQuery.Drug res = readJson(rs.getString(1), DrugWithUnwrappedAdvisoryTypeNamesQuery.Drug.class);
         assertEquals(2, res.id);
         assertEquals(
            new HashSet<>(Arrays.asList("Caution", "Rumor", "Boxed Warning")),
            new HashSet<>(res.advisoryTypeNames)
         );
      });
   }

   void readAdvisoryWithInlineAdvisoryTypeParent() throws Exception
   {
      String sql = getGeneratedQuerySql("advisory with inline advisory type query(json object rows).sql");

      SqlParameterSource params = params(AdvisoryWithInlineAdvisoryTypeQuery.idParam, 201L);

      doQuery(sql, params, rs -> {
         AdvisoryWithInlineAdvisoryTypeQuery.Advisory res = readJson(rs.getString(1), AdvisoryWithInlineAdvisoryTypeQuery.Advisory.class);
         assertEquals(201, res.id);
         assertEquals(2, res.drugId);
         assertEquals("Boxed Warning", res.advisoryType);
         assertEquals(2L, res.exprYieldingTwo);
      });
   }

   void readAdvisoryWithCustomJoinedInlineAdvisoryTypeParent() throws Exception
   {
      String sql = getGeneratedQuerySql("advisory with inline custom joined advisory type query(json object rows).sql");

      SqlParameterSource params = params(AdvisoryWithInlineCustomJoinedAdvisoryTypeQuery.idParam, 201L);

      doQuery(sql, params, rs -> {
         AdvisoryWithInlineCustomJoinedAdvisoryTypeQuery.Advisory res = readJson(rs.getString(1), AdvisoryWithInlineCustomJoinedAdvisoryTypeQuery.Advisory.class);
         assertEquals(201, res.id);
         assertEquals(2, res.drugId);
         assertEquals("Boxed Warning", res.advisoryType);
         assertEquals(2L, res.exprYieldingTwo);
      });
   }

   void readDrugWithWrappedAnalystParent() throws Exception
   {
      String sql = getGeneratedQuerySql("drug with wrapped analyst query(json object rows).sql");

      SqlParameterSource params = params(DrugWithWrappedAnalystQuery.idParam, 2L);

      doQuery(sql, params, rs -> {
         DrugWithWrappedAnalystQuery.Drug res = readJson(rs.getString(1), DrugWithWrappedAnalystQuery.Drug.class);
         assertEquals(2, res.id);
         assertEquals(2, res.registeredByAnalyst.id);
         assertEquals("sch", res.registeredByAnalyst.shortName);
      });
   }

   void readDrugWithWrappedAnalystParentViaCustomJoin() throws Exception
   {
      String sql = getGeneratedQuerySql("drug with wrapped analyst via custom join query(json object rows).sql");

      SqlParameterSource params = params(DrugWithWrappedAnalystViaCustomJoinQuery.idParam, 2L);

      doQuery(sql, params, rs -> {
         DrugWithWrappedAnalystViaCustomJoinQuery.Drug res =
            readJson(rs.getString(1), DrugWithWrappedAnalystViaCustomJoinQuery.Drug.class);
         assertEquals(2, res.id);
         assertEquals(2, res.registeredByAnalyst.id);
         assertEquals("sch", res.registeredByAnalyst.shortName);
      });
   }

   void readDrugWithCompoundByExplicitForeignKey() throws Exception
   {
      String sql = getGeneratedQuerySql("drug with explicit compound reference query(json object rows).sql");

      SqlParameterSource params = params(DrugWithExplicitCompoundReferenceQuery.idParam, 2L);

      doQuery(sql, params, rs -> {
         DrugWithExplicitCompoundReferenceQuery.Drug res = readJson(rs.getString(1), DrugWithExplicitCompoundReferenceQuery.Drug.class);
         assertEquals(2, res.id );
         assertEquals("Test Compound 2", res.compound.displayName);
      });
   }

   void rejectBadForeignKeyReferenceInQuerySpec()
   {
      QueryGroupSpec queryGroupSpec = readBadQuerySpec("bad-foreign-key-field-ref.yaml");
      QuerySqlGenerator queryGenerator =
         new QuerySqlGenerator(
            dbmd,
            queryGroupSpec.getDefaultSchema(),
            new HashSet<>(queryGroupSpec.getGenerateUnqualifiedNamesForSchemas()),
            queryGroupSpec.getPropertyNameDefault().toFunctionOfFieldName()
         );
      Throwable t = assertThrows(RuntimeException.class, () ->
         queryGenerator.generateSqls(queryGroupSpec.getQuerySpecs().get(0))
      );
      String msg = t.getMessage().toLowerCase();
      assertTrue(msg.contains("no foreign key found") && msg.contains("x_compound_id"));
   }

   void rejectBadFieldReferenceInQuerySpec()
   {
      QueryGroupSpec queryGroupSpec = readBadQuerySpec("bad-field-ref.yaml");

      QuerySqlGenerator queryGenerator =
         new QuerySqlGenerator(
            dbmd,
            queryGroupSpec.getDefaultSchema(),
            new HashSet<>(queryGroupSpec.getGenerateUnqualifiedNamesForSchemas()),
            queryGroupSpec.getPropertyNameDefault().toFunctionOfFieldName()
         );
      Throwable t = assertThrows(RuntimeException.class, () ->
         queryGenerator.generateSqls(queryGroupSpec.getQuerySpecs().get(0))
      );
      String msg = t.getMessage().toLowerCase();
      assertTrue(msg.contains("[xname]"));
   }

   void rejectBadTableReferenceInQuerySpec()
   {
      QueryGroupSpec queryGroupSpec = readBadQuerySpec("bad-table-ref.yaml");
      QuerySqlGenerator queryGenerator =
         new QuerySqlGenerator(
            dbmd,
            queryGroupSpec.getDefaultSchema(),
            new HashSet<>(queryGroupSpec.getGenerateUnqualifiedNamesForSchemas()),
            queryGroupSpec.getPropertyNameDefault().toFunctionOfFieldName()
         );
      Throwable t = assertThrows(RuntimeException.class, () ->
         queryGenerator.generateSqls(queryGroupSpec.getQuerySpecs().get(0))
      );
      String msg = t.getMessage().toLowerCase();
      assertTrue(msg.contains("'xdrug'"));
   }


   void rejectBadFieldInChildInQuerySpec()
   {
      QueryGroupSpec queryGroupSpec = readBadQuerySpec("drug-with-bad-field-in-child.yaml");
      QuerySqlGenerator queryGenerator =
         new QuerySqlGenerator(
            dbmd,
            queryGroupSpec.getDefaultSchema(),
            new HashSet<>(queryGroupSpec.getGenerateUnqualifiedNamesForSchemas()),
            queryGroupSpec.getPropertyNameDefault().toFunctionOfFieldName()
         );
      Throwable t = assertThrows(RuntimeException.class, () ->
         queryGenerator.generateSqls(queryGroupSpec.getQuerySpecs().get(0))
      );
      String msg = t.getMessage().toLowerCase();
      assertTrue(msg.contains("[brand_namex]"));
   }


   void rejectBadFieldInChildsInlineParentInQuerySpec()
   {
      QueryGroupSpec queryGroupSpec = readBadQuerySpec("drug-with-bad-field-in-childs-inline-parent.yaml");
      QuerySqlGenerator queryGenerator =
         new QuerySqlGenerator(
            dbmd,
            queryGroupSpec.getDefaultSchema(),
            new HashSet<>(queryGroupSpec.getGenerateUnqualifiedNamesForSchemas()),
            queryGroupSpec.getPropertyNameDefault().toFunctionOfFieldName()
         );
      Throwable t = assertThrows(RuntimeException.class, () ->
         queryGenerator.generateSqls(queryGroupSpec.getQuerySpecs().get(0))
      );
      String msg = t.getMessage().toLowerCase();
      assertTrue(msg.contains("[namex]"));
   }

   void rejectBadParentFieldInCustomJoinInQuerySpec()
   {
      QueryGroupSpec queryGroupSpec = readBadQuerySpec("drug-with-bad-parent-field-in-custom-join.yaml");
      QuerySqlGenerator queryGenerator =
         new QuerySqlGenerator(
            dbmd,
            queryGroupSpec.getDefaultSchema(),
            new HashSet<>(queryGroupSpec.getGenerateUnqualifiedNamesForSchemas()),
            queryGroupSpec.getPropertyNameDefault().toFunctionOfFieldName()
         );
      Throwable t = assertThrows(RuntimeException.class, () ->
         queryGenerator.generateSqls(queryGroupSpec.getQuerySpecs().get(0))
      );
      String msg = t.getMessage().toLowerCase();
      assertTrue(msg.contains("[idx]"));
   }

   void rejectBadChildFieldInCustomJoinInQuerySpec()
   {
      QueryGroupSpec queryGroupSpec = readBadQuerySpec("drug-with-bad-child-field-in-custom-join.yaml");
      QuerySqlGenerator queryGenerator =
         new QuerySqlGenerator(
            dbmd,
            queryGroupSpec.getDefaultSchema(),
            new HashSet<>(queryGroupSpec.getGenerateUnqualifiedNamesForSchemas()),
            queryGroupSpec.getPropertyNameDefault().toFunctionOfFieldName()
         );
      Throwable t = assertThrows(RuntimeException.class, () ->
         queryGenerator.generateSqls(queryGroupSpec.getQuerySpecs().get(0))
      );
      String msg = t.getMessage().toLowerCase();
      assertTrue(msg.contains("[drug_idx]"));
   }
}
