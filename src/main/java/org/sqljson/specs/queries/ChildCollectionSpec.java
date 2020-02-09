package org.sqljson.specs.queries;

import java.util.*;

import org.checkerframework.checker.nullness.qual.Nullable;
import com.fasterxml.jackson.annotation.JsonIgnore;

import static org.sqljson.util.Nullables.applyIfPresent;


public final class ChildCollectionSpec
{
   private String collectionName;
   private TableJsonSpec tableJson;
   private @Nullable List<String> foreignKeyFields = null;
   private @Nullable String filter = null;
   private boolean unwrap = false;

   private ChildCollectionSpec()
   {
      this.collectionName = "";
      this.tableJson = new TableJsonSpec();
   }

   public ChildCollectionSpec
   (
      String collectionName,
      TableJsonSpec tableJson,
      @Nullable List<String> fkFields,
      @Nullable String filter,
      boolean unwrap
   )
   {
      this.collectionName = collectionName;
      this.tableJson = tableJson;
      this.foreignKeyFields = applyIfPresent(fkFields, Collections::unmodifiableList);
      this.filter = filter;
      this.unwrap = unwrap;
   }

   public String getCollectionName() { return collectionName; }

   public TableJsonSpec getTableJson() { return tableJson; }

   public @Nullable List<String> getForeignKeyFields() { return foreignKeyFields; }

   @JsonIgnore
   public @Nullable Set<String> getForeignKeyFieldsSet() { return applyIfPresent(foreignKeyFields, HashSet::new); }

   public @Nullable String getFilter() { return filter; }

   public boolean getUnwrap() { return unwrap; }
}
