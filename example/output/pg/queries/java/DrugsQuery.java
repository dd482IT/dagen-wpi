// ---------------------------------------------------------------------------
// [ THIS SOURCE CODE WAS AUTO-GENERATED, ANY CHANGES MADE HERE MAY BE LOST. ]
// ---------------------------------------------------------------------------
import java.util.*;
import java.math.*;
import java.time.*;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.checkerframework.framework.qual.TypeUseLocation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
// Can add more imports or other items via via "--types-file-header" option here.


public class DrugsQuery
{
   public static final String sqlResource = "drugs query(json object rows).sql";

   public static final Class<Drug> principalResultClass = Drug.class;


   @SuppressWarnings("nullness") // because fields will be set directly by the deserializer not by constructor
   public static class Drug
   {
      public String name;
      public @Nullable String meshId;
      public @Nullable Integer cid;
      public @Nullable OffsetDateTime registered;
      public @Nullable LocalDate marketEntryDate;
      public @Nullable String therapeuticIndications;
      public @Nullable Integer cidPlus1000;
      public List<DrugReference> references;
      public List<Brand> brands;
      public List<Advisory> advisories;
      public List<DrugFunctionalCategory> functionalCategories;
      public Analyst registeredByAnalyst;
      public Compound compound;
   }

   @SuppressWarnings("nullness") // because fields will be set directly by the deserializer not by constructor
   public static class Analyst
   {
      public long id;
      public String shortName;
   }

   @SuppressWarnings("nullness") // because fields will be set directly by the deserializer not by constructor
   public static class Compound
   {
      public @Nullable String displayName;
      public @Nullable String nctrIsisId;
      public @Nullable String cas;
      public @Nullable OffsetDateTime entered;
      public Analyst enteredByAnalyst;
   }

   @SuppressWarnings("nullness") // because fields will be set directly by the deserializer not by constructor
   public static class DrugReference
   {
      public String publication;
   }

   @SuppressWarnings("nullness") // because fields will be set directly by the deserializer not by constructor
   public static class Brand
   {
      public String brandName;
      public @Nullable String manufacturer;
   }

   @SuppressWarnings("nullness") // because fields will be set directly by the deserializer not by constructor
   public static class Advisory
   {
      public String advisoryText;
      public String advisoryType;
      public String authorityName;
      public @Nullable String authorityUrl;
      public @Nullable String authorityDescription;
      public long exprYieldingTwo;
   }

   @SuppressWarnings("nullness") // because fields will be set directly by the deserializer not by constructor
   public static class DrugFunctionalCategory
   {
      public String categoryName;
      public @Nullable String description;
      public String authorityName;
      public @Nullable String authorityUrl;
      public @Nullable String authorityDescription;
   }
}
