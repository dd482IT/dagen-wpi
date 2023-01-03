package org.sqljson.query_specs;

import java.util.*;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import static org.sqljson.util.Nullables.applyIfPresent;


public final class ChildCollectionSpec
{
   private final String collectionName;
   private final TableJsonSpec tableJson;
   private final List<String> foreignKeyFields;
   private final CustomJoinCondition customJoinCondition;
   private final String filter;
   private final Boolean unwrap;
   private final String orderBy;

   private ChildCollectionSpec()
   {
      this.collectionName = "";
      this.tableJson = new TableJsonSpec();
      this.foreignKeyFields = null;
      this.customJoinCondition = null;
      this.filter = null;
      this.unwrap = false;
      this.orderBy = null;
   }

   public ChildCollectionSpec
      (
         String collectionName,
         TableJsonSpec tableJson,
         List<String> fkFields,
         String filter,
         Boolean unwrap,
         String orderBy
      )
   {
      this.collectionName = collectionName;
      this.tableJson = tableJson;
      this.foreignKeyFields = applyIfPresent(fkFields, Collections::unmodifiableList);
      this.customJoinCondition = null;
      this.filter = filter;
      this.unwrap = unwrap;
      this.orderBy = orderBy;
   }

   public ChildCollectionSpec
      (
         String collectionName,
         TableJsonSpec tableJson,
         CustomJoinCondition customJoinCondition,
         String filter,
         Boolean unwrap,
         String orderBy
      )
   {
      this.collectionName = collectionName;
      this.tableJson = tableJson;
      this.foreignKeyFields = null;
      this.customJoinCondition = customJoinCondition;
      this.filter = filter;
      this.unwrap = unwrap;
      this.orderBy = orderBy;
   }

   public String getCollectionName() { return collectionName; }

   public TableJsonSpec getTableJson() { return tableJson; }

   public List<String> getForeignKeyFields() { return foreignKeyFields; }

   public CustomJoinCondition getCustomJoinCondition() { return customJoinCondition; }

   public Set<String> getForeignKeyFieldsSet()
   {
      return applyIfPresent(foreignKeyFields, HashSet::new);
   }

   public String getFilter() { return filter; }

   public Boolean getUnwrap() { return unwrap; }

   public String getOrderBy() { return orderBy; }
}
