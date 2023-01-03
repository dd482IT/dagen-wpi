package org.sqljson.query_specs;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import static org.sqljson.util.Nullables.applyIfPresent;


public final class ParentSpec
{
   private final TableJsonSpec tableJson;
   private final String referenceName;
   private final List<String> viaForeignKeyFields;
   private final CustomJoinCondition customJoinCondition;

   private ParentSpec()
   {
      this(new TableJsonSpec(), null, null, null);
   }

   public ParentSpec
      (
         TableJsonSpec tableJson,
         String referenceName,
         List<String> viaForeignKeyFields
      )
   {
      this(tableJson, referenceName, viaForeignKeyFields, null);
   }

   public ParentSpec
      (
         TableJsonSpec tableJson,
         String referenceName,
         List<String> viaForeignKeyFields,
         CustomJoinCondition customJoinCondition
      )
   {
      this.tableJson = tableJson;
      this.referenceName = referenceName;
      this.viaForeignKeyFields = viaForeignKeyFields;
      this.customJoinCondition = customJoinCondition;
   }
   public TableJsonSpec getTableJson() { return getParentTableJsonSpec(); }

   public String getReferenceName() { return referenceName; }

   public List<String> getViaForeignKeyFields() { return viaForeignKeyFields; }

   public CustomJoinCondition getCustomJoinCondition() { return customJoinCondition; }

   public TableJsonSpec getParentTableJsonSpec() { return tableJson; }

   public Set<String> getChildForeignKeyFieldsSet() { return applyIfPresent(viaForeignKeyFields, HashSet::new); }
}

