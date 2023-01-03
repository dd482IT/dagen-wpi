package org.sqljson.query_specs;

import java.util.*;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;


public final class TableJsonSpec
{
   private final String table; // possibly qualified

   private final List<TableFieldExpr> fieldExpressions;

   private final List<ParentSpec> parentTables;

   private final List<ChildCollectionSpec> childTableCollections;

   private RecordCondition recordCondition = null;

   TableJsonSpec()
   {
      this("", null, null, null, null);
   }

   public TableJsonSpec
      (
         String table,
         List<TableFieldExpr> fieldExpressions,
         List<ParentSpec> parentTables,
         List<ChildCollectionSpec> childTableCollections,
         RecordCondition recordCondition
      )
   {
      requireNonNull(table);

      this.table = table;
      this.fieldExpressions = fieldExpressions != null ? unmodifiableList(fieldExpressions) : null;
      this.parentTables = parentTables != null ? unmodifiableList(new ArrayList<>(parentTables)) : null;
      this.childTableCollections = childTableCollections != null ? unmodifiableList(new ArrayList<>(childTableCollections)): null;
      this.recordCondition = recordCondition;
   }

   /// The table name, possibly schema-qualified, of this output specification.
   public String getTable() { return table; }

   /// The output fields which originate from fields of this table.
   public List<TableFieldExpr> getFieldExpressions() { return fieldExpressions; }

   public List<ParentSpec> getParentTables() { return parentTables; }

   public List<ChildCollectionSpec> getChildTableCollections()
   {
      return childTableCollections;
   }

   public RecordCondition getRecordCondition() { return recordCondition; }


   public List<TableFieldExpr> getFieldExpressionsList()
   {
      return fieldExpressions != null ? fieldExpressions : emptyList();
   }

   public List<ParentSpec> getParentTablesList()
   {
      return parentTables != null ? parentTables : emptyList();
   }

   public List<ParentSpec> getReferencedParentTablesList()
   {
      return parentTables != null ?
         parentTables.stream().filter(t -> t.getReferenceName() != null).collect(toList())
         : emptyList();
   }

   public List<ParentSpec> getInlineParentTablesList()
   {
      return parentTables != null ?
         parentTables.stream().filter(t -> t.getReferenceName() == null).collect(toList())
         : emptyList();
   }

   public List<ChildCollectionSpec> getChildTableCollectionsList()
   {
      return childTableCollections != null ? childTableCollections : emptyList();
   }

   public boolean hasCondition()
   {
      return recordCondition != null;
   }

   public int getJsonPropertiesCount()
   {
       return
          (fieldExpressions != null ? fieldExpressions.size(): 0) +
          (childTableCollections != null ? childTableCollections.size(): 0) +
          getReferencedParentTablesList().size() +
          getInlineParentTablesList().stream()
             .mapToInt(ip -> ip.getParentTableJsonSpec().getJsonPropertiesCount())
             .sum();
   }
}

