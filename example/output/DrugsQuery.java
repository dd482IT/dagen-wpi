// --------------------------------------------------------------------------
// [ THIS SOURCE CODE WAS AUTO-GENERATED, ANY CHANGES MADE HERE MAY BE LOST. ]
// --------------------------------------------------------------------------
import java.util.*;
import java.math.*;
import java.time.*;
import com.fasterxml.jackson.databind.JsonNode;
// Can add more imports or other items via via "--types-file-header" option here.

public class DrugsQuery
{
   public static final String sqlResource = "drugs query(json object rows).sql";

   public static final String drugMeshIdListParam = "drugMeshIdList";

   public static final Class<Drug> principalResultClass = Drug.class;


   public static class Drug
   {
      public String name;
      public Optional<String> meshId;
      public Optional<Integer> cid;
      public Optional<Instant> registered;
      public Optional<LocalDate> marketEntryDate;
      public Optional<String> therapeuticIndications;
      public Optional<Integer> cidPlus1000;
      public List<DrugReference> references;
      public List<Brand> brands;
      public List<Advisory> advisories;
      public List<DrugFunctionalCategory> functionalCategories;
      public Analyst registeredByAnalyst;
      public Compound compound;
   }

   public static class Analyst
   {
      public long id;
      public String shortName;
   }

   public static class Compound
   {
      public Optional<String> displayName;
      public Optional<String> nctrIsisId;
      public Optional<String> cas;
      public Optional<Instant> entered;
      public Analyst enteredByAnalyst;
   }

   public static class DrugReference
   {
      public String publication;
   }

   public static class Brand
   {
      public String brandName;
      public Optional<String> manufacturer;
   }

   public static class Advisory
   {
      public String advisoryText;
      public String advisoryType;
      public String authorityName;
      public Optional<String> authorityUrl;
      public Optional<String> authorityDescription;
      public long exprYieldingTwo;
   }

   public static class DrugFunctionalCategory
   {
      public String categoryName;
      public Optional<String> description;
      public String authorityName;
      public Optional<String> authorityUrl;
      public Optional<String> authorityDescription;
   }
}
