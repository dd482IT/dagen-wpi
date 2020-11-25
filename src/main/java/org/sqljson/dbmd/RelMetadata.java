package org.sqljson.dbmd;

import java.util.*;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"relationId", "relationType", "fields"})
public class RelMetadata
{
   private final RelId relationId;

   private final RelType relationType;

   private final List<Field> fields;

   public enum RelType { Table, View, Unknown }


   public RelMetadata
      (
         RelId relationId,
         RelType relationType,
         List<Field> fields
      )
   {
      this.relationId = requireNonNull(relationId);
      this.relationType = requireNonNull(relationType);
      this.fields = unmodifiableList(new ArrayList<>(requireNonNull(fields)));
   }

   RelMetadata()
   {
      this.relationId = RelId.DUMMY_INSTANCE;
      this.relationType = RelType.Table;
      this.fields = emptyList();
   }

   public RelId getRelationId() { return relationId; }

   public RelType getRelationType() { return relationType; }

   public List<Field> getFields() { return fields; }

   @JsonIgnore()
   public List<Field> getPrimaryKeyFields()
   {
      List<Field> pks = new ArrayList<>();

      for ( Field f: fields )
      {
         if ( f.getPrimaryKeyPartNumber() != null )
            pks.add(f);
      }

      pks.sort(Comparator.comparingInt(f -> {
         @Nullable Integer pn = f.getPrimaryKeyPartNumber();
         return pn != null ? pn : 0;
      }));

      return pks;
   }

   @JsonIgnore()
   public List<String> getPrimaryKeyFieldNames()
   {
      return getPrimaryKeyFieldNames(null);
   }

   public List<String> getPrimaryKeyFieldNames(@Nullable String alias)
   {
      return
         getPrimaryKeyFields().stream()
         .map(f -> alias != null ? alias + "." + f.getName() : f.getName())
         .collect(toList());
   }
}
